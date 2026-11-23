package dev.catzeyea.thorbro.android.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Only the "not installed" path is tested here. The other paths need a real android.content.Intent,
 * which local unit tests cannot create without Robolectric (on the PC the Android classes are stubs).
 */
class AppOpenerTest {

    @Test
    fun `missing launch intent means not installed and nothing is started`() {
        var asked: String? = null
        var started = false

        val result = AppOpener.open(
            packageName = KnownApps.TAILSCALE_PACKAGE,
            appName = "Tailscale",
            launchIntentFor = { asked = it; null },
            start = { started = true },
        )

        assertEquals(OpenAppResult.NotInstalled("Tailscale"), result)
        assertEquals("Tailscale is not installed", (result as OpenAppResult.NotInstalled).message)
        assertEquals("com.tailscale.ipn", asked)
        assertFalse(started)
    }

    @Test
    fun `not installed message names the app`() {
        val result = AppOpener.open(KnownApps.MOONLIGHT_PACKAGE, "Moonlight", { null }, { })
        assertEquals("Moonlight is not installed", (result as OpenAppResult.NotInstalled).message)
    }
}
