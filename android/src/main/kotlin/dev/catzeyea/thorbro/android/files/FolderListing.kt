package dev.catzeyea.thorbro.android.files

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One entry of an open folder. [childCount] is filled in a second, slower pass (null = not counted
 * yet, or the folder can't be read).
 */
data class FileEntry(
    val file: File,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val childCount: Int? = null,
) {
    val name: String get() = file.name
}

/** What the list is sorted by. Only the name for now; size and date come later. */
enum class SortKey { Name }

data class SortOrder(val key: SortKey = SortKey.Name, val descending: Boolean = false)

/** The kind of file a type chip shows (colour in the UI). */
enum class FileKind { Game, Archive, App, Save, Text, Image, Video, Audio, Other }

/** The small square in front of a file: [label] like "XCI", "ZIP". */
data class TypeChip(val label: String, val kind: FileKind)

/** Listing, sorting, filtering and labels for the Files tab. Pure functions plus two file reads. */
object FolderListing {

    /** Entries of [folder] without hidden (dot) files, unsorted. Null if the folder can't be read. */
    fun read(folder: File): List<FileEntry>? {
        val files = folder.listFiles() ?: return null
        return files.filter { !it.name.startsWith(".") }.map { file ->
            val dir = file.isDirectory
            FileEntry(file, dir, if (dir) 0L else file.length(), file.lastModified())
        }
    }

    /** Visible entries of [dir] (names only, no stat per child). Null if it can't be read. */
    fun countChildren(dir: File): Int? = dir.list()?.count { !it.startsWith(".") }

    /** Folders first, then files; each group by name ([naturalNames]), in the order's direction. */
    fun sort(entries: List<FileEntry>, order: SortOrder): List<FileEntry> {
        val byName = Comparator<FileEntry> { a, b -> naturalNames.compare(a.name, b.name) }
        val within = if (order.descending) byName.reversed() else byName
        return entries.sortedWith(compareBy<FileEntry> { !it.isDir }.then(within))
    }

    /** Entries whose name contains [query] (case-insensitive). A blank query keeps everything. */
    fun filter(entries: List<FileEntry>, query: String): List<FileEntry> {
        val q = query.trim()
        if (q.isEmpty()) return entries
        return entries.filter { it.name.contains(q, ignoreCase = true) }
    }

    /**
     * Name order as people expect it: case-insensitive, and runs of digits compared by value, so
     * "Screenshot_2" comes before "Screenshot_10". Ties fall back to the plain string order, so
     * the order is total (needed for a stable sort with "a" and "A").
     */
    val naturalNames: Comparator<String> = Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val endA = digitsEnd(a, i)
                val endB = digitsEnd(b, j)
                val na = a.substring(i, endA).trimStart('0')
                val nb = b.substring(j, endB).trimStart('0')
                if (na.length != nb.length) return@Comparator na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return@Comparator c
                i = endA
                j = endB
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return@Comparator c
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        if (rest != 0) rest else a.compareTo(b)
    }

    private fun digitsEnd(s: String, start: Int): Int {
        var k = start
        while (k < s.length && s[k].isDigit()) k++
        return k
    }

    private val ARCHIVE = setOf(".zip", ".7z", ".rar", ".tar", ".gz", ".xz", ".bz2", ".zst", ".tgz")
    private val APP = setOf(".apk", ".xapk", ".apks", ".apkm", ".jar", ".jad")
    private val SAVE = setOf(".sav", ".srm", ".state", ".sv", ".dsv", ".mcr", ".mcd", ".sa1", ".sa2")
    private val TEXT = setOf(".txt", ".md", ".log", ".json", ".xml", ".ini", ".cfg", ".conf", ".html", ".htm", ".csv", ".yml", ".yaml", ".pdf")
    private val IMAGE = setOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".heic", ".heif")
    private val VIDEO = setOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v", ".3gp")
    private val AUDIO = setOf(".mp3", ".ogg", ".flac", ".wav", ".m4a", ".opus", ".aac")

    /** Game extensions of the known systems, without archives and generic program files. */
    private val GAME: Set<String> by lazy {
        KnownSystems.ALL.flatMap { it.exts }.toSet() - ARCHIVE - setOf(".elf", ".dol", ".app", ".bin", ".img")
    }

    fun typeChip(name: String): TypeChip {
        val ext = FileNames.extOf(name)
        val kind = when (ext) {
            in ARCHIVE -> FileKind.Archive
            in APP -> FileKind.App
            in SAVE -> FileKind.Save
            in TEXT -> FileKind.Text
            in IMAGE -> FileKind.Image
            in VIDEO -> FileKind.Video
            in AUDIO -> FileKind.Audio
            in GAME -> FileKind.Game
            else -> FileKind.Other
        }
        val label = if (ext.isEmpty()) "FILE" else ext.drop(1).take(4).uppercase(Locale.ROOT)
        return TypeChip(label, kind)
    }

    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)

    /** "22.11.2026", like the boards. Thread-safe (rows call it while composing). */
    fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = DATE.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "12 items" / "1 item" for folders (or "" while not counted), "2.8 GB" for files. */
    fun sizeText(entry: FileEntry): String = when {
        !entry.isDir -> FileNames.human(entry.size)
        entry.childCount == null -> ""
        entry.childCount == 1 -> "1 item"
        else -> "${entry.childCount} items"
    }

    /** Spaces between size and date in [metaText], and the length of a date ("22.11.2026"). */
    const val META_GAP = 6
    const val DATE_CHARS = 10

    /**
     * Size (or item count) and date as one right-aligned text: "2.8 GB      22.11.2026". In a
     * monospace font the date always takes the same width, so the size column stays aligned.
     */
    fun metaText(entry: FileEntry, date: String): String = sizeText(entry) + " ".repeat(META_GAP) + date

    /**
     * Footer numbers: page of the first visible row and the number of pages, for [perPage] rows on
     * screen. ("1 of 4 pages"). An empty list is page 1 of 1.
     */
    fun pages(total: Int, perPage: Int, firstVisible: Int): Pair<Int, Int> {
        if (total <= 0 || perPage <= 0) return 1 to 1
        val pages = (total + perPage - 1) / perPage
        val page = (firstVisible / perPage + 1).coerceIn(1, pages)
        // The last rows can be on screen without the first visible row reaching the last page.
        val last = if (firstVisible + perPage >= total) pages else page
        return last to pages
    }
}
