package dev.catzeyea.thorbro.android.files

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Helpers to build fake libraries and downloads inside a JUnit temporary folder. */
object TestFiles {

    fun file(dir: File, name: String, bytes: ByteArray = ByteArray(16)): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(bytes) }
    }

    /** A Switch NSP header: "PFS0" at offset 0. */
    fun nspBytes(size: Int = 0x200) = ByteArray(size).also { "PFS0".toByteArray().copyInto(it) }

    /** A zip with the given entries (name -> size in bytes). */
    fun zip(dir: File, name: String, entries: Map<String, Int>): File {
        dir.mkdirs()
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entry, size) in entries) {
                out.putNextEntry(ZipEntry(entry))
                out.write(ByteArray(size))
                out.closeEntry()
            }
        }
        return file
    }

    /**
     * `<root>/ROMs` with an ES-DE switch folder (systeminfo.txt) holding [switchGames] games, a
     * gba folder by name, and an empty snes folder.
     */
    fun library(root: File, switchGames: Int = 1): GameLibrary {
        val roms = File(root, "ROMs")
        val switch = File(roms, "switch")
        file(switch, "systeminfo.txt", SWITCH_INFO.toByteArray())
        repeat(switchGames) { file(switch, "Game $it [0100AAAA0000${it}000].nsp") }
        file(File(roms, "Game Boy Advance"), "x.gba")
        File(roms, "snes").mkdirs()
        return Libraries.read(roms, "Internal storage")!!
    }

    const val SWITCH_INFO = """System name:
switch

Full system name:
Nintendo Switch

Supported file extensions:
.nca .NCA .nro .NRO .nso .NSO .nsp .NSP .xci .XCI

Launch command:
%EMULATOR_KENJI-NX%
"""
}
