package dev.catzeyea.thorbro.android.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The PC rules (library.rs) on the Thor, plus title IDs between underscores. */
class ClassifierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun lib() = TestFiles.library(tmp.newFolder("internal"))
    private val dl get() = File(tmp.root, "Download")

    @Test
    fun `libraries read systeminfo, folder names and counts`() {
        val lib = lib()
        assertEquals(setOf("Game Boy Advance", "snes", "switch"), lib.systems.keys)
        val switch = lib.systems.getValue("switch")
        assertEquals("systeminfo", switch.origin)
        assertEquals("Nintendo Switch", switch.full)
        assertTrue(".nsp" in switch.exts && ".nca" in switch.exts)
        assertEquals(1, switch.count) // systeminfo.txt does not count
        assertEquals("gba", lib.systems.getValue("Game Boy Advance").id)
        assertEquals("name", lib.systems.getValue("snes").origin)
        assertEquals("Game Boy Advance", lib.folderFor("gba"))
    }

    @Test
    fun `the library with the most games is the main one`() {
        val internal = File(tmp.root, "internal")
        val sd = File(tmp.root, "sd")
        TestFiles.library(internal, switchGames = 1)
        TestFiles.library(sd, switchGames = 5)
        val found = Libraries.find(listOf(StorageRoot("Internal storage", internal, false), StorageRoot("SD card", sd, true)))
        assertEquals("SD card", found.first().label)
        assertNull(Libraries.read(File(tmp.root, "nothing/ROMs"), "x"))
    }

    @Test
    fun `title IDs in brackets, parentheses and between underscores`() {
        assertEquals("01002E7016C46800", Classifier.switchId("Cult of the Lamb [01002E7016C46800][v1966080].nsp"))
        assertEquals("0100ABCDEF012000", Classifier.switchId("x (0100abcdef012000).xci"))
        assertEquals("01007EF00011E800", Classifier.switchId("The Legend of Zelda (Update 1.6.0)_01007EF00011E800__US__v786432_.zip"))
        assertNull(Classifier.switchId("Hades [01002E70].xci"))
        assertNull(Classifier.switchId("plain 01007EF00011E800 name.nsp"))
    }

    @Test
    fun `switch game, update and DLC by title ID`() {
        val lib = lib()
        val game = Classifier.classify(TestFiles.file(dl, "Dead Cells [0100646009FBE000][v0].xci"), lib)
        assertEquals("switch", game.system)
        assertEquals("", game.sub)
        assertEquals(SwitchKind.Game, game.switchKind)
        assertTrue(game.sure)

        val update = Classifier.classify(TestFiles.file(dl, "Dead Cells [0100646009FBE800][v3014656].nsp"), lib)
        assertEquals("update", update.sub)
        assertEquals(SwitchKind.Update, update.switchKind)

        val dlc = Classifier.classify(TestFiles.file(dl, "Dead Cells [0100646009FBF001][v65536].nsp"), lib)
        assertEquals("update", dlc.sub)
        assertEquals(SwitchKind.Dlc, dlc.switchKind)
    }

    @Test
    fun `switch header without title ID`() {
        val guess = Classifier.classify(TestFiles.file(dl, "Some Game.nsp", TestFiles.nspBytes()), lib())
        assertEquals("switch", guess.system)
        assertEquals("Switch header", guess.reason)
        assertTrue(guess.sure)
    }

    @Test
    fun `header checks for other systems`() {
        val wbfs = ByteArray(0x200).also { "WBFS".toByteArray().copyInto(it) }
        assertEquals("wii" to "Wii header", Classifier.sniffConsoleHeader(wbfs))
        val gc = ByteArray(0x200).also { byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D).copyInto(it, 0x1C) }
        assertEquals("gc", Classifier.sniffConsoleHeader(gc)?.first)
        val rvzWii = ByteArray(0x200).also { byteArrayOf('R'.code.toByte(), 'V'.code.toByte(), 'Z'.code.toByte(), 1).copyInto(it); it[0x4B] = 2 }
        assertEquals("wii", Classifier.sniffConsoleHeader(rvzWii)?.first)
        val pvd = ByteArray(0x40).also { "CD001".toByteArray().copyInto(it, 1); "PSP GAME".padEnd(32).toByteArray().copyInto(it, 8) }
        assertEquals("psp", Classifier.sniffIso(pvd, 100)?.first)
        val ps = ByteArray(0x40).also { "CD001".toByteArray().copyInto(it, 1); "PLAYSTATION".padEnd(32).toByteArray().copyInto(it, 8) }
        assertEquals("psx", Classifier.sniffIso(ps, 600L * 1024 * 1024)?.first)
        assertEquals("ps2", Classifier.sniffIso(ps, 900L * 1024 * 1024)?.first)
    }

    @Test
    fun `extension with one system is sure, unknown extension has no guess`() {
        val lib = lib()
        val gba = Classifier.classify(TestFiles.file(dl, "Mario.gba"), lib)
        assertEquals("Game Boy Advance", gba.system)
        assertTrue(gba.sure)
        val none = Classifier.classify(TestFiles.file(dl, "notes.txt"), lib)
        assertNull(none.system)
        assertEquals("No system knows .txt", none.reason)
    }

    @Test
    fun `extension shared by several systems is unsure, systems with games first`() {
        val roms = File(tmp.root, "multi/ROMs")
        TestFiles.file(File(roms, "nes"), "a.nes")
        File(roms, "snes").mkdirs()
        val lib = Libraries.read(roms, "x")!!
        val guess = Classifier.classify(TestFiles.file(dl, "Game.zip"), lib)
        assertEquals("nes", guess.system) // nes has a game, snes not
        assertFalse(guess.sure)
        assertEquals(listOf("nes", "snes"), guess.candidates)
    }

    @Test
    fun `a zip is judged by its largest file and only stays packed if the system reads zip`() {
        val lib = lib()
        val zipped = TestFiles.zip(dl, "pack.zip", mapOf("readme.txt" to 10, "Game [0100AAAA00001000].nsp" to 500))
        val guess = Classifier.classify(zipped, lib)
        assertEquals("switch", guess.system)
        assertEquals(".zip", guess.archiveExt)
        assertFalse(guess.packedOk) // the switch folder doesn't list .zip

        val gbaZip = TestFiles.zip(dl, "gba.zip", mapOf("Mario.gba" to 100))
        val gba = Classifier.classify(gbaZip, lib)
        assertEquals("Game Boy Advance", gba.system)
        assertTrue(gba.packedOk) // the built-in gba entry lists .zip
    }
}
