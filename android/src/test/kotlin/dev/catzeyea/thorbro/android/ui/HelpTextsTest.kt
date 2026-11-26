package dev.catzeyea.thorbro.android.ui

import dev.catzeyea.thorbro.android.files.ScanGroup
import dev.catzeyea.thorbro.android.files.ScanPlanner
import dev.catzeyea.thorbro.android.files.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The Scan explanations must stay true for the code. */
class HelpTextsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `everything the card says stays where it is really stays`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        File(dl, "A folder").mkdirs()
        listOf(
            "game.nsp.part", "game.nsp.crdownload", // unfinished downloads
            "Tailscale.apk", "helper.jar", // apps
            "Hollow Knight.sav", "my saves.zip", // saves
            "botw-cheats.zip", "main.pchtxt", // cheats
            "Firmware.23.0.1.zip", // firmware
        ).forEach { TestFiles.file(dl, it) }
        val result = ScanPlanner.plan(dl, lib, now = System.currentTimeMillis() + 10 * ScanPlanner.RECENT_MILLIS) { _, _, _ -> false }
        assertEquals(10, result.items.size)
        assertTrue(result.toMove.isEmpty())
        assertTrue(result.items.all { it.group == ScanGroup.Review || it.group == ScanGroup.Skipped })
    }

    @Test
    fun `unsure files start switched off, as the second point says`() {
        val lib = TestFiles.library(tmp.newFolder("sd"))
        val dl = tmp.newFolder("Download")
        TestFiles.file(dl, "Rom.zip")
        val result = ScanPlanner.plan(dl, lib, now = System.currentTimeMillis() + 10 * ScanPlanner.RECENT_MILLIS) { _, _, _ -> false }
        assertTrue(result.defaultSelection.isEmpty())
        assertTrue(HelpTexts.SCAN_POINTS[1].text.contains("Unsure files start switched off"))
    }

    @Test
    fun `no text promises an undo later`() {
        val all = HelpTexts.SCAN_POINTS.joinToString(" ") { it.text } + HelpTexts.FILES + HelpTexts.SAVES
        assertFalse(all.contains("any time", ignoreCase = true))
        assertFalse(all.contains("anytime", ignoreCase = true))
        assertTrue(HelpTexts.SCAN_POINTS[2].text.contains("Undo on the result screen"))
        assertTrue(HelpTexts.SCAN_POINTS[2].text.contains("ThorBro/logs"))
    }
}
