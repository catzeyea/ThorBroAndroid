package dev.catzeyea.thorbro.android.saves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Saves on a fake Android/data tree in a temporary folder (never on a device). */
class SwitchSavesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val androidData get() = File(tmp.root, "Android/data")
    private val backups get() = File(tmp.root, "ThorBro/SaveBackups")

    /** The user UUID as stored in profiles.dat; the save folder name is its bytes reversed. */
    private val uuid = byteArrayOf(0x20, 0xC1.toByte(), 0x43, 0x2B, 0xBC.toByte(), 0x48, 0x9E.toByte(), 0xAA.toByte(), 0x1D, 0x58, 0x9A.toByte(), 0x08, 0xDA.toByte(), 0x4C, 0x1B, 0x5D)
    private val user = "5D1B4CDA089A581DAA9E48BC2B43C120" // real value from the Thor

    private fun profilesDat(slot: Int = 0): ByteArray = ByteArray(0x10 + 8 * 0xC8).also { uuid.copyInto(it, 0x10 + slot * 0xC8) }

    /** An Eden-style app folder: config.ini, profiles.dat, saves of [user] and of an orphan user. */
    private fun yuzuApp(pkg: String, withProfiles: Boolean = true): File {
        val app = File(androidData, pkg)
        val nand = File(app, "files/nand")
        File(app, "files/config").mkdirs()
        File(app, "files/config/config.ini").writeText("[Data%20Storage]\nnand_directory=${nand.path}\n\n[System]\ncurrent_user=0\n")
        if (withProfiles) File(nand, "system/save/8000000000000010/su/avators").apply { mkdirs() }.resolve("profiles.dat").writeBytes(profilesDat())
        return nand
    }

    private fun save(nand: File, userFolder: String, titleId: String, content: String): File =
        File(nand, "user/save/0000000000000000/$userFolder/$titleId").apply {
            mkdirs()
            File(this, "progress.dat").writeText(content)
            File(this, "sub").mkdirs()
            File(this, "sub/slot1.bin").writeText("$content-slot")
        }

    @Test
    fun `user folder from profiles dat, bytes reversed`() {
        assertEquals(user, SwitchSaves.userFolder(profilesDat(), 0))
        assertEquals(user, SwitchSaves.userFolder(profilesDat(slot = 2), 0)) // empty slot 0: first user
        assertNull(SwitchSaves.userFolder(ByteArray(0x200), 0))
        assertEquals("/x/nand", SwitchSaves.iniValue("[Data%20Storage]\nnand_directory=/x/nand\nnand_directory\\default=true", "Data%20Storage", "nand_directory"))
        assertNull(SwitchSaves.iniValue("[Other]\nnand_directory=/x", "Data%20Storage", "nand_directory"))
        assertNull(SwitchSaves.iniValue("[Data%20Storage]\nnand_directory=", "Data%20Storage", "nand_directory"))
    }

    @Test
    fun `only the current user's saves count, orphan user folders are ignored`() {
        val nand = yuzuApp("dev.igawa6.edenduo")
        save(nand, user, "0100646009FBE000", "dc")
        save(nand, "1DAA9E48BC2B43C12000000000000000", "01006A800016E000", "orphan") // real orphan name from the Thor
        File(nand, "user/save/0000000000000000/$user/8000000000000010").mkdirs() // system save
        val scan = SwitchSaves(backups).find(androidData, mapOf("0100646009FBE000" to "Dead Cells"))
        assertEquals(listOf("Dead Cells"), scan.saves.map { it.name })
        assertTrue(scan.notes.isEmpty())
    }

    @Test
    fun `without profiles dat every user folder is used and the app says so`() {
        val nand = yuzuApp("dev.eden.eden_emulator", withProfiles = false)
        save(nand, user, "0100646009FBE000", "a")
        save(nand, "1DAA9E48BC2B43C12000000000000000", "01006A800016E000", "b")
        val scan = SwitchSaves(backups).find(androidData, emptyMap())
        assertEquals(2, scan.saves.size)
        assertEquals(1, scan.notes.size)
        assertTrue(scan.notes.single().contains("profiles.dat"))
    }

    @Test
    fun `Peanuts saves are read, work and lock files ignored, restore is refused`() {
        val saves = File(androidData, "dev.nxnative.android.peanuts/files/saves")
        File(saves, "01007EF00011E000").apply { mkdirs() }.resolve("game.sav").writeText("botw")
        File(saves, "01007EF00011E000.work").apply { mkdirs() }.resolve("game.sav").writeText("botw")
        File(saves, "01007EF00011E000.lock").writeText("")
        val switchSaves = SwitchSaves(backups)
        val save = switchSaves.find(androidData, mapOf("01007EF00011E000" to "Zelda")).saves.single()
        assertEquals("Peanuts", save.emulator.label)
        assertFalse(save.emulator.canRestore)
        val dir = switchSaves.backUp(save, time = 1_000)
        assertEquals("botw", File(dir, "save/game.sav").readText())
        val backup = switchSaves.find(androidData, emptyMap()).saves.single().backups.single()
        assertTrue(runCatching { switchSaves.restore(save, backup) }.isFailure)
        assertEquals("botw", File(saves, "01007EF00011E000/game.sav").readText())
    }

    @Test
    fun `game names come from title IDs in ROM file names`() {
        val names = SwitchSaves.namesFromFileNames(
            listOf(
                "Dead Cells [0100646009FBE000][v0].xci",
                "Dead Cells [0100646009FBE800][v3014656][US].nsp",
                "Super Mario Odyssey[0100000000010000][V0].nsp",
                "Neon Abyss [0100BAB01113A000]+[v1.0.4].nsp",
                "systeminfo.txt",
            ),
        )
        assertEquals("Dead Cells", names["0100646009FBE000"])
        assertEquals("Super Mario Odyssey", names["0100000000010000"])
        assertEquals("Neon Abyss", names["0100BAB01113A000"])
        assertEquals(3, names.size)
    }

    @Test
    fun `BASE files and DLC give names too`() {
        val names = SwitchSaves.namesFromFileNames(
            listOf(
                "The Legend of Zelda Breath of the Wild [01007EF00011E800][BASE][v1.5.0].xci",
                "Dead Cells [DLC Fatal Falls][0100646009FBF003][v0].nsp",
            ),
        )
        assertEquals("The Legend of Zelda Breath of the Wild", names["01007EF00011E000"])
        assertEquals("Dead Cells", names["0100646009FBE000"])
        assertEquals("0100646009FBE000" to 2, SwitchSaves.baseTitleId("0100646009FBF001"))
        assertEquals("01007EF00011E000" to 0, SwitchSaves.baseTitleId("01007EF00011E800", "x [BASE]"))
    }

    @Test
    fun `backups are found in one pass and sorted newest first`() {
        val nand = yuzuApp("dev.eden.eden_emulator")
        save(nand, user, "0100646009FBE000", "v1")
        val switchSaves = SwitchSaves(backups)
        val save = switchSaves.find(androidData, mapOf("0100646009FBE000" to "Dead Cells")).saves.single()
        switchSaves.backUp(save, time = 1_000)
        switchSaves.backUp(save, time = 5_000)
        File(backups, "Junk/folder").mkdirs() // no backup.json: ignored
        val index = switchSaves.backupIndex()
        assertEquals(listOf(5_000L, 1_000L), index.getValue("dev.eden.eden_emulator/0100646009FBE000").map { it.time })
        val again = switchSaves.find(androidData, emptyMap()).saves.single()
        assertEquals(5_000L, again.lastBackup)
        assertEquals(2, again.backups.size)
        assertEquals("v1-slot", File(again.backups.last().saveDir, "sub/slot1.bin").readText())
    }

    @Test
    fun `restore puts the selected backup back and backs up the current save first`() {
        val nand = yuzuApp("dev.eden.eden_emulator")
        val dir = save(nand, user, "0100646009FBE000", "old")
        val switchSaves = SwitchSaves(backups)
        var save = switchSaves.find(androidData, mapOf("0100646009FBE000" to "Dead Cells")).saves.single()
        switchSaves.backUp(save, time = 1_000) // "old"
        File(dir, "progress.dat").writeText("good")
        switchSaves.backUp(save, time = 2_000) // "good"
        File(dir, "progress.dat").writeText("broken")
        File(dir, "new-file.tmp").writeText("x")

        save = switchSaves.find(androidData, emptyMap()).saves.single()
        val oldest = save.backups.last()
        switchSaves.restore(save, oldest, time = 3_000)
        assertEquals("old", File(dir, "progress.dat").readText())
        assertFalse(File(dir, "new-file.tmp").exists())
        assertEquals(listOf(dir.name), dir.parentFile!!.list()!!.toList()) // nothing of ours left next to it
        val newest = switchSaves.find(androidData, emptyMap()).saves.single().backups.first()
        assertEquals(3_000L, newest.time)
        assertEquals("before restore", newest.note)
        assertEquals("broken", File(newest.saveDir, "progress.dat").readText())
    }
}
