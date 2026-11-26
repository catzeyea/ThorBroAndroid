package dev.catzeyea.thorbro.android.files

import java.io.File

/**
 * Game libraries: an ES-DE `ROMs` folder on a storage. Same rules as the PC app (`library.rs`):
 *
 * - Every storage root is checked for a `ROMs` folder. It counts as library once it has at least
 *   one sub folder.
 * - A system folder's extensions come from its `systeminfo.txt` ("Full system name" and
 *   "Supported file extensions", value on the next line). Folders without one are matched by name
 *   against [KnownSystems]. (The PC also knows systems the user picked by hand; those live in the
 *   PC settings and are not available on the Thor.)
 * - The library with the most games is the main library (index 0).
 */
data class SystemFolder(
    /** Canonical system id ("wii" for a folder called "Wii"); empty if unknown. */
    val id: String,
    /** "systeminfo", "name" or "" (unknown). */
    val origin: String,
    val full: String,
    val exts: Set<String>,
    /** Number of entries in the folder, without systeminfo.txt. */
    val count: Int,
)

data class GameLibrary(
    val path: File,
    val label: String,
    /** Folder name -> system, sorted by folder name. */
    val systems: Map<String, SystemFolder>,
) {
    val total: Int get() = systems.values.sumOf { it.count }

    /** The folder that holds the system [id]. A folder named exactly like the id wins. */
    fun folderFor(id: String): String? {
        systems[id]?.let { if (it.id == id) return id }
        return systems.entries.firstOrNull { it.value.id == id }?.key
    }

    /** Display name of a system folder, or the folder name itself. */
    fun full(folder: String): String = systems[folder]?.full?.takeIf { it.isNotEmpty() } ?: folder
}

/** A storage root as the UI shows it, e.g. ("Internal storage", /storage/emulated/0). */
data class StorageRoot(val label: String, val root: File, val removable: Boolean)

object Libraries {

    const val ROMS = "ROMs"
    const val SYSTEM_INFO = "systeminfo.txt"

    /** All libraries on [storages], the one with the most games first. */
    fun find(storages: List<StorageRoot>): List<GameLibrary> =
        storages.mapNotNull { storage -> read(File(storage.root, ROMS), storage.label) }
            .sortedByDescending { it.total }

    /** Reads one `ROMs` folder. Null if it doesn't exist or has no sub folders. */
    fun read(romsDir: File, label: String): GameLibrary? {
        val folders = romsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: return null
        if (folders.isEmpty()) return null
        val systems = LinkedHashMap<String, SystemFolder>()
        for (folder in folders) {
            val names = folder.list()?.toList() ?: emptyList()
            val count = names.count { it != SYSTEM_INFO }
            val info = File(folder, SYSTEM_INFO).takeIf { SYSTEM_INFO in names }?.let { runCatching { it.readText() }.getOrNull() }
            systems[folder.name] = resolve(folder.name, info?.let(::parseSystemInfo), count)
        }
        return GameLibrary(romsDir, label, systems)
    }

    /** "Full system name" and "Supported file extensions" from a systeminfo.txt (value on the next line). */
    fun parseSystemInfo(text: String): Pair<String, Set<String>> {
        val lines = text.lines()
        var full = ""
        var exts = emptySet<String>()
        for ((index, line) in lines.withIndex()) {
            val value = lines.getOrNull(index + 1)?.trim() ?: continue
            when {
                line.startsWith("Full system name") -> full = value
                line.startsWith("Supported file ext") -> exts = value.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.lowercase() }.toSet()
            }
        }
        return full to exts
    }

    private fun resolve(folder: String, info: Pair<String, Set<String>>?, count: Int): SystemFolder {
        if (info != null && (info.first.isNotEmpty() || info.second.isNotEmpty())) {
            return SystemFolder(folder, "systeminfo", info.first, info.second, count)
        }
        val known = KnownSystems.byFolderName(folder) ?: return SystemFolder("", "", "", emptySet(), count)
        return SystemFolder(known.id, "name", known.full, known.exts, count)
    }
}
