package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.catzeyea.thorbro.android.R
import dev.catzeyea.thorbro.android.art.CoverLoader
import dev.catzeyea.thorbro.android.saves.Backup
import dev.catzeyea.thorbro.android.saves.SaveEntry
import dev.catzeyea.thorbro.android.saves.SwitchSaves
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.ListRow
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.controllerClickable
import dev.catzeyea.thorbro.android.ui.components.focusRing
import dev.catzeyea.thorbro.android.ui.components.hardShadow
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.navigation.ControllerButton
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.navigation.focusEntry
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Dates and placeholders
// ---------------------------------------------------------------------------------------------

/** "22.11." for a tile. */
private fun shortDate(time: Long): String = SimpleDateFormat("dd.MM.", Locale.ROOT).format(Date(time))

/** "Today, 16:52" or "22.11.2026, 21:10" for the backup list. */
fun backupLabel(time: Long, now: Long = System.currentTimeMillis()): String {
    val day = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) } }
    val clock = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(time))
    return if (day(time) == day(now)) "Today, $clock" else SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.ROOT).format(Date(time))
}

/** "Cult of the Lamb" -> "CO", "Hades" -> "H" (first letters of the first two words). */
fun initials(name: String): String =
    name.split(' ', '-', ':', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private val PLACEHOLDER_COLORS = listOf(ThorColors.Sage, ThorColors.Orange, ThorColors.Danger, Color(0xFFD9CFA8), Color(0xFF8FA7A0), Color(0xFFC9A27E))

/**
 * A square cover: iiSU's icon if there is one, else initials on a colour. The first frame draws
 * what is in memory (or the initials); a missing cover is loaded in the background and swapped in.
 */
@Composable
fun CoverImage(name: String, cover: CoverRef?, loader: CoverLoader, size: Dp) {
    val bitmap by produceState(cover?.let { loader.cached(it.key) }, cover) {
        if (value == null && cover != null) value = loader.load(cover.file, cover.key)
    }
    val shape = ThorShapes.GameRow.shape()
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(PLACEHOLDER_COLORS[Math.floorMod(name.hashCode(), PLACEHOLDER_COLORS.size)]), contentAlignment = Alignment.Center) {
                Text(initials(name), style = ThorType.style(40, FontWeight.Bold, ThorColors.OnAccent))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The tab: sections per emulator, cover tiles
// ---------------------------------------------------------------------------------------------

private sealed interface SavesCell {
    data class Heading(val group: EmulatorSaves) : SavesCell
    data class Tile(val save: SaveEntry) : SavesCell
}

/**
 * Saves tab. One section per emulator: a chip with "x of y backed up" (not focusable, it has no
 * action), then cover tiles. The D-pad moves between tiles, across sections too; A opens a game.
 */
@Composable
fun SavesScreen(state: AppUiState, saves: SavesController, entry: FocusRequester, actions: AppActions) {
    LaunchedEffect(Unit) { saves.refresh() }
    OnControllerKeys { false }
    val groups = saves.groups
    if (groups.isEmpty()) {
        if (!saves.loading || saves.problem != null) EmptySaves(state, saves.problem, actions)
        return
    }
    val cells = remember(groups) { groups.flatMap { g -> listOf<SavesCell>(SavesCell.Heading(g)) + g.saves.map { SavesCell.Tile(it) } } }
    val focusKey = saves.focusedKey?.takeIf { key -> saves.all.any { it.key == key } } ?: saves.all.first().key
    // Start scrolled to the tile that gets focus (e.g. the game that was just open): a lazy grid only
    // creates visible tiles, and focus can only land on a tile that exists in the first frame.
    val focusCell = cells.indexOfFirst { it is SavesCell.Tile && it.save.key == focusKey }.coerceAtLeast(0)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = focusCell)
    Column(Modifier.fillMaxSize()) {
        if (!state.allFilesAccess) AccessBanner(actions)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TILE + 24.du),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = ThorDimens.ScrollBleed, top = ThorDimens.ScrollBleed, end = ThorDimens.ScrollBleed, bottom = 40.du),
            horizontalArrangement = Arrangement.spacedBy(20.du),
            verticalArrangement = Arrangement.spacedBy(16.du),
        ) {
            items(cells, key = { cell -> if (cell is SavesCell.Tile) cell.save.key else "h:" + (cell as SavesCell.Heading).group.emulator.packageName }, span = { cell ->
                if (cell is SavesCell.Heading) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }) { cell ->
                when (cell) {
                    is SavesCell.Heading -> EmulatorChip(cell.group)
                    is SavesCell.Tile -> SaveTile(
                        cell.save,
                        saves.coverFiles[cell.save.key],
                        saves.covers,
                        onOpen = { actions.onOpenSubScreen(SubScreen.SaveGame(cell.save.key)) },
                        onFocused = { saves.focusedKey = cell.save.key },
                        modifier = if (cell.save.key == focusKey) Modifier.focusEntry(entry) else Modifier,
                    )
                }
            }
        }
        if (saves.notes.isNotEmpty()) Text(saves.notes.joinToString(" "), style = ThorType.small())
    }
}

private val TILE: Dp @Composable get() = 150.du

@Composable
private fun EmulatorChip(group: EmulatorSaves) {
    val shape = ThorShapes.StatusChip.shape()
    Row(Modifier.padding(top = 10.du, bottom = 2.du)) {
        Row(
            Modifier.border(ThorDimens.Outline, ThorColors.InkLine, shape).padding(horizontal = 16.du, vertical = 6.du),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.du),
        ) {
            Text(group.emulator.label, style = ThorType.style(18, FontWeight.Bold))
            Text("${group.backedUp} of ${group.saves.size} backed up", style = ThorType.style(13, color = ThorColors.Mute))
        }
    }
}

@Composable
private fun SaveTile(save: SaveEntry, cover: CoverRef?, loader: CoverLoader, onOpen: () -> Unit, onFocused: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = ThorShapes.GameRow.shape()
    Column(Modifier.width(TILE), verticalArrangement = Arrangement.spacedBy(4.du)) {
        Box(
            modifier
                .focusRing(shape)
                .hardShadow(ThorShapes.PcCardShadow, shape)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .controllerClickable(interaction, onOpen)
                .border(ThorDimens.Outline, ThorColors.InkLine, shape),
        ) { CoverImage(save.name, cover, loader, TILE) }
        // The focus ring is drawn outside the cover: keep the name clear of it.
        Spacer(Modifier.height(ThorDimens.FocusRingOffset + ThorDimens.FocusRingWidth))
        Text(save.name, style = ThorType.style(15, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val last = save.lastBackup
        Text(
            if (last != null) "✓ ${shortDate(last)}" else "no backup yet",
            style = ThorType.style(13, color = if (last != null) ThorColors.Sage else ThorColors.Mute),
        )
    }
}

@Composable
private fun EmptySaves(state: AppUiState, problem: String?, actions: AppActions) {
    Box(Modifier.fillMaxSize().panel(ThorShapes.PcCard, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)) {
        Column(Modifier.fillMaxSize().padding(24.du)) {
            if (!state.allFilesAccess) AccessBanner(actions)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.du, Alignment.CenterHorizontally),
            ) {
                Image(painterResource(R.drawable.thorbro_mood_wait_off), contentDescription = null, modifier = Modifier.size(170.du))
                Column(Modifier.widthIn(max = 560.du), verticalArrangement = Arrangement.spacedBy(12.du)) {
                    Text("No saves found yet", style = ThorType.style(32, FontWeight.Bold))
                    Text(
                        problem ?: "This tab lists the saves of your emulators, grouped by emulator, with the game cover. " +
                            "Start a game once in an emulator so it creates a save, then come back.",
                        style = ThorType.style(17, color = ThorColors.Body, lineHeight = 1.45f),
                    )
                    Text("Press A on a game to back it up or restore it.", style = ThorType.small())
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Game detail
// ---------------------------------------------------------------------------------------------

/**
 * One game: cover, emulator, backups (newest first). A backs up now (also on a backup row),
 * X restores the selected backup (asks first). The selection follows the focus.
 */
@Composable
fun SaveGameScreen(key: String, saves: SavesController, entry: FocusRequester, actions: AppActions) {
    val save = saves.byKey(key)
    var selected by remember(key) { mutableIntStateOf(0) }
    OnControllerKeys { button ->
        if (button != ControllerButton.X || save == null) return@OnControllerKeys false
        val backup = save.backups.getOrNull(selected)
        if (save.emulator.canRestore && backup != null && !saves.busy) actions.onOpenSubScreen(SubScreen.ConfirmRestore(save.key, backup.dir.path))
        true
    }
    if (save == null) {
        Text("This save is gone.", style = ThorType.cardTitle())
        return
    }
    // File counts read the backup folders: off the main thread, shown when ready.
    val counts by produceState(emptyMap<String, Int>(), save.backups) {
        value = withContext(Dispatchers.IO) { save.backups.associate { it.dir.path to SwitchSaves.fileCount(it.saveDir) } }
    }
    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(24.du)) {
        Column(
            Modifier
                .width(470.du)
                .fillMaxHeight()
                .panel(ThorShapes.SelectedGameCard, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
                .padding(22.du),
            verticalArrangement = Arrangement.spacedBy(14.du),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.du), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.hardShadow(ThorShapes.PcCardShadow, ThorShapes.GameRow.shape()).border(ThorDimens.Outline, ThorColors.Ink, ThorShapes.GameRow.shape())) {
                    CoverImage(save.name, saves.coverFiles[save.key], saves.covers, 130.du)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.du)) {
                    Text(save.name, style = ThorType.style(26, FontWeight.Bold, lineHeight = 1.15f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        save.emulator.label,
                        style = ThorType.style(16, FontWeight.Bold),
                        modifier = Modifier.border(ThorDimens.Outline, ThorColors.InkLine, ThorShapes.StatusChip.shape()).padding(horizontal = 14.du, vertical = 4.du),
                    )
                }
            }
            Text(
                "Back up copies this save to your backup folder. Restore puts a backup back. Before it does, ThorBro backs up " +
                    "the save that is there now, so you can always go back.",
                style = ThorType.style(15, color = ThorColors.Body, lineHeight = 1.45f),
            )
            save.emulator.restoreNote?.let { Text(it, style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Orange, lineHeight = 1.4f)) }
            if (saves.busy) Text("Working …", style = ThorType.style(15, FontWeight.SemiBold, ThorColors.Orange))
            Spacer(Modifier.weight(1f))
            ThorButton(
                "Back up now",
                { saves.backUp(save) },
                if (save.backups.isEmpty()) Modifier.focusRequester(entry) else Modifier,
                kind = ButtonKind.Main,
                fontSize = 20,
            )
            if (save.emulator.canRestore && save.backups.isNotEmpty()) {
                ThorButton(
                    "Restore selected",
                    { save.backups.getOrNull(selected)?.let { actions.onOpenSubScreen(SubScreen.ConfirmRestore(save.key, it.dir.path)) } },
                    kind = ButtonKind.Secondary,
                    badge = "X",
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .panel(ThorShapes.GamesList, shadow = ThorShapes.PcCardShadow)
                .padding(horizontal = 18.du, vertical = 18.du),
            verticalArrangement = Arrangement.spacedBy(4.du),
        ) {
            Text("backups of this save · newest first", style = ThorType.label(), modifier = Modifier.padding(start = 10.du, bottom = 4.du))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ThorDimens.ScrollBleed), verticalArrangement = Arrangement.spacedBy(4.du)) {
                if (save.backups.isEmpty()) Text("No backup yet. Press A to make the first one.", style = ThorType.body(), modifier = Modifier.padding(10.du))
                save.backups.forEachIndexed { index, backup ->
                    ListRow(
                        backupLabel(backup.time) + if (backup.note.isNotEmpty()) " (${backup.note})" else "",
                        onActivate = { saves.backUp(save) },
                        onTap = { selected = index },
                        trailing = counts[backup.dir.path]?.let { "$it ${if (it == 1) "file" else "files"}" },
                        selected = index == selected,
                        onFocused = { selected = index },
                        titleSize = 18,
                        modifier = if (index == 0) Modifier.focusRequester(entry) else Modifier,
                    )
                }
            }
            Text("Stored in ThorBro/SaveBackups/${save.backups.firstOrNull()?.dir?.parentFile?.name ?: save.name}", style = ThorType.small(), modifier = Modifier.padding(start = 10.du))
        }
    }
}

/** "Restore selected" asks first. Focus starts on Cancel, so a double press of A restores nothing. */
@Composable
fun ConfirmRestoreScreen(key: String, backupPath: String, saves: SavesController, entry: FocusRequester, actions: AppActions) {
    OnControllerKeys { false }
    val save = saves.byKey(key)
    val backup: Backup? = save?.backups?.firstOrNull { it.dir.path == backupPath }
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
            if (save == null || backup == null) {
                Text("This backup is gone.", style = ThorType.cardTitle())
                ThorButton("Close", actions.onCloseSubScreen, Modifier.focusRequester(entry))
                return@Column
            }
            Text("Restore ${save.name}?", style = ThorType.cardTitle())
            Text(
                "This puts the backup from ${backupLabel(backup.time)} back into ${save.emulator.label}. " +
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
                        saves.restore(save, backup)
                    },
                    kind = ButtonKind.Main,
                    badge = null,
                )
            }
        }
    }
}
