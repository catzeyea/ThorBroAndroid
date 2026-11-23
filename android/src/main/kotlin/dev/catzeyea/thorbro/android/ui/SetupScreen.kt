package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.ui.components.StepNumber
import dev.catzeyea.thorbro.android.ui.components.focusableCard
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du

private data class SetupStep(val title: String, val text: String, val tag: String)

// Texts from docs/design/thorbro-android-design/reference/setup.dc.html.
private val STEPS = listOf(
    SetupStep(
        "Tailscale on PC and Thor",
        "Install Tailscale on both and log in to the same account. This lets the Thor reach your PC from anywhere.",
        "once",
    ),
    SetupStep("Sunshine on the PC", "Install Sunshine on the PC. It is the part that streams your games.", "once"),
    SetupStep(
        "Send the PC profile",
        "On the PC open ThorBro, go to Stream and press Send to Thor app.",
        "again when your games change",
    ),
    SetupStep(
        "Pair Moonlight",
        "Press Connect on the Stream tab. Moonlight shows a PIN. Type it in Sunshine on the PC.",
        "once",
    ),
)

/**
 * Setup tab: four static steps in a 2x2 grid. The cards are focusable (without an action), so the D-pad
 * can reach and scroll to every card if the grid ever does not fit on the screen.
 */
@Composable
fun SetupScreen(entry: FocusRequester) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bleed = ThorDimens.ScrollBleed
        val gap = 22.du
        var titleHeightPx by remember { mutableIntStateOf(0) }
        val titleHeight = with(density) { titleHeightPx.toDp() }
        // Each grid row is at least half of the space below the title, like the design's 2x2 grid.
        val rowMin = ((maxHeight - titleHeight - gap * 2 - bleed * 2) / 2).coerceAtLeast(0.dp)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = bleed),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Column(
                Modifier.onSizeChanged { titleHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(4.du),
            ) {
                Text("Connect your PC in 4 steps", style = ThorType.style(34, FontWeight.Bold, letterSpacingEm = -0.02f))
                Text("You only do this once. After that, Connect on the Stream tab is enough.", style = ThorType.style(16, color = ThorColors.Mute))
            }
            for (row in 0 until 2) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = rowMin)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(26.du),
                ) {
                    for (column in 0 until 2) {
                        val index = row * 2 + column
                        StepCard(
                            number = index + 1,
                            step = STEPS[index],
                            index = index,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(if (index == 0) Modifier.focusRequester(entry) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, step: SetupStep, index: Int, modifier: Modifier) {
    val corners = ThorShapes.SetupCards[index]
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .focusableCard(corners.shape(), interaction)
            .panel(corners, shadow = ThorShapes.SetupShadows[index])
            .padding(horizontal = 24.du, vertical = 20.du),
        horizontalArrangement = Arrangement.spacedBy(18.du),
    ) {
        StepNumber(number, ThorColors.Sage, diameter = 48, fontSize = 22)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.du)) {
            Text(step.title, style = ThorType.style(22, FontWeight.Bold, lineHeight = 1.2f))
            Text(step.text, style = ThorType.style(15, color = ThorColors.Body, lineHeight = 1.5f))
            Spacer(Modifier.weight(1f))
            Text(step.tag, style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Orange))
        }
    }
}
