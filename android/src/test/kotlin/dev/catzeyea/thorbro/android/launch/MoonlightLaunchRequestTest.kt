package dev.catzeyea.thorbro.android.launch

import dev.catzeyea.thorbro.android.profile.StreamGame
import dev.catzeyea.thorbro.android.profile.StreamHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the launch data (target, action, extras). The final android.content.Intent itself cannot be
 * built in a local unit test, see the comment on [LaunchRequest].
 */
class MoonlightLaunchRequestTest {

    private val host = StreamHost(uuid = "0123abcd-4567-89ef-0123-456789abcdef", name = "Gaming-PC")

    @Test
    fun `targets the exported Moonlight trampoline with action VIEW`() {
        val request = MoonlightLauncher.buildRequest(host, StreamGame("Desktop", appId = null, hdr = false))

        assertEquals("com.limelight", request.packageName)
        assertEquals("com.limelight.ShortcutTrampoline", request.className)
        assertEquals("android.intent.action.VIEW", request.action)
    }

    @Test
    fun `starts Moonlight in a new task`() {
        val request = MoonlightLauncher.buildRequest(host, StreamGame("Desktop", appId = null, hdr = false))
        val flagActivityNewTask = 0x10000000 // value of Intent.FLAG_ACTIVITY_NEW_TASK
        assertEquals(flagActivityNewTask, request.flags and flagActivityNewTask)
    }

    @Test
    fun `UUID and AppName are passed as strings`() {
        val request = MoonlightLauncher.buildRequest(host, StreamGame("Desktop", appId = null, hdr = false))

        assertEquals(host.uuid, request.stringExtras["UUID"])
        assertEquals("Desktop", request.stringExtras["AppName"])
        assertFalse("AppId must be absent when the profile has none", request.stringExtras.containsKey("AppId"))
    }

    @Test
    fun `AppId is passed as a String extra when present`() {
        val request = MoonlightLauncher.buildRequest(host, StreamGame("Elden Ring", appId = "881448767", hdr = false))

        // Moonlight reads AppId with getStringExtra(); it must be in the String map, not anywhere else.
        assertEquals("881448767", request.stringExtras["AppId"])
        assertFalse(request.booleanExtras.containsKey("AppId"))
    }

    @Test
    fun `HDR is a boolean extra and only sent when true`() {
        val withHdr = MoonlightLauncher.buildRequest(host, StreamGame("Steam", appId = null, hdr = true))
        val withoutHdr = MoonlightLauncher.buildRequest(host, StreamGame("Steam", appId = null, hdr = false))

        assertEquals(mapOf("HDR" to true), withHdr.booleanExtras)
        assertFalse(withHdr.stringExtras.containsKey("HDR"))
        assertTrue(withoutHdr.booleanExtras.isEmpty())
    }

    @Test
    fun `host name is not sent to Moonlight`() {
        val request = MoonlightLauncher.buildRequest(host, StreamGame("Desktop", appId = null, hdr = false))
        assertFalse(request.stringExtras.containsKey("Name"))
        assertEquals(setOf("UUID", "AppName"), request.stringExtras.keys)
    }
}
