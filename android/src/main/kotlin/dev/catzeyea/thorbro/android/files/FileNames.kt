package dev.catzeyea.thorbro.android.files

import java.io.File
import java.util.Locale

/** Small, pure helpers for file names. */
object FileNames {

    /** Lowercase extension including the dot (`"Game.NSP"` -> `".nsp"`), or `""`. */
    fun extOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot).lowercase()
    }

    /**
     * A file name that FAT/exFAT accepts (the Thor's SD card rejects `a:b` with EPERM).
     * Same rule as the PC app (`unpack::safe_name`): `:` becomes ` -`, `* ? " < > | \` and control
     * characters become `_`, trailing dots/spaces and leading spaces are removed.
     */
    fun safeName(name: String): String {
        val result = StringBuilder(name.length)
        for (c in name) {
            when {
                c == ':' -> result.append(" -")
                c in "*?\"<>|\\" -> result.append('_')
                c.code < 0x20 -> result.append('_')
                else -> result.append(c)
            }
        }
        val cleaned = result.toString().replace("  -", " -").trimEnd('.', ' ').trimStart()
        return cleaned.ifEmpty { "_" }
    }

    /**
     * Endings of downloads that are not finished yet. `.thorbro-part` is the PC app's own
     * temporary upload name.
     */
    private val UNFINISHED_ENDINGS = listOf(".crdownload", ".part", ".partial", ".download", ".tmp", ".thorbro-part", ".thorbro-copy")

    fun isUnfinishedName(name: String): Boolean {
        val lower = name.lowercase()
        return UNFINISHED_ENDINGS.any { lower.endsWith(it) }
    }

    /** "1.4 GB", "48 KB": decimal units like Android's storage screen. */
    fun human(bytes: Long): String {
        if (bytes < 1000) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1000 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        // Locale.ROOT: the UI is English, so always a decimal point.
        val text = String.format(Locale.ROOT, if (value >= 10) "%.0f" else "%.1f", value)
        return "$text ${units[unit]}"
    }

    /** True if [child] is [parent] itself or inside it (path based, no file system access). */
    fun isInside(parent: File, child: File): Boolean {
        val p = parent.absoluteFile.normalize().path.trimEnd('/')
        val c = child.absoluteFile.normalize().path
        return c == p || c.startsWith("$p/")
    }
}
