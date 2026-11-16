package dev.catzeyea.thorbro.core.fs

/**
 * Zugriff auf das Dateisystem vom Thor, egal wie man drankommt.
 *
 * Implementierungen:
 * - PC: AdbFileSystem (jvmMain), alles per adb über USB, wie die alten Scripts
 * - Thor: LocalFileSystem (androidMain), direkter Zugriff auf /sdcard und SD-Karte
 * - später evtl.: WLAN-Transport zwischen PC-App und Thor-App
 *
 * Alle Pfade sind Pfade auf dem Thor (z.B. /sdcard/Download).
 */
interface ThorFileSystem {
    /** Interner Speicher plus SD-Karten. */
    suspend fun storages(): List<Storage>

    suspend fun list(path: String): List<FileEntry>
    suspend fun exists(path: String): Boolean
    suspend fun mkdirs(path: String)
    suspend fun move(from: String, to: String)
    suspend fun rename(path: String, newName: String)
    suspend fun delete(path: String)
}

data class Storage(val name: String, val root: String)

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
)
