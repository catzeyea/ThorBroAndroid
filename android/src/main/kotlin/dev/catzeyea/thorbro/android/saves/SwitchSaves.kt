package dev.catzeyea.thorbro.android.saves

import dev.catzeyea.thorbro.android.files.Classifier
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

/** How an emulator keeps its saves. */
enum class SaveLayout {
    /** yuzu forks: `<nand>/user/save/0000000000000000/<user>/<title id>`. */
    Yuzu,

    /** Peanuts: `files/saves/<title id>` (next to it `<id>.work` and `<id>.lock`, which are ignored). */
    Peanuts,
}

/**
 * A Switch emulator. [restoreNote] is set when ThorBro can back up its saves but must not write
 * them back (shown in the UI instead of the Restore button).
 */
data class Emulator(val packageName: String, val label: String, val layout: SaveLayout, val restoreNote: String? = null) {
    val canRestore: Boolean get() = restoreNote == null
}

/** One backup of a save. */
data class Backup(val dir: File, val time: Long, val note: String) {
    val saveDir: File get() = File(dir, SwitchSaves.SAVE_DIR)
}

/** One game's save folder in one emulator, with its backups (newest first). */
data class SaveEntry(
    val titleId: String,
    val name: String,
    val emulator: Emulator,
    val dir: File,
    val backups: List<Backup>,
) {
    val lastBackup: Long? get() = backups.firstOrNull()?.time
    val key: String get() = "${emulator.packageName}/$titleId"
}

/** Result of looking for saves: the saves, plus notes about fallbacks (shown to the user / in the report). */
data class SavesScan(val saves: List<SaveEntry>, val notes: List<String>)

/**
 * Switch saves on the Thor: list, back up, restore. No sync with the PC, no import.
 *
 * The saves live in other apps' private folders (`Android/data/<emulator>`). This works on the Thor
 * firmware because every app may read and write there; on stock Android it would not.
 *
 * yuzu forks (Eden, Eden Duo, …) use the PC app's rule: the NAND folder from `config.ini`
 * (`[Data%20Storage] nand_directory`), the user folder from `profiles.dat` (the current user's UUID,
 * bytes reversed). Other user folders are orphans and ignored. Checked on a real Thor: Eden Duo has
 * such an orphan (`1DAA9E48BC2B43C12000000000000000`).
 *
 * Backups: `ThorBro/SaveBackups/<game>/<yyyy-MM-dd_HH-mm-ss>/` with the save in `save/` and a
 * `backup.json` that says where it came from.
 */
class SwitchSaves(private val backupRoot: File) {

    companion object {
        const val PEANUTS_NOTE = "Restore isn't available for Peanuts yet: Peanuts keeps a second copy of each save " +
            "(.work), and writing a save back hasn't been tested. Back up works."

        /** Only those that are installed show up. */
        val EMULATORS = listOf(
            Emulator("dev.eden.eden_emulator", "Eden", SaveLayout.Yuzu),
            Emulator("dev.igawa6.edenduo", "Eden Duo", SaveLayout.Yuzu),
            Emulator("org.citron.citron_emu", "Citron", SaveLayout.Yuzu),
            Emulator("org.sudachi.sudachi_emu", "Sudachi", SaveLayout.Yuzu),
            Emulator("org.yuzu.yuzu_emu", "yuzu", SaveLayout.Yuzu),
            Emulator("dev.nxnative.android.peanuts", "Peanuts", SaveLayout.Peanuts, PEANUTS_NOTE),
        )
        private val TITLE_ID = Regex("^0100[0-9A-Fa-f]{12}$")
        private val NAME_TITLE_ID = Regex("""[\[(_](0100[0-9A-Fa-f]{12})[\])_]""")
        const val META = "backup.json"
        const val SAVE_DIR = "save"
        const val NEW_SUFFIX = ".thorbro-new"
        const val OLD_SUFFIX = ".thorbro-old"
        private const val PROFILES_DAT = "system/save/8000000000000010/su/avators/profiles.dat"
        private const val DEVICE_SAVES = "user/save/0000000000000000"

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
                val (base, rank) = baseTitleId(id, name) ?: continue
                val title = name.substring(0, match.range.first).substringBefore('[').substringBefore('(').trim()
                if (title.isEmpty()) continue
                val current = best[base]
                if (current == null || rank < current.first) best[base] = rank to title
            }
            return best.mapValues { it.value.second }
        }

        /** (base game title ID, 0 = game / 1 = update / 2 = DLC). A `[BASE]` tag in [name] makes it a game. */
        fun baseTitleId(id: String, name: String = ""): Pair<String, Int>? {
            val value = id.toLongOrNull(16) ?: return null
            return when {
                id.endsWith("000") -> id to 0
                Classifier.isTaggedBase(name) -> String.format(Locale.ROOT, "%016X", value and 0xFFFL.inv()) to 0
                id.endsWith("800") -> id.dropLast(3) + "000" to 1
                else -> String.format(Locale.ROOT, "%016X", (value and 0xFFFL.inv()) - 0x1000L) to 2
            }
        }

        fun folderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        fun fileCount(dir: File): Int = dir.walkTopDown().count { it.isFile }

        /** Value of `key=` in an ini file inside `[section]`; empty values count as missing (as on the PC). */
        fun iniValue(text: String, section: String, key: String): String? {
            var current: String? = null
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = line.substring(1, line.length - 1)
                    continue
                }
                if (current != section) continue
                if (line.startsWith("$key=")) return line.substring(key.length + 1).trim().ifEmpty { null }
            }
            return null
        }

        /**
         * The save folder name of a user in a yuzu `profiles.dat`: the slot's 16-byte UUID with its
         * bytes reversed, uppercase hex (PC: ids.rs). [index] is `current_user`; an empty slot falls
         * back to the first user. Null if there is no user.
         */
        fun userFolder(data: ByteArray, index: Int?): String? {
            fun uuidAt(slot: Int): ByteArray? {
                val start = 0x10 + slot * 0xC8
                if (start + 16 > data.size) return null
                val uuid = data.copyOfRange(start, start + 16)
                return if (uuid.any { it.toInt() != 0 }) uuid else null
            }
            val uuid = index?.let(::uuidAt) ?: (0 until 8).firstNotNullOfOrNull(::uuidAt) ?: return null
            return uuid.reversed().joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF) }
        }
    }

    // --- Finding saves ----------------------------------------------------------------------------

    /** All saves of the installed emulators. [names]: title ID -> game name. */
    fun find(androidData: File, names: Map<String, String>): SavesScan {
        val index = backupIndex()
        val notes = mutableListOf<String>()
        val saves = mutableListOf<SaveEntry>()
        for (emulator in EMULATORS) {
            val appData = File(androidData, emulator.packageName)
            if (!appData.isDirectory) continue
            val folders = when (emulator.layout) {
                SaveLayout.Yuzu -> yuzuSaveFolders(appData, emulator, notes)
                SaveLayout.Peanuts -> File(appData, "files/saves").listFiles().orEmpty().filter { it.isDirectory && TITLE_ID.matches(it.name) }
            }
            for (dir in folders) {
                val id = dir.name.uppercase()
                saves += SaveEntry(id, names[id] ?: id, emulator, dir, index["${emulator.packageName}/$id"].orEmpty())
            }
        }
        return SavesScan(saves.sortedWith(compareBy({ EMULATORS.indexOf(it.emulator) }, { it.name.lowercase() })), notes)
    }

    private fun yuzuSaveFolders(appData: File, emulator: Emulator, notes: MutableList<String>): List<File> {
        val config = runCatching { File(appData, "files/config/config.ini").readText() }.getOrNull()
        val nand = config?.let { iniValue(it, "Data%20Storage", "nand_directory") }?.let(::File)?.takeIf { it.isDirectory }
            ?: File(appData, "files/nand")
        val index = config?.let { iniValue(it, "System", "current_user") }?.toIntOrNull()
        val user = runCatching { File(nand, PROFILES_DAT).readBytes() }.getOrNull()?.let { userFolder(it, index) }
        val deviceSaves = File(nand, DEVICE_SAVES)
        val userDirs = if (user != null) {
            listOf(File(deviceSaves, user))
        } else {
            // Fallback: profiles.dat not readable. Use every user folder (as before) and say so.
            notes += "${emulator.label}: couldn't read profiles.dat, so the saves of every user folder are shown."
            deviceSaves.listFiles().orEmpty().filter { it.isDirectory }
        }
        return userDirs.flatMap { dir -> dir.listFiles().orEmpty().filter { it.isDirectory && TITLE_ID.matches(it.name) } }
    }

    // --- Backups ----------------------------------------------------------------------------------

    /**
     * All backups by "<package>/<title id>", newest first. One pass over ThorBro/SaveBackups: every
     * backup.json is read once, never per save.
     */
    fun backupIndex(): Map<String, List<Backup>> {
        val result = mutableMapOf<String, MutableList<Backup>>()
        for (game in backupRoot.listFiles().orEmpty()) {
            for (dir in game.listFiles().orEmpty()) {
                val meta = runCatching { Json.parseToJsonElement(File(dir, META).readText()).jsonObject }.getOrNull() ?: continue
                val id = meta["titleId"]?.jsonPrimitive?.content?.uppercase() ?: continue
                val pkg = meta["emulator"]?.jsonPrimitive?.content ?: continue
                val time = meta["time"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                if (!File(dir, SAVE_DIR).isDirectory) continue
                result.getOrPut("$pkg/$id") { mutableListOf() } += Backup(dir, time, meta["note"]?.jsonPrimitive?.content.orEmpty())
            }
        }
        return result.mapValues { (_, list) -> list.sortedByDescending { it.time } }
    }

    private fun gameDir(name: String) = File(backupRoot, FileNames.safeName(name))

    /** Copies the save into a new backup folder and checks the copy. Returns the backup folder. */
    fun backUp(save: SaveEntry, time: Long = System.currentTimeMillis(), note: String = ""): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date(time))
        var dir = File(gameDir(save.name), stamp)
        var n = 2
        while (dir.exists()) dir = File(gameDir(save.name), "$stamp.${n++}")
        copyTreeChecked(save.dir, File(dir, SAVE_DIR))
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
     * Puts [backup] back. First backs up the current save (note "before restore"), then builds the
     * new save next to the old one, checks it, and only then swaps the folders. If the swap fails,
     * the old save is put back. Not for emulators with a [Emulator.restoreNote] (Peanuts).
     */
    fun restore(save: SaveEntry, backup: Backup, time: Long = System.currentTimeMillis()) {
        if (!save.emulator.canRestore) throw IOException(save.emulator.restoreNote)
        val source = backup.saveDir
        if (!source.isDirectory) throw IOException("This backup is gone")
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
