package dev.catzeyea.thorbro.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import dev.catzeyea.thorbro.android.R
import dev.catzeyea.thorbro.android.apps.InstalledApp
import dev.catzeyea.thorbro.android.profile.StoredProfile
import dev.catzeyea.thorbro.android.ui.components.Hint
import dev.catzeyea.thorbro.android.ui.components.HintBar
import dev.catzeyea.thorbro.android.ui.components.KeyChip
import dev.catzeyea.thorbro.android.ui.components.ListRow
import dev.catzeyea.thorbro.android.ui.components.Notice
import dev.catzeyea.thorbro.android.ui.components.controllerClickable
import dev.catzeyea.thorbro.android.ui.components.focusRing
import dev.catzeyea.thorbro.android.ui.components.hardShadow
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.navigation.AppTab
import dev.catzeyea.thorbro.android.ui.navigation.KeyRouter
import dev.catzeyea.thorbro.android.ui.navigation.LocalKeyRouter
import dev.catzeyea.thorbro.android.ui.navigation.LocalPendingFocus
import dev.catzeyea.thorbro.android.ui.navigation.PendingFocus
import dev.catzeyea.thorbro.android.ui.navigation.tryFocus
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
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

/** Everything the screens show that MainActivity owns. The UI only reads it. */
data class AppUiState(
    val moonlight: InstalledApp,
    val tailscale: InstalledApp,
    val storedProfile: StoredProfile?,
    val importMessage: ImportMessage?,
    val appVersion: String,
    /** Screens on top of the tabs, last = shown. */
    val subScreens: List<SubScreen> = emptyList(),
    /** Short message in the hint bar; disappears after a few seconds. */
    val notice: Notice? = null,
    /** PC connected over USB; null = can't be read. */
    val pcConnected: Boolean? = null,
    /** "All files access" granted (Settings). */
    val allFilesAccess: Boolean = false,
) {
    val subScreen: SubScreen? get() = subScreens.lastOrNull()
}

/** What the UI can ask MainActivity to do. */
class AppActions(
    val onConnect: () -> Unit,
    val onLaunchGame: (gameName: String) -> Unit,
    val onOpenTailscale: () -> Unit,
    val onOpenMoonlight: () -> Unit,
    val onOpenUrl: (url: String) -> Unit,
    val onShowFontLicense: () -> Unit,
    val onOpenSubScreen: (SubScreen) -> Unit,
    val onCloseSubScreen: () -> Unit,
    val onNoticeExpired: () -> Unit,
    val onNotice: (text: String, isError: Boolean) -> Unit,
    val onOpenAllFilesAccess: () -> Unit,
)

/** The state holders of the tabs. */
class Controllers(val health: HealthController, val files: FilesController, val saves: SavesController)

private const val NOTICE_MILLIS = 4_000L

/**
 * Root of the UI: header with tabs, the current tab (or a sub-screen), hint bar.
 *
 * Focus: every screen attaches [contentEntry] to its first element. It gets focus in the same frame
 * the new screen is composed (SideEffect below, before layout and draw); together with the focus ring
 * that is drawn from focus state at draw time, the ring appears in the same frame as the new screen.
 * Measured on a Thor: ring in the 1st drawn frame after L1/R1 (before this: the 3rd).
 */
@Composable
fun ThorBroApp(
    state: AppUiState,
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    actions: AppActions,
    controllers: Controllers,
    keyRouter: KeyRouter,
) {
    CompositionLocalProvider(LocalKeyRouter provides keyRouter) {
        ThorBroTheme {
            val contentEntry = remember { FocusRequester() }
            val menuEntry = remember { FocusRequester() }
            // Bumped when a tab is activated again, so focus moves into its content even if the tab did not change.
            var focusTick by remember { mutableIntStateOf(0) }
            val subScreen = state.subScreen
            val menuOpen = subScreen == SubScreen.Menu
            val activateTab: (AppTab) -> Unit = { tab ->
                if (state.subScreens.isEmpty()) {
                    onSelectTab(tab)
                    focusTick++
                }
            }

            val pendingFocus = remember { PendingFocus() }
            val focusKey = listOf(selectedTab, subScreen, focusTick)
            val requester = if (menuOpen) menuEntry else contentEntry
            // Runs right after the new screen was applied, in the same frame and before it is laid out
            // and drawn: its focus targets exist already. (A LaunchedEffect would start one frame later.)
            SideEffect {
                if (pendingFocus.key != focusKey) {
                    pendingFocus.key = focusKey
                    // Not there yet (a lazy list row, or a list that is still loading): the element takes
                    // focus when it is placed (Modifier.focusEntry), or one of the next frames does it.
                    pendingFocus.armed = !requester.tryFocus()
                }
            }
            LaunchedEffect(focusKey) {
                repeat(FOCUS_ATTEMPTS) {
                    if (!pendingFocus.armed) return@LaunchedEffect
                    withFrameNanos { }
                    if (requester.tryFocus()) pendingFocus.armed = false
                }
            }

            LaunchedEffect(state.notice) {
                if (state.notice != null) {
                    delay(NOTICE_MILLIS)
                    actions.onNoticeExpired()
                }
            }

            // System back gesture / button: closes a sub-screen. On the main tabs it keeps Android's default.
            BackHandler(enabled = subScreen != null) { actions.onCloseSubScreen() }

            CompositionLocalProvider(LocalPendingFocus provides pendingFocus) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ThorColors.Bg)
                        .safeDrawingPadding()
                        .padding(start = 40.du, end = 40.du, top = 20.du, bottom = 18.du),
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.du)) {
                        Header(
                            selectedTab = selectedTab,
                            tabsEnabled = state.subScreens.isEmpty(),
                            pcConnected = state.pcConnected,
                            onActivateTab = activateTab,
                            onOpenMenu = { actions.onOpenSubScreen(SubScreen.Menu) },
                            contentEntry = contentEntry,
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            val covering = state.subScreens.lastOrNull { it != SubScreen.Menu }
                            when {
                                covering != null -> SubScreenContent(covering, state, contentEntry, actions, controllers)
                                else -> when (selectedTab) {
                                    AppTab.Start -> StartScreen(state, controllers.health)
                                    AppTab.Files -> FilesScreen(state, controllers.files, contentEntry, actions)
                                    AppTab.Saves -> SavesScreen(state, controllers.saves, contentEntry, actions)
                                    AppTab.Stream -> StreamScreen(state, contentEntry, actions)
                                }
                            }
                            if (menuOpen) MenuOverlay(menuEntry, actions, Modifier.align(Alignment.TopEnd))
                        }
                        HintBar(hints = hintsFor(selectedTab, subScreen, controllers), notice = state.notice)
                    }
                }
            }
        }
    }
}

private const val FOCUS_ATTEMPTS = 4

private val A = listOf("A")
private val B = listOf("B")
private val X = listOf("X")
private val Y = listOf("Y")
private val SELECT = listOf("Select")
private val SHOULDERS = listOf("L1", "R1")

private fun hintsFor(tab: AppTab, subScreen: SubScreen?, controllers: Controllers): List<Hint> {
    val switchTab = Hint(SHOULDERS, "Switch tab")
    return when (subScreen) {
        null -> when (tab) {
            AppTab.Start -> listOf(Hint(Y, "Menu"), switchTab)
            AppTab.Files -> listOf(Hint(A, "Open"), Hint(B, "Back"), Hint(X, "Scan this folder"), Hint(SELECT, "Pin or folder options"), switchTab)
            AppTab.Saves -> listOf(Hint(A, "Open game"), Hint(Y, "Menu"), switchTab)
            AppTab.Stream -> listOf(Hint(A, "Select"), Hint(Y, "Menu"), switchTab)
        }
        SubScreen.Menu -> listOf(Hint(A, "Open"), Hint(B, "Close"))
        SubScreen.Scan -> when (controllers.files.scan) {
            is ScanState.Preview -> {
                val scan = controllers.files.scan as ScanState.Preview
                when {
                    // "A Move" only when something can move.
                    controllers.files.previewFocusOnRow -> listOf(Hint(A, "Switch file on/off"), Hint(B, "Cancel"))
                    scan.selected.isNotEmpty() -> listOf(Hint(A, "Move"), Hint(B, "Cancel"))
                    else -> listOf(Hint(A, "Close"), Hint(B, "Close"))
                }
            }
            is ScanState.Moving -> listOf(Hint(B, "Stop after this file"))
            is ScanState.Done -> listOf(Hint(A, "Select"), Hint(B, "Close"))
            else -> listOf(Hint(B, "Close"))
        }
        is SubScreen.ConfirmRestore -> listOf(Hint(A, "Select"), Hint(B, "Cancel"))
        is SubScreen.SaveGame -> {
            val save = controllers.saves.byKey(subScreen.saveKey)
            if (save != null && save.emulator.canRestore && save.backups.isNotEmpty()) {
                listOf(Hint(A, "Back up"), Hint(X, "Restore selected"), Hint(B, "Back"))
            } else {
                listOf(Hint(A, "Back up"), Hint(B, "Back"))
            }
        }
        is SubScreen.ScanInfo -> listOf(Hint(A, "Scan"), Hint(B, "Cancel"), Hint(SELECT, "Don't show this again"))
        SubScreen.Help -> listOf(Hint(B, "Back"))
        else -> listOf(Hint(A, "Select"), Hint(B, "Back"))
    }
}

// ---------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------

@Composable
private fun Header(
    selectedTab: AppTab,
    tabsEnabled: Boolean,
    pcConnected: Boolean?,
    onActivateTab: (AppTab) -> Unit,
    onOpenMenu: () -> Unit,
    contentEntry: FocusRequester,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.du)) {
            Box(Modifier.width(92.du)) {
                listOf(R.drawable.thorbro_bro_sage, R.drawable.thorbro_bro_orange, R.drawable.thorbro_bro_coral).forEachIndexed { i, id ->
                    Image(painterResource(id), contentDescription = null, modifier = Modifier.offset(x = (i * 26).du).size(40.du))
                }
            }
            Text("ThorBro", style = ThorType.brand())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.du)) {
            KeyChip("L1", ThorShapes.ChipLeft, ThorColors.InkSoft, ThorColors.Mute, FontWeight.SemiBold)
            AppTab.entries.forEach { tab ->
                TabButton(tab, selected = tab == selectedTab, enabled = tabsEnabled, onActivate = { onActivateTab(tab) }, contentEntry)
            }
            KeyChip("R1", ThorShapes.ChipRight, ThorColors.InkSoft, ThorColors.Mute, FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.du)) {
            StatusPill(pcConnected)
            Image(
                painterResource(if (pcConnected == false) R.drawable.thorbro_mood_wait_off else R.drawable.thorbro_mood_done),
                contentDescription = if (pcConnected == false) "ThorBro is asleep" else "ThorBro is awake",
                modifier = Modifier.size(52.du),
            )
            MenuButton(onOpenMenu, enabled = tabsEnabled)
        }
    }
}

@Composable
private fun StatusPill(pcConnected: Boolean?) {
    val shape = ThorShapes.StatusChip.shape()
    val (text, dot) = when (pcConnected) {
        true -> "PC connected" to ThorColors.Sage
        false -> "No PC" to ThorColors.Mute
        null -> "PC unknown" to ThorColors.Mute
    }
    Row(
        Modifier.border(ThorDimens.Outline, ThorColors.InkLine, shape).padding(horizontal = 16.du, vertical = 6.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.du),
    ) {
        Box(Modifier.size(10.du).background(dot, ThorShapes.Circle))
        Text(text, style = ThorType.style(15, FontWeight.Medium))
    }
}

@Composable
private fun MenuButton(onOpen: () -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val shape = ThorShapes.ButtonSmall.shape()
    Row(
        Modifier
            .focusRing(shape)
            .border(ThorDimens.Outline, ThorColors.InkLine, shape)
            .focusProperties { canFocus = enabled }
            .controllerClickable(interaction, onOpen)
            .padding(horizontal = 14.du, vertical = 6.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.du),
    ) {
        Text("···", style = ThorType.style(18, FontWeight.Bold))
        Text("Y", style = ThorType.style(14, FontWeight.Bold, ThorColors.Mute))
    }
}

@Composable
private fun TabButton(tab: AppTab, selected: Boolean, enabled: Boolean, onActivate: () -> Unit, contentEntry: FocusRequester) {
    val interaction = remember { MutableInteractionSource() }
    val shape = (if (selected) ThorShapes.TabActive else ThorShapes.TabIdle).shape()
    Box(
        Modifier
            .then(if (selected) Modifier.rotate(ThorShapes.ACTIVE_TAB_ROTATION) else Modifier)
            .focusRing(shape)
            .then(if (selected) Modifier.hardShadow(ThorShapes.ActiveTabShadow, shape) else Modifier)
            .background(if (selected) ThorColors.Orange else Color.Transparent, shape)
            .border(ThorDimens.Outline, if (selected) ThorColors.Ink else Color.Transparent, shape)
            // D-pad Down from any tab goes to the current screen's first element.
            .focusProperties {
                down = contentEntry
                canFocus = enabled
            }
            .controllerClickable(interaction, onActivate)
            .heightIn(min = ThorDimens.MinTarget)
            .padding(horizontal = (if (selected) 22 else 18).du, vertical = 9.du),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tab.title,
            style = ThorType.style(
                19,
                if (selected) FontWeight.Bold else FontWeight.Medium,
                if (selected) ThorColors.OnAccent else ThorColors.Ink,
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Menu (Y)
// ---------------------------------------------------------------------------------------------

@Composable
private fun MenuOverlay(entry: FocusRequester, actions: AppActions, modifier: Modifier) {
    OnControllerKeys { false }
    Column(
        modifier
            .width(300.du)
            .panel(ThorShapes.PcCard, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
            .padding(16.du),
        verticalArrangement = Arrangement.spacedBy(6.du),
    ) {
        Text("menu", style = ThorType.label(), modifier = Modifier.padding(start = 8.du, bottom = 4.du))
        ListRow("Setup", onActivate = { actions.onCloseSubScreen(); actions.onOpenSubScreen(SubScreen.Setup) }, modifier = Modifier.focusRequester(entry))
        ListRow("About", onActivate = { actions.onCloseSubScreen(); actions.onOpenSubScreen(SubScreen.About) })
        ListRow("How it works", onActivate = { actions.onCloseSubScreen(); actions.onOpenSubScreen(SubScreen.Help) })
    }
}
