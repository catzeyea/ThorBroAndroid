package dev.catzeyea.thorbro.android.files

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Pinned folders and the default scan folder. */
data class FolderPrefs(val pins: List<String>, val defaultScan: String) {

    fun isPinned(path: String) = path in pins

    fun togglePin(path: String): FolderPrefs =
        if (isPinned(path)) copy(pins = pins - path) else copy(pins = (pins + path).take(MAX_PINS))

    /** The default scan folder is always pinned, so it stays reachable from the places list. */
    fun withDefaultScan(path: String): FolderPrefs = copy(defaultScan = path, pins = if (isPinned(path)) pins else pins + path)

    fun toJson(): String = JsonObject(
        mapOf("pins" to JsonArray(pins.map(::JsonPrimitive)), "defaultScan" to JsonPrimitive(defaultScan)),
    ).toString()

    companion object {
        const val MAX_PINS = 12

        fun defaults(download: File) = FolderPrefs(listOf(download.path), download.path)

        fun fromJson(text: String, download: File): FolderPrefs = runCatching {
            val json = Json.parseToJsonElement(text).jsonObject
            val pins = json["pins"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val scan = json["defaultScan"]?.jsonPrimitive?.content ?: download.path
            FolderPrefs(pins, scan)
        }.getOrElse { defaults(download) }
    }
}

/** Stores [FolderPrefs] in the app's private folder. Unpinning never touches the folder itself. */
class FolderPrefsStore(private val file: File, private val download: File) {
    fun load(): FolderPrefs = if (file.isFile) FolderPrefs.fromJson(file.readText(), download) else FolderPrefs.defaults(download)

    fun save(prefs: FolderPrefs) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(prefs.toJson())
        // The app's own private settings file: replacing it on purpose (atomic, so it is never half written).
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
