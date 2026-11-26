package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.focusableCard
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import kotlinx.coroutines.launch

/** A screen on top of the tabs. B, the Back/OK button or the system back gesture closes it. */
@Composable
fun SubScreenContent(subScreen: SubScreen, state: AppUiState, entry: FocusRequester, actions: AppActions, controllers: Controllers) {
    when (subScreen) {
        is SubScreen.LaunchError -> LaunchErrorScreen(subScreen.message, entry, actions.onCloseSubScreen)
        is SubScreen.FontLicense -> FontLicenseScreen(subScreen.text, entry, actions.onCloseSubScreen)
        SubScreen.Setup -> {
            OnControllerKeys { false }
            SetupScreen(entry)
        }
        SubScreen.About -> {
            OnControllerKeys { false }
            AboutScreen(state, entry, actions)
        }
        is SubScreen.FolderOptions -> FolderOptionsScreen(subScreen.path, controllers.files, entry, actions)
        SubScreen.Scan -> ScanScreen(controllers.files, entry, actions)
        is SubScreen.SaveGame -> SaveGameScreen(subScreen.saveKey, controllers.saves, entry, actions)
        is SubScreen.ConfirmRestore -> ConfirmRestoreScreen(subScreen.saveKey, subScreen.backupPath, controllers.saves, entry, actions)
        SubScreen.Help -> HelpScreen(entry)
        is SubScreen.ScanInfo -> ScanInfoScreen(subScreen.folderName, controllers.files, entry, actions)
        // Drawn as an overlay by ThorBroApp, never as a full screen.
        SubScreen.Menu -> Unit
    }
}

@Composable
private fun LaunchErrorScreen(message: String, entry: FocusRequester, onClose: () -> Unit) {
    OnControllerKeys { false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 760.du)
                .panel(
                    ThorShapes.StepCardMain,
                    background = ThorColors.PanelSelected,
                    border = ThorColors.Danger,
                    shadow = ThorShapes.MainCardShadow,
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.du, vertical = 28.du),
            verticalArrangement = Arrangement.spacedBy(14.du),
        ) {
            Text("something went wrong", style = ThorType.label())
            Text("Could not start Moonlight", style = ThorType.cardTitle())
            Text(message, style = ThorType.body())
            ThorButton("OK", onClose, Modifier.padding(top = 8.du).focusRequester(entry))
        }
    }
}

/**
 * The full OFL text of the bundled font. The text box is focusable: D-pad Up/Down scroll it, and at
 * the end Down moves on to the Back button.
 */
@Composable
private fun FontLicenseScreen(text: String, entry: FocusRequester, onClose: () -> Unit) {
    OnControllerKeys { false }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 120.dp.toPx() }
    val interaction = remember { MutableInteractionSource() }
    val shape = ThorShapes.PcCard.shape()

    Column(Modifier.fillMaxSize().padding(bottom = 6.du), verticalArrangement = Arrangement.spacedBy(18.du)) {
        Text("JetBrains Mono font license", style = ThorType.cardTitle())
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(entry)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionDown -> if (scroll.value < scroll.maxValue) step else return@onPreviewKeyEvent false
                        Key.DirectionUp -> if (scroll.value > 0) -step else return@onPreviewKeyEvent false
                        else -> return@onPreviewKeyEvent false
                    }
                    scope.launch { scroll.animateScrollBy(delta) }
                    true
                }
                .focusableCard(shape, interaction)
                .panel(ThorShapes.PcCard, shadow = ThorShapes.PcCardShadow)
                .verticalScroll(scroll)
                .padding(horizontal = 28.du, vertical = 24.du),
        ) {
            Text(text, style = ThorType.style(14, color = ThorColors.Body, lineHeight = 1.5f))
        }
        ThorButton("Back", onClose, kind = ButtonKind.Secondary, fontSize = 16)
    }
}
