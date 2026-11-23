package dev.catzeyea.thorbro.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import dev.catzeyea.thorbro.android.R
import dev.catzeyea.thorbro.android.apps.InstalledApp
import dev.catzeyea.thorbro.android.profile.StoredProfile
import dev.catzeyea.thorbro.android.ui.components.Hint
import dev.catzeyea.thorbro.android.ui.components.HintBar
import dev.catzeyea.thorbro.android.ui.components.KeyChip
import dev.catzeyea.thorbro.android.ui.components.Notice
import dev.catzeyea.thorbro.android.ui.components.controllerClickable
import dev.catzeyea.thorbro.android.ui.components.focusRing
import dev.catzeyea.thorbro.android.ui.components.hardShadow
import dev.catzeyea.thorbro.android.ui.navigation.AppTab
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.theme.ThorBroTheme
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import kotlinx.coroutines.delay

/** A short message about the last profile handoff (success or error). */
data class ImportMessage(val isError: Boolean, val text: String)

/** Everything the screens show. MainActivity owns it; the UI only reads it. */
data class AppUiState(
    val moonlight: InstalledApp,
    val tailscale: InstalledApp,
    val storedProfile: StoredProfile?,
    val importMessage: ImportMessage?,
    val appVersion: String,
    /** Shown instead of the tabs while not null. */
    val subScreen: SubScreen? = null,
    /** Short message in the hint bar; disappears after a few seconds. */
    val notice: Notice? = null,
)

/** What the UI can ask MainActivity to do. */
class AppActions(
    val onConnect: () -> Unit,
    val onLaunchGame: (gameName: String) -> Unit,
    val onOpenTailscale: () -> Unit,
    val onOpenMoonlight: () -> Unit,
    val onOpenUrl: (url: String) -> Unit,
    val onShowFontLicense: () -> Unit,
    val onCloseSubScreen: () -> Unit,
    val onNoticeExpired: () -> Unit,
)

private const val NOTICE_MILLIS = 4_000L

/**
 * Root of the UI. In Jetpack Compose the UI is a function of the state: whenever [state] or
 * [selectedTab] change, Compose calls this again and redraws what changed.
 *
 * Focus: every screen attaches [contentEntry] to its first element (Stream: Connect to PC, Games: the
 * selected game, ...). It gets focus when a tab opens, and D-pad Down from the tab row goes there.
 */
@Composable
fun ThorBroApp(
    state: AppUiState,
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    actions: AppActions,
) {
    ThorBroTheme {
        val contentEntry = remember { FocusRequester() }
        // Bumped when a tab is activated again, so focus moves into its content even if the tab did not change.
        var focusTick by remember { mutableIntStateOf(0) }
        val subScreen = state.subScreen
        val activateTab: (AppTab) -> Unit = { tab ->
            onSelectTab(tab)
            focusTick++
        }

        LaunchedEffect(selectedTab, subScreen != null, focusTick) {
            // Wait one frame so the new screen is laid out before it takes focus.
            withFrameNanos { }
            // Throws if nothing is attached to the requester; then focus simply stays where it is.
            runCatching { contentEntry.requestFocus() }
        }

        LaunchedEffect(state.notice) {
            if (state.notice != null) {
                delay(NOTICE_MILLIS)
                actions.onNoticeExpired()
            }
        }

        // System back gesture / button: closes a sub-screen. On the main tabs it keeps Android's default.
        BackHandler(enabled = subScreen != null) { actions.onCloseSubScreen() }

        Box(
            Modifier
                .fillMaxSize()
                .background(ThorColors.Bg)
                .safeDrawingPadding()
                .padding(start = 40.du, end = 40.du, top = 24.du, bottom = 20.du),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.du)) {
                Header(
                    selectedTab = selectedTab,
                    showTabs = subScreen == null,
                    onActivateTab = activateTab,
                    contentEntry = contentEntry,
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        subScreen != null -> SubScreenContent(subScreen, contentEntry, actions)
                        else -> when (selectedTab) {
                            AppTab.Stream -> StreamScreen(state, contentEntry, actions, onOpenSetup = { activateTab(AppTab.Setup) })
                            AppTab.Games -> GamesScreen(state, contentEntry, actions, onOpenSetup = { activateTab(AppTab.Setup) })
                            AppTab.Setup -> SetupScreen(contentEntry)
                            AppTab.About -> AboutScreen(state, contentEntry, actions)
                        }
                    }
                }
                HintBar(hints = hintsFor(selectedTab, subScreen, state), notice = state.notice)
            }
        }
    }
}

private val A = listOf("A")
private val B = listOf("B")
private val SHOULDERS = listOf("L1", "R1")

private fun hintsFor(tab: AppTab, subScreen: SubScreen?, state: AppUiState): List<Hint> {
    if (subScreen != null) return listOf(Hint(A, "Select"), Hint(B, "Back"))
    val switchTab = Hint(SHOULDERS, "Switch tab")
    return when (tab) {
        // Setup has nothing to activate, like the reference design.
        AppTab.Setup -> listOf(Hint(B, "Back"), switchTab)
        AppTab.Games -> {
            val hasGames = StreamModel.games(state.storedProfile) is GamesContent.Games
            listOf(Hint(A, if (hasGames) "Play" else "Select"), Hint(B, "Back"), switchTab)
        }
        else -> listOf(Hint(A, "Select"), Hint(B, "Back"), switchTab)
    }
}

@Composable
private fun Header(
    selectedTab: AppTab,
    showTabs: Boolean,
    onActivateTab: (AppTab) -> Unit,
    contentEntry: FocusRequester,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.du)) {
            Image(painterResource(R.drawable.thorbro_logo), contentDescription = null, modifier = Modifier.size(52.du))
            Text("ThorBro", style = ThorType.brand())
        }
        if (showTabs) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.du)) {
                KeyChip("L1", ThorShapes.ChipLeft, ThorColors.InkSoft, ThorColors.Mute, FontWeight.SemiBold)
                AppTab.entries.forEach { tab ->
                    TabButton(tab, selected = tab == selectedTab, onActivate = { onActivateTab(tab) }, contentEntry)
                }
                KeyChip("R1", ThorShapes.ChipRight, ThorColors.InkSoft, ThorColors.Mute, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TabButton(tab: AppTab, selected: Boolean, onActivate: () -> Unit, contentEntry: FocusRequester) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = (if (selected) ThorShapes.TabActive else ThorShapes.TabIdle).shape()
    Box(
        Modifier
            .then(if (selected) Modifier.rotate(ThorShapes.ACTIVE_TAB_ROTATION) else Modifier)
            .focusRing(focused, shape)
            .then(if (selected) Modifier.hardShadow(ThorShapes.ActiveTabShadow, shape) else Modifier)
            .background(if (selected) ThorColors.Orange else Color.Transparent, shape)
            .border(ThorDimens.Outline, if (selected) ThorColors.Ink else Color.Transparent, shape)
            // D-pad Down from any tab goes to the current screen's first element.
            .focusProperties { down = contentEntry }
            .controllerClickable(interaction, onActivate)
            .heightIn(min = ThorDimens.MinTarget)
            .padding(horizontal = (if (selected) 24 else 22).du, vertical = 11.du),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tab.title,
            style = ThorType.style(
                18,
                if (selected) FontWeight.SemiBold else FontWeight.Medium,
                if (selected) ThorColors.OnAccent else ThorColors.Ink,
            ),
        )
    }
}
