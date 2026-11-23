package dev.catzeyea.thorbro.android.profile

import java.io.File
import java.io.IOException

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
     * Saves the JSON text. It is written to a temporary file first and then renamed, so a crash
     * while writing never leaves a half-written profile behind.
     */
    @Throws(IOException::class)
    fun save(json: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not save the profile to ${file.path}")
        }
    }

    /**
     * Loads and re-validates the saved profile. Returns null if there is none, or if it can no
     * longer be read (for example after an app update that dropped an old format).
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
