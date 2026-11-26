package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import dev.catzeyea.thorbro.android.files.FileNames
import dev.catzeyea.thorbro.android.saves.SaveEntry
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.ListRow
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.navigation.ControllerButton
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import java.text.DateFormat
import java.util.Date

private fun backupText(entry: SaveEntry): String =
    entry.lastBackup?.let { "backed up ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}" } ?: "never backed up"

/** Saves tab: Switch saves on this Thor. A on a save = Back up, X = Restore latest (asks first). */
@Composable
fun SavesScreen(state: AppUiState, saves: SavesController, entry: FocusRequester, actions: AppActions) {
    LaunchedEffect(Unit) { saves.refresh() }
    // The list arrives after the tab is shown (the first time): then focus goes to the selected save.
    val hasSaves = saves.list.isNotEmpty()
    LaunchedEffect(hasSaves) { if (hasSaves) focusSoon(entry) }
    OnControllerKeys { button ->
        if (button == ControllerButton.X) {
            saves.current?.let { if (it.lastBackup != null && !saves.busy) actions.onOpenSubScreen(SubScreen.ConfirmRestore(saves.key(it))) }
            true
        } else {
            false
        }
    }
    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(24.du)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .panel(ThorShapes.GamesList, shadow = ThorShapes.PcCardShadow)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.du, vertical = 20.du),
            verticalArrangement = Arrangement.spacedBy(4.du),
        ) {
            Text("saves on this Thor", style = ThorType.label(), modifier = Modifier.padding(start = 12.du, bottom = 6.du))
            if (!state.allFilesAccess) AccessBanner(actions)
            val problem = saves.problem
            when {
                saves.list.isEmpty() && saves.loading -> Text("Looking for saves …", style = ThorType.body(), modifier = Modifier.padding(12.du))
                saves.list.isEmpty() -> Text(problem ?: "", style = ThorType.body(), modifier = Modifier.padding(12.du))
                else -> saves.list.forEachIndexed { index, save ->
                    ListRow(
                        save.name,
                        onActivate = { saves.backUp(save) },
                        onTap = { saves.selected = index },
                        subtitle = "${backupText(save)} · ${save.emulator.label}",
                        trailing = FileNames.human(save.size),
                        selected = index == saves.selected,
                        onFocused = { saves.selected = index },
                        modifier = if (index == saves.selected) Modifier.focusRequester(entry) else Modifier,
                    )
                }
            }
        }
        val current = saves.current
        Column(
            Modifier
                .width(420.du)
                .fillMaxHeight()
                .panel(ThorShapes.SelectedGameCard, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
                .padding(26.du),
            verticalArrangement = Arrangement.spacedBy(12.du),
        ) {
            Text("selected", style = ThorType.label())
            if (current == null) {
                Text("No save selected", style = ThorType.cardTitle())
            } else {
                Text(current.name, style = ThorType.style(30, FontWeight.Bold, lineHeight = 1.15f))
                val last = current.lastBackup?.let { "Last backup ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" } ?: "Not backed up yet"
                Text(
                    "$last, ${FileNames.human(current.size)}. Back up copies the save to ThorBro/SaveBackups. Restore puts the latest backup back.",
                    style = ThorType.body(),
                )
                Text("${current.emulator.label} · title ID ${current.titleId}", style = ThorType.small())
                if (saves.busy) Text("Working …", style = ThorType.style(15, FontWeight.SemiBold, ThorColors.Orange))
                Spacer(Modifier.weight(1f))
                ThorButton("Back up", { saves.backUp(current) }, kind = ButtonKind.Main, fontSize = 22)
                if (current.lastBackup != null) {
                    ThorButton(
                        "Restore latest",
                        { actions.onOpenSubScreen(SubScreen.ConfirmRestore(saves.key(current))) },
                        kind = ButtonKind.Secondary,
                        badge = "X",
                    )
                }
            }
        }
    }
}

/** "Restore latest" asks first. Focus starts on Cancel, so a double press of A restores nothing. */
@Composable
fun ConfirmRestoreScreen(key: String, saves: SavesController, entry: FocusRequester, actions: AppActions) {
    OnControllerKeys { false }
    val save = saves.byKey(key)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 760.du)
                .panel(ThorShapes.StepCardMain, background = ThorColors.PanelSelected, border = ThorColors.Orange, shadow = ThorShapes.MainCardShadow)
                .padding(horizontal = 32.du, vertical = 28.du)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.du),
        ) {
            Text("restore", style = ThorType.label())
            if (save == null) {
                Text("This save is gone.", style = ThorType.cardTitle())
                ThorButton("Close", actions.onCloseSubScreen, Modifier.focusRequester(entry))
                return@Column
            }
            Text("Restore ${save.name}?", style = ThorType.cardTitle())
            Text(
                "This puts the latest backup (${backupText(save).removePrefix("backed up ")}) back into ${save.emulator.label}. " +
                    "The save that is there now is backed up first, so you can go back.",
                style = ThorType.body(),
            )
            Text("Close ${save.emulator.label} before you restore, or it may overwrite the save again.", style = ThorType.style(15, FontWeight.SemiBold, ThorColors.Orange))
            Row(horizontalArrangement = Arrangement.spacedBy(16.du), verticalAlignment = Alignment.CenterVertically) {
                ThorButton("Cancel", actions.onCloseSubScreen, Modifier.focusRequester(entry), kind = ButtonKind.Secondary)
                ThorButton(
                    "Restore",
                    {
                        actions.onCloseSubScreen()
                        saves.restore(save)
                    },
                    kind = ButtonKind.Main,
                    badge = null,
                )
            }
        }
    }
}
