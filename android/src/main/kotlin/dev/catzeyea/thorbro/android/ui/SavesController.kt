package dev.catzeyea.thorbro.android.ui

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.catzeyea.thorbro.android.device.Storages
import dev.catzeyea.thorbro.android.files.Libraries
import dev.catzeyea.thorbro.android.saves.SaveEntry
import dev.catzeyea.thorbro.android.saves.SwitchSaves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** State of the Saves tab: Switch saves of the installed emulators, Back up and Restore latest. */
class SavesController(private val context: Context, private val scope: CoroutineScope, private val notify: (String, Boolean) -> Unit) {

    private val internal: File = Environment.getExternalStorageDirectory()
    private val androidData = File(internal, "Android/data")
    val backupRoot = File(internal, "ThorBro/SaveBackups")
    private val saves = SwitchSaves(backupRoot)

    var list by mutableStateOf<List<SaveEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** Message when nothing can be listed (e.g. Android/data not readable). */
    var problem by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf(0)
    /** A backup or restore is running. */
    var busy by mutableStateOf(false)
        private set

    val current: SaveEntry? get() = list.getOrNull(selected)

    fun key(entry: SaveEntry) = "${entry.emulator.packageName}/${entry.titleId}"

    fun byKey(key: String) = list.firstOrNull { key(it) == key }

    fun refresh() {
        loading = true
        scope.launch {
            val (found, message) = withContext(Dispatchers.IO) {
                if (androidData.list() == null) {
                    emptyList<SaveEntry>() to "Can't read the emulators' folders (Android/data) on this device."
                } else {
                    val names = gameNames()
                    val result = saves.find(androidData, names)
                    result to if (result.isEmpty()) "No Switch saves found (Eden, Eden Duo, Citron, Sudachi, yuzu)." else null
                }
            }
            list = found
            problem = message
            selected = selected.coerceIn(0, (found.size - 1).coerceAtLeast(0))
            loading = false
        }
    }

    /** Game names from the Switch folders of all libraries (file names carry the title ID). */
    private fun gameNames(): Map<String, String> {
        val names = mutableListOf<String>()
        for (library in Libraries.find(Storages.list(context))) {
            val folder = library.folderFor("switch") ?: continue
            File(library.path, folder).list()?.let { names += it }
        }
        return SwitchSaves.namesFromFileNames(names)
    }

    fun backUp(entry: SaveEntry) = runBusy("Backed up ${entry.name}.") { saves.backUp(entry) }

    fun restore(entry: SaveEntry) = runBusy("Restored ${entry.name}. The save before it is in the backups too.") { saves.restoreLatest(entry) }

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
