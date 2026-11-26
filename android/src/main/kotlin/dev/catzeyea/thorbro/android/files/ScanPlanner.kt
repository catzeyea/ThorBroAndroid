package dev.catzeyea.thorbro.android.files

import java.io.File

/** Where a scanned file ends up in the preview. */
enum class ScanGroup(val title: String) {
    Games("Games"),
    Updates("Updates"),
    Dlc("DLC"),

    /** Recognized, and already in the folder it belongs in: nothing to do. */
    InPlace("Already in place"),

    /** Unclear: stays where it is, nothing is moved. */
    Review("Unclear"),

    /** Not finished downloading (or still growing): ignored this time. */
    Skipped("Still downloading"),
}

data class ScanItem(
    val file: File,
    val size: Long,
    /** Last modified time when scanned; the move is skipped if the file changed since. */
    val modified: Long,
    val group: ScanGroup,
    /** Target folder for moved groups, null for Review / Skipped / InPlace. */
    val target: File?,
    /** Short explanation shown in the preview. */
    val reason: String,
    /**
     * Switched on in the preview at first. Sure guesses start on; an unsure guess (several systems
     * would fit) can be moved too, but starts off.
     */
    val onByDefault: Boolean = target != null,
)

data class ScanResult(
    val folder: File,
    val library: GameLibrary?,
    val items: List<ScanItem>,
) {
    /** Files that can be moved (each one can be switched on or off in the preview). */
    val toMove: List<ScanItem> get() = items.filter { it.target != null }

    /** The files switched on at first. */
    val defaultSelection: Set<File> get() = toMove.filter { it.onByDefault }.map { it.file }.toSet()
    fun group(group: ScanGroup): List<ScanItem> = items.filter { it.group == group }
}

/**
 * Builds the scan preview for one folder. Reads files, never changes anything.
 *
 * - Only files directly in the folder are looked at (no sub folders, no hidden files). Folders are
 *   listed in Review.
 * - Unfinished downloads (by name, or a size/time change while scanning) are skipped.
 * - Saves, cheats, APKs and firmware are not guessed: they go to Review.
 * - Java apps (.jar, .jad) are not guessed either (ES-DE's j2me takes .jar, but most are tools).
 * - Games, updates and DLC use [Classifier] (the PC rules). Sure guesses start switched on in the
 *   preview; an unsure guess (several systems fit) gets its best target but starts switched off.
 * - Files that are already in the folder they belong in are listed as "Already in place".
 * - .7z and .rar go to Review; a .zip only moves if the target system reads it packed.
 */
object ScanPlanner {

    /** Files changed less than this long ago are sampled twice to see whether they still grow. */
    const val RECENT_MILLIS = 60_000L

    private val APK_EXTS = setOf(".apk", ".apks", ".xapk", ".apkm")
    private val SAVE_EXTS = setOf(".sav", ".srm", ".dsv", ".sv", ".sa1", ".mcr", ".mcd", ".ps2", ".gci", ".sgm", ".state")
    private val CHEAT_EXTS = setOf(".pchtxt", ".cht", ".chtx")
    private val JAVA_EXTS = setOf(".jar", ".jad")

    /**
     * [library]: the main library (null = none found). [stillGrowing] is asked for recently changed
     * files; it should look at the file again a moment later (injectable for tests).
     */
    fun plan(
        folder: File,
        library: GameLibrary?,
        now: Long = System.currentTimeMillis(),
        stillGrowing: (File, Long, Long) -> Boolean = ::sampleAgain,
    ): ScanResult {
        val entries = folder.listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
        val items = entries.map { entry -> planOne(entry, library, now, stillGrowing) }
        return ScanResult(folder, library, items)
    }

    private fun planOne(file: File, library: GameLibrary?, now: Long, stillGrowing: (File, Long, Long) -> Boolean): ScanItem {
        val size = if (file.isFile) file.length() else 0L
        val modified = file.lastModified()
        fun item(group: ScanGroup, reason: String, target: File? = null) = ScanItem(file, size, modified, group, target, reason)

        if (file.isDirectory) return item(ScanGroup.Review, "Folder: not sorted automatically")
        if (FileNames.isUnfinishedName(file.name)) return item(ScanGroup.Skipped, "Download not finished")
        if (now - modified in 0 until RECENT_MILLIS && stillGrowing(file, size, modified)) {
            return item(ScanGroup.Skipped, "Still being written")
        }
        notGuessed(file.name)?.let { return item(ScanGroup.Review, it) }

        val ext = FileNames.extOf(file.name)
        if (ext == ".7z" || ext == ".rar") return item(ScanGroup.Review, "$ext archive: not looked into on the Thor")
        if (library == null) return item(ScanGroup.Review, "No game library (ROMs folder) found")

        val guess = Classifier.classify(file, library)
        val system = guess.system ?: return item(ScanGroup.Review, guess.reason)
        if (!guess.packedOk) {
            return item(ScanGroup.Review, "${guess.reason}: extract it first (not done on the Thor)")
        }
        val target = if (guess.sub.isEmpty()) File(library.path, system) else File(File(library.path, system), guess.sub)
        if (file.parentFile?.absoluteFile?.normalize() == target.absoluteFile.normalize()) {
            return item(ScanGroup.InPlace, "Already in ${library.full(system)}")
        }
        val group = when (guess.switchKind) {
            SwitchKind.Update -> ScanGroup.Updates
            SwitchKind.Dlc -> ScanGroup.Dlc
            else -> ScanGroup.Games
        }
        return ScanItem(file, size, modified, group, target, guess.reason, onByDefault = guess.sure)
    }

    /** Saves, cheats, APKs and firmware: recognized only to keep them out of the library. */
    fun notGuessed(name: String): String? {
        val lower = name.lowercase()
        val ext = FileNames.extOf(name)
        return when {
            ext in APK_EXTS -> "App (APK): install it yourself"
            ext in JAVA_EXTS -> "Java app: not sorted"
            "firmware" in lower -> "Looks like firmware: not sorted"
            ext in CHEAT_EXTS || "cheat" in lower -> "Looks like cheats: not sorted yet"
            ext in SAVE_EXTS || Regex("""(^|[^a-z])saves?([^a-z]|$)""").containsMatchIn(lower) -> "Looks like a save: not sorted yet"
            else -> null
        }
    }

    /** Looks at a file again after a short pause: true if size or time changed (still downloading). */
    fun sampleAgain(file: File, size: Long, modified: Long): Boolean {
        Thread.sleep(1_500)
        return file.length() != size || file.lastModified() != modified
    }
}
