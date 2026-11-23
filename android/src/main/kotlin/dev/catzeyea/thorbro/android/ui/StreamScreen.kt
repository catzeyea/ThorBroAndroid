package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du

/**
 * Stream tab: the PC from the profile on the left, the two steps (Tailscale, Connect) on the right.
 * Fills the screen; if it ever does not fit (large font setting), the whole tab scrolls.
 */
@Composable
fun StreamScreen(state: AppUiState, entry: FocusRequester, actions: AppActions, onOpenSetup: () -> Unit) {
    val overview = StreamModel.overview(state.storedProfile)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Room below the cards for their hard shadows, which the scroll container would cut off.
        val shadowRoom = 8.du
        val viewport = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = shadowRoom)
                    .heightIn(min = viewport - shadowRoom)
                    // Both columns get the height of the taller one (and at least the screen height).
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(28.du),
            ) {
                PcCard(
                    overview = overview,
                    importMessage = state.importMessage,
                    entry = entry,
                    onOpenSetup = onOpenSetup,
                    modifier = Modifier.width(380.du).fillMaxHeight(),
                )
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(22.du)) {
                    TailscaleStep(tailscaleInstalled = state.tailscale.installed, onOpen = actions.onOpenTailscale)
                    ConnectStep(
                        overview = overview,
                        moonlightInstalled = state.moonlight.installed,
                        entry = entry,
                        actions = actions,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PcCard(
    overview: StreamOverview,
    importMessage: ImportMessage?,
    entry: FocusRequester,
    onOpenSetup: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .panel(ThorShapes.PcCard, shadow = ThorShapes.PcCardShadow)
            .padding(horizontal = 28.du, vertical = 26.du),
        verticalArrangement = Arrangement.spacedBy(14.du),
    ) {
        Text("your pc", style = ThorType.label())
        when (overview) {
            StreamOverview.NoProfile -> {
                Text("No PC profile yet. Open Setup.", style = ThorType.cardTitle())
                Text(
                    "Follow the steps on the Setup tab, then press Send to Thor app on the PC.",
                    style = ThorType.body(),
                )
                ImportError(importMessage)
                // The "Setup tab hint": gets the initial focus when there is no profile.
                ThorButton("Open Setup", onOpenSetup, Modifier.focusRequester(entry))
            }
            is StreamOverview.Ready -> {
                Text(overview.pcName, style = ThorType.bigTitle())
                StatusChip("✓ profile received")
                ImportError(importMessage)
                Column(Modifier.padding(top = 10.du), verticalArrangement = Arrangement.spacedBy(12.du)) {
                    InfoRow("Tailscale address", overview.tailscaleAddress ?: "not in profile")
                    InfoRow("Home network", overview.homeAddress ?: "not in profile")
                    InfoRow("Games", overview.gameCount.toString(), divider = false)
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
            .padding(horizontal = 26.du, vertical = 22.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.du),
    ) {
        StepNumber(1, ThorColors.Sage)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.du)) {
            Text("Turn on Tailscale", style = ThorType.cardTitle())
            Text("Open it and switch it on. Skip this if you are at home on the same Wi-Fi.", style = ThorType.body())
            if (!tailscaleInstalled) NotInstalled("Tailscale")
        }
        ThorButton("Open Tailscale", onOpen)
    }
}

@Composable
private fun ConnectStep(
    overview: StreamOverview,
    moonlightInstalled: Boolean,
    entry: FocusRequester,
    actions: AppActions,
    modifier: Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .panel(
                ThorShapes.StepCardMain,
                background = ThorColors.PanelSelected,
                border = ThorColors.Ink,
                shadow = ThorShapes.MainCardShadow,
            )
            .padding(horizontal = 26.du, vertical = 22.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.du),
    ) {
        StepNumber(2, ThorColors.Orange)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.du)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.du)) {
                Text("Connect to your PC", style = ThorType.style(30, FontWeight.Bold, lineHeight = 1.2f))
                Text("Starts Moonlight and takes you straight into your PC.", style = ThorType.body())
                if (!moonlightInstalled) NotInstalled("Moonlight")
                if (overview is StreamOverview.NoProfile) {
                    Text("Needs a PC profile first, see Setup.", style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Orange))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.du)) {
                if (overview is StreamOverview.Ready) {
                    ThorButton(
                        "Connect to PC",
                        actions.onConnect,
                        Modifier.focusRequester(entry),
                        kind = ButtonKind.Main,
                    )
                }
                ThorButton("Open Moonlight only", actions.onOpenMoonlight, kind = ButtonKind.Small)
            }
        }
    }
}

@Composable
private fun NotInstalled(appName: String) {
    Text(
        "$appName is not installed on this Thor. Download links: About tab.",
        style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Danger, lineHeight = 1.4f),
    )
}
