package dev.catzeyea.thorbro.android.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileMoverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val renameMover = FileMover { _, _ -> true }
    private val copyMover = FileMover { _, _ -> false }

    @Test
    fun `same storage moves by rename and creates the target folder`() {
        val src = TestFiles.file(tmp.newFolder("dl"), "a.nsp", "AAAA".toByteArray())
        val target = File(tmp.root, "lib/switch/update")
        val outcome = renameMover.move(src, target)
        assertTrue(outcome is MoveOutcome.Moved)
        assertFalse((outcome as MoveOutcome.Moved).copied)
        assertFalse(src.exists())
        assertEquals("AAAA", File(target, "a.nsp").readText())
    }

    @Test
    fun `an existing target is never overwritten`() {
        val src = TestFiles.file(tmp.newFolder("dl"), "a.nsp", "NEW".toByteArray())
        val target = tmp.newFolder("lib")
        TestFiles.file(target, "a.nsp", "OLD".toByteArray())
        for (mover in listOf(renameMover, copyMover)) {
            val outcome = mover.move(src, target)
            assertTrue(outcome is MoveOutcome.Skipped)
            assertEquals("OLD", File(target, "a.nsp").readText())
            assertEquals("NEW", src.readText())
        }
    }

    @Test
    fun `other storage copies, checks, then deletes the source`() {
        val bytes = ByteArray(3_000_000) { (it % 251).toByte() }
        val src = TestFiles.file(tmp.newFolder("internal"), "big.xci", bytes)
        val modified = src.lastModified()
        val target = File(tmp.root, "sd/ROMs/switch")
        var progress = 0L
        val outcome = copyMover.move(src, target, expectedSize = bytes.size.toLong(), expectedModified = modified) { progress = it }
        assertTrue((outcome as MoveOutcome.Moved).copied)
        assertFalse(src.exists())
        val copied = File(target, "big.xci")
        assertTrue(copied.readBytes().contentEquals(bytes))
        assertEquals(bytes.size.toLong(), progress)
        assertEquals(listOf("big.xci"), target.list()!!.toList()) // no .thorbro-copy left
    }

    @Test
    fun `target names are made FAT-safe`() {
        val src = TestFiles.file(tmp.newFolder("dl"), "Zelda: BotW.nsp")
        val outcome = copyMover.move(src, File(tmp.root, "sd"))
        assertEquals("Zelda - BotW.nsp", (outcome as MoveOutcome.Moved).to.name)
    }

    @Test
    fun `a file that changed since the scan is skipped`() {
        val src = TestFiles.file(tmp.newFolder("dl"), "a.nsp", "1234".toByteArray())
        val outcome = renameMover.move(src, tmp.newFolder("lib"), expectedSize = 2)
        assertEquals("Changed since the scan", (outcome as MoveOutcome.Skipped).reason)
        assertTrue(src.exists())
    }

    @Test
    fun `a leftover copy of this app is replaced, nothing else`() {
        val src = TestFiles.file(tmp.newFolder("dl"), "a.nsp", "DATA".toByteArray())
        val target = tmp.newFolder("sd")
        TestFiles.file(target, "a.nsp" + FileMover.COPY_SUFFIX, "half".toByteArray())
        assertTrue(copyMover.move(src, target) is MoveOutcome.Moved)
        assertEquals("DATA", File(target, "a.nsp").readText())
        assertFalse(File(target, "a.nsp" + FileMover.COPY_SUFFIX).exists())
    }

    @Test
    fun `a case-only rename goes through a temporary name`() {
        val dir = tmp.newFolder("lib")
        val wii = File(dir, "Wii").apply { mkdirs() }
        val renamed = renameMover.rename(wii, "wii")
        assertEquals("wii", renamed.name)
        assertEquals(listOf("wii"), dir.list()!!.toList())
    }

    @Test
    fun `a rename never overwrites`() {
        val dir = tmp.newFolder("lib")
        val a = TestFiles.file(dir, "a.txt", "A".toByteArray())
        TestFiles.file(dir, "b.txt", "B".toByteArray())
        val error = runCatching { renameMover.rename(a, "b.txt") }.exceptionOrNull()
        assertTrue(error is java.nio.file.FileAlreadyExistsException)
        assertEquals("B", File(dir, "b.txt").readText())
    }

    @Test
    fun `the log records every move and undo restores the original names`() {
        val dl = tmp.newFolder("Download")
        val a = TestFiles.file(dl, "Zelda: BotW [0100AAAA00000000].nsp", "A".toByteArray())
        val b = TestFiles.file(dl, "b.gba", "B".toByteArray())
        val logDir = File(tmp.root, "ThorBro/logs")
        val log = MoveLog.create(logDir, 1_700_000_000_000)
        log.begin(dl, 1_700_000_000_000)
        for ((file, target) in listOf(a to File(tmp.root, "sd/switch"), b to File(tmp.root, "sd/gba"))) {
            val outcome = copyMover.move(file, target) as MoveOutcome.Moved
            log.moved(MoveLog.Entry(outcome.from, outcome.to, 1, outcome.copied))
        }
        assertTrue(log.file.name.startsWith("moves-") && log.file.name.endsWith(".jsonl"))
        assertEquals(2, log.entries().size)
        assertEquals(log.file, MoveLog.lastUndoable(logDir)?.file)

        val result = Undo.run(log, copyMover)
        assertEquals(2, result.restored)
        assertTrue(result.problems.isEmpty())
        assertEquals("A", File(dl, "Zelda: BotW [0100AAAA00000000].nsp").readText())
        assertEquals("B", File(dl, "b.gba").readText())
        assertTrue(log.isUndone)
        assertEquals(null, MoveLog.lastUndoable(logDir))
    }

    @Test
    fun `undo does not overwrite a new file at the old place`() {
        val dl = tmp.newFolder("Download")
        val a = TestFiles.file(dl, "a.nsp", "OLD".toByteArray())
        val log = MoveLog.create(File(tmp.root, "logs"), 1)
        val moved = renameMover.move(a, File(tmp.root, "lib")) as MoveOutcome.Moved
        log.moved(MoveLog.Entry(moved.from, moved.to, 3, false))
        TestFiles.file(dl, "a.nsp", "NEWER".toByteArray())
        val result = Undo.run(log, renameMover)
        assertEquals(0, result.restored)
        assertEquals(1, result.problems.size)
        assertEquals("NEWER", File(dl, "a.nsp").readText())
        assertEquals("OLD", moved.to.readText())
    }

    @Test
    fun `pins and the default scan folder`() {
        val download = File("/storage/emulated/0/Download")
        val prefs = FolderPrefs.defaults(download)
        assertEquals(listOf(download.path), prefs.pins)
        val pinned = prefs.togglePin("/x/ROMs/switch")
        assertTrue(pinned.isPinned("/x/ROMs/switch"))
        assertFalse(pinned.togglePin("/x/ROMs/switch").isPinned("/x/ROMs/switch"))
        val scan = prefs.withDefaultScan("/x/Games")
        assertEquals("/x/Games", scan.defaultScan)
        assertTrue(scan.isPinned("/x/Games"))
        assertEquals(scan, FolderPrefs.fromJson(scan.toJson(), download))
        assertEquals(FolderPrefs.defaults(download), FolderPrefs.fromJson("{broken", download))

        val file = File(tmp.root, "folders.json")
        val store = FolderPrefsStore(file, download)
        assertEquals(FolderPrefs.defaults(download), store.load())
        store.save(scan)
        assertEquals(scan, store.load())
    }
}
