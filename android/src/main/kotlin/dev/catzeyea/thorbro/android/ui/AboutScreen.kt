package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import dev.catzeyea.thorbro.android.MainActivity
import dev.catzeyea.thorbro.android.apps.InstalledApp
import dev.catzeyea.thorbro.android.apps.KnownApps
import dev.catzeyea.thorbro.android.profile.StreamPc
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.focusableCard
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.theme.Corners
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import java.text.DateFormat
import java.util.Date

/**
 * About tab: version, installed apps with official download links, profile details and background info.
 * Text-only cards are focusable so the D-pad can scroll through the whole tab.
 */
@Composable
fun AboutScreen(state: AppUiState, entry: FocusRequester, actions: AppActions) {
    Row(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = ThorDimens.ScrollBleed),
        horizontalArrangement = Arrangement.spacedBy(28.du),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.du)) {
            AboutCard("ThorBro stream launcher", ThorShapes.SetupCards[0], focusable = true, modifier = Modifier.focusRequester(entry)) {
                Text("Version ${state.appVersion}", style = ThorType.style(16, FontWeight.SemiBold))
                BodyText(
                    "Starts PC games on the Thor through Moonlight. This app does not stream, pair or talk to the " +
                        "PC itself: ThorBro on the PC sends the game list, Moonlight does the streaming.",
                )
            }
            AboutCard("Apps on this Thor", ThorShapes.SetupCards[1]) {
                AppStatus(
                    name = "Moonlight",
                    app = state.moonlight,
                    missingHint = "Required. Moonlight does the actual streaming. Install the official app, pair it " +
                        "with your PC inside Moonlight, then open the PC's game list in Moonlight once so it knows " +
                        "the game names.",
                    installedHint = "Make sure Moonlight is paired with your PC (pairing happens inside Moonlight).",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.du)) {
                    ThorButton("moonlight-stream.org", { actions.onOpenUrl(KnownApps.MOONLIGHT_WEBSITE) }, kind = ButtonKind.Small, fontSize = 14)
                    ThorButton("Google Play", { actions.onOpenUrl(KnownApps.MOONLIGHT_PLAY_STORE) }, kind = ButtonKind.Small, fontSize = 14)
                }
                AppStatus(
                    name = "Tailscale",
                    app = state.tailscale,
                    missingHint = "Optional. Only needed to stream when the Thor is not on the same network as the PC.",
                    installedHint = "This app cannot see whether Tailscale is connected. Check that in the Tailscale app.",
                )
                ThorButton("tailscale.com/download/android", { actions.onOpenUrl(KnownApps.TAILSCALE_DOWNLOAD) }, kind = ButtonKind.Small, fontSize = 14)
            }
            AboutCard("Font", ThorShapes.SetupCards[2]) {
                BodyText("JetBrains Mono, Copyright 2020 The JetBrains Mono Project Authors. SIL Open Font License 1.1.")
                ThorButton("Show font license", actions.onShowFontLicense, kind = ButtonKind.Small, fontSize = 14)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.du)) {
            AboutCard("Stream profile", ThorShapes.SetupCards[3], focusable = true) {
                ProfileDetails(state)
            }
            AboutCard("How the game list arrives", ThorShapes.SetupCards[1], focusable = true) {
                BodyText("ThorBro sends it over USB (adb). The command it runs looks like this:")
                Text(
                    "adb shell am start -n ${MainActivity.COMPONENT_FOR_ADB} --es ${MainActivity.EXTRA_PROFILE_B64} <base64 JSON>",
                    style = ThorType.style(13, color = ThorColors.Ink, lineHeight = 1.4f),
                )
                SmallText("The profile never contains passwords or keys. It is stored only inside this app.")
            }
            AboutCard("Good to know", ThorShapes.SetupCards[0], focusable = true) {
                BodyText("The stream stops when Moonlight is not on screen.")
                BodyText("Moonlight may show its own messages: PC not paired, PC offline, or another game running.")
            }
        }
    }
}

/** A panel with a title. [focusable] cards (text only) get a focus ring; cards with buttons do not. */
@Composable
private fun AboutCard(
    title: String,
    corners: Corners,
    modifier: Modifier = Modifier,
    focusable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (focusable) Modifier.focusableCard(corners.shape(), interaction) else Modifier)
            .panel(corners, shadow = ThorShapes.PcCardShadow)
            .padding(horizontal = 24.du, vertical = 20.du),
        verticalArrangement = Arrangement.spacedBy(10.du),
    ) {
        Text(title, style = ThorType.style(22, FontWeight.Bold, lineHeight = 1.2f))
        content()
    }
}

@Composable
private fun AppStatus(name: String, app: InstalledApp, missingHint: String, installedHint: String) {
    if (app.installed) {
        Text(
            "$name: installed" + (app.versionName?.let { " (version $it)" } ?: ""),
            style = ThorType.style(16, FontWeight.SemiBold, ThorColors.Sage),
        )
        SmallText(installedHint)
    } else {
        Text("$name: not installed", style = ThorType.style(16, FontWeight.SemiBold, ThorColors.Danger))
        SmallText(missingHint)
    }
}

@Composable
private fun ProfileDetails(state: AppUiState) {
    state.importMessage?.let { message ->
        Text(
            message.text,
            style = ThorType.style(15, FontWeight.SemiBold, if (message.isError) ThorColors.Danger else ThorColors.Sage, lineHeight = 1.4f),
        )
    }
    val stored = state.storedProfile
    if (stored == null) {
        BodyText("No profile received yet. Connect the Thor to your PC by USB and send the profile from ThorBro.")
        return
    }
    val profile = stored.profile
    val pc = profile.pc
    BodyText("Last received: ${DateFormat.getDateTimeInstance().format(Date(stored.receivedAtMillis))}")
    BodyText("PC: ${pc.name.ifBlank { "(no name)" }}")
    SmallText("Host UUID: ${pc.uniqueid}")
    BodyText("Games: ${profile.games.size}")
    PcAddresses(pc)
}

/**
 * The PC's addresses from the profile. They are only hints for adding the PC in Moonlight by hand
 * (Moonlight usually finds the PC on the LAN by itself); the PC is identified by its UUID.
 */
@Composable
private fun PcAddresses(pc: StreamPc) {
    val addresses = listOfNotNull(
        pc.lanAddress?.let { "LAN" to it },
        pc.tailscaleName?.let { "Tailscale name" to it },
        pc.tailscaleAddress?.let { "Tailscale IP" to it },
    )
    if (addresses.isEmpty()) return
    SmallText("If Moonlight does not find the PC, add it there with:")
    addresses.forEach { (label, address) ->
        Text("$label: ${pc.addressForMoonlight(address)}", style = ThorType.style(14, color = ThorColors.Ink))
    }
}

@Composable
private fun BodyText(text: String) = Text(text, style = ThorType.body())

@Composable
private fun SmallText(text: String) = Text(text, style = ThorType.small())
