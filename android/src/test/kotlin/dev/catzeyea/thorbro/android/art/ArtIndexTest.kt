package dev.catzeyea.thorbro.android.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** iiSU's art layout with folder names copied from the Thor (and the PC app's art.rs tests). */
class ArtIndexTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun icon(consoles: File, rel: String): File = File(consoles, rel).apply {
        parentFile.mkdirs()
        writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
    }

    @Test
    fun `icons are found by ROM name, folder and base title ID`() {
        val sd = tmp.newFolder("sd")
        val consoles = File(sd, ArtIndex.CONSOLES)
        val botw = icon(consoles, "switch/The Legend of Zelda Breath of the Wild [01007EF00011E800][BASE][v1.5.0]/icon.jpg")
        icon(consoles, "switch/Nine Sols [0100D810170D0800][v327680]/icon.png")
        val ml = icon(consoles, "n3ds/Mario & Luigi - Dream Team (USA) (En,Fr,Es)/icon.jpg")
        val smashDir = "Super Smash Bros. for Nintendo 3DS (Europe) (Rev 11)"
        icon(consoles, "n3ds/$smashDir/$smashDir/icon.png")
        icon(consoles, "switch/Dead Cells [0100646009FBE000][v0]/title.png") // not a cover

        val index = ArtIndex.build(listOf(sd, tmp.newFolder("internal-without-iisu")))
        assertEquals(botw, index.find(listOf("switch"), "The Legend of Zelda Breath of the Wild [01007EF00011E800][BASE][v1.5.0].xci", null))
        // A game Peanuts brings without a ROM: matched by the base title ID in iiSU's folder name.
        assertEquals(botw, index.find(listOf("switch"), null, "01007EF00011E000"))
        assertEquals("The Legend of Zelda Breath of the Wild", index.namesByTitleId["01007EF00011E000"])
        // Updates in iiSU never count as a game's cover.
        assertNull(index.find(listOf("switch"), null, "0100D810170D0000"))
        assertEquals(ml, index.find(listOf("n3ds"), "Mario & Luigi - Dream Team (USA) (En,Fr,Es).3ds", null))
        assertTrue(index.find(listOf("n3ds"), "$smashDir/$smashDir.3ds", null) != null)
        assertTrue(index.find(listOf("n3ds"), "$smashDir.3ds", null) != null)
        assertNull(index.find(listOf("switch"), "Dead Cells [0100646009FBE000][v0].xci", null))
        assertNull(index.find(listOf("gba"), "Pokemon.gba", null))
    }

    @Test
    fun `extensions are stripped like on the PC`() {
        assertEquals("Folder/Game", ArtIndex.stripExt("Folder/Game.3ds"))
        assertEquals("Game [v1.0.4]", ArtIndex.stripExt("Game [v1.0.4]"))
        assertEquals("Neon Abyss [0100BAB01113A000]+[v1.0.4]", ArtIndex.stripExt("Neon Abyss [0100BAB01113A000]+[v1.0.4].nsp"))
    }
}
