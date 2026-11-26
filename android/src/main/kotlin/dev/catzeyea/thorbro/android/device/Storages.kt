package dev.catzeyea.thorbro.android.device

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import dev.catzeyea.thorbro.android.files.FileNames
import dev.catzeyea.thorbro.android.files.StorageRoot
import java.io.File

/** The storages of the device: internal shared storage plus every mounted SD card / USB stick. */
object Storages {

    fun list(context: Context): List<StorageRoot> {
        val manager = context.getSystemService(StorageManager::class.java)
        val volumes = manager.storageVolumes.filter { it.state == Environment.MEDIA_MOUNTED && it.directory != null }
        val result = volumes.map { volume ->
            val label = if (volume.isPrimary) "Internal storage" else if (volume.isRemovable) "SD card" else volume.getDescription(context)
            StorageRoot(label, volume.directory!!, volume.isRemovable)
        }.sortedBy { !it.root.path.startsWith(Environment.getExternalStorageDirectory().path) }
        return result.ifEmpty { listOf(StorageRoot("Internal storage", Environment.getExternalStorageDirectory(), false)) }
    }

    /** The storage [file] is on, or null. */
    fun rootOf(file: File, storages: List<StorageRoot>): StorageRoot? =
        storages.filter { FileNames.isInside(it.root, file) }.maxByOrNull { it.root.path.length }

    /** True if both paths are on the same storage (a rename instead of a copy). */
    fun sameStorage(a: File, b: File, storages: List<StorageRoot>): Boolean {
        val ra = rootOf(a, storages) ?: return false
        val rb = rootOf(b, storages) ?: return false
        return ra.root == rb.root
    }
}
