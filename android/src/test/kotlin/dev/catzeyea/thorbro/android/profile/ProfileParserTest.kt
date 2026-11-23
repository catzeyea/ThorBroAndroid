package dev.catzeyea.thorbro.android.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ProfileParserTest {

    /** The example from docs/STREAM-PROFILE.md in the ThorBro repo, copied unchanged. */
    private val documentedExample = """
        {
          "format": "thorbro.stream-profile",
          "version": 1,
          "pc": {
            "name": "my-pc",
            "uniqueid": "0123ABCD-4567-89EF-0123-456789ABCDEF",
            "lan_address": "192.168.1.20",
            "tailscale_address": "100.101.102.103",
            "tailscale_name": "my-pc.tail1234.ts.net",
            "sunshine_port": 47989
          },
          "games": ["Desktop", "Steam Big Picture"]
        }
    """.trimIndent()

    private val upperUuid = "0123ABCD-4567-89EF-0123-456789ABCDEF"

    private fun expectSuccess(result: ParseResult): StreamProfile = when (result) {
        is ParseResult.Success -> result.profile
        is ParseResult.Failure -> throw AssertionError("Expected success but got: ${result.message}")
    }

    private fun expectFailure(result: ParseResult): String = when (result) {
        is ParseResult.Failure -> result.message
        is ParseResult.Success -> throw AssertionError("Expected failure but parsing succeeded")
    }

    /** A minimal valid profile; [pcFields] and [rootFields] are inserted as extra JSON members. */
    private fun minimalProfile(
        games: String = """["Desktop"]""",
        uniqueid: String = upperUuid,
        pcFields: String = "",
        rootFields: String = "",
    ) = """{"format":"thorbro.stream-profile","version":1$rootFields,""" +
        """"pc":{"name":"my-pc","uniqueid":"$uniqueid"$pcFields},"games":$games}"""

    private fun base64(json: String) = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    // --- The documented example ---

    @Test
    fun `documented example from the PC repo is parsed with all fields`() {
        val profile = expectSuccess(ProfileParser.parse(documentedExample))

        assertEquals(1, profile.version)
        assertEquals(
            StreamPc(
                name = "my-pc",
                uniqueid = "0123ABCD-4567-89EF-0123-456789ABCDEF",
                lanAddress = "192.168.1.20",
                tailscaleAddress = "100.101.102.103",
                tailscaleName = "my-pc.tail1234.ts.net",
                sunshinePort = 47989,
            ),
            profile.pc,
        )
        assertEquals(listOf("Desktop", "Steam Big Picture"), profile.games)
    }

    @Test
    fun `success keeps the original json text for saving`() {
        val result = ProfileParser.parse(documentedExample)
        assertEquals(documentedExample, (result as ParseResult.Success).json)
    }

    @Test
    fun `documented example sent as base64 is parsed`() {
        val profile = expectSuccess(ProfileParser.parseBase64(base64(documentedExample)))
        assertEquals(listOf("Desktop", "Steam Big Picture"), profile.games)
    }

    @Test
    fun `base64 with line breaks is accepted`() {
        val encoded = Base64.getMimeEncoder().encodeToString(documentedExample.toByteArray(Charsets.UTF_8))
        assertTrue(encoded.contains("\r\n"))
        expectSuccess(ProfileParser.parseBase64(encoded))
    }

    // --- format ---

    @Test
    fun `missing format is rejected`() {
        val json = documentedExample.replace("\"format\": \"thorbro.stream-profile\",", "")
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("\"format\" field is missing"))
    }

    @Test
    fun `wrong format is rejected`() {
        val json = documentedExample.replace("thorbro.stream-profile", "thorbro.something-else")
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("thorbro.something-else"))
        assertTrue(message, message.contains("expected \"thorbro.stream-profile\""))
    }

    @Test
    fun `format with different case is rejected`() {
        expectFailure(ProfileParser.parse(documentedExample.replace("thorbro.stream-profile", "ThorBro.Stream-Profile")))
    }

    @Test
    fun `format given as non-string is rejected`() {
        expectFailure(ProfileParser.parse(documentedExample.replace("\"thorbro.stream-profile\"", "1")))
    }

    @Test
    fun `unrelated json without format is rejected before anything else`() {
        // Old Android-only format: has a version but is not a ThorBro stream profile.
        val old = """{"version":1,"host":{"uuid":"$upperUuid"},"games":[{"name":"Desktop"}]}"""
        val message = expectFailure(ProfileParser.parse(old))
        assertTrue(message, message.contains("not a ThorBro stream profile"))
    }

    // --- version ---

    @Test
    fun `unknown version is rejected with a readable message`() {
        val json = documentedExample.replace("\"version\": 1", "\"version\": 2")
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("Unsupported profile version 2"))
        assertTrue(message, message.contains("version 1"))
    }

    @Test
    fun `missing version is rejected`() {
        val message = expectFailure(ProfileParser.parse(documentedExample.replace("\"version\": 1,", "")))
        assertTrue(message, message.contains("version"))
    }

    @Test
    fun `version given as string is rejected`() {
        expectFailure(ProfileParser.parse(documentedExample.replace("\"version\": 1", "\"version\": \"1\"")))
    }

    @Test
    fun `fractional version is rejected`() {
        expectFailure(ProfileParser.parse(documentedExample.replace("\"version\": 1", "\"version\": 1.5")))
    }

    // --- pc ---

    @Test
    fun `missing pc object is rejected`() {
        val json = """{"format":"thorbro.stream-profile","version":1,"games":[]}"""
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("\"pc\""))
    }

    @Test
    fun `old host object instead of pc is rejected`() {
        val json = """{"format":"thorbro.stream-profile","version":1,"host":{"uuid":"$upperUuid"},"games":[]}"""
        expectFailure(ProfileParser.parse(json))
    }

    @Test
    fun `uniqueid in upper case is accepted and kept unchanged`() {
        val profile = expectSuccess(ProfileParser.parse(minimalProfile(uniqueid = upperUuid)))
        assertEquals(upperUuid, profile.pc.uniqueid)
    }

    @Test
    fun `uniqueid in lower case is accepted and kept unchanged`() {
        val lower = upperUuid.lowercase()
        val profile = expectSuccess(ProfileParser.parse(minimalProfile(uniqueid = lower)))
        assertEquals(lower, profile.pc.uniqueid)
    }

    @Test
    fun `uniqueid in mixed case is accepted and kept unchanged`() {
        val mixed = "0123abcd-4567-89EF-0123-456789abcDEF"
        assertEquals(mixed, expectSuccess(ProfileParser.parse(minimalProfile(uniqueid = mixed))).pc.uniqueid)
    }

    @Test
    fun `invalid uniqueid is rejected`() {
        val message = expectFailure(ProfileParser.parse(minimalProfile(uniqueid = "my-pc")))
        assertTrue(message, message.contains("pc.uniqueid"))
    }

    @Test
    fun `missing uniqueid is rejected`() {
        val json = """{"format":"thorbro.stream-profile","version":1,"pc":{"name":"my-pc"},"games":[]}"""
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("pc.uniqueid"))
    }

    @Test
    fun `empty pc name is allowed and the uniqueid is shown instead`() {
        val profile = expectSuccess(ProfileParser.parse(minimalProfile().replace("\"name\":\"my-pc\"", "\"name\":\"\"")))
        assertEquals("", profile.pc.name)
        assertEquals(upperUuid, profile.pc.displayName)
    }

    @Test
    fun `optional pc fields may be null or missing`() {
        val withNulls = minimalProfile(
            pcFields = ""","lan_address":null,"tailscale_address":null,"tailscale_name":null""",
        )
        for (json in listOf(withNulls, minimalProfile())) {
            val pc = expectSuccess(ProfileParser.parse(json)).pc
            assertNull(pc.lanAddress)
            assertNull(pc.tailscaleAddress)
            assertNull(pc.tailscaleName)
            assertNull(pc.sunshinePort)
        }
    }

    @Test
    fun `non-default sunshine port is read`() {
        val pc = expectSuccess(ProfileParser.parse(minimalProfile(pcFields = ""","sunshine_port":48000"""))).pc
        assertEquals(48000, pc.sunshinePort)
    }

    @Test
    fun `invalid sunshine port is rejected`() {
        for (port in listOf("0", "70000", "\"47989\"", "47989.5")) {
            val message = expectFailure(ProfileParser.parse(minimalProfile(pcFields = ""","sunshine_port":$port""")))
            assertTrue(message, message.contains("pc.sunshine_port"))
        }
    }

    @Test
    fun `address field with the wrong type is rejected`() {
        val message = expectFailure(ProfileParser.parse(minimalProfile(pcFields = ""","lan_address":42""")))
        assertTrue(message, message.contains("pc.lan_address"))
    }

    // --- games ---

    @Test
    fun `empty games list is allowed`() {
        val profile = expectSuccess(ProfileParser.parse(minimalProfile(games = "[]")))
        assertTrue(profile.games.isEmpty())
    }

    @Test
    fun `missing games list is rejected`() {
        val json = """{"format":"thorbro.stream-profile","version":1,"pc":{"name":"my-pc","uniqueid":"$upperUuid"}}"""
        val message = expectFailure(ProfileParser.parse(json))
        assertTrue(message, message.contains("games"))
    }

    @Test
    fun `game names with unicode and quotes survive json and base64 unchanged`() {
        // Same names as ThorBro's test game_names_with_quotes_survive, plus more scripts and an emoji.
        val names = listOf("Tom's \"Game\"", "Ünïcödé – 2", "Ōkami HD", "ファイナルファンタジー", "Game 🎮")
        val gamesJson = names.joinToString(",", "[", "]") { "\"" + it.replace("\"", "\\\"") + "\"" }
        val json = minimalProfile(games = gamesJson)

        assertEquals(names, expectSuccess(ProfileParser.parse(json)).games)
        assertEquals(names, expectSuccess(ProfileParser.parseBase64(base64(json))).games)
    }

    @Test
    fun `game names are kept exactly, including case and spaces`() {
        val profile = expectSuccess(ProfileParser.parse(minimalProfile(games = """[" Desktop ","steam big picture"]""")))
        assertEquals(listOf(" Desktop ", "steam big picture"), profile.games)
    }

    @Test
    fun `old game objects are rejected`() {
        val message = expectFailure(ProfileParser.parse(minimalProfile(games = """[{"name":"Desktop"}]""")))
        assertTrue(message, message.contains("games[0] must be a string"))
    }

    @Test
    fun `blank game name is rejected`() {
        val message = expectFailure(ProfileParser.parse(minimalProfile(games = """["Desktop","  "]""")))
        assertTrue(message, message.contains("games[1]"))
    }

    // --- unknown fields, credentials, size, broken input ---

    @Test
    fun `unknown fields are ignored`() {
        val json = minimalProfile(
            rootFields = ""","futureField":{"a":[1,2]}""",
            pcFields = ""","mac_address":"aa:bb:cc:dd:ee:ff"""",
        )
        assertEquals(listOf("Desktop"), expectSuccess(ProfileParser.parse(json)).games)
    }

    @Test
    fun `credential fields are rejected at any depth`() {
        for (key in listOf("password", "PIN", "token", "apiKey")) {
            for (json in listOf(minimalProfile(pcFields = ""","$key":"x""""), minimalProfile(rootFields = ""","$key":"x""""))) {
                val message = expectFailure(ProfileParser.parse(json))
                assertTrue(message, message.contains("credentials"))
            }
        }
    }

    @Test
    fun `game names that look like credential words are fine`() {
        // Only field names are checked, not values: a game may be called "Password Manager".
        val profile = expectSuccess(ProfileParser.parse(minimalProfile(games = """["Password Manager","Token Quest"]""")))
        assertEquals(2, profile.games.size)
    }

    @Test
    fun `oversized profile is rejected even if it is valid json`() {
        // A real, well-formed profile with so many games that it is larger than the limit.
        val manyGames = List(30_000) { "\"Game number $it\"" }.joinToString(",", "[", "]")
        val huge = minimalProfile(games = manyGames)
        assertTrue(huge.length > ProfileParser.MAX_JSON_CHARS)

        val message = expectFailure(ProfileParser.parse(huge))
        assertTrue(message, message.contains("too large"))
        val messageB64 = expectFailure(ProfileParser.parseBase64(base64(huge)))
        assertTrue(messageB64, messageB64.contains("too large"))
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
}
