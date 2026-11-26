package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.files.FileNames
import dev.catzeyea.thorbro.android.files.ScanGroup
import dev.catzeyea.thorbro.android.files.ScanItem
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.ListRow
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.focusableCard
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
import kotlinx.coroutines.launch
import java.io.File

/** Tries to focus [requester] now, then on the next few frames (a lazy row may not exist yet). */
suspend fun focusSoon(requester: FocusRequester, attempts: Int = 4) {
    repeat(attempts) {
        if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return
        withFrameNanos { }
    }
}

/**
 * Files tab: storages and pinned folders on the left, the open folder on the right.
 * A opens, B goes up (at a storage root: to the places), X scans the open folder, Select = folder options.
 */
@Composable
fun FilesScreen(state: AppUiState, files: FilesController, entry: FocusRequester, actions: AppActions) {
    val placesEntry = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { files.start() }
    OnControllerKeys { button ->
        when (button) {
            ControllerButton.X -> {
                if (files.dir != null) {
                    files.startScan()
                    actions.onOpenSubScreen(SubScreen.Scan)
                }
                true
            }
            ControllerButton.Select -> {
                files.selectTarget()?.let { actions.onOpenSubScreen(SubScreen.FolderOptions(it.path)) }
                true
            }
            ControllerButton.B -> {
                if (!files.up()) scope.launch { focusSoon(placesEntry) }
                true
            }
            else -> false
        }
    }

    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(24.du)) {
        PlacesPanel(files, placesEntry, Modifier.width(280.du).fillMaxHeight())
        FolderPanel(state, files, entry, actions, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun PlacesPanel(files: FilesController, placesEntry: FocusRequester, modifier: Modifier) {
    val current = files.dir
    Column(
        modifier
            .panel(ThorShapes.GamesList, shadow = ThorShapes.PcCardShadow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.du, vertical = 18.du),
        verticalArrangement = Arrangement.spacedBy(4.du),
    ) {
        val storages = files.places.filter { it.isStorage }
        val pins = files.places.filter { !it.isStorage }
        Text("storage", style = ThorType.label(), modifier = Modifier.padding(start = 12.du, bottom = 4.du))
        storages.forEachIndexed { index, place ->
            ListRow(
                place.label,
                onActivate = { files.open(place.path) },
                subtitle = place.detail.ifEmpty { null },
                titleSize = 18,
                selected = current == place.path,
                onFocused = { files.focusedFolder = place.path },
                modifier = if (index == 0) Modifier.focusRequester(placesEntry) else Modifier,
            )
        }
        if (pins.isNotEmpty()) {
            Text("pinned", style = ThorType.label(), modifier = Modifier.padding(start = 12.du, top = 14.du, bottom = 4.du))
            pins.forEach { place ->
                ListRow(
                    place.label,
                    onActivate = { files.open(place.path) },
                    subtitle = place.detail.ifEmpty { null },
                    titleSize = 18,
                    selected = current == place.path,
                    onFocused = { files.focusedFolder = place.path },
                )
            }
        }
    }
}

@Composable
private fun FolderPanel(state: AppUiState, files: FilesController, entry: FocusRequester, actions: AppActions, modifier: Modifier) {
    val dir = files.dir
    Column(
        modifier
            .panel(ThorShapes.SelectedGameCard, border = ThorColors.InkLine, shadow = ThorShapes.MainCardShadow)
            .padding(horizontal = 24.du, vertical = 20.du),
        verticalArrangement = Arrangement.spacedBy(12.du),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.du)) {
            Text(
                dir?.let(files::breadcrumb) ?: "",
                style = ThorType.style(15, color = ThorColors.Mute),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (dir != null && files.prefs.isPinned(dir.path)) PinnedChip()
            Spacer(Modifier.weight(1f))
            ThorButton(
                "Scan this folder",
                {
                    files.startScan()
                    actions.onOpenSubScreen(SubScreen.Scan)
                },
                kind = ButtonKind.Main,
                fontSize = 18,
                badge = "X",
                minHeight = ThorDimens.MinTarget,
                modifier = if (files.entries.isEmpty()) Modifier.focusRequester(entry) else Modifier,
            )
        }
        if (!state.allFilesAccess) AccessBanner(actions)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                files.loading && files.entries.isEmpty() -> Text("Opening …", style = ThorType.body())
                files.listError != null -> Text(files.listError ?: "", style = ThorType.body())
                files.entries.isEmpty() -> Text("This folder is empty.", style = ThorType.body())
                else -> key(dir) { EntryList(files, entry) }
            }
        }
        Text(
            "Scans the folder you have open. ${files.pinLabel(File(files.prefs.defaultScan))} is the default scan folder. " +
                "Press Select on a folder to pin it or set it as the default scan folder.",
            style = ThorType.small(),
        )
    }
}

@Composable
private fun EntryList(files: FilesController, entry: FocusRequester) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = files.focusIndex)
    val entries = files.entries
    // After a folder was opened (or re-listed), focus goes to the remembered row.
    LaunchedEffect(entries) {
        listState.scrollToItem(files.focusIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))
        focusSoon(entry)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(ThorDimens.ScrollBleed),
        verticalArrangement = Arrangement.spacedBy(4.du),
    ) {
        itemsIndexed(entries, key = { _, e -> e.file.path }) { index, e ->
            ListRow(
                e.file.name,
                onActivate = { if (e.isDir) files.open(e.file) else files.notifyFile(e) },
                trailing = if (e.isDir) "folder" else FileNames.human(e.size),
                titleSize = 17,
                onFocused = {
                    files.focusIndex = index
                    files.focusedFolder = if (e.isDir) e.file else null
                },
                modifier = if (index == files.focusIndex) Modifier.focusEntry(entry) else Modifier,
            )
        }
    }
}

@Composable
private fun PinnedChip() {
    val shape = ThorShapes.StatusChip.shape()
    Text(
        "pinned",
        style = ThorType.style(14, FontWeight.Medium, ThorColors.Orange),
        modifier = Modifier.border(ThorDimens.Outline, ThorColors.InkSoft, shape).padding(horizontal = 12.du, vertical = 4.du),
    )
}

/** Shown while "All files access" is off: the app may still work (Thor firmware), but asks for it. */
@Composable
fun AccessBanner(actions: AppActions) {
    Row(
        Modifier.fillMaxWidth().border(ThorDimens.Outline, ThorColors.Orange, ThorShapes.StatusChip.shape()).padding(horizontal = 14.du, vertical = 4.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.du),
    ) {
        Text(
            "All files access is off. It works on this firmware, but may stop after an update.",
            style = ThorType.style(13, color = ThorColors.Body),
            modifier = Modifier.weight(1f),
        )
        ThorButton("Allow", actions.onOpenAllFilesAccess, kind = ButtonKind.Small, fontSize = 14, minHeight = 40.dp)
    }
}

// ---------------------------------------------------------------------------------------------
// Folder options (Select)
// ---------------------------------------------------------------------------------------------

@Composable
fun FolderOptionsScreen(path: String, files: FilesController, entry: FocusRequester, actions: AppActions) {
    OnControllerKeys { false }
    val folder = File(path)
    val pinned = files.prefs.isPinned(path)
    val isDefault = files.prefs.defaultScan == path
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 640.du)
                .panel(ThorShapes.StepCardMain, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
                .padding(horizontal = 30.du, vertical = 26.du),
            verticalArrangement = Arrangement.spacedBy(12.du),
        ) {
            Text("folder options", style = ThorType.label())
            Text(files.pinLabel(folder), style = ThorType.cardTitle())
            Text(files.breadcrumb(folder), style = ThorType.small())
            // Room between the rows for the focus ring, which is drawn outside the row.
            Column(Modifier.padding(top = 8.du), verticalArrangement = Arrangement.spacedBy(16.du)) {
                ListRow(
                    if (pinned) "Unpin" else "Pin to the places list",
                    onActivate = {
                        files.togglePin(folder)
                        actions.onCloseSubScreen()
                        actions.onNotice(if (pinned) "Unpinned ${folder.name}." else "Pinned ${folder.name}.", false)
                    },
                    modifier = Modifier.focusRequester(entry),
                )
                if (!isDefault) {
                    ListRow(
                        "Set as default scan folder",
                        onActivate = {
                            files.setDefaultScan(folder)
                            actions.onCloseSubScreen()
                            actions.onNotice("${folder.name} is the default scan folder now.", false)
                        },
                    )
                } else {
                    Text("This is the default scan folder.", style = ThorType.small(), modifier = Modifier.padding(start = 20.du))
                }
                ListRow("Close", onActivate = actions.onCloseSubScreen)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Scan: preview, moving, result
// ---------------------------------------------------------------------------------------------

@Composable
fun ScanScreen(files: FilesController, entry: FocusRequester, actions: AppActions) {
    OnControllerKeys { button ->
        if (button != ControllerButton.B) return@OnControllerKeys false
        when (files.scan) {
            is ScanState.Moving -> {
                files.stopMove()
                true
            }
            ScanState.Undoing -> true
            else -> false // closes the screen (MainActivity also resets the scan)
        }
    }
    when (val scan = files.scan) {
        ScanState.Idle -> Unit
        is ScanState.Scanning -> MessagePanel("scanning", "Looking through ${scan.folder.name} …", "Nothing is moved while scanning.")
        is ScanState.Preview -> PreviewScreen(scan, files, entry, actions)
        is ScanState.Moving -> MovingPanel(scan)
        is ScanState.Done -> DonePanel(scan, files, entry, actions)
        ScanState.Undoing -> MessagePanel("undo", "Putting the files back …", "Nothing is overwritten.")
        is ScanState.Undone -> MessagePanel(
            "undo",
            "Put back ${scan.restored} files",
            scan.problems.joinToString("\n").ifEmpty { "Everything is where it was before." },
            button = { ThorButton("Close", actions.onCloseSubScreen, Modifier.focusRequester(entry)) },
        )
    }
}

@Composable
private fun MessagePanel(label: String, title: String, text: String, button: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 760.du)
                .panel(ThorShapes.StepCardMain, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
                .padding(horizontal = 32.du, vertical = 28.du),
            verticalArrangement = Arrangement.spacedBy(14.du),
        ) {
            Text(label, style = ThorType.label())
            Text(title, style = ThorType.cardTitle())
            Text(text, style = ThorType.body())
            button?.invoke()
        }
    }
}

@Composable
private fun PreviewScreen(scan: ScanState.Preview, files: FilesController, entry: FocusRequester, actions: AppActions) {
    val result = scan.result
    val toMove = result.toMove
    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(24.du)) {
        ScrollPanel(Modifier.weight(1f).fillMaxHeight()) {
            Text("scan result for ${result.folder.name}", style = ThorType.label())
            Text("${result.items.size} files found", style = ThorType.style(32, FontWeight.Bold))
            val library = result.library
            for (group in ScanGroup.entries) {
                val items = result.group(group)
                if (items.isEmpty()) continue
                val target = when {
                    group == ScanGroup.Review -> "stays in ${result.folder.name} (Review)"
                    group == ScanGroup.Skipped -> "ignored this time"
                    else -> items.mapNotNull { it.target }.distinct().joinToString(", ") { t -> library?.let { relativeTarget(it.path, t) } ?: t.path }
                }
                GroupBlock(group, items, target)
            }
            if (library == null) Text("No game library (ROMs folder) was found, so nothing can be sorted.", style = ThorType.small())
        }
        Column(
            Modifier
                .width(400.du)
                .fillMaxHeight()
                .panel(ThorShapes.SelectedGameCard, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
                .padding(26.du),
            verticalArrangement = Arrangement.spacedBy(12.du),
        ) {
            Text("nothing has moved yet", style = ThorType.label())
            if (toMove.isEmpty()) {
                Text("Nothing to move", style = ThorType.style(28, FontWeight.Bold))
                Text("No file was recognized for sure. Unclear files are not touched.", style = ThorType.body())
                Spacer(Modifier.weight(1f))
                ThorButton("Close", actions.onCloseSubScreen, Modifier.focusRequester(entry), kind = ButtonKind.Secondary)
            } else {
                val size = toMove.sumOf { it.size }
                Text("Move ${toMove.size} files?", style = ThorType.style(28, FontWeight.Bold))
                Text(
                    "${FileNames.human(size)} in total. Nothing is overwritten, and a log in ThorBro/logs lets you undo. Unclear files are not touched.",
                    style = ThorType.body(),
                )
                if (scan.copies > 0) {
                    Text(
                        "${scan.copies} of them go to another storage: they are copied, checked, and only then deleted here. That takes longer.",
                        style = ThorType.style(15, FontWeight.SemiBold, ThorColors.Orange, lineHeight = 1.4f),
                    )
                }
                Spacer(Modifier.weight(1f))
                ThorButton("Move ${toMove.size} files", files::confirmMove, Modifier.focusRequester(entry), kind = ButtonKind.Main, fontSize = 22)
                ThorButton("Cancel", actions.onCloseSubScreen, kind = ButtonKind.Secondary, fontSize = 18)
            }
        }
    }
}

/** "ROMs/switch/update" for a target inside the library. */
private fun relativeTarget(libraryPath: File, target: File): String =
    "${libraryPath.name}/${target.relativeTo(libraryPath).path}"

@Composable
private fun GroupBlock(group: ScanGroup, items: List<ScanItem>, target: String) {
    Column(Modifier.padding(top = 10.du), verticalArrangement = Arrangement.spacedBy(4.du)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.du)) {
            Text("${group.title} · ${items.size}", style = ThorType.style(20, FontWeight.Bold))
            val color = if (group == ScanGroup.Review || group == ScanGroup.Skipped) ThorColors.Orange else ThorColors.Sage
            Text("→ $target", style = ThorType.style(15, color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        for (item in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.du)) {
                Column(Modifier.weight(1f)) {
                    Text(item.file.name, style = ThorType.style(15, color = ThorColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.reason, style = ThorType.style(13, color = ThorColors.Mute), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.size > 0) Text(FileNames.human(item.size), style = ThorType.style(15, color = ThorColors.Body))
            }
        }
    }
}

/** A panel whose content scrolls with D-pad Up/Down while it has focus. */
@Composable
private fun ScrollPanel(modifier: Modifier, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 120.dp.toPx() }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionDown -> if (scroll.value < scroll.maxValue) step else return@onPreviewKeyEvent false
                    Key.DirectionUp -> if (scroll.value > 0) -step else return@onPreviewKeyEvent false
                    else -> return@onPreviewKeyEvent false
                }
                scope.launch { scroll.scrollBy(delta) }
                true
            }
            .focusableCard(ThorShapes.GamesList.shape(), interaction)
            .panel(ThorShapes.GamesList, shadow = ThorShapes.PcCardShadow),
    ) {
        Column(Modifier.verticalScroll(scroll).padding(horizontal = 26.du, vertical = 22.du), verticalArrangement = Arrangement.spacedBy(4.du)) {
            content()
        }
    }
}

@Composable
private fun MovingPanel(scan: ScanState.Moving) {
    val detail = buildString {
        append(scan.current)
        if (scan.copying && scan.bytesTotal > 0) append("\ncopying ${FileNames.human(scan.bytes)} of ${FileNames.human(scan.bytesTotal)}")
        append(if (scan.stopping) "\nStopping after this file …" else "\nPress B to stop after this file.")
    }
    MessagePanel("moving", "Moving ${scan.done + 1} of ${scan.total}", detail)
}

@Composable
private fun DonePanel(scan: ScanState.Done, files: FilesController, entry: FocusRequester, actions: AppActions) {
    val title = if (scan.stopped) "Stopped: moved ${scan.moved} of ${scan.total}" else "Moved ${scan.moved} of ${scan.total} files"
    val text = scan.problems.joinToString("\n").ifEmpty { "All done. The log is in ThorBro/logs." }
    MessagePanel("done", title, text) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.du)) {
            ThorButton("Close", actions.onCloseSubScreen, Modifier.focusRequester(entry))
            if (scan.log != null) ThorButton("Undo", files::undo, kind = ButtonKind.Secondary)
        }
    }
}
