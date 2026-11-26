package dev.catzeyea.thorbro.android.art

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

/**
 * Loads covers off the main thread. A source icon is 1024×1024 and about 1 MB; decoding it scaled
 * takes about 13 ms (measured on a Thor), so it is done once and kept as a small thumbnail in the
 * app's cache folder. Shown bitmaps are hardware bitmaps (no upload on the render thread while
 * drawing) and kept in a small memory cache, so a tab that comes back draws its covers in the
 * first frame.
 */
class CoverLoader(cacheDir: File) {

    companion object {
        /** Thumbnail size in px. A 150 du tile is about 225 px on the Thor. */
        const val THUMB_PX = 256
        private const val MEMORY_ITEMS = 64
    }

    private val thumbs = File(cacheDir, "covers").apply { mkdirs() }
    private val memory = LruCache<String, ImageBitmap>(MEMORY_ITEMS)
    /** At most two decodes at the same time, so loading 20+ covers doesn't flood the CPU. */
    private val decoder = Dispatchers.IO.limitedParallelism(2)

    /** Cache key: path, time and size, so a changed icon gets a new thumbnail (as on the PC). */
    fun key(file: File): String {
        val digest = MessageDigest.getInstance("MD5").digest("${file.path}|${file.lastModified()}|${file.length()}".toByteArray())
        return digest.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    /** A cover that is already in memory (no file access): safe to call while composing. */
    fun cached(key: String): ImageBitmap? = memory.get(key)

    /** The cover of [file], from memory, the thumbnail or the source. Null if it can't be decoded. */
    suspend fun load(file: File, key: String): ImageBitmap? = memory.get(key) ?: withContext(decoder) {
        memory.get(key) ?: runCatching { decode(file, key) }.getOrNull()?.also { memory.put(key, it) }
    }

    private fun decode(file: File, key: String): ImageBitmap {
        val thumb = File(thumbs, "$key.webp")
        if (thumb.isFile) {
            // Default allocator: a hardware bitmap.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(thumb)).asImageBitmap()
        }
        val software = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = THUMB_PX.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val temp = File(thumbs, "$key.webp.tmp")
        temp.outputStream().use { software.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) }
        // The thumbnail is our own cache file: replacing a leftover is fine (atomic, never half written).
        Files.move(temp.toPath(), thumb.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        val hardware = software.copy(Bitmap.Config.HARDWARE, false) ?: software
        if (hardware !== software) software.recycle()
        return hardware.asImageBitmap()
    }
}
