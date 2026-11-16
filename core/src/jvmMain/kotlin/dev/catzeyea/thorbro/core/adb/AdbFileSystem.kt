package dev.catzeyea.thorbro.core.adb

import dev.catzeyea.thorbro.core.fs.FileEntry
import dev.catzeyea.thorbro.core.fs.Storage
import dev.catzeyea.thorbro.core.fs.ThorFileSystem

/**
 * Thor-Dateisystem über adb (USB). Für die PC-App.
 * Port von thorBro / thor-drop (~/System/Scripts/thor).
 *
 * TODO: adb-Binary mitliefern (Windows + Linux) oder Lib wie dadb nutzen, noch nicht entschieden.
 */
class AdbFileSystem(private val adbPath: String = "adb") : ThorFileSystem {
    override suspend fun storages(): List<Storage> = TODO("ls /storage, wie thorBro storages()")
    override suspend fun list(path: String): List<FileEntry> = TODO("stat -c '%F|%s|%n', wie thorBro listdir()")
    override suspend fun exists(path: String): Boolean = TODO()
    override suspend fun mkdirs(path: String) = TODO()
    override suspend fun move(from: String, to: String) = TODO()
    override suspend fun rename(path: String, newName: String) = TODO()
    override suspend fun delete(path: String) = TODO()
}
