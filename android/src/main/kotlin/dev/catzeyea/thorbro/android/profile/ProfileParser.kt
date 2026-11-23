package dev.catzeyea.thorbro.android.profile

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/** Result of reading a profile: either a valid profile or a message a user can read. */
sealed interface ParseResult {
    /** [json] is the original text, so it can be saved exactly as received. */
    data class Success(val profile: StreamProfile, val json: String) : ParseResult
    data class Failure(val message: String) : ParseResult
}

/**
 * Reads and validates the stream profile JSON.
 *
 * Pure Kotlin on purpose (no android.* classes), so it runs in local unit tests on the PC.
 * That is also why java.util.Base64 is used instead of android.util.Base64.
 */
object ProfileParser {

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

    private val DIGITS_PATTERN = Regex("^[0-9]{1,10}$")

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

        // Check the version first, so a future format gets a clear "unsupported version"
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

        val hostObject = root["host"] as? JsonObject ?: fail("The profile has no \"host\" object.")
        val host = readHost(hostObject)

        val gamesArray = root["games"] as? JsonArray ?: fail("The profile has no \"games\" list.")
        val games = gamesArray.mapIndexed { index, element -> readGame(element, index) }

        return StreamProfile(
            version = version.toInt(),
            host = host,
            games = games,
            createdBy = root.optionalString("createdBy", "createdBy"),
        )
    }

    private fun readHost(obj: JsonObject): StreamHost {
        val uuid = obj.optionalString("uuid", "host.uuid") ?: fail("\"host.uuid\" is missing.")
        if (!UUID_PATTERN.matches(uuid)) {
            fail("\"host.uuid\" is not a valid UUID: \"$uuid\". Use Sunshine's uniqueid from /serverinfo.")
        }
        return StreamHost(uuid = uuid, name = obj.optionalString("name", "host.name")?.takeIf { it.isNotBlank() })
    }

    private fun readGame(element: JsonElement, index: Int): StreamGame {
        val where = "games[$index]"
        val obj = element as? JsonObject ?: fail("$where must be an object.")
        val name = obj.optionalString("name", "$where.name")
        if (name.isNullOrBlank()) fail("$where.name is missing or empty.")
        return StreamGame(
            name = name,
            appId = readAppId(obj["appId"], "$where.appId"),
            hdr = readOptionalBoolean(obj["hdr"], "$where.hdr") ?: false,
        )
    }

    /**
     * Moonlight wants AppId as a String (it calls getStringExtra + Integer.parseInt).
     * Accept a digit string or a JSON integer and always return a String.
     */
    private fun readAppId(element: JsonElement?, where: String): String? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: fail("$where must be a string of digits.")
        val text = if (primitive.isString) primitive.content else primitive.longOrNull?.toString()
        if (text == null || !DIGITS_PATTERN.matches(text) || text.toLong() > Int.MAX_VALUE) {
            fail("$where must be a whole number between 0 and ${Int.MAX_VALUE}, given as a string.")
        }
        return text
    }

    private fun readOptionalBoolean(element: JsonElement?, where: String): Boolean? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString) fail("$where must be true or false.")
        return primitive.booleanOrNull ?: fail("$where must be true or false.")
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
