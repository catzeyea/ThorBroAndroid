package dev.catzeyea.thorbro.android.saves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Saves on a fake Android/data tree in a temporary folder. */
class SwitchSavesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val eden = SwitchSaves.EMULATORS.first { it.packageName == "dev.igawa6.edenduo" }
    private val androidData get() = File(tmp.root, "Android/data")
    private val backups get() = File(tmp.root, "ThorBro/SaveBackups")

    private fun saveDir(titleId: String) =
        File(SwitchSaves.saveRoot(androidData, eden), "0000000000000000/5D1B4CDA089A581DAA9E48BC2B43C120/$titleId")

    private fun makeSave(titleId: String, content: String): File = saveDir(titleId).apply {
        mkdirs()
        File(this, "progress.dat").writeText(content)
        File(this, "sub").mkdirs()
        File(this, "sub/slot1.bin").writeText("$content-slot")
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
        assertEquals(3, names.size) // the update (…800) belongs to Dead Cells, the base file name wins
    }

    @Test
    fun `without a base game file the name comes from an update or DLC`() {
        val names = SwitchSaves.namesFromFileNames(
            listOf(
                // Real names from the Thor: the BotW base file carries the update ID.
                "The Legend of Zelda Breath of the Wild [01007EF00011E800][BASE][v1.5.0].xci",
                "Dead Cells [DLC Fatal Falls][0100646009FBF003][v0].nsp",
            ),
        )
        assertEquals("The Legend of Zelda Breath of the Wild", names["01007EF00011E000"])
        assertEquals("Dead Cells", names["0100646009FBE000"])
        assertEquals("0100646009FBE000" to 2, SwitchSaves.baseTitleId("0100646009FBF001"))
    }

    @Test
    fun `saves are found by title ID, unknown names show the title ID`() {
        makeSave("0100646009FBE000", "dc")
        makeSave("01006A800016E000", "smash")
        File(SwitchSaves.saveRoot(androidData, eden), "0000000000000000/5D1B4CDA089A581DAA9E48BC2B43C120/8000000000000010").mkdirs()
        val found = SwitchSaves(backups).find(androidData, mapOf("0100646009FBE000" to "Dead Cells"))
        assertEquals(listOf("01006A800016E000", "Dead Cells"), found.map { it.name }) // system save ignored
        assertNull(found.first().lastBackup)
        assertTrue(found.all { it.size > 0 })
    }

    @Test
    fun `back up copies the save with a note where it came from`() {
        makeSave("0100646009FBE000", "v1")
        val saves = SwitchSaves(backups)
        val save = saves.find(androidData, mapOf("0100646009FBE000" to "Dead Cells")).single()
        val dir = saves.backUp(save, time = 1_700_000_000_000)
        assertEquals(File(backups, "Dead Cells"), dir.parentFile)
        assertEquals("v1", File(dir, "save/progress.dat").readText())
        assertEquals("v1-slot", File(dir, "save/sub/slot1.bin").readText())
        assertTrue(File(dir, SwitchSaves.META).readText().contains("dev.igawa6.edenduo"))
        assertEquals(1_700_000_000_000, saves.find(androidData, emptyMap()).single().lastBackup)
    }

    @Test
    fun `restore puts the latest backup back and backs up the current save first`() {
        makeSave("0100646009FBE000", "good")
        val saves = SwitchSaves(backups)
        val save = saves.find(androidData, mapOf("0100646009FBE000" to "Dead Cells")).single()
        saves.backUp(save, time = 1_000)
        // The game overwrites the save with something worse.
        File(saveDir("0100646009FBE000"), "progress.dat").writeText("broken")
        File(saveDir("0100646009FBE000"), "new-file.tmp").writeText("x")

        saves.restoreLatest(save, time = 2_000)
        val dir = saveDir("0100646009FBE000")
        assertEquals("good", File(dir, "progress.dat").readText())
        assertFalse(File(dir, "new-file.tmp").exists())
        // Nothing of ours is left next to the save.
        assertEquals(listOf(dir.name), dir.parentFile!!.list()!!.toList())
        // The broken save was backed up before (note "before restore"), and it is now the newest backup.
        val newest = saves.backups(save.titleId, eden).first()
        assertEquals(2_000L, newest.second)
        assertEquals("broken", File(newest.first, "save/progress.dat").readText())
    }

    @Test
    fun `restore without a backup fails and changes nothing`() {
        makeSave("0100646009FBE000", "v1")
        val saves = SwitchSaves(backups)
        val save = saves.find(androidData, emptyMap()).single()
        assertTrue(runCatching { saves.restoreLatest(save) }.isFailure)
        assertEquals("v1", File(saveDir("0100646009FBE000"), "progress.dat").readText())
        assertFalse(backups.exists())
    }
}
