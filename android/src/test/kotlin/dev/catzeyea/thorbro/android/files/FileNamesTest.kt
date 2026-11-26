package dev.catzeyea.thorbro.android.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileNamesTest {

    @Test
    fun `safe names follow the PC rule`() {
        // Same cases as the PC app's unpack.rs tests.
        assertEquals("The Legend of Zelda - Breath of the Wild.nsp", FileNames.safeName("The Legend of Zelda: Breath of the Wild.nsp"))
        assertEquals("a_b_c", FileNames.safeName("a?b*c."))
        assertEquals("Zelda - BotW", FileNames.safeName("Zelda : BotW"))
        assertEquals("_", FileNames.safeName(" . "))
        assertEquals("x_y", FileNames.safeName("x\u0001y"))
        assertEquals("Normal [0100ABCD00000000].nsp", FileNames.safeName("Normal [0100ABCD00000000].nsp"))
    }

    @Test
    fun `extension is lowercase with dot`() {
        assertEquals(".nsp", FileNames.extOf("Game.NSP"))
        assertEquals(".zip", FileNames.extOf("a.b.zip"))
        assertEquals("", FileNames.extOf("README"))
        assertEquals("", FileNames.extOf(".hidden"))
    }

    @Test
    fun `unfinished downloads are recognized by name`() {
        listOf("a.apk.part", "b.crdownload", "C.PART", "d.nsp.thorbro-part", "e.partial", "f.download").forEach {
            assertTrue(it, FileNames.isUnfinishedName(it))
        }
        listOf("Partition.nsp", "game.nsp", "party.zip").forEach { assertFalse(it, FileNames.isUnfinishedName(it)) }
    }

    @Test
    fun `sizes are decimal with a point`() {
        assertEquals("999 B", FileNames.human(999))
        assertEquals("48 KB", FileNames.human(48_000))
        assertEquals("1.4 GB", FileNames.human(1_400_000_000))
        assertEquals("5.1 GB", FileNames.human(5_120_000_000))
    }

    @Test
    fun `inside checks whole path parts`() {
        assertTrue(FileNames.isInside(File("/storage/emulated/0"), File("/storage/emulated/0/Download")))
        assertTrue(FileNames.isInside(File("/storage/emulated/0"), File("/storage/emulated/0")))
        assertFalse(FileNames.isInside(File("/storage/emulated/0"), File("/storage/emulated/01/x")))
    }
}
