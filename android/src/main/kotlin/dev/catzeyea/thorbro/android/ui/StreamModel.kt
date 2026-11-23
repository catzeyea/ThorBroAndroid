package dev.catzeyea.thorbro.android.ui

import dev.catzeyea.thorbro.android.profile.StoredProfile
import dev.catzeyea.thorbro.android.profile.StreamProfile

// What the Stream and Games tabs show, derived from the saved profile. Plain logic, unit-tested.

/** What "Connect to PC" does. */
sealed interface ConnectAction {
    /** Start this Sunshine app through Moonlight, the same way "Play on Thor" starts a game. */
    data class StartApp(val appName: String) : ConnectAction

    /** The profile has no Desktop entry: open Moonlight at its PC list instead. */
    data object OpenMoonlight : ConnectAction
}

/** Content of the Stream tab. */
sealed interface StreamOverview {
    /** No profile received yet: the tab shows "No PC profile yet. Open Setup." */
    data object NoProfile : StreamOverview

    data class Ready(
        val pcName: String,
        /** Tailscale IP (or MagicDNS name if there is no IP), with `:port` if Sunshine uses a non-default port. */
        val tailscaleAddress: String?,
        /** LAN address, with `:port` if needed. */
        val homeAddress: String?,
        val gameCount: Int,
        val connect: ConnectAction,
    ) : StreamOverview
}

/** Content of the Games tab. */
sealed interface GamesContent {
    data object NoProfile : GamesContent

    data class NoGames(val pcName: String) : GamesContent

    data class Games(val pcName: String, val games: List<String>) : GamesContent
}

object StreamModel {

    /** Name of Sunshine's built-in app that streams the whole desktop. */
    const val DESKTOP_APP = "Desktop"

    fun overview(stored: StoredProfile?): StreamOverview {
        val profile = stored?.profile ?: return StreamOverview.NoProfile
        val pc = profile.pc
        return StreamOverview.Ready(
            pcName = pc.displayName,
            tailscaleAddress = (pc.tailscaleAddress ?: pc.tailscaleName)?.let(pc::addressForMoonlight),
            homeAddress = pc.lanAddress?.let(pc::addressForMoonlight),
            gameCount = profile.games.size,
            connect = connectAction(profile),
        )
    }

    /**
     * "Connect to PC" starts the profile's Desktop entry if there is one, otherwise it opens Moonlight.
     * An exact "Desktop" wins; otherwise a case/space-insensitive match is accepted. The name is passed
     * on exactly as it is in the profile, because Moonlight compares app names exactly.
     */
    fun connectAction(profile: StreamProfile): ConnectAction {
        val desktop = profile.games.firstOrNull { it == DESKTOP_APP }
            ?: profile.games.firstOrNull { it.trim().equals(DESKTOP_APP, ignoreCase = true) }
        return if (desktop != null) ConnectAction.StartApp(desktop) else ConnectAction.OpenMoonlight
    }

    fun games(stored: StoredProfile?): GamesContent {
        val profile = stored?.profile ?: return GamesContent.NoProfile
        val pcName = profile.pc.displayName
        return if (profile.games.isEmpty()) GamesContent.NoGames(pcName) else GamesContent.Games(pcName, profile.games)
    }
}
