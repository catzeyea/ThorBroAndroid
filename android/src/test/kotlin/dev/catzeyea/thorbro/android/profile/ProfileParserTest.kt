package dev.catzeyea.thorbro.android.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ProfileParserTest {

    private val validUuid = "0123abcd-4567-89ef-0123-456789abcdef"

    private val validJson = """
        {
          "version": 1,
          "createdBy": "ThorBro 0.5.0",
          "host": { "uuid": "$validUuid", "name": "Gaming-PC" },
          "games": [
            { "name": "Desktop" },
            { "name": "Steam Big Picture", "hdr": true },
            { "name": "Elden Ring", "appId": "881448767" }
          ]
        }
    """.trimIndent()

    private fun expectSuccess(result: ParseResult): StreamProfile = when (result) {
        is ParseResult.Success -> result.profile
        is ParseResult.Failure -> throw AssertionError("Expected success but got: ${result.message}")
    }

    private fun expectFailure(result: ParseResult): String = when (result) {
        is ParseResult.Failure -> result.message
        is ParseResult.Success -> throw AssertionError("Expected failure but parsing succeeded")
    }

    /** Builds a one-game profile with [gameFields] inserted into the game object. */
    private fun profileWithGame(gameFields: String) =
        """{"version":1,"host":{"uuid":"$validUuid"},"games":[{"name":"Desktop"$gameFields}]}"""

    @Test
    fun `valid profile is parsed with all fields`() {
        val profile = expectSuccess(ProfileParser.parse(validJson))

        assertEquals(1, profile.version)
        assertEquals("ThorBro 0.5.0", profile.createdBy)
        assertEquals(validUuid, profile.host.uuid)
        assertEquals("Gaming-PC", profile.host.name)
        assertEquals(3, profile.games.size)
        assertEquals(StreamGame(name = "Desktop", appId = null, hdr = false), profile.games[0])
        assertEquals(StreamGame(name = "Steam Big Picture", appId = null, hdr = true), profile.games[1])
        assertEquals(StreamGame(name = "Elden Ring", appId = "881448767", hdr = false), profile.games[2])
    }

    @Test
    fun `success keeps the original json text for saving`() {
        val result = ProfileParser.parse(validJson)
        assertTrue(result is ParseResult.Success)
        assertEquals(validJson, (result as ParseResult.Success).json)
    }

    @Test
    fun `base64 profile is decoded and parsed`() {
        val encoded = Base64.getEncoder().encodeToString(validJson.toByteArray(Charsets.UTF_8))
        val profile = expectSuccess(ProfileParser.parseBase64(encoded))
        assertEquals(3, profile.games.size)
    }

    @Test
    fun `base64 with line breaks is accepted`() {
        val encoded = Base64.getMimeEncoder().encodeToString(validJson.toByteArray(Charsets.UTF_8))
        assertTrue(encoded.contains("\r\n"))
        expectSuccess(ProfileParser.parseBase64(encoded))
    }

    @Test
    fun `non-ascii game names survive base64`() {
        val json = profileWithGame("").replace("Desktop", "Ōkami HD – Ü")
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        assertEquals("Ōkami HD – Ü", expectSuccess(ProfileParser.parseBase64(encoded)).games[0].name)
    }

    @Test
    fun `invalid base64 is rejected`() {
        val message = expectFailure(ProfileParser.parseBase64("not*base64!"))
        assertTrue(message, message.contains("base64"))
    }

    @Test
    fun `empty base64 is rejected`() {
        expectFailure(ProfileParser.parseBase64("   "))
    }

    @Test
    fun `invalid json is rejected`() {
        val message = expectFailure(ProfileParser.parse("{ this is not json"))
        assertTrue(message, message.contains("not valid JSON"))
    }

    @Test
    fun `json that is not an object is rejected`() {
        expectFailure(ProfileParser.parse("[1, 2, 3]"))
    }

    @Test
    fun `unknown version is rejected with a readable message`() {
        val json = validJson.replace("\"version\": 1", "\"version\": 2")
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("Unsupported profile version 2"))
        assertTrue(message, message.contains("version 1"))
    }

    @Test
    fun `missing version is rejected`() {
        val message = expectFailure(ProfileParser.parse("""{"host":{"uuid":"$validUuid"},"games":[]}"""))
        assertTrue(message, message.contains("version"))
    }

    @Test
    fun `version given as string is rejected`() {
        expectFailure(ProfileParser.parse(validJson.replace("\"version\": 1", "\"version\": \"1\"")))
    }

    @Test
    fun `fractional version is rejected`() {
        expectFailure(ProfileParser.parse(validJson.replace("\"version\": 1", "\"version\": 1.5")))
    }

    @Test
    fun `missing host is rejected`() {
        val message = expectFailure(ProfileParser.parse("""{"version":1,"games":[]}"""))
        assertTrue(message, message.contains("host"))
    }

    @Test
    fun `invalid host uuid is rejected`() {
        val message = expectFailure(ProfileParser.parse(validJson.replace(validUuid, "my-pc")))
        assertTrue(message, message.contains("host.uuid"))
    }

    @Test
    fun `missing games list is rejected`() {
        val message = expectFailure(ProfileParser.parse("""{"version":1,"host":{"uuid":"$validUuid"}}"""))
        assertTrue(message, message.contains("games"))
    }

    @Test
    fun `empty games list is allowed`() {
        val profile = expectSuccess(ProfileParser.parse("""{"version":1,"host":{"uuid":"$validUuid"},"games":[]}"""))
        assertTrue(profile.games.isEmpty())
        assertNull(profile.host.name)
    }

    @Test
    fun `blank game name is rejected`() {
        val json = """{"version":1,"host":{"uuid":"$validUuid"},"games":[{"name":"  "}]}"""
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("games[0].name"))
    }

    @Test
    fun `appId given as json integer is converted to string`() {
        val profile = expectSuccess(ProfileParser.parse(profileWithGame(""","appId":42""")))
        assertEquals("42", profile.games[0].appId)
    }

    @Test
    fun `appId with letters is rejected`() {
        expectFailure(ProfileParser.parse(profileWithGame(""","appId":"12ab"""")))
    }

    @Test
    fun `appId larger than Int is rejected`() {
        expectFailure(ProfileParser.parse(profileWithGame(""","appId":"9999999999"""")))
    }

    @Test
    fun `hdr given as string is rejected`() {
        expectFailure(ProfileParser.parse(profileWithGame(""","hdr":"true"""")))
    }

    @Test
    fun `unknown extra fields are ignored`() {
        val json = """{"version":1,"futureField":{"a":1},"host":{"uuid":"$validUuid","port":47989},
            |"games":[{"name":"Desktop","cover":"x.png"}]}""".trimMargin()
        assertFalse(expectSuccess(ProfileParser.parse(json)).games.isEmpty())
    }

    @Test
    fun `credential fields are rejected at any depth`() {
        for (key in listOf("password", "PIN", "token", "apiKey")) {
            val json = """{"version":1,"host":{"uuid":"$validUuid","$key":"x"},"games":[]}"""
            val message = expectFailure(ProfileParser.parse(json))
            assertTrue(message, message.contains("credentials"))
        }
    }

    @Test
    fun `oversized profile is rejected`() {
        val huge = "x".repeat(ProfileParser.MAX_JSON_CHARS + 1)
        val message = expectFailure(ProfileParser.parse(huge))
        assertTrue(message, message.contains("too large"))
    }
}
