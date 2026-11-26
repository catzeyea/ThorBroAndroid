package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.ButtonGlyph
import dev.catzeyea.thorbro.android.ui.components.KeyChip
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.controllerClickable
import dev.catzeyea.thorbro.android.ui.components.focusRing
import dev.catzeyea.thorbro.android.ui.components.focusableCard
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.navigation.ControllerButton
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du

/** The three Scan points (orange dot, bold title, text). Shared by the info card and "How it works". */
@Composable
fun ScanPoints(textSize: Int = 16, gap: Int = 14) {
    Column(verticalArrangement = Arrangement.spacedBy(gap.du)) {
        for (point in HelpTexts.SCAN_POINTS) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.du)) {
                Box(Modifier.padding(top = 8.du).size(10.du).background(ThorColors.Orange, ThorShapes.Circle))
                Column(verticalArrangement = Arrangement.spacedBy(2.du)) {
                    Text(point.title, style = ThorType.style(textSize + 2, FontWeight.Bold))
                    Text(point.text, style = ThorType.style(textSize - 1, color = ThorColors.Body, lineHeight = 1.4f))
                }
            }
        }
    }
}

/**
 * "What does Scan do?" before a scan. A scans, B cancels, Select (or A on the checkbox) turns the
 * card off for good ("Don't show this again", remembered).
 */
@Composable
fun ScanInfoScreen(folderName: String, files: FilesController, entry: FocusRequester, actions: AppActions) {
    val toggleHidden = { files.rememberScanInfoHidden(!files.scanInfoHidden) }
    OnControllerKeys { button ->
        if (button == ControllerButton.Select) {
            toggleHidden()
            true
        } else {
            false
        }
    }
    val scan = {
        actions.onCloseSubScreen()
        files.startScan()
        actions.onOpenSubScreen(SubScreen.Scan)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 900.du)
                .panel(ThorShapes.StepCardMain, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
                .padding(horizontal = 34.du, vertical = 28.du),
            verticalArrangement = Arrangement.spacedBy(18.du),
        ) {
            Text("What does Scan do?", style = ThorType.style(30, FontWeight.Bold))
            Text(
                buildAnnotatedString {
                    val intro = HelpTexts.scanIntro(folderName)
                    val at = intro.indexOf(folderName)
                    append(intro.substring(0, at))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ThorColors.Ink)) { append(folderName) }
                    append(intro.substring(at + folderName.length))
                },
                style = ThorType.style(16, color = ThorColors.Body),
            )
            ScanPoints()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.du)) {
                DontShowAgain(checked = files.scanInfoHidden, onToggle = toggleHidden, Modifier.weight(1f))
                ThorButton("Scan", scan, Modifier.focusRequester(entry), kind = ButtonKind.Main, fontSize = 20, minHeight = ThorDimens.MinTarget)
                ThorButton("Cancel", actions.onCloseSubScreen, kind = ButtonKind.Secondary, badge = "B", fontSize = 18)
            }
        }
    }
}

@Composable
private fun DontShowAgain(checked: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = ThorShapes.ButtonSmall.shape()
    Box(modifier) {
        Row(
            Modifier
                .focusRing(shape)
                .controllerClickable(interaction, onToggle)
                .heightIn(min = ThorDimens.MinTarget)
                .padding(horizontal = 8.du, vertical = 6.du),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.du),
        ) {
            CheckBox(checked)
            Text("Don't show this again", style = ThorType.style(15, color = ThorColors.Body))
            Text("(Select)", style = ThorType.style(13, color = ThorColors.Mute))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// How it works (menu)
// ---------------------------------------------------------------------------------------------

/** One card per tab, with the buttons used there. The Files card repeats the Scan explanation. */
@Composable
fun HelpScreen(entry: FocusRequester) {
    OnControllerKeys { false }
    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(24.du)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.du)) {
            HelpCard("Start", HelpTexts.START, emptyList(), Modifier.weight(1f))
            HelpCard("Saves", HelpTexts.SAVES, listOf("A" to "open", "X" to "restore"), Modifier.weight(1f))
            HelpCard("Stream", HelpTexts.STREAM, listOf("A" to "connect"), Modifier.weight(1f))
        }
        val interaction = remember { MutableInteractionSource() }
        HelpCard(
            "Files",
            HelpTexts.FILES,
            listOf("X" to "scan", "Select" to "pin"),
            Modifier.weight(1.15f).fillMaxHeight().focusRequester(entry).focusableCard(ThorShapes.PcCard.shape(), interaction),
        ) {
            Text("What does Scan do?", style = ThorType.style(17, FontWeight.Bold, ThorColors.Orange))
            Text("Scan reads the folder you have open. Nothing is moved until you say so.", style = ThorType.style(14, color = ThorColors.Body, lineHeight = 1.35f))
            ScanPoints(textSize = 14, gap = 8)
        }
    }
}

@Composable
private fun HelpCard(
    title: String,
    text: String,
    keys: List<Pair<String, String>>,
    modifier: Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .panel(ThorShapes.PcCard, shadow = ThorShapes.PcCardShadow)
            .padding(horizontal = 22.du, vertical = 14.du),
        verticalArrangement = Arrangement.spacedBy(6.du),
    ) {
        Text(title, style = ThorType.style(20, FontWeight.Bold))
        Text(text, style = ThorType.style(14, color = ThorColors.Body, lineHeight = 1.35f))
        extra()
        Spacer(Modifier.weight(1f))
        if (keys.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.du), verticalAlignment = Alignment.CenterVertically) {
                for ((key, label) in keys) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.du), verticalAlignment = Alignment.CenterVertically) {
                        if (key.length == 1) ButtonGlyph(key, diameter = 24.du, fontSize = 13) else KeyChip(key, ThorShapes.ChipLeft, ThorColors.Ink, ThorColors.Ink, FontWeight.Bold)
                        Text(label, style = ThorType.style(14, color = ThorColors.Mute))
                    }
                }
            }
        }
    }
}
