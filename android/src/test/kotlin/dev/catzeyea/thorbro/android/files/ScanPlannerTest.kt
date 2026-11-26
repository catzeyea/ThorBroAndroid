package dev.catzeyea.thorbro.android.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScanPlannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Files in the tests are "old" unless a test says otherwise, so no second sample is taken. */
    private val later = System.currentTimeMillis() + 10 * ScanPlanner.RECENT_MILLIS
    private val neverGrowing: (File, Long, Long) -> Boolean = { _, _, _ -> false }

    private fun plan(dl: File, lib: GameLibrary?, now: Long = later, grow: (File, Long, Long) -> Boolean = neverGrowing) =
        ScanPlanner.plan(dl, lib, now, grow)

    private fun ScanResult.item(name: String) = items.single { it.file.name == name }

    @Test
    fun `games, updates and DLC get their library targets`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "Celeste [0100ABCD12345000].nsp")
        TestFiles.file(dl, "Celeste [0100ABCD12345800][v1].nsp")
        TestFiles.file(dl, "Celeste [0100ABCD12346001].nsp")
        TestFiles.file(dl, "Mario.gba")
        val result = plan(dl, lib)
        val switch = File(lib.path, "switch")
        assertEquals(ScanGroup.Games, result.item("Celeste [0100ABCD12345000].nsp").group)
        assertEquals(switch, result.item("Celeste [0100ABCD12345000].nsp").target)
        assertEquals(ScanGroup.Updates, result.item("Celeste [0100ABCD12345800][v1].nsp").group)
        assertEquals(File(switch, "update"), result.item("Celeste [0100ABCD12345800][v1].nsp").target)
        assertEquals(ScanGroup.Dlc, result.item("Celeste [0100ABCD12346001].nsp").group)
        assertEquals(File(lib.path, "Game Boy Advance"), result.item("Mario.gba").target)
        assertEquals(4, result.toMove.size)
    }

    @Test
    fun `saves, cheats, APKs and firmware are never guessed`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        listOf("Hollow Knight.sav", "botw-cheats.zip", "main.pchtxt", "Tailscale.apk", "Firmware.23.0.1.zip", "my saves.zip")
            .forEach { TestFiles.file(dl, it) }
        val result = plan(dl, lib)
        result.items.forEach {
            assertEquals(it.file.name, ScanGroup.Review, it.group)
            assertNull(it.target)
        }
        assertTrue(result.item("Tailscale.apk").reason.startsWith("App"))
        assertTrue(result.item("Firmware.23.0.1.zip").reason.contains("firmware"))
        assertTrue(result.item("botw-cheats.zip").reason.contains("cheats"))
        assertTrue(result.item("Hollow Knight.sav").reason.contains("save"))
    }

    @Test
    fun `unfinished downloads are skipped, by name and when still growing`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "gamenative-v1.4.0.apk.part")
        TestFiles.file(dl, "Game [0100ABCD12345000].nsp.crdownload")
        val growing = TestFiles.file(dl, "Growing [0100ABCD22345000].nsp")
        val now = System.currentTimeMillis()
        val result = plan(dl, lib, now = now) { file, _, _ -> file == growing }
        assertEquals(ScanGroup.Skipped, result.item("gamenative-v1.4.0.apk.part").group)
        assertEquals(ScanGroup.Skipped, result.item("Game [0100ABCD12345000].nsp.crdownload").group)
        assertEquals(ScanGroup.Skipped, result.item("Growing [0100ABCD22345000].nsp").group)
        assertTrue(result.toMove.isEmpty())
    }

    @Test
    fun `7z, rar, zips that need extracting and folders stay in Review`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "TLOZ-BOTW-_Update160_.rar")
        TestFiles.file(dl, "pack.7z")
        TestFiles.zip(dl, "BotW (Update)_01007EF00011E800__US_.zip", mapOf("update.nsp" to 100))
        File(dl, "Some folder").mkdirs()
        val result = plan(dl, lib)
        result.items.forEach { assertEquals(it.file.name, ScanGroup.Review, it.group) }
        assertTrue(result.item("BotW (Update)_01007EF00011E800__US_.zip").reason.contains("extract"))
        assertTrue(result.item("Some folder").reason.startsWith("Folder"))
    }

    @Test
    fun `an unsure guess can move but starts switched off, sure guesses start on`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "Rom.zip") // .zip fits gba and snes, no readable inner file: unsure
        TestFiles.file(dl, "Celeste [0100ABCD12345000].nsp")
        val result = plan(dl, lib)
        val unsure = result.item("Rom.zip")
        assertEquals(ScanGroup.Games, unsure.group)
        assertEquals(File(lib.path, "Game Boy Advance"), unsure.target) // the one that already has games
        assertTrue(!unsure.onByDefault)
        assertTrue(result.item("Celeste [0100ABCD12345000].nsp").onByDefault)
        assertEquals(setOf(File(dl, "Celeste [0100ABCD12345000].nsp")), result.defaultSelection)
        assertEquals(2, result.toMove.size)
    }

    @Test
    fun `Java apps are never sorted`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val roms = File(lib.path.parentFile, "ROMs")
        TestFiles.file(File(roms, "j2me"), "systeminfo.txt", "Full system name:\nJ2ME\n\nSupported file extensions:\n.jar .JAR\n".toByteArray())
        val withJ2me = Libraries.read(roms, "x")!!
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "aynthor_secondscreen_v1_21-1.0.1.jar")
        TestFiles.file(dl, "game.jad")
        val result = plan(dl, withJ2me)
        result.items.forEach {
            assertEquals(ScanGroup.Review, it.group)
            assertEquals("Java app: not sorted", it.reason)
        }
    }

    @Test
    fun `data files are never offered as games, not even unsure ones`() {
        // A library whose wii folder lists .json, like ES-DE's on the Thor.
        val roms = File(tmp.newFolder("sd"), "ROMs")
        TestFiles.file(File(roms, "wii"), "systeminfo.txt", "Full system name:\nNintendo Wii\n\nSupported file extensions:\n.iso .json .rvz\n".toByteArray())
        TestFiles.file(File(roms, "wii"), "Kirby.rvz")
        val lib = Libraries.read(roms, "x")!!
        val dl = tmp.newFolder("Download")
        listOf("Rocket_League_config.json", "emuladores.json", "list.xml", "notes.txt", "game.cfg", "setup.INI").forEach { TestFiles.file(dl, it) }
        val result = plan(dl, lib)
        assertEquals(6, result.items.size)
        result.items.forEach {
            assertEquals(it.file.name, ScanGroup.Review, it.group)
            assertEquals("Not a game file", it.reason)
            assertNull(it.target)
        }
        assertTrue(result.toMove.isEmpty())
    }

    @Test
    fun `a base game tagged BASE goes to the switch folder, not to updates`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "The Legend of Zelda Breath of the Wild [01007EF00011E800][BASE][v1.5.0].xci")
        val item = plan(dl, lib).items.single()
        assertEquals(ScanGroup.Games, item.group)
        assertEquals(File(lib.path, "switch"), item.target)
    }

    @Test
    fun `without a library nothing moves, hidden files are ignored`() {
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "Game [0100ABCD12345000].nsp")
        TestFiles.file(dl, ".thorbro-probe")
        val result = plan(dl, null)
        assertEquals(1, result.items.size)
        assertEquals(ScanGroup.Review, result.items.single().group)
    }

    @Test
    fun `a file that is already in its target folder is listed as already in place`() {
        val lib = TestFiles.library(tmp.newFolder("sd"), switchGames = 1)
        val result = plan(File(lib.path, "switch"), lib)
        assertTrue(result.toMove.isEmpty())
        val nsp = result.items.first { it.file.name.endsWith(".nsp") }
        assertEquals(ScanGroup.InPlace, nsp.group)
        assertEquals("Already in Nintendo Switch", nsp.reason)
        assertEquals(1, result.group(ScanGroup.InPlace).size)
        assertTrue(result.group(ScanGroup.Review).none { it.file.name.endsWith(".nsp") })
    }
}
