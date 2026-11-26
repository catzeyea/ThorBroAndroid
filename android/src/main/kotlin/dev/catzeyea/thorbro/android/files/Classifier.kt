package dev.catzeyea.thorbro.android.files

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/** What a Switch title ID says about a file. */
enum class SwitchKind { Game, Update, Dlc }

/** The result of guessing where a file belongs (port of the PC app's `library::Guess`). */
data class Guess(
    /** Best system folder, or null if we can't tell. */
    val system: String? = null,
    /** "update" for Switch updates/DLC, else empty. */
    val sub: String = "",
    val reason: String = "",
    /** All systems that could fit, best first. */
    val candidates: List<String> = emptyList(),
    /** true = confident guess, false = unsure. */
    val sure: Boolean = false,
    /** Set when the Switch title ID decided. */
    val switchKind: SwitchKind? = null,
    /** The target system reads the archive packed (e.g. arcade .zip). Only meaningful for archives. */
    val packedOk: Boolean = true,
    /** Extension of the archive we looked into, else empty. */
    val archiveExt: String = "",
)

/**
 * Guesses the target system of a file. Same order and rules as the PC app (`library.rs`):
 * 1. Switch title ID in the name, 2. file header (magic bytes), 3. file extension.
 *
 * Differences on the Thor (decisions for the Android app):
 * - Title IDs are also accepted between underscores (`_01007EF00011E800_`).
 * - Only `.zip` archives are looked into (java.util.zip). `.7z` and `.rar` are not classified here,
 *   the scan sends them to Review.
 */
object Classifier {

    /** For ambiguous extensions, systems that already contain games win, then this order. */
    private val PREFERRED = listOf(
        "switch", "n3ds", "nds", "ps2", "psp", "psx", "gc", "wii", "wiiu", "gba", "gbc", "gb", "n64",
        "snes", "nes", "megadrive", "genesis", "dreamcast", "saturn", "ps3", "psvita",
    )
    private const val NOT_PREFERRED = 999

    val ARCHIVES = setOf(".zip", ".7z", ".rar")
    private val SWITCH_EXTS = setOf(".nsp", ".xci", ".nsz", ".xcz")
    private const val HEADER_SIZE = 0x200
    private const val ISO_PVD_OFFSET = 0x8000L
    private const val ISO_PVD_SIZE = 0x40
    private const val PS2_MIN_ISO_SIZE = 800L * 1024 * 1024
    private const val MAX_UNSURE_CANDIDATES = 5

    /** `0100` + 12 hex digits in `[...]`, `(...)` or `_..._`. The PC app only accepts brackets. */
    private val TITLE_ID = Regex("""[\[(_](0100[0-9A-Fa-f]{12})[\])_]""")

    /** Switch title ID from a file name, uppercase. */
    fun switchId(name: String): String? = TITLE_ID.find(name)?.groupValues?.get(1)?.uppercase()

    /**
     * Game, update or DLC by the title ID. A file tagged `[BASE]` in [name] is a base game even
     * with another ID (as in the PC app's ids.rs): on the Thor,
     * `The Legend of Zelda Breath of the Wild [01007EF00011E800][BASE][v1.5.0].xci` is the base game.
     */
    fun switchKind(titleId: String, name: String = ""): SwitchKind = when {
        titleId.endsWith("000") -> SwitchKind.Game
        isTaggedBase(name) -> SwitchKind.Game
        titleId.endsWith("800") -> SwitchKind.Update
        else -> SwitchKind.Dlc
    }

    fun isTaggedBase(name: String): Boolean = name.contains("[BASE]", ignoreCase = true)

    /** The file that is examined: the file itself, or for a .zip the largest file inside (by name only). */
    private data class Probe(val file: File, val name: String, val ext: String, val archiveExt: String, val via: String)

    fun classify(file: File, lib: GameLibrary): Guess {
        val probe = probeFor(file)
        val candidates = lib.systems.filter { probe.ext.isNotEmpty() && probe.ext in it.value.exts }.keys.toList()
        val guess = guessSwitch(lib, file.name, probe, candidates)
            ?: guessByHeader(lib, probe, candidates)
            ?: guessByExtension(lib, probe, candidates)
        // An archive may stay packed if the chosen system lists the archive extension itself and it
        // isn't a Switch update (PC rule). Otherwise it would have to be extracted first.
        if (guess.system != null && probe.archiveExt.isNotEmpty()) {
            val readsArchive = lib.systems[guess.system]?.exts?.contains(probe.archiveExt) == true
            return guess.copy(packedOk = readsArchive && guess.sub.isEmpty(), archiveExt = probe.archiveExt)
        }
        return guess
    }

    private fun probeFor(file: File): Probe {
        val ext = FileNames.extOf(file.name)
        if (ext == ".zip") {
            val inner = zipLargestEntry(file)
            if (inner != null) {
                val innerExt = FileNames.extOf(inner)
                // An archive inside an archive tells us nothing, keep looking at the outer one.
                if (innerExt !in ARCHIVES) {
                    val innerBase = inner.substringAfterLast('/')
                    return Probe(file, innerBase, innerExt, ext, " (in archive: $innerBase)")
                }
            }
        }
        return Probe(file, file.name, ext, "", "")
    }

    /** Name of the largest file inside a zip, or null if it can't be read. */
    fun zipLargestEntry(file: File): String? = runCatching {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.maxByOrNull { it.size }?.name
        }
    }.getOrNull()

    private fun guessSwitch(lib: GameLibrary, name: String, probe: Probe, candidates: List<String>): Guess? {
        val titleId = switchId(name) ?: switchId(probe.name) ?: return null
        val isSwitchExt = probe.ext in SWITCH_EXTS
        val switch = lib.folderFor("switch") ?: return null
        if (!(isSwitchExt || candidates.isEmpty())) return null
        val tail = titleId.takeLast(4)
        val tagged = if (switchId(name) != null) name else probe.name
        return when (val kind = switchKind(titleId, tagged)) {
            SwitchKind.Game -> Guess(switch, "", "Switch game (title ID …$tail)", listOf(switch), true, kind)
            else -> {
                val what = if (kind == SwitchKind.Update) "Update" else "DLC"
                Guess(switch, "update", "Switch $what (title ID …$tail)", listOf(switch), true, kind)
            }
        }
    }

    private fun guessByHeader(lib: GameLibrary, probe: Probe, candidates: List<String>): Guess? {
        if (probe.archiveExt.isNotEmpty() || !probe.file.isFile) return null
        val (systemId, why) = sniff(probe.file) ?: return null
        val system = lib.folderFor(systemId) ?: return null
        return Guess(system, "", why + probe.via, rank(lib, candidates + system), true)
    }

    private fun guessByExtension(lib: GameLibrary, probe: Probe, candidates: List<String>): Guess {
        val ext = probe.ext
        if (candidates.isEmpty()) {
            val what = ext.ifEmpty { "this file" }
            return Guess(reason = "No system knows $what${probe.via}")
        }
        val ranked = rank(lib, candidates)
        val top = ranked[0]
        if (ranked.size == 1) return Guess(top, "", "Extension $ext${probe.via}", ranked, true)
        if (ranked.size <= MAX_UNSURE_CANDIDATES || (lib.systems[top]?.count ?: 0) > 0) {
            val ellipsis = if (ranked.size > 4) " …" else ""
            val others = ranked.subList(1, minOf(4, ranked.size)).joinToString(", ") + ellipsis
            return Guess(top, "", "Unsure: $ext also fits $others${probe.via}", ranked, false)
        }
        return Guess(reason = "$ext fits ${ranked.size} systems${probe.via}", candidates = ranked)
    }

    /** Systems with games first, then [PREFERRED], then alphabetically. Duplicates removed. */
    fun rank(lib: GameLibrary, names: List<String>): List<String> =
        names.distinct().sortedWith(
            compareBy<String>(
                { (lib.systems[it]?.count ?: 0) == 0 },
                {
                    val id = lib.systems[it]?.id?.takeIf { id -> id.isNotEmpty() } ?: it
                    PREFERRED.indexOf(id).takeIf { index -> index >= 0 } ?: NOT_PREFERRED
                },
                { it },
            ),
        )

    // --- Header detection ------------------------------------------------------------------------

    /** Recognizes the system from the file header. Returns (canonical system id, reason) or null. */
    fun sniff(file: File): Pair<String, String>? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val size = raf.length()
            val header = ByteArray(HEADER_SIZE)
            val headerLen = raf.read(header).coerceAtLeast(0)
            sniffConsoleHeader(header.copyOf(headerLen)) ?: run {
                if (size <= ISO_PVD_OFFSET + ISO_PVD_SIZE) return@run null
                raf.seek(ISO_PVD_OFFSET)
                val pvd = ByteArray(ISO_PVD_SIZE)
                val pvdLen = raf.read(pvd).coerceAtLeast(0)
                sniffIso(pvd.copyOf(pvdLen), size)
            }
        }
    }.getOrNull()

    private fun ByteArray.at(offset: Int, len: Int): ByteArray? =
        if (offset + len <= size) copyOfRange(offset, offset + len) else null

    private fun ByteArray?.isBytes(vararg expected: Int): Boolean =
        this != null && size == expected.size && indices.all { (this[it].toInt() and 0xFF) == expected[it] }

    private fun ByteArray?.isText(text: String): Boolean = this != null && String(this, Charsets.ISO_8859_1) == text

    fun sniffConsoleHeader(h: ByteArray): Pair<String, String>? {
        if (h.at(0, 4).isText("PFS0") || h.at(0x100, 4).isText("HEAD")) return "switch" to "Switch header"
        if (h.at(0x100, 4).isText("NCSD")) return "n3ds" to "3DS header"
        // WIA / RVZ (Dolphin's compressed formats) store the disc type at offset 0x48.
        if (h.at(0, 4).isBytes('W'.code, 'I'.code, 'A'.code, 1) || h.at(0, 4).isBytes('R'.code, 'V'.code, 'Z'.code, 1)) {
            val type = h.at(0x48, 4)
            if (type != null) {
                val value = type.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
                if (value == 1L) return "gc" to "GameCube header"
                if (value == 2L) return "wii" to "Wii header"
            }
        }
        if (h.at(0x1C, 4).isBytes(0xC2, 0x33, 0x9F, 0x3D)) return "gc" to "GameCube header"
        if (h.at(0x18, 4).isBytes(0x5D, 0x1C, 0x9E, 0xA3) || h.at(0, 4).isText("WBFS")) return "wii" to "Wii header"
        if (h.at(0, 4).isText("CISO")) return "psp" to "CSO image (usually PSP)"
        return null
    }

    fun sniffIso(pvd: ByteArray, size: Long): Pair<String, String>? {
        if (!pvd.at(1, 5).isText("CD001")) return null
        val systemId = String(pvd.at(8, 32) ?: return null, Charsets.ISO_8859_1).trim()
        if (systemId.startsWith("PSP GAME")) return "psp" to "PSP-ISO"
        if (systemId.startsWith("PLAYSTATION")) return if (size > PS2_MIN_ISO_SIZE) "ps2" to "PS2-ISO" else "psx" to "PS1 image"
        return null
    }
}
