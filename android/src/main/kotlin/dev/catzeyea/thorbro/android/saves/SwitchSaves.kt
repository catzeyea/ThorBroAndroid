package dev.catzeyea.thorbro.android.saves

import dev.catzeyea.thorbro.android.files.FileNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A Switch emulator with the yuzu folder layout (`files/nand/user/save/<a>/<user>/<title id>`). */
data class Emulator(val packageName: String, val label: String)

/** One game's save folder in one emulator. */
data class SaveEntry(
    val titleId: String,
    val name: String,
    val emulator: Emulator,
    val dir: File,
    val size: Long,
    /** Newest backup time (ms), or null if never backed up. */
    val lastBackup: Long?,
)

/**
 * Switch saves on the Thor: list, back up, restore. No sync with the PC, no import.
 *
 * The saves live in other apps' private folders (`Android/data/<emulator>`). This works on the Thor
 * firmware because every app may read and write there; on stock Android it would not.
 *
 * Backups: `ThorBro/SaveBackups/<game>/<yyyy-MM-dd_HH-mm-ss>/` with the save in `save/` and a
 * `backup.json` that says where it came from.
 */
class SwitchSaves(private val backupRoot: File) {

    companion object {
        /** yuzu-layout emulators. Only those that are installed show up. */
        val EMULATORS = listOf(
            Emulator("dev.eden.eden_emulator", "Eden"),
            Emulator("dev.igawa6.edenduo", "Eden Duo"),
            Emulator("org.citron.citron_emu", "Citron"),
            Emulator("org.sudachi.sudachi_emu", "Sudachi"),
            Emulator("org.yuzu.yuzu_emu", "yuzu"),
        )
        private val TITLE_ID = Regex("^0100[0-9A-Fa-f]{12}$")
        private val NAME_TITLE_ID = Regex("""[\[(_](0100[0-9A-Fa-f]{12})[\])_]""")
        const val META = "backup.json"
        const val SAVE_DIR = "save"
        const val NEW_SUFFIX = ".thorbro-new"
        const val OLD_SUFFIX = ".thorbro-old"

        fun saveRoot(androidData: File, emulator: Emulator) = File(androidData, "${emulator.packageName}/files/nand/user/save")

        /**
         * Game names by base title ID, from file names like `Dead Cells [0100646009FBE000][v0].xci`
         * (the text before the first bracket). A base game file wins; without one, the name of an
         * update (…800 → …000) or a DLC (base = DLC ID without the low 12 bits, minus 0x1000) is used.
         */
        fun namesFromFileNames(names: List<String>): Map<String, String> {
            val best = mutableMapOf<String, Pair<Int, String>>()
            for (name in names) {
                val match = NAME_TITLE_ID.find(name) ?: continue
                val id = match.groupValues[1].uppercase()
                val (base, rank) = baseTitleId(id) ?: continue
                val title = name.substring(0, match.range.first).substringBefore('[').substringBefore('(').trim()
                if (title.isEmpty()) continue
                val current = best[base]
                if (current == null || rank < current.first) best[base] = rank to title
            }
            return best.mapValues { it.value.second }
        }

        /** (base game title ID, 0 = game / 1 = update / 2 = DLC). */
        fun baseTitleId(id: String): Pair<String, Int>? {
            val value = id.toLongOrNull(16) ?: return null
            return when {
                id.endsWith("000") -> id to 0
                id.endsWith("800") -> id.dropLast(3) + "000" to 1
                else -> String.format(Locale.ROOT, "%016X", (value and 0xFFFL.inv()) - 0x1000L) to 2
            }
        }

        fun folderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** All saves of the installed emulators. [names]: title ID -> game name. */
    fun find(androidData: File, names: Map<String, String>): List<SaveEntry> {
        val result = mutableListOf<SaveEntry>()
        for (emulator in EMULATORS) {
            val root = saveRoot(androidData, emulator)
            for (first in root.listFiles().orEmpty().filter { it.isDirectory }) {
                for (user in first.listFiles().orEmpty().filter { it.isDirectory }) {
                    for (title in user.listFiles().orEmpty().filter { it.isDirectory && TITLE_ID.matches(it.name) }) {
                        val id = title.name.uppercase()
                        val name = names[id] ?: id
                        result += SaveEntry(id, name, emulator, title, folderSize(title), latestBackup(id, emulator)?.second)
                    }
                }
            }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    private fun gameDir(name: String) = File(backupRoot, FileNames.safeName(name))

    /** All backups of this save as (folder, time), newest first. */
    fun backups(titleId: String, emulator: Emulator): List<Pair<File, Long>> =
        backupRoot.listFiles().orEmpty().filter { it.isDirectory }
            .flatMap { it.listFiles().orEmpty().toList() }
            .mapNotNull { dir ->
                val meta = runCatching { Json.parseToJsonElement(File(dir, META).readText()).jsonObject }.getOrNull() ?: return@mapNotNull null
                val id = meta["titleId"]?.jsonPrimitive?.content
                val pkg = meta["emulator"]?.jsonPrimitive?.content
                val time = meta["time"]?.jsonPrimitive?.content?.toLongOrNull()
                if (id.equals(titleId, ignoreCase = true) && pkg == emulator.packageName && time != null && File(dir, SAVE_DIR).isDirectory) dir to time else null
            }
            .sortedByDescending { it.second }

    fun latestBackup(titleId: String, emulator: Emulator) = backups(titleId, emulator).firstOrNull()

    /** Copies the save into a new backup folder and checks the copy. Returns the backup folder. */
    fun backUp(save: SaveEntry, time: Long = System.currentTimeMillis(), note: String = ""): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date(time))
        var dir = File(gameDir(save.name), stamp)
        var n = 2
        while (dir.exists()) dir = File(gameDir(save.name), "$stamp.${n++}")
        val target = File(dir, SAVE_DIR)
        copyTreeChecked(save.dir, target)
        val meta = JsonObject(
            mapOf(
                "titleId" to JsonPrimitive(save.titleId),
                "name" to JsonPrimitive(save.name),
                "emulator" to JsonPrimitive(save.emulator.packageName),
                "source" to JsonPrimitive(save.dir.path),
                "time" to JsonPrimitive(time.toString()),
                "note" to JsonPrimitive(note),
            ),
        )
        File(dir, META).writeText(meta.toString())
        return dir
    }

    /**
     * Puts the newest backup back. First backs up the current save (note "before restore"), then
     * builds the new save next to the old one, checks it, and only then swaps the folders. If the
     * swap fails, the old save is put back.
     */
    fun restoreLatest(save: SaveEntry, time: Long = System.currentTimeMillis()): File {
        val (backupDir, _) = latestBackup(save.titleId, save.emulator) ?: throw IOException("There is no backup of ${save.name} yet")
        val source = File(backupDir, SAVE_DIR)
        backUp(save, time, note = "before restore")
        val parent = save.dir.parentFile ?: throw IOException("Invalid save folder")
        val fresh = File(parent, save.dir.name + NEW_SUFFIX)
        val old = File(parent, save.dir.name + OLD_SUFFIX)
        if (fresh.exists()) deleteTree(fresh, parent)
        if (old.exists()) throw IOException("${old.name} is left over from an earlier restore. Check it first.")
        copyTreeChecked(source, fresh)
        Files.move(save.dir.toPath(), old.toPath())
        try {
            Files.move(fresh.toPath(), save.dir.toPath())
        } catch (e: IOException) {
            Files.move(old.toPath(), save.dir.toPath())
            throw e
        }
        deleteTree(old, parent)
        return backupDir
    }

    /** Copies a folder tree; fails if the target exists or a copied file has another size. */
    private fun copyTreeChecked(from: File, to: File) {
        if (to.exists()) throw IOException("${to.path} already exists")
        for (file in from.walkTopDown()) {
            val relative = file.relativeTo(from)
            val target = File(to, relative.path)
            if (file.isDirectory) {
                Files.createDirectories(target.toPath())
            } else {
                Files.copy(file.toPath(), target.toPath())
                if (target.length() != file.length()) throw IOException("Copy of ${file.name} is incomplete")
            }
        }
    }

    /** Deletes [dir] recursively, but only if it is a `.thorbro-new` / `.thorbro-old` folder inside [parent]. */
    private fun deleteTree(dir: File, parent: File) {
        val ours = dir.name.endsWith(NEW_SUFFIX) || dir.name.endsWith(OLD_SUFFIX)
        require(ours && dir.parentFile?.absoluteFile == parent.absoluteFile) { "Refusing to delete ${dir.path}" }
        dir.walkBottomUp().forEach { Files.deleteIfExists(it.toPath()) }
    }
}
