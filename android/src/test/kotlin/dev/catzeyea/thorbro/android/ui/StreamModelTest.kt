package dev.catzeyea.thorbro.android.ui

import dev.catzeyea.thorbro.android.profile.StoredProfile
import dev.catzeyea.thorbro.android.profile.StreamPc
import dev.catzeyea.thorbro.android.profile.StreamProfile
import dev.catzeyea.thorbro.android.ui.theme.DesignScale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Empty states, what "Connect to PC" does, and the design scale. */
class StreamModelTest {

    private fun pc(
        name: String = "Gaming PC",
        lan: String? = "192.168.1.20",
        tailscaleIp: String? = "100.101.102.103",
        tailscaleName: String? = "gaming-pc.tail1234.ts.net",
        port: Int? = null,
    ) = StreamPc(
        name = name,
        uniqueid = "0123ABCD-4567-89EF-0123-456789ABCDEF",
        lanAddress = lan,
        tailscaleAddress = tailscaleIp,
        tailscaleName = tailscaleName,
        sunshinePort = port,
    )

    private fun stored(games: List<String>, pc: StreamPc = pc()) =
        StoredProfile(StreamProfile(version = 1, pc = pc, games = games), receivedAtMillis = 0L)

    @Test
    fun `no profile gives the Stream empty state`() {
        assertEquals(StreamOverview.NoProfile, StreamModel.overview(null))
    }

    @Test
    fun `no profile gives the Games empty state`() {
        assertEquals(GamesContent.NoProfile, StreamModel.games(null))
    }

    @Test
    fun `profile without games gives the no-games state with the PC name`() {
        assertEquals(GamesContent.NoGames("Gaming PC"), StreamModel.games(stored(emptyList())))
    }

    @Test
    fun `profile with games lists them in profile order`() {
        val games = listOf("Hollow Knight", "Desktop", "Celeste")
        assertEquals(GamesContent.Games("Gaming PC", games), StreamModel.games(stored(games)))
    }

    @Test
    fun `stream overview shows PC name, addresses and game count`() {
        val overview = StreamModel.overview(stored(listOf("Desktop", "Celeste"))) as StreamOverview.Ready
        assertEquals("Gaming PC", overview.pcName)
        assertEquals("100.101.102.103", overview.tailscaleAddress)
        assertEquals("192.168.1.20", overview.homeAddress)
        assertEquals(2, overview.gameCount)
    }

    @Test
    fun `addresses get the port when Sunshine does not use the default port`() {
        val overview = StreamModel.overview(stored(listOf("Desktop"), pc(port = 48000))) as StreamOverview.Ready
        assertEquals("100.101.102.103:48000", overview.tailscaleAddress)
        assertEquals("192.168.1.20:48000", overview.homeAddress)
    }

    @Test
    fun `Tailscale name is used when there is no Tailscale IP, missing addresses stay null`() {
        val overview = StreamModel.overview(stored(listOf("Desktop"), pc(lan = null, tailscaleIp = null))) as StreamOverview.Ready
        assertEquals("gaming-pc.tail1234.ts.net", overview.tailscaleAddress)
        assertEquals(null, overview.homeAddress)
    }

    @Test
    fun `PC without a name is shown by its uniqueid`() {
        val overview = StreamModel.overview(stored(listOf("Desktop"), pc(name = ""))) as StreamOverview.Ready
        assertEquals("0123ABCD-4567-89EF-0123-456789ABCDEF", overview.pcName)
    }

    @Test
    fun `Connect starts the Desktop entry when the profile has one`() {
        val overview = StreamModel.overview(stored(listOf("Hollow Knight", "Desktop"))) as StreamOverview.Ready
        assertEquals(ConnectAction.StartApp("Desktop"), overview.connect)
    }

    @Test
    fun `Connect accepts a differently written Desktop entry and passes its exact name on`() {
        val profile = stored(listOf("Celeste", "desktop ")).profile
        assertEquals(ConnectAction.StartApp("desktop "), StreamModel.connectAction(profile))
    }

    @Test
    fun `exact Desktop wins over a differently written one`() {
        val profile = stored(listOf("DESKTOP", "Desktop")).profile
        assertEquals(ConnectAction.StartApp("Desktop"), StreamModel.connectAction(profile))
    }

    @Test
    fun `Connect opens Moonlight when there is no Desktop entry`() {
        val profile = stored(listOf("Hollow Knight", "Desktop Games")).profile
        assertEquals(ConnectAction.OpenMoonlight, StreamModel.connectAction(profile))
        assertEquals(ConnectAction.OpenMoonlight, StreamModel.connectAction(stored(emptyList()).profile))
    }

    @Test
    fun `design scale fits the 1280x720 reference into the window and stays within limits`() {
        // Thor-like window at a 400 dpi density: 768 x 432 dp -> 0.6 dp per design unit.
        assertEquals(0.6f, DesignScale.forWindow(768f, 432f), 0.0001f)
        // The smaller side decides.
        assertEquals(0.5f, DesignScale.forWindow(1280f, 360f), 0.0001f)
        // Clamped at both ends.
        assertEquals(DesignScale.MIN, DesignScale.forWindow(320f, 180f), 0.0001f)
        assertEquals(DesignScale.MAX, DesignScale.forWindow(4000f, 3000f), 0.0001f)
    }
}
