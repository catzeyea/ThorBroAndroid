package dev.catzeyea.thorbro.android.launch

import dev.catzeyea.thorbro.android.profile.StreamPc
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the launch data (target, action, extras). The final android.content.Intent itself cannot be
 * built in a local unit test, see the comment on [LaunchRequest].
 */
class MoonlightLaunchRequestTest {

    private val pc = StreamPc(
        name = "my-pc",
        uniqueid = "0123ABCD-4567-89EF-0123-456789ABCDEF",
        lanAddress = "192.168.1.20",
        tailscaleAddress = "100.101.102.103",
        tailscaleName = "my-pc.tail1234.ts.net",
        sunshinePort = 47989,
    )

    @Test
    fun `targets the exported Moonlight trampoline with action VIEW`() {
        val request = MoonlightLauncher.buildRequest(pc, "Desktop")

        assertEquals("com.limelight", request.packageName)
        assertEquals("com.limelight.ShortcutTrampoline", request.className)
        assertEquals("android.intent.action.VIEW", request.action)
    }

    @Test
    fun `starts Moonlight in a new task`() {
        val request = MoonlightLauncher.buildRequest(pc, "Desktop")
        val flagActivityNewTask = 0x10000000 // value of Intent.FLAG_ACTIVITY_NEW_TASK
        assertEquals(flagActivityNewTask, request.flags and flagActivityNewTask)
    }

    @Test
    fun `sends exactly UUID and AppName as string extras`() {
        val request = MoonlightLauncher.buildRequest(pc, "Steam Big Picture")

        // No name, addresses, port, AppId or HDR: the PC format has only these two values for Moonlight.
        assertEquals(
            mapOf("UUID" to "0123ABCD-4567-89EF-0123-456789ABCDEF", "AppName" to "Steam Big Picture"),
            request.stringExtras,
        )
    }

    @Test
    fun `uniqueid is passed unchanged in lower case too`() {
        val lowerPc = pc.copy(uniqueid = "0123abcd-4567-89ef-0123-456789abcdef")
        assertEquals("0123abcd-4567-89ef-0123-456789abcdef", MoonlightLauncher.buildRequest(lowerPc, "Desktop").stringExtras["UUID"])
    }

    @Test
    fun `game name is passed exactly, including unicode, quotes, case and spaces`() {
        for (name in listOf("Tom's \"Game\"", "Ünïcödé – 2", " steam big picture ", "ファイナルファンタジー")) {
            assertEquals(name, MoonlightLauncher.buildRequest(pc, name).stringExtras["AppName"])
        }
    }

    @Test
    fun `address for Moonlight's Add PC gets the port only when it is not the default`() {
        assertEquals("192.168.1.20", pc.addressForMoonlight("192.168.1.20"))
        assertEquals("192.168.1.20", pc.copy(sunshinePort = null).addressForMoonlight("192.168.1.20"))
        assertEquals("192.168.1.20:48000", pc.copy(sunshinePort = 48000).addressForMoonlight("192.168.1.20"))
    }
}
