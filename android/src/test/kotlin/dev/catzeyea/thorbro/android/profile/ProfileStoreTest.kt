package dev.catzeyea.thorbro.android.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class ProfileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val goodJson = """{"format":"thorbro.stream-profile","version":1,""" +
        """"pc":{"name":"my-pc","uniqueid":"0123ABCD-4567-89EF-0123-456789ABCDEF"},"games":["Desktop"]}"""

    private fun base64(json: String) = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    private fun savedFile() = File(folder.root, "stream-profile.json")

    @Test
    fun `valid profile is saved and loaded again`() {
        val store = ProfileStore(folder.root)
        assertTrue(store.importBase64(base64(goodJson)) is ParseResult.Success)

        assertEquals(listOf("Desktop"), store.load()?.profile?.games)
        assertEquals(goodJson, savedFile().readText())
    }

    @Test
    fun `broken new profile leaves the previous one unchanged`() {
        val store = ProfileStore(folder.root)
        store.importBase64(base64(goodJson))

        val broken = listOf(
            "not*base64!",
            base64("{ not json"),
            base64(goodJson.replace("thorbro.stream-profile", "other")),
            base64(goodJson.replace("\"version\":1", "\"version\":2")),
            base64(goodJson.replace("\"Desktop\"", "{\"name\":\"Desktop\"}")),
            base64(goodJson.replace("\"name\":\"my-pc\"", "\"password\":\"x\"")),
        )
        for (encoded in broken) {
            assertTrue(store.importBase64(encoded) is ParseResult.Failure)
            assertEquals(goodJson, savedFile().readText())
            assertEquals(listOf("Desktop"), store.load()?.profile?.games)
        }
    }

    @Test
    fun `profile saved in the old android-only format is not loaded`() {
        savedFile().writeText("""{"version":1,"host":{"uuid":"0123abcd-4567-89ef-0123-456789abcdef"},"games":[]}""")
        assertNull(ProfileStore(folder.root).load())
    }
}
