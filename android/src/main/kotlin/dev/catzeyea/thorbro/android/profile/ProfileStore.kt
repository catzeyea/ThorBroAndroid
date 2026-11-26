package dev.catzeyea.thorbro.android.profile

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A saved profile plus the time it was received (milliseconds since 1970). */
data class StoredProfile(val profile: StreamProfile, val receivedAtMillis: Long)

/**
 * Keeps the last valid profile in the app's private storage.
 *
 * [directory] is `Context.filesDir`: a folder only this app can read. It survives app updates and is
 * deleted when the app is uninstalled. No permission is needed for it.
 */
class ProfileStore(directory: File) {

    private val file = File(directory, "stream-profile.json")

    /**
     * Validates a profile received as base64 and saves it only if it is valid. A rejected profile
     * never touches the saved one, so the previous profile stays in place.
     *
     * @throws IOException if the profile was valid but could not be written.
     */
    @Throws(IOException::class)
    fun importBase64(encoded: String): ParseResult {
        val result = ProfileParser.parseBase64(encoded)
        if (result is ParseResult.Success) save(result.json)
        return result
    }

    /**
     * Saves the JSON text. It is written to a temporary file first and then moved over the old one
     * (atomic, replacing on purpose: it is the app's own private file), so a crash while writing
     * never leaves a half-written profile behind.
     */
    @Throws(IOException::class)
    fun save(json: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json, Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            temp.delete()
            throw IOException("Could not save the profile to ${file.path}", e)
        }
    }

    /**
     * Loads and re-validates the saved profile. Returns null if there is none, or if it can no
     * longer be read (for example a profile saved by an older app version in an earlier format).
     */
    fun load(): StoredProfile? {
        if (!file.isFile) return null
        val json = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            return null
        }
        return when (val result = ProfileParser.parse(json)) {
            is ParseResult.Success -> StoredProfile(result.profile, file.lastModified())
            is ParseResult.Failure -> null
        }
    }
}
