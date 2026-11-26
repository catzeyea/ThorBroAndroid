package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.InfoRow
import dev.catzeyea.thorbro.android.ui.components.StatusChip
import dev.catzeyea.thorbro.android.ui.components.StepNumber
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du

/**
 * Stream tab: the PC from the profile on the left; on the right step 1 (Tailscale) and step 2
 * (Connect to PC) with the PC's games, so a game can be started directly. Without a profile the PC
 * card points to Setup.
 */
@Composable
fun StreamScreen(state: AppUiState, entry: FocusRequester, actions: AppActions) {
    OnControllerKeys { false }
    val overview = StreamModel.overview(state.storedProfile)
    val openSetup = { actions.onOpenSubScreen(SubScreen.Setup) }
    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(24.du)) {
        PcCard(overview, state.importMessage, entry, openSetup, Modifier.width(340.du).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.du)) {
            TailscaleStep(tailscaleInstalled = state.tailscale.installed, onOpen = actions.onOpenTailscale)
            ConnectStep(state, overview, entry, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PcCard(overview: StreamOverview, importMessage: ImportMessage?, entry: FocusRequester, onOpenSetup: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .panel(ThorShapes.PcCard, shadow = ThorShapes.PcCardShadow)
            .padding(horizontal = 22.du, vertical = 22.du),
        verticalArrangement = Arrangement.spacedBy(12.du),
    ) {
        Text("your pc", style = ThorType.label())
        when (overview) {
            StreamOverview.NoProfile -> {
                Text("No PC profile yet. Open Setup.", style = ThorType.cardTitle())
                Text("Follow the steps in Setup, then press Send to Thor app on the PC.", style = ThorType.body())
                ImportError(importMessage)
                // The Setup hint: gets the first focus while there is no profile.
                ThorButton("Open Setup", onOpenSetup, Modifier.focusRequester(entry))
            }
            is StreamOverview.Ready -> {
                Text(overview.pcName, style = ThorType.bigTitle())
                StatusChip("✓ profile received")
                ImportError(importMessage)
                Column(Modifier.padding(top = 8.du), verticalArrangement = Arrangement.spacedBy(10.du)) {
                    InfoRow("Tailscale address", overview.tailscaleAddress ?: "not in profile")
                    InfoRow("Home network", overview.homeAddress ?: "not in profile", divider = false)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Wrong PC or old list? Press Send to Thor app on the PC again.", style = ThorType.small())
    }
}

/** Shows why the last profile was rejected. A successful import is already visible as the profile itself. */
@Composable
private fun ImportError(message: ImportMessage?) {
    if (message != null && message.isError) {
        Text(message.text, style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Danger, lineHeight = 1.4f))
    }
}

@Composable
private fun TailscaleStep(tailscaleInstalled: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .panel(ThorShapes.StepCardQuiet, shadow = ThorShapes.StepQuietShadow)
            .padding(horizontal = 20.du, vertical = 14.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.du),
    ) {
        StepNumber(1, ThorColors.Sage, diameter = 46, fontSize = 22)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.du)) {
            Text("Turn on Tailscale", style = ThorType.style(21, FontWeight.Bold))
            Text("Skip this if you are at home on the same Wi-Fi.", style = ThorType.style(15, color = ThorColors.Body))
            if (!tailscaleInstalled) NotInstalled("Tailscale")
        }
        ThorButton("Open Tailscale", onOpen, fontSize = 17)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectStep(state: AppUiState, overview: StreamOverview, entry: FocusRequester, actions: AppActions, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .panel(ThorShapes.StepCardMain, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.du, vertical = 18.du),
        verticalArrangement = Arrangement.spacedBy(14.du),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.du)) {
            StepNumber(2, ThorColors.Orange, diameter = 46, fontSize = 22)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.du)) {
                Text("Connect to your PC", style = ThorType.style(25, FontWeight.Bold))
                Text("Starts Moonlight and takes you straight into your PC.", style = ThorType.style(15, color = ThorColors.Body))
                if (!state.moonlight.installed) NotInstalled("Moonlight")
                if (overview is StreamOverview.NoProfile) {
                    Text("Needs a PC profile first, see Setup.", style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Orange))
                }
            }
            if (overview is StreamOverview.Ready) {
                ThorButton("Connect to PC", actions.onConnect, Modifier.focusRequester(entry), kind = ButtonKind.Main, fontSize = 21)
            }
        }
        when (val games = StreamModel.games(state.storedProfile)) {
            is GamesContent.Games -> {
                Text("or start a game directly", style = ThorType.label())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.du), verticalArrangement = Arrangement.spacedBy(10.du)) {
                    games.games.forEach { game ->
                        ThorButton(game, { actions.onLaunchGame(game) }, kind = ButtonKind.Secondary, fontSize = 17)
                    }
                }
            }
            is GamesContent.NoGames -> Text(
                "No games from ${games.pcName}. Add them in Sunshine on the PC, then press Send to Thor app in ThorBro again.",
                style = ThorType.small(),
            )
            GamesContent.NoProfile -> Unit
        }
        ThorButton("Open Moonlight only", actions.onOpenMoonlight, kind = ButtonKind.Small, fontSize = 15)
    }
}

@Composable
private fun NotInstalled(appName: String) {
    Text(
        "$appName is not installed on this Thor. Download links: menu (Y) → About.",
        style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Danger, lineHeight = 1.4f),
    )
}
