package dev.catzeyea.thorbro.core.local

import dev.catzeyea.thorbro.core.fs.FileEntry
import dev.catzeyea.thorbro.core.fs.Storage
import dev.catzeyea.thorbro.core.fs.ThorFileSystem

/**
 * Direkter Dateizugriff auf dem Thor. Für die APK.
 * Braucht die Berechtigung "Zugriff auf alle Dateien" (MANAGE_EXTERNAL_STORAGE).
 * Android/data und /data/data gehen damit nicht, dafür evtl. später Shizuku.
 */
class LocalFileSystem : ThorFileSystem {
    override suspend fun storages(): List<Storage> = TODO("/sdcard + /storage/<SD>")
    override suspend fun list(path: String): List<FileEntry> = TODO("java.io.File / java.nio")
    override suspend fun exists(path: String): Boolean = TODO()
    override suspend fun mkdirs(path: String) = TODO()
    override suspend fun move(from: String, to: String) = TODO("gleicher Speicher: rename, sonst kopieren + löschen")
    override suspend fun rename(path: String, newName: String) = TODO()
    override suspend fun delete(path: String) = TODO()
}
