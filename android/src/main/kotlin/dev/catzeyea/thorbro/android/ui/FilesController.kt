package dev.catzeyea.thorbro.android.ui

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.catzeyea.thorbro.android.device.Storages
import dev.catzeyea.thorbro.android.files.FileEntry
import dev.catzeyea.thorbro.android.files.FileMover
import dev.catzeyea.thorbro.android.files.FileNames
import dev.catzeyea.thorbro.android.files.FolderListing
import dev.catzeyea.thorbro.android.files.FolderPrefs
import dev.catzeyea.thorbro.android.files.FolderPrefsStore
import dev.catzeyea.thorbro.android.files.Libraries
import dev.catzeyea.thorbro.android.files.MoveLog
import dev.catzeyea.thorbro.android.files.MoveOutcome
import dev.catzeyea.thorbro.android.files.ScanPlanner
import dev.catzeyea.thorbro.android.files.ScanResult
import dev.catzeyea.thorbro.android.files.SortOrder
import dev.catzeyea.thorbro.android.files.StorageRoot
import dev.catzeyea.thorbro.android.files.Undo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** A storage or pinned folder in the left column of Files. */
data class Place(val label: String, val path: File, val detail: String, val isStorage: Boolean)

/** The actions menu (Select): [target] is the folder it works on, [fromPlace] = opened on the left bar. */
data class ActionsMenu(val target: File, val fromPlace: Boolean)

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val folder: File) : ScanState
    /**
     * [selected]: the files switched on (only these move). [otherStorage]: files whose target is on
     * another storage (they are copied, checked and then deleted).
     */
    data class Preview(val result: ScanResult, val selected: Set<File>, val otherStorage: Set<File>) : ScanState {
        val selectedItems get() = result.toMove.filter { it.file in selected }
        val copies get() = selected.count { it in otherStorage }
    }
    data class Moving(val done: Int, val total: Int, val current: String, val copying: Boolean, val bytes: Long, val bytesTotal: Long, val stopping: Boolean) : ScanState
    data class Done(val moved: Int, val total: Int, val problems: List<String>, val stopped: Boolean, val log: MoveLog?) : ScanState
    data object Undoing : ScanState
    data class Undone(val restored: Int, val problems: List<String>) : ScanState
}

/**
 * State of the Files tab: places, the open folder, pins and "Scan this folder" (preview, move,
 * undo). All file system work runs on Dispatchers.IO.
 */
class FilesController(private val context: Context, private val scope: CoroutineScope, private val notify: (String, Boolean) -> Unit) {

    private val internal: File = Environment.getExternalStorageDirectory()
    private val download = File(internal, Environment.DIRECTORY_DOWNLOADS)
    private val prefsStore = FolderPrefsStore(File(context.filesDir, "folders.json"), download)
    val logDir = File(internal, "ThorBro/logs")

    var storages by mutableStateOf<List<StorageRoot>>(emptyList())
        private set
    var places by mutableStateOf<List<Place>>(emptyList())
        private set
    var prefs by mutableStateOf(FolderPrefs.defaults(download))
        private set
    var dir by mutableStateOf<File?>(null)
        private set
    /** The open folder as listed (unsorted, unfiltered). */
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var sortOrder by mutableStateOf(SortOrder())
        private set
    /** Name filter of the open folder ("" = off). Cleared when another folder is opened. */
    var query by mutableStateOf("")
        private set
    /** The search field is shown. */
    var searchOpen by mutableStateOf(false)
        private set
    /** What the list shows: [entries] filtered by [query] and sorted. */
    val shown: List<FileEntry> by derivedStateOf { FolderListing.sort(FolderListing.filter(entries, query), sortOrder) }
    /** Bumped when a folder was opened and listed (not when counts arrive), so the list takes focus once. */
    var listToken by mutableIntStateOf(0)
        private set
    /** Items in the Trash on all storages. */
    var trashCount by mutableIntStateOf(0)
        private set
    /** The actions menu (Select), or null. */
    var actions by mutableStateOf<ActionsMenu?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var listError by mutableStateOf<String?>(null)
        private set
    /** Index of the row that gets focus when the list is shown (restored when going back up). */
    var focusIndex by mutableStateOf(0)
    /** Folder under the focus (a folder row or a place), for Select. Null = the open folder. */
    var focusedFolder: File? = null
    var scan by mutableStateOf<ScanState>(ScanState.Idle)
        private set

    private companion object {
        const val PROGRESS_STEP = 8L shl 20
        const val KEY_SCAN_INFO_HIDDEN = "scan_info_hidden"
        const val KEY_SORT_DESCENDING = "sort_descending"
        /** Per-storage Trash folder (filled from a later step on; only counted here). */
        const val TRASH_FILES = "ThorBro/trash/files"
    }

    private val settings = context.getSharedPreferences("files", Context.MODE_PRIVATE)

    init {
        sortOrder = SortOrder(descending = settings.getBoolean(KEY_SORT_DESCENDING, false))
    }

    /** "Don't show this again" on the Scan info card (remembered). */
    var scanInfoHidden by mutableStateOf(settings.getBoolean(KEY_SCAN_INFO_HIDDEN, false))
        private set

    fun rememberScanInfoHidden(hidden: Boolean) {
        scanInfoHidden = hidden
        settings.edit().putBoolean(KEY_SCAN_INFO_HIDDEN, hidden).apply()
    }

    private val focusMemory = mutableMapOf<String, Int>()
    private val stop = AtomicBoolean(false)
    private var started = false
    private var countJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { prefsStore.load() }
            prefs = loaded
            refreshPlaces()
            open(File(loaded.defaultScan).takeIf { it.isDirectory } ?: download)
        }
    }

    fun refreshPlaces() {
        scope.launch {
            val found = withContext(Dispatchers.IO) { Storages.list(context) }
            storages = found
            places = withContext(Dispatchers.IO) { buildPlaces(found, prefs) }
            trashCount = withContext(Dispatchers.IO) { found.sumOf { File(it.root, TRASH_FILES).list()?.size ?: 0 } }
        }
    }

    private fun buildPlaces(found: List<StorageRoot>, prefs: FolderPrefs): List<Place> {
        val storagePlaces = found.map { root ->
            val free = runCatching { StatFs(root.root.path).availableBytes }.getOrNull()
            Place(root.label, root.root, free?.let { "${FileNames.human(it)} free" } ?: "", isStorage = true)
        }
        val pins = prefs.pins.map(::File).map { path ->
            Place(pinLabel(path, found), path, if (path.path == prefs.defaultScan) "default scan" else "", isStorage = false)
        }
        return storagePlaces + pins
    }

    /** "Download", "ROMs / switch": the path below its storage, at most the last two parts. */
    fun pinLabel(path: File, found: List<StorageRoot> = storages): String {
        val root = Storages.rootOf(path, found)?.root ?: return path.name
        val parts = path.relativeTo(root).path.split('/').filter { it.isNotEmpty() }
        return if (parts.isEmpty()) path.name else parts.takeLast(2).joinToString(" / ")
    }

    /** "Internal storage / Download". */
    fun breadcrumb(path: File): String {
        val root = Storages.rootOf(path, storages) ?: return path.path
        val parts = path.relativeTo(root.root).path.split('/').filter { it.isNotEmpty() }
        return (listOf(root.label) + parts).joinToString(" / ")
    }

    /** Opens [folder]. [focusName]: the entry that gets focus (the folder we came from). */
    fun open(folder: File, focusName: String? = null) {
        dir?.let { focusMemory[it.path] = focusIndex }
        val sameFolder = dir == folder
        dir = folder
        loading = true
        if (!sameFolder) {
            query = ""
            searchOpen = false
        }
        countJob?.cancel()
        scope.launch {
            val listed = withContext(Dispatchers.IO) { FolderListing.read(folder) }
            if (dir != folder) return@launch
            loading = false
            if (listed == null) {
                entries = emptyList()
                listError = "This folder can't be opened."
            } else {
                entries = listed
                listError = null
            }
            val list = shown
            val byName = focusName?.let { name -> list.indexOfFirst { it.name == name } }?.takeIf { it >= 0 }
            focusIndex = (byName ?: focusMemory[folder.path] ?: 0).coerceIn(0, (list.size - 1).coerceAtLeast(0))
            listToken++
            if (listed != null && listed.any { it.isDir }) countChildren(folder, listed)
        }
    }

    /** Second pass: item counts of the sub folders, applied in one update (one recomposition). */
    private fun countChildren(folder: File, listed: List<FileEntry>) {
        countJob = scope.launch {
            val counted = withContext(Dispatchers.IO) {
                listed.map { e ->
                    ensureActive()
                    if (e.isDir) e.copy(childCount = FolderListing.countChildren(e.file)) else e
                }
            }
            if (dir == folder && entries === listed) entries = counted
        }
    }

    /** Name order, A to Z or Z to A (folders stay first). Remembered. */
    fun toggleSortDirection() {
        sortOrder = sortOrder.copy(descending = !sortOrder.descending)
        settings.edit().putBoolean(KEY_SORT_DESCENDING, sortOrder.descending).apply()
        focusIndex = 0
    }

    fun openSearch() {
        searchOpen = true
    }

    /** Hides the search field; the filter stays until [clearSearch] or another folder. */
    fun closeSearch() {
        searchOpen = false
    }

    fun updateQuery(text: String) {
        query = text
        focusIndex = 0
    }

    fun clearSearch() {
        query = ""
        searchOpen = false
        focusIndex = 0
    }

    // --- Actions menu (Select) ---------------------------------------------------------------------

    /** Opens the actions menu for the focused folder (row or left bar), else for the open folder. */
    fun openActions(fromPlace: Boolean) {
        val target = focusedFolder ?: dir ?: return
        actions = ActionsMenu(target, fromPlace)
    }

    fun closeActions() {
        actions = null
    }

    fun refresh() = dir?.let { open(it, shown.getOrNull(focusIndex)?.name) }

    /** B: one folder up. False at a storage root (nothing above it here). */
    fun up(): Boolean {
        val current = dir ?: return false
        if (storages.any { it.root == current }) return false
        val parent = current.parentFile ?: return false
        if (Storages.rootOf(parent, storages) == null) return false
        open(parent, focusName = current.name)
        return true
    }

    fun togglePin(path: File) = updatePrefs(prefs.togglePin(path.path))

    fun setDefaultScan(path: File) = updatePrefs(prefs.withDefaultScan(path.path))

    private fun updatePrefs(next: FolderPrefs) {
        prefs = next
        scope.launch {
            withContext(Dispatchers.IO) { prefsStore.save(next) }
            places = withContext(Dispatchers.IO) { buildPlaces(storages, next) }
        }
    }

    // --- Scan ----------------------------------------------------------------------------------------

    /** Scans [folder] (the folder the actions menu was opened for). */
    fun startScan(folder: File) {
        scan = ScanState.Scanning(folder)
        scope.launch {
            val preview = withContext(Dispatchers.IO) {
                val found = Storages.list(context)
                val library = Libraries.find(found).firstOrNull()
                val result = ScanPlanner.plan(folder, library)
                val otherStorage = result.toMove.filter { !Storages.sameStorage(it.file, it.target!!, found) }.map { it.file }.toSet()
                ScanState.Preview(result, result.defaultSelection, otherStorage)
            }
            if (scan is ScanState.Scanning) scan = preview
        }
    }

    fun confirmMove() {
        val preview = scan as? ScanState.Preview ?: return
        val items = preview.selectedItems
        if (items.isEmpty()) return
        stop.set(false)
        scope.launch {
            val found = storages.ifEmpty { withContext(Dispatchers.IO) { Storages.list(context) } }
            val mover = FileMover { a, b -> Storages.sameStorage(a, b, found) }
            val now = System.currentTimeMillis()
            val log = MoveLog.create(logDir, now)
            val problems = mutableListOf<String>()
            var moved = 0
            var logStarted = false
            var stopped = false
            for ((index, item) in items.withIndex()) {
                if (stop.get()) {
                    stopped = true
                    break
                }
                val target = item.target ?: continue
                val copying = !Storages.sameStorage(item.file, target, found)
                scan = ScanState.Moving(index, items.size, item.file.name, copying, 0, item.size, stop.get())
                var reported = 0L
                val outcome = withContext(Dispatchers.IO) {
                    if (!logStarted) {
                        log.begin(preview.result.folder, now)
                        logStarted = true
                    }
                    mover.move(item.file, target, item.size, item.modified) { bytes ->
                        // Progress for copies, at most every PROGRESS_STEP bytes.
                        if (copying && bytes - reported >= PROGRESS_STEP) {
                            reported = bytes
                            scope.launch { (scan as? ScanState.Moving)?.let { scan = it.copy(bytes = bytes, stopping = stop.get()) } }
                        }
                    }.also { result ->
                        if (result is MoveOutcome.Moved) log.moved(MoveLog.Entry(result.from, result.to, item.size, result.copied))
                    }
                }
                when (outcome) {
                    is MoveOutcome.Moved -> moved++
                    is MoveOutcome.Skipped -> problems += "${item.file.name}: ${outcome.reason}"
                    is MoveOutcome.Failed -> problems += "${item.file.name}: ${outcome.reason}"
                }
            }
            scan = ScanState.Done(moved, items.size, problems, stopped, log.takeIf { moved > 0 })
            refresh()
            refreshPlaces()
        }
    }

    /** Switches one file of the preview on or off. */
    fun toggle(file: File) {
        val preview = scan as? ScanState.Preview ?: return
        if (preview.result.toMove.none { it.file == file }) return
        scan = preview.copy(selected = if (file in preview.selected) preview.selected - file else preview.selected + file)
    }

    /** Focus is on a file row of the preview (A switches it), not on the Move button. For the hint bar. */
    var previewFocusOnRow by mutableStateOf(false)

    /** B while moving: stops after the current file. */
    fun stopMove() {
        stop.set(true)
        (scan as? ScanState.Moving)?.let { scan = it.copy(stopping = true) }
    }

    fun undo() {
        val done = scan as? ScanState.Done ?: return
        val log = done.log ?: return
        scan = ScanState.Undoing
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val found = Storages.list(context)
                Undo.run(log, FileMover { a, b -> Storages.sameStorage(a, b, found) })
            }
            scan = ScanState.Undone(result.restored, result.problems)
            refresh()
            refreshPlaces()
        }
    }

    fun closeScan() {
        scan = ScanState.Idle
    }

}
