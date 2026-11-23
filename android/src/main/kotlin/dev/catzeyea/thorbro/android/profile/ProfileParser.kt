package dev.catzeyea.thorbro.android.profile

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/** Result of reading a profile: either a valid profile or a message a user can read. */
sealed interface ParseResult {
    /** [json] is the original text, so it can be saved exactly as received. */
    data class Success(val profile: StreamProfile, val json: String) : ParseResult
    data class Failure(val message: String) : ParseResult
}

/**
 * Reads and validates the stream profile JSON that ThorBro (PC) builds.
 * Format: docs/STREAM-PROFILE.md in the ThorBro repo (format "thorbro.stream-profile", version 1).
 * Unknown fields are ignored, as that doc requires.
 *
 * Pure Kotlin on purpose (no android.* classes), so it runs in local unit tests on the PC.
 * That is also why java.util.Base64 is used instead of android.util.Base64.
 */
object ProfileParser {

    /** Value of the "format" field. Anything else is not a ThorBro stream profile. */
    const val FORMAT = "thorbro.stream-profile"

    const val SUPPORTED_VERSION = 1

    /** Upper limit for the JSON text, to refuse absurd input early. */
    const val MAX_JSON_CHARS = 256 * 1024

    /** Field names that hint at secrets. A profile must never carry credentials. */
    private val FORBIDDEN_KEYS = setOf(
        "password", "passwd", "pin", "token", "secret", "credentials", "apikey", "api_key", "auth",
    )

    /** Same layout that java.util.UUID prints: 8-4-4-4-12 hex digits. */
    private val UUID_PATTERN =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** Decodes the base64 text from the `profile_b64` extra, then parses it. */
    fun parseBase64(encoded: String): ParseResult {
        // Line breaks or spaces can sneak in when the text is copied around; they are never valid base64.
        val compact = encoded.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return ParseResult.Failure("The received profile is empty.")
        // Base64 grows data by 4/3, so this is a cheap size check before decoding.
        if (compact.length > MAX_JSON_CHARS / 3 * 4 + 4) {
            return ParseResult.Failure("The received profile is too large (limit ${MAX_JSON_CHARS / 1024} KiB).")
        }
        val bytes = try {
            Base64.getDecoder().decode(compact)
        } catch (e: IllegalArgumentException) {
            return ParseResult.Failure("The received profile is not valid base64: ${e.message}")
        }
        return parse(bytes.toString(Charsets.UTF_8))
    }

    /** Parses and validates the profile JSON text. */
    fun parse(json: String): ParseResult {
        if (json.length > MAX_JSON_CHARS) {
            return ParseResult.Failure("The profile is too large (limit ${MAX_JSON_CHARS / 1024} KiB).")
        }
        return try {
            ParseResult.Success(readProfile(json), json)
        } catch (e: InvalidProfileException) {
            ParseResult.Failure(e.message)
        }
    }

    /** Thrown internally for any validation problem; [message] is shown to the user. */
    private class InvalidProfileException(override val message: String) : Exception(message)

    private fun fail(message: String): Nothing = throw InvalidProfileException(message)

    private fun readProfile(json: String): StreamProfile {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            fail("The profile is not valid JSON.")
        }
        if (root !is JsonObject) fail("The profile must be a JSON object.")

        // "format" first: it tells a stream profile apart from any other JSON.
        val format = root.optionalString("format", "\"format\"")
            ?: fail("This is not a ThorBro stream profile: the \"format\" field is missing.")
        if (format != FORMAT) {
            fail("This is not a ThorBro stream profile: \"format\" is \"${format.take(60)}\", expected \"$FORMAT\".")
        }

        // Then the version, so a future format gets a clear "unsupported version"
        // message instead of a confusing error about some field.
        val version = root.requireWholeNumber("version")
        if (version != SUPPORTED_VERSION.toLong()) {
            fail(
                "Unsupported profile version $version. This app understands version $SUPPORTED_VERSION. " +
                    "Update the app or ThorBro so both use the same version.",
            )
        }

        findForbiddenKey(root)?.let { key ->
            fail("The profile contains the field \"$key\". Profiles must not contain credentials.")
        }

        val pcObject = root["pc"] as? JsonObject ?: fail("The profile has no \"pc\" object.")
        val pc = readPc(pcObject)

        val gamesArray = root["games"] as? JsonArray ?: fail("The profile has no \"games\" list.")
        val games = gamesArray.mapIndexed { index, element -> readGame(element, index) }

        return StreamProfile(version = version.toInt(), pc = pc, games = games)
    }

    private fun readPc(obj: JsonObject): StreamPc {
        val uniqueid = obj.optionalString("uniqueid", "pc.uniqueid") ?: fail("\"pc.uniqueid\" is missing.")
        // Upper and lower case are both fine. The value is kept as it is, because Moonlight
        // stores Sunshine's uniqueid unchanged and looks the PC up with it.
        if (!UUID_PATTERN.matches(uniqueid)) {
            fail("\"pc.uniqueid\" is not a valid UUID: \"${uniqueid.take(60)}\". Use Sunshine's uniqueid from /serverinfo.")
        }
        return StreamPc(
            // May be empty (Sunshine reported no hostname); the UI then shows the uniqueid.
            name = obj.optionalString("name", "pc.name") ?: "",
            uniqueid = uniqueid,
            // Addresses are only hints; a blank value means the same as null.
            lanAddress = obj.optionalString("lan_address", "pc.lan_address")?.takeIf { it.isNotBlank() },
            tailscaleAddress = obj.optionalString("tailscale_address", "pc.tailscale_address")?.takeIf { it.isNotBlank() },
            tailscaleName = obj.optionalString("tailscale_name", "pc.tailscale_name")?.takeIf { it.isNotBlank() },
            sunshinePort = readOptionalPort(obj["sunshine_port"], "pc.sunshine_port"),
        )
    }

    /** A game is a plain string: Sunshine's app name, kept exactly (no trimming, Moonlight matches it exactly). */
    private fun readGame(element: JsonElement, index: Int): String {
        val where = "games[$index]"
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) fail("$where must be a string (the app name in Sunshine).")
        val name = primitive.content
        if (name.isBlank()) fail("$where is empty. Every game needs its app name from Sunshine.")
        return name
    }

    private fun readOptionalPort(element: JsonElement?, where: String): Int? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString) fail("$where must be a number.")
        val port = primitive.longOrNull ?: fail("$where must be a whole number.")
        if (port !in 1L..65535L) fail("$where must be between 1 and 65535, not $port.")
        return port.toInt()
    }

    private fun JsonObject.requireWholeNumber(key: String): Long {
        val primitive = this[key] as? JsonPrimitive ?: fail("The profile has no \"$key\" field.")
        if (primitive.isString) fail("\"$key\" must be a number, not a string.")
        return primitive.longOrNull ?: fail("\"$key\" must be a whole number.")
    }

    /** Returns the string value, null if missing, and fails if it has another type. */
    private fun JsonObject.optionalString(key: String, where: String): String? {
        val element = this[key]
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) fail("$where must be a string.")
        return primitive.content
    }

    /** Walks the whole JSON tree and returns the first key that looks like a secret. */
    private fun findForbiddenKey(element: JsonElement): String? = when (element) {
        is JsonObject -> element.entries.firstNotNullOfOrNull { (key, value) ->
            if (key.lowercase() in FORBIDDEN_KEYS) key else findForbiddenKey(value)
        }
        is JsonArray -> element.firstNotNullOfOrNull { findForbiddenKey(it) }
        else -> null
    }
}
