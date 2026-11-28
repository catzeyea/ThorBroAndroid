package dev.catzeyea.thorbro.android.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

class FolderListingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(name: String) = FileEntry(File("/x/$name"), isDir = false, size = 1, modified = 0)
    private fun dir(name: String, count: Int? = null) = FileEntry(File("/x/$name"), isDir = true, size = 0, modified = 0, childCount = count)

    @Test
    fun `folders come first, names in natural order`() {
        val entries = listOf(file("Shot_10.png"), dir("b"), file("shot_2.png"), dir("A"), file("Shot_1.png"))
        val sorted = FolderListing.sort(entries, SortOrder()).map { it.name }
        assertEquals(listOf("A", "b", "Shot_1.png", "shot_2.png", "Shot_10.png"), sorted)
    }

    @Test
    fun `descending reverses names but keeps folders first`() {
        val entries = listOf(file("a.txt"), dir("x"), file("c.txt"), dir("y"))
        val sorted = FolderListing.sort(entries, SortOrder(descending = true)).map { it.name }
        assertEquals(listOf("y", "x", "c.txt", "a.txt"), sorted)
    }

    @Test
    fun `natural order compares numbers by value and is total`() {
        val c = FolderListing.naturalNames
        assertTrue(c.compare("file2", "file10") < 0)
        assertTrue(c.compare("file010", "file9") > 0)
        assertTrue(c.compare("ABC", "abd") < 0)
        // Same letters, different case: still a defined order (stable sort needs it).
        assertTrue(c.compare("a", "A") != 0)
        assertTrue(c.compare("a", "a") == 0)
        assertTrue(c.compare("Disc 1", "Disc 1 (copy)") < 0)
    }

    @Test
    fun `filter is case-insensitive and blank keeps all`() {
        val entries = listOf(file("Hades.xci"), file("celeste.zip"), dir("Switch"))
        assertEquals(listOf("Hades.xci"), FolderListing.filter(entries, "hAD").map { it.name })
        assertEquals(3, FolderListing.filter(entries, "  ").size)
        assertEquals(0, FolderListing.filter(entries, "zelda").size)
    }

    @Test
    fun `type chips follow the extension`() {
        assertEquals(TypeChip("XCI", FileKind.Game), FolderListing.typeChip("Hades [01002E70].xci"))
        assertEquals(TypeChip("NSP", FileKind.Game), FolderListing.typeChip("Mario.NSP"))
        assertEquals(TypeChip("ZIP", FileKind.Archive), FolderListing.typeChip("Celeste_update.zip"))
        assertEquals(TypeChip("APK", FileKind.App), FolderListing.typeChip("app.apk"))
        assertEquals(TypeChip("SAV", FileKind.Save), FolderListing.typeChip("Hollow Knight.sav"))
        assertEquals(TypeChip("TXT", FileKind.Text), FolderListing.typeChip("setup-notes.txt"))
        assertEquals(TypeChip("PNG", FileKind.Image), FolderListing.typeChip("shot.png"))
        assertEquals(TypeChip("FILE", FileKind.Other), FolderListing.typeChip("README"))
        // Long extensions are cut to four letters.
        assertEquals("THOR", FolderListing.typeChip("x.thorbro-copy").label)
    }

    @Test
    fun `size text shows item counts for folders`() {
        assertEquals("", FolderListing.sizeText(dir("a")))
        assertEquals("1 item", FolderListing.sizeText(dir("a", 1)))
        assertEquals("12 items", FolderListing.sizeText(dir("a", 12)))
        assertEquals("2.8 GB", FolderListing.sizeText(FileEntry(File("/x/g.xci"), false, 2_800_000_000, 0)))
    }

    @Test
    fun `size and date are one text with a fixed-width date`() {
        val date = "22.11.2026"
        assertEquals(FolderListing.DATE_CHARS, date.length)
        assertEquals("12 items      22.11.2026", FolderListing.metaText(dir("a", 12), date))
        // Not counted yet: only the date, still right-aligned at the same place.
        assertEquals("      22.11.2026", FolderListing.metaText(dir("a"), date))
    }

    @Test
    fun `date like the boards`() {
        // 2026-11-22 12:00 UTC
        assertEquals("22.11.2026", FolderListing.formatDate(1_795_348_800_000, ZoneId.of("UTC")))
    }

    @Test
    fun `pages for the footer`() {
        assertEquals(1 to 4, FolderListing.pages(total = 38, perPage = 10, firstVisible = 0))
        assertEquals(2 to 4, FolderListing.pages(total = 38, perPage = 10, firstVisible = 10))
        // The last rows are on screen: last page, even if the first visible row is not on it.
        assertEquals(4 to 4, FolderListing.pages(total = 38, perPage = 10, firstVisible = 28))
        assertEquals(1 to 1, FolderListing.pages(total = 0, perPage = 10, firstVisible = 0))
        assertEquals(1 to 1, FolderListing.pages(total = 5, perPage = 10, firstVisible = 0))
    }

    @Test
    fun `read hides dot files and counts children`() {
        val root = tmp.newFolder("root")
        File(root, ".hidden").writeText("x")
        File(root, "a.txt").writeText("hello")
        val sub = File(root, "sub").apply { mkdir() }
        File(sub, "1").writeText("")
        File(sub, ".nomedia").writeText("")
        val listed = FolderListing.read(root)!!.associateBy { it.name }
        assertEquals(setOf("a.txt", "sub"), listed.keys)
        assertEquals(5L, listed.getValue("a.txt").size)
        assertEquals(1, FolderListing.countChildren(sub))
        assertNull(FolderListing.read(File(root, "missing")))
    }
}
