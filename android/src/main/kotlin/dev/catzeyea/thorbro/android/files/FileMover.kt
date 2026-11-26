package dev.catzeyea.thorbro.android.files

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** Result of moving one file. */
sealed interface MoveOutcome {
    data class Moved(val from: File, val to: File, val copied: Boolean) : MoveOutcome
    data class Skipped(val file: File, val reason: String) : MoveOutcome
    data class Failed(val file: File, val reason: String) : MoveOutcome
}

/**
 * Moves files without ever overwriting anything.
 *
 * - Same storage: `Files.move` without REPLACE_EXISTING (fails if the target exists). Never
 *   `File.renameTo`: on the Thor it silently overwrites an existing target.
 * - Different storage: copy to `<name>.thorbro-copy` in the target folder, check the size, rename to
 *   the final name (again without replacing), then delete the source. A failed copy only removes
 *   its own temporary file.
 * - Target names are made FAT-safe ([FileNames.safeName]).
 *
 * [sameStorage] decides between rename and copy; on the Thor it compares the storage roots.
 */
class FileMover(private val sameStorage: (File, File) -> Boolean) {

    companion object {
        const val COPY_SUFFIX = ".thorbro-copy"
        const val RENAME_SUFFIX = ".thorbro-rename"
        private const val BUFFER = 1 shl 20
    }

    /**
     * Moves [source] into [targetDir]. [progress] gets copied bytes (only for copies);
     * [expectedSize] / [expectedModified]: the move is skipped if the file changed since the scan.
     */
    fun move(
        source: File,
        targetDir: File,
        expectedSize: Long? = null,
        expectedModified: Long? = null,
        progress: (Long) -> Unit = {},
    ): MoveOutcome = moveTo(source, File(targetDir, FileNames.safeName(source.name)), expectedSize, expectedModified, progress)

    /** Like [move], but to an exact target file (used by undo, which restores the original name). */
    fun moveTo(
        source: File,
        target: File,
        expectedSize: Long? = null,
        expectedModified: Long? = null,
        progress: (Long) -> Unit = {},
    ): MoveOutcome {
        if (!source.isFile) return MoveOutcome.Skipped(source, "No longer there")
        if (expectedSize != null && source.length() != expectedSize ||
            expectedModified != null && source.lastModified() != expectedModified
        ) {
            return MoveOutcome.Skipped(source, "Changed since the scan")
        }
        val targetDir = target.parentFile ?: return MoveOutcome.Failed(source, "Invalid target")
        if (target.exists()) return MoveOutcome.Skipped(source, "Already exists there")
        return try {
            Files.createDirectories(targetDir.toPath())
            if (sameStorage(source, targetDir)) {
                Files.move(source.toPath(), target.toPath())
                MoveOutcome.Moved(source, target, copied = false)
            } else {
                copyCheckDelete(source, target, progress)
                MoveOutcome.Moved(source, target, copied = true)
            }
        } catch (e: FileAlreadyExistsException) {
            MoveOutcome.Skipped(source, "Already exists there")
        } catch (e: IOException) {
            MoveOutcome.Failed(source, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun copyCheckDelete(source: File, target: File, progress: (Long) -> Unit) {
        val temp = File(target.parentFile, target.name + COPY_SUFFIX)
        // A leftover of an earlier, interrupted copy by this app: only our own suffix is removed.
        if (temp.exists()) Files.delete(temp.toPath())
        try {
            var copied = 0L
            FileInputStream(source).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        progress(copied)
                    }
                    output.fd.sync()
                }
            }
            if (temp.length() != source.length()) throw IOException("Copy is incomplete (${temp.length()} of ${source.length()} bytes)")
            Files.move(temp.toPath(), target.toPath())
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
        target.setLastModified(source.lastModified())
        Files.delete(source.toPath())
    }

    /**
     * Renames [file] to [newName] in the same folder, never overwriting. A rename that only changes
     * upper/lower case goes through a temporary name: shared storage on the Thor ignores case, so a
     * direct rename "succeeds" but keeps the old name.
     */
    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        if (file.name == newName) return file
        if (file.name.equals(newName, ignoreCase = true)) {
            val temp = File(file.parentFile, file.name + RENAME_SUFFIX)
            if (temp.exists()) throw FileAlreadyExistsException(temp.path)
            Files.move(file.toPath(), temp.toPath())
            Files.move(temp.toPath(), target.toPath())
            return target
        }
        Files.move(file.toPath(), target.toPath())
        return target
    }
}
