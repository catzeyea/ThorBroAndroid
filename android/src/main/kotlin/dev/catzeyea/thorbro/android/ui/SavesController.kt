package dev.catzeyea.thorbro.android.ui

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.catzeyea.thorbro.android.art.ArtIndex
import dev.catzeyea.thorbro.android.art.CoverLoader
import dev.catzeyea.thorbro.android.device.Storages
import dev.catzeyea.thorbro.android.files.Classifier
import dev.catzeyea.thorbro.android.files.Libraries
import dev.catzeyea.thorbro.android.files.StorageRoot
import dev.catzeyea.thorbro.android.saves.Backup
import dev.catzeyea.thorbro.android.saves.Emulator
import dev.catzeyea.thorbro.android.saves.SaveEntry
import dev.catzeyea.thorbro.android.saves.SwitchSaves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A cover file and its cache key (worked out off the main thread: the key reads file times). */
data class CoverRef(val file: File, val key: String)

/** The saves of one emulator, for one section of the Saves tab. */
data class EmulatorSaves(val emulator: Emulator, val saves: List<SaveEntry>) {
    /** "x of y backed up": saves with at least one backup. */
    val backedUp: Int get() = saves.count { it.backups.isNotEmpty() }
}

/** State of the Saves tab: saves grouped by emulator, covers, Back up and Restore. */
class SavesController(private val context: Context, private val scope: CoroutineScope, private val notify: (String, Boolean) -> Unit) {

    private val internal: File = Environment.getExternalStorageDirectory()
    private val androidData = File(internal, "Android/data")
    val backupRoot = File(internal, "ThorBro/SaveBackups")
    private val saves = SwitchSaves(backupRoot)
    val covers = CoverLoader(context.cacheDir)

    var groups by mutableStateOf<List<EmulatorSaves>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** Message when nothing can be listed (e.g. Android/data not readable). */
    var problem by mutableStateOf<String?>(null)
        private set
    /** Fallbacks used while looking for saves (e.g. profiles.dat not readable). */
    var notes by mutableStateOf<List<String>>(emptyList())
        private set
    /** Cover per save key (missing = no art: initials). */
    var coverFiles by mutableStateOf<Map<String, CoverRef>>(emptyMap())
        private set
    /** The tile that gets focus when the tab is shown (the last one that had it). */
    var focusedKey: String? = null
    /** A backup or restore is running. */
    var busy by mutableStateOf(false)
        private set

    private var art: ArtIndex? = null

    val all: List<SaveEntry> get() = groups.flatMap { it.saves }

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    fun refresh() {
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { load() }
            groups = result.groups
            problem = result.problem
            notes = result.notes
            coverFiles = result.covers
            loading = false
        }
    }

    private data class Loaded(val groups: List<EmulatorSaves>, val problem: String?, val notes: List<String>, val covers: Map<String, CoverRef>)

    private fun load(): Loaded {
        if (androidData.list() == null) return Loaded(emptyList(), "Can't read the emulators' folders (Android/data) on this device.", emptyList(), emptyMap())
        val storages = Storages.list(context)
        // iiSU's art: walked once per app start (about 0.5 s on the Thor), then kept.
        val index = art ?: ArtIndex.build(storages.map { it.root }).also { art = it }
        val romNames = romFileNames(storages)
        val names = index.namesByTitleId + SwitchSaves.namesFromFileNames(romNames.values.toList())
        val scan = saves.find(androidData, names)
        val groups = scan.saves.groupBy { it.emulator }.map { (emulator, list) -> EmulatorSaves(emulator, list) }
        val covers = scan.saves.mapNotNull { save ->
            index.find(listOf("switch"), romNames[save.titleId], save.titleId)?.let { save.key to CoverRef(it, this.covers.key(it)) }
        }.toMap()
        return Loaded(groups, null, scan.notes, covers)
    }

    /** ROM file names in the Switch folders of all libraries, by base title ID (base game files only). */
    private fun romFileNames(storages: List<StorageRoot>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (library in Libraries.find(storages)) {
            val folder = library.folderFor("switch") ?: continue
            for (name in File(library.path, folder).list().orEmpty()) {
                val id = Classifier.switchId(name) ?: continue
                val (base, rank) = SwitchSaves.baseTitleId(id, name) ?: continue
                if (rank == 0) result.putIfAbsent(base, name)
            }
        }
        return result
    }

    fun backUp(entry: SaveEntry) = runBusy("Backed up ${entry.name}.") { saves.backUp(entry) }

    fun restore(entry: SaveEntry, backup: Backup) =
        runBusy("Restored ${entry.name}. The save before it is in the backups too.") { saves.restore(entry, backup) }

    private fun runBusy(success: String, work: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val error = withContext(Dispatchers.IO) { runCatching(work).exceptionOrNull() }
            busy = false
            if (error == null) notify(success, false) else notify(error.message ?: "Failed", true)
            refresh()
        }
    }
}
