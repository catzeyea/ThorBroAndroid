package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.files.FileEntry
import dev.catzeyea.thorbro.android.files.FileKind
import dev.catzeyea.thorbro.android.files.FolderListing
import dev.catzeyea.thorbro.android.ui.components.controllerClickable
import dev.catzeyea.thorbro.android.ui.components.focusRing
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.navigation.ControllerButton
import dev.catzeyea.thorbro.android.ui.navigation.ListNavigation
import dev.catzeyea.thorbro.android.ui.navigation.tryFocus
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.navigation.focusEntry
import dev.catzeyea.thorbro.android.ui.theme.Corners
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

// The Files tab as a file manager (boards AndroidFM1-5): a narrow left bar (storages, pinned
// folders, Trash), the open folder as a wide list, and the actions menu behind Select.
// Scan, pins and the default scan folder work as before; they moved into the actions menu.

/** "Scan this folder" in the actions menu: first the info card, unless it was turned off. */
fun openScan(folder: File, files: FilesController, actions: AppActions) {
    if (files.scanInfoHidden) {
        files.startScan(folder)
        actions.onOpenSubScreen(SubScreen.Scan)
    } else {
        actions.onOpenSubScreen(SubScreen.ScanInfo(folder.path))
    }
}

/** Where focus is in the Files tab, for Select (actions of what?) and for giving focus back. */
private enum class FocusArea { List, Places, Trash, Toolbar }

/** Row height and room for the focus ring above the first / below the last row. */
private const val ROW_HEIGHT = 42
/**
 * Size and date are one text (fewer text layouts per row, see FolderListing.metaText). In the
 * monospace font a character is 0.6 em, so the date column is exactly [DATE_COLUMN] wide.
 */
private const val META_FONT = 14
private const val DATE_COLUMN = (FolderListing.META_GAP + FolderListing.DATE_CHARS) * 0.6f * META_FONT
private const val SIZE_COLUMN = 110f
private val RingRoom = ThorDimens.FocusRingOffset + ThorDimens.FocusRingWidth
private val SmallShape = Corners(12, 10, 13, 11)

/** Fill of a type chip, per kind (board colours; image, video and audio are not in the boards). */
private fun chipColor(kind: FileKind): Color = when (kind) {
    FileKind.Game -> ThorColors.Sage
    FileKind.Archive -> ThorColors.Orange
    FileKind.App -> ThorColors.Danger
    FileKind.Save -> Color(0xFF8FA7A0)
    FileKind.Text -> Color(0xFFD9CFA9)
    FileKind.Image -> Color(0xFF8FA3B8)
    FileKind.Video -> Color(0xFFB3A0C4)
    FileKind.Audio -> Color(0xFFC9B07A)
    FileKind.Other -> ThorColors.Mute
}

/**
 * Files tab. A opens (a folder here, a file in another app), B goes up (at a storage root: to the
 * left bar; with a filter: clears it), Select opens the actions menu for the focused folder.
 */
@Composable
fun FilesScreen(state: AppUiState, files: FilesController, entry: FocusRequester, actions: AppActions) {
    val placesEntry = remember { FocusRequester() }
    val placeRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val area = remember { mutableStateOf(FocusArea.List) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { files.start() }
    // The menu and the search field belong to this visit of the tab.
    DisposableEffect(Unit) {
        onDispose {
            files.closeActions()
            files.closeSearch()
        }
    }

    // [rowRemoved]: the action removes the left-bar row the menu came from (Unpin), so focus goes
    // to the first storage instead of a row that is about to disappear.
    val closeMenu: (rowRemoved: Boolean) -> Unit = { rowRemoved ->
        val menu = files.actions
        files.closeActions()
        val back = when {
            menu?.fromPlace != true -> entry
            rowRemoved -> placesEntry
            else -> placeRequesters[menu.target.path] ?: placesEntry
        }
        scope.launch { focusSoon(back) }
    }

    OnControllerKeys { button ->
        when (button) {
            ControllerButton.Select -> {
                when {
                    files.actions != null -> closeMenu(false)
                    area.value != FocusArea.Trash -> files.openActions(fromPlace = area.value == FocusArea.Places)
                }
                true
            }
            ControllerButton.B -> {
                when {
                    files.actions != null -> closeMenu(false)
                    files.searchOpen || files.query.isNotEmpty() -> {
                        files.clearSearch()
                        scope.launch { focusSoon(entry) }
                    }
                    !files.up() -> scope.launch { focusSoon(placesEntry) }
                }
                true
            }
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().padding(bottom = 6.du)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.du)) {
            PlacesBar(files, actions, placesEntry, placeRequesters, area, Modifier.width(200.du).fillMaxHeight())
            FolderPanel(state, files, entry, actions, area, Modifier.weight(1f).fillMaxHeight())
        }
        files.actions?.let { menu ->
            ActionsMenuOverlay(menu.target, files, actions, closeMenu, Modifier.align(Alignment.TopEnd).padding(top = 60.du, end = 16.du))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Left bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlacesBar(
    files: FilesController,
    actions: AppActions,
    placesEntry: FocusRequester,
    requesters: MutableMap<String, FocusRequester>,
    area: MutableState<FocusArea>,
    modifier: Modifier,
) {
    val current = files.dir
    val storages = files.places.filter { it.isStorage }
    val pins = files.places.filter { !it.isStorage }
    Column(
        modifier
            .panel(ThorShapes.GamesList, shadow = ThorShapes.PcCardShadow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.du, vertical = 14.du),
        verticalArrangement = Arrangement.spacedBy(3.du),
    ) {
        SideLabel("storage")
        storages.forEachIndexed { index, place ->
            val requester = requesters.getOrPut(place.path.path) { FocusRequester() }
            PlaceRow(
                shortStorageLabel(place.label),
                place.detail.ifEmpty { null },
                selected = current == place.path,
                onActivate = { files.open(place.path) },
                onFocused = {
                    files.focusedFolder = place.path
                    area.value = FocusArea.Places
                },
                modifier = Modifier.focusRequester(requester).then(if (index == 0) Modifier.focusRequester(placesEntry) else Modifier),
            )
        }
        if (pins.isNotEmpty()) {
            Spacer(Modifier.height(6.du))
            SideLabel("pinned")
            pins.forEach { place ->
                val requester = requesters.getOrPut(place.path.path) { FocusRequester() }
                PlaceRow(
                    place.label,
                    place.detail.ifEmpty { null },
                    selected = current == place.path,
                    onActivate = { files.open(place.path) },
                    onFocused = {
                        files.focusedFolder = place.path
                        area.value = FocusArea.Places
                    },
                    modifier = Modifier.focusRequester(requester),
                )
            }
        }
        Spacer(Modifier.height(18.du))
        val count = files.trashCount
        PlaceRow(
            "Trash",
            if (count == 0) "empty" else if (count == 1) "1 item" else "$count items",
            selected = false,
            onActivate = { actions.onNotice(if (count == 0) "The Trash is empty." else "The Trash view is not there yet.", false) },
            onFocused = {
                files.focusedFolder = null
                area.value = FocusArea.Trash
            },
        )
    }
}

/** "Internal storage" is "Internal" in the narrow bar (as in the boards). */
private fun shortStorageLabel(label: String) = if (label == "Internal storage") "Internal" else label

@Composable
private fun SideLabel(text: String) {
    BasicText(text, Modifier.padding(start = 6.du, bottom = 3.du), style = ThorType.style(13, color = ThorColors.Mute))
}

/** A place in the left bar: sage when it is the open folder; focus adds the outline and the ring. */
@Composable
private fun PlaceRow(title: String, subtitle: String?, selected: Boolean, onActivate: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    val shape = SmallShape.shape()
    Column(
        modifier
            .fillMaxWidth()
            .focusRing(shape)
            .rowBackground(shape, focused, selected)
            .onFocusChanged {
                focused.value = it.isFocused
                if (it.isFocused) onFocused()
            }
            .controllerClickable(interaction, onActivate)
            .padding(horizontal = 12.du, vertical = 7.du),
    ) {
        BasicText(
            title,
            style = ThorType.style(15, FontWeight.SemiBold, if (selected) ThorColors.OnAccent else ThorColors.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            BasicText(
                subtitle,
                style = ThorType.style(11, FontWeight.Medium, if (selected) Color(0xFF2A2F26) else ThorColors.Mute),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Row look, decided while drawing (no recomposition when focus moves): [selected] = sage fill;
 * focused = darker fill plus a cream outline (the ring itself comes from focusRing).
 */
private fun Modifier.rowBackground(shape: Shape, focused: MutableState<Boolean>, selected: Boolean): Modifier = drawBehind {
    if (!selected && !focused.value) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, if (selected) ThorColors.Sage else ThorColors.PanelSelected)
    if (focused.value) drawOutline(outline, ThorColors.Ink, style = Stroke(ThorDimens.Outline.toPx()))
}

// ---------------------------------------------------------------------------------------------
// The open folder
// ---------------------------------------------------------------------------------------------

@Composable
private fun FolderPanel(
    state: AppUiState,
    files: FilesController,
    entry: FocusRequester,
    actions: AppActions,
    area: MutableState<FocusArea>,
    modifier: Modifier,
) {
    val dir = files.dir
    Column(
        modifier
            .panel(ThorShapes.SelectedGameCard, border = ThorColors.InkLine, shadow = ThorShapes.MainCardShadow)
            .padding(horizontal = 16.du, vertical = 14.du),
        verticalArrangement = Arrangement.spacedBy(4.du),
    ) {
        TopBar(files, entry, actions, area)
        if (!state.allFilesAccess) AccessLine(actions)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                files.loading && files.entries.isEmpty() -> Text("Opening …", style = ThorType.body(), modifier = Modifier.padding(4.du))
                files.listError != null -> Text(files.listError ?: "", style = ThorType.body(), modifier = Modifier.padding(4.du))
                else -> key(dir) { EntryList(files, entry, actions, area) }
            }
        }
    }
}

@Composable
private fun TopBar(files: FilesController, entry: FocusRequester, actions: AppActions, area: MutableState<FocusArea>) {
    val scope = rememberCoroutineScope()
    val searchField = remember { FocusRequester() }
    val onToolbar = {
        files.focusedFolder = null
        area.value = FocusArea.Toolbar
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.du, end = 4.du, bottom = 6.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.du),
    ) {
        Box(Modifier.weight(1f).padding(end = 4.du)) {
            if (files.searchOpen) {
                SearchField(files, searchField) {
                    files.closeSearch()
                    scope.launch { focusSoon(entry) }
                }
                LaunchedEffect(Unit) { focusSoon(searchField) }
            } else {
                Breadcrumb(files.dir?.let(files::breadcrumb) ?: "")
            }
        }
        // With an empty list the Sort button is the tab's first element.
        val first = if (files.shown.isEmpty()) Modifier.focusRequester(entry) else Modifier
        ToolButton(if (files.sortOrder.descending) "Name ▴" else "Name ▾", active = false, onClick = files::toggleSortDirection, onFocused = onToolbar, modifier = first)
        ToolButton("List", active = true, onClick = {}, onFocused = onToolbar)
        ToolButton("Grid", active = false, onClick = { actions.onNotice("Grid view is not there yet.", false) }, onFocused = onToolbar)
        val query = files.query
        ToolButton(
            if (query.isEmpty()) "Search" else "Search: ${query.take(12)}",
            active = query.isNotEmpty(),
            onClick = files::openSearch,
            onFocused = onToolbar,
        )
    }
}

/** The "All files access" hint in one line, so the list keeps its rows (the big banner costs two). */
@Composable
private fun AccessLine(actions: AppActions) {
    Row(
        Modifier.fillMaxWidth().border(ThorDimens.Outline, ThorColors.Orange, SmallShape.shape()).padding(start = 12.du, end = 4.du, top = 3.du, bottom = 3.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.du),
    ) {
        BasicText(
            "All files access is off. It works on this firmware, but may stop after an update.",
            Modifier.weight(1f),
            style = ThorType.style(13, color = ThorColors.Body),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ToolButton("Allow", active = false, onClick = actions.onOpenAllFilesAccess, onFocused = {})
    }
}

/** "Internal storage / Download", slashes quieter. Long paths keep the storage and the last two folders. */
@Composable
private fun Breadcrumb(path: String) {
    val parts = path.split(" / ")
    val shown = if (parts.size > 4) listOf(parts.first(), "…") + parts.takeLast(2) else parts
    val text = buildAnnotatedString {
        shown.forEachIndexed { i, part ->
            if (i > 0) withStyle(SpanStyle(color = ThorColors.Mute)) { append(" / ") }
            append(part)
        }
    }
    BasicText(text, style = ThorType.style(15, FontWeight.SemiBold, ThorColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SearchField(files: FilesController, requester: FocusRequester, onDone: () -> Unit) {
    val shape = SmallShape.shape()
    BasicTextField(
        value = files.query,
        onValueChange = files::updateQuery,
        singleLine = true,
        textStyle = ThorType.style(15, FontWeight.SemiBold, ThorColors.Ink),
        cursorBrush = SolidColor(ThorColors.Orange),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .focusRequester(requester)
            .border(ThorDimens.Outline, ThorColors.Orange, shape)
            .padding(horizontal = 12.du, vertical = 6.du),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.du)) {
                BasicText("filter", style = ThorType.style(13, color = ThorColors.Mute))
                Box(Modifier.weight(1f)) {
                    if (files.query.isEmpty()) BasicText("part of a name", style = ThorType.style(15, color = ThorColors.Mute))
                    inner()
                }
            }
        },
    )
}

/** A small toolbar button ("Name ▾", "List"): orange when [active]. Smaller than ThorButton, like the boards. */
@Composable
private fun ToolButton(text: String, active: Boolean, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = SmallShape.shape()
    Box(
        modifier
            .focusRing(shape)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                if (active) drawOutline(outline, ThorColors.Orange)
                drawOutline(outline, ThorColors.InkLine, style = Stroke(ThorDimens.Outline.toPx()))
            }
            .onFocusChanged { if (it.isFocused) onFocused() }
            .controllerClickable(interaction, onClick)
            .padding(horizontal = 14.du, vertical = 6.du),
    ) {
        BasicText(text, style = ThorType.style(14, FontWeight.SemiBold, if (active) ThorColors.OnAccent else ThorColors.Ink), maxLines = 1)
    }
}

@Composable
private fun EntryList(files: FilesController, entry: FocusRequester, actions: AppActions, area: MutableState<FocusArea>) {
    val list = files.shown
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = files.focusIndex.coerceAtLeast(0))
    // After a folder was opened (or re-listed), focus goes to the remembered row. Item counts that
    // arrive later don't move focus (they don't change listToken).
    LaunchedEffect(files.listToken) {
        if (list.isNotEmpty()) listState.scrollToItem(files.focusIndex.coerceIn(0, list.size - 1))
        focusSoon(entry)
    }
    // A new sort order or filter starts at the top (focus stays where it is, e.g. on the Sort button).
    LaunchedEffect(Unit) {
        snapshotFlow { files.sortOrder to files.query }.drop(1).collect { if (files.shown.isNotEmpty()) listState.scrollToItem(0) }
    }
    if (list.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(4.du), verticalArrangement = Arrangement.spacedBy(8.du)) {
            Text(
                if (files.query.isNotEmpty()) "Nothing here matches “${files.query}”. B clears the filter." else "This folder is empty.",
                style = ThorType.body(),
            )
        }
        return
    }
    // Texts that repeat a lot (chip labels) are drawn with one shared, cached measurer.
    val measurer = rememberTextMeasurer(cacheSize = 32)
    // The first frame of a newly shown list draws icons and names only; sizes, dates and chip
    // labels follow one frame later. Measured: that part of the row is the costly one, and the
    // first frame of a tab switch is already over budget.
    val details = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        details.value = true
    }
    val styles = RowStyles(
        name = ThorType.style(16, FontWeight.SemiBold, ThorColors.Ink),
        meta = ThorType.style(META_FONT, color = ThorColors.Mute).copy(textAlign = TextAlign.End),
        chip = ThorType.style(11, FontWeight.Bold, ThorColors.OnAccent),
        chipSmall = ThorType.style(9, FontWeight.Bold, ThorColors.OnAccent),
    )
    val scope = rememberCoroutineScope()
    // Each composed row's own focus requester, by index (rows off screen are not composed).
    val rowRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    /** Focuses row [target]: right away if it is fully on screen, else after scrolling it into view. */
    fun focusRow(target: Int) {
        files.focusIndex = target
        val info = listState.layoutInfo
        val full = info.visibleItemsInfo.filter { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
        val scrollTo = ListNavigation.scrollFor(target, full.firstOrNull()?.index ?: -1, full.lastOrNull()?.index ?: -1)
        if (scrollTo == null && rowRequesters[target]?.tryFocus() == true) return
        scope.launch {
            if (scrollTo != null) listState.scrollToItem(scrollTo)
            // The row exists after the next layout; stop if another press already moved on.
            repeat(4) {
                if (files.focusIndex != target) return@launch
                if (rowRequesters[target]?.tryFocus() == true) return@launch
                withFrameNanos { }
            }
        }
    }

    // The remembered row is scrolled off screen (e.g. after a touch scroll while focus was in the
    // tab bar): the screen's entry point is then a 1 px stand-in that scrolls back and focuses it,
    // so D-pad Down from the tab bar always lands on a visible row.
    val entryOffscreen by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && info.visibleItemsInfo.none { it.index == files.focusIndex }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ColumnHeader()
        Box(
            Modifier
                .size(1.dp)
                .focusProperties { canFocus = entryOffscreen }
                .then(if (entryOffscreen) Modifier.focusRequester(entry) else Modifier)
                .onFocusChanged { if (it.isFocused) focusRow(files.focusIndex.coerceIn(0, list.size - 1)) }
                .focusable(),
        )
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Up/Down move by row here, so fast presses never jump out of the list. Only Up on
                // the first row leaves it (to the toolbar, then the tab bar).
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val down = when (event.key) {
                        Key.DirectionDown -> true
                        Key.DirectionUp -> false
                        else -> return@onPreviewKeyEvent false
                    }
                    when (val step = ListNavigation.step(files.focusIndex, list.size, down)) {
                        ListNavigation.Step.LeaveUp -> false
                        ListNavigation.Step.Stay -> true
                        is ListNavigation.Step.To -> {
                            focusRow(step.index)
                            true
                        }
                    }
                },
            state = listState,
            // Vertically only the room the focus ring needs, so more rows fit.
            contentPadding = PaddingValues(horizontal = ThorDimens.ScrollBleed, vertical = RingRoom),
            verticalArrangement = Arrangement.spacedBy(2.du),
        ) {
            itemsIndexed(list, key = { _, e -> e.file.path }) { index, e ->
                // Only the rows whose "is the entry row" value changes recompose when focus moves.
                val isEntry by remember(index) { derivedStateOf { files.focusIndex == index } }
                val rowRequester = remember { FocusRequester() }
                DisposableEffect(index, rowRequester) {
                    rowRequesters[index] = rowRequester
                    onDispose { if (rowRequesters[index] === rowRequester) rowRequesters.remove(index) }
                }
                FileRow(
                    e,
                    styles,
                    measurer,
                    details.value,
                    onActivate = { if (e.isDir) files.open(e.file) else actions.onOpenFile(e.file) },
                    onFocused = {
                        files.focusIndex = index
                        files.focusedFolder = if (e.isDir) e.file else null
                        area.value = FocusArea.List
                    },
                    modifier = Modifier.focusRequester(rowRequester).then(if (isEntry) Modifier.focusEntry(entry) else Modifier),
                )
            }
        }
        Footer(list.size, listState, files.query)
    }
}

@Composable
private fun ColumnHeader() {
    val style = ThorType.style(12, color = ThorColors.Mute)
    // Same right edges as the one-text size + date of the rows.
    Row(Modifier.fillMaxWidth().padding(start = ThorDimens.ScrollBleed + 14.du, end = ThorDimens.ScrollBleed + 14.du)) {
        BasicText("name", Modifier.weight(1f), style = style)
        BasicText("size", Modifier.width(SIZE_COLUMN.du), style = style.copy(textAlign = TextAlign.End))
        BasicText("changed", Modifier.width(DATE_COLUMN.du), style = style.copy(textAlign = TextAlign.End))
    }
}

/** Text styles of the rows, made once per list (not per row). */
private data class RowStyles(val name: TextStyle, val meta: TextStyle, val chip: TextStyle, val chipSmall: TextStyle)

/** One file or folder: icon or type chip, name, then size (or item count) and date as one text. */
@Composable
private fun FileRow(e: FileEntry, styles: RowStyles, measurer: TextMeasurer, details: Boolean, onActivate: () -> Unit, onFocused: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    val shape = ThorShapes.GameRow.shape()
    val meta = remember(e.size, e.childCount, e.modified) { FolderListing.metaText(e, FolderListing.formatDate(e.modified)) }
    Row(
        modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.du)
            .focusRing(shape)
            .rowBackground(shape, focused, selected = false)
            .onFocusChanged {
                focused.value = it.isFocused
                if (it.isFocused) onFocused()
            }
            .controllerClickable(interaction, onActivate)
            .padding(horizontal = 14.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.du),
    ) {
        if (e.isDir) FolderIcon() else TypeChipIcon(e.name, measurer, styles, details)
        BasicText(e.name, Modifier.weight(1f), style = styles.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val metaWidth = Modifier.width((SIZE_COLUMN + DATE_COLUMN).du)
        if (details) BasicText(meta, metaWidth, style = styles.meta, maxLines = 1) else Spacer(metaWidth)
    }
}

/**
 * The orange folder of the boards, as two rounded rectangles (tab and body). Uniform corners are
 * drawn as plain rounded rects; the board's curved path cost noticeably more on the render thread.
 */
@Composable
private fun FolderIcon() {
    Spacer(
        Modifier.size(30.du).drawBehind {
            val u = size.width / 30f
            val stroke = Stroke(2f * u)
            val radius = CornerRadius(2f * u)
            val tabTop = Offset(4f * u, 6f * u)
            val tab = Size(11f * u, 6f * u)
            val bodyTop = Offset(4f * u, 9f * u)
            val body = Size(22f * u, 15f * u)
            drawRoundRect(ThorColors.Orange, tabTop, tab, radius)
            drawRoundRect(ThorColors.OrangeShadow, tabTop, tab, radius, style = stroke)
            drawRoundRect(ThorColors.Orange, bodyTop, body, radius)
            drawRoundRect(ThorColors.OrangeShadow, bodyTop, body, radius, style = stroke)
        },
    )
}

/**
 * "XCI", "ZIP": a coloured square with the extension. The label is drawn with the list's shared
 * [measurer] (no text layout node per row; "PNG" is measured once per folder).
 */
@Composable
private fun TypeChipIcon(name: String, measurer: TextMeasurer, styles: RowStyles, withLabel: Boolean) {
    val chip = remember(name) { FolderListing.typeChip(name) }
    val fill = chipColor(chip.kind)
    val style = if (chip.label.length > 3) styles.chipSmall else styles.chip
    val corner = 7.du
    Spacer(
        Modifier.size(30.du).drawWithCache {
            val label = if (withLabel) measurer.measure(chip.label, style) else null
            val radius = CornerRadius(corner.toPx())
            val width = ThorDimens.Outline.toPx()
            onDrawBehind {
                drawRoundRect(fill, cornerRadius = radius)
                drawRoundRect(
                    ThorColors.Ink,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = radius,
                    style = Stroke(width),
                )
                if (label != null) drawText(label, topLeft = Offset((size.width - label.size.width) / 2f, (size.height - label.size.height) / 2f))
            }
        },
    )
}

/** "10 of 38 items" and "1 of 4 pages", from the rows that are fully on screen. */
@Composable
private fun Footer(total: Int, listState: LazyListState, query: String) {
    val numbers by remember(total) {
        derivedStateOf {
            val info = listState.layoutInfo
            val fully = info.visibleItemsInfo.count { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
            val perPage = fully.coerceAtLeast(1)
            Triple(fully.coerceAtMost(total), FolderListing.pages(total, perPage, listState.firstVisibleItemIndex), perPage)
        }
    }
    val (onScreen, pages) = numbers
    val style = ThorType.style(13, color = ThorColors.Mute)
    Row(Modifier.fillMaxWidth().padding(start = 4.du, end = 4.du, top = 6.du)) {
        BasicText(
            "$onScreen of $total ${if (total == 1) "item" else "items"}" + if (query.isNotEmpty()) " matching “$query”" else "",
            Modifier.weight(1f),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText("${pages.first} of ${pages.second} ${if (pages.second == 1) "page" else "pages"}", style = style)
    }
}

// ---------------------------------------------------------------------------------------------
// Actions menu (Select)
// ---------------------------------------------------------------------------------------------

/**
 * The actions for [folder], over the list (board AndroidFM3). D-pad up/down stays inside the menu;
 * B or Select closes it. For now: Scan this folder, pin / unpin, default scan folder.
 */
@Composable
private fun ActionsMenuOverlay(folder: File, files: FilesController, actions: AppActions, onClose: (rowRemoved: Boolean) -> Unit, modifier: Modifier) {
    val pinned = files.prefs.isPinned(folder.path)
    val isDefault = files.prefs.defaultScan == folder.path
    val name = files.breadcrumb(folder).substringAfterLast(" / ")
    val items = buildList<Pair<String, () -> Unit>> {
        add(
            "Scan this folder" to {
                onClose(false)
                openScan(folder, files, actions)
            },
        )
        add(
            (if (pinned) "Unpin folder" else "Pin folder") to {
                files.togglePin(folder)
                onClose(pinned)
                actions.onNotice(if (pinned) "Unpinned $name." else "Pinned $name.", false)
            },
        )
        if (!isDefault) {
            add(
                "Set as default scan folder" to {
                    files.setDefaultScan(folder)
                    onClose(false)
                    actions.onNotice("$name is the default scan folder now.", false)
                },
            )
        }
    }
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val focusedIndex = remember { mutableStateOf(0) }
    LaunchedEffect(folder) { focusSoon(requesters[0]) }
    Column(
        modifier
            .width(370.du)
            .panel(ThorShapes.PcCard, background = ThorColors.Panel, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
            .onPreviewKeyEvent { event ->
                // Focus stays in the menu: up/down move by item, left/right do nothing.
                val step = when (event.key) {
                    Key.DirectionUp -> -1
                    Key.DirectionDown -> 1
                    Key.DirectionLeft, Key.DirectionRight -> 0
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown && step != 0) {
                    val next = (focusedIndex.value + step).coerceIn(0, requesters.lastIndex)
                    runCatching { requesters[next].requestFocus() }
                }
                true
            }
            .padding(horizontal = 12.du, vertical = 14.du),
        verticalArrangement = Arrangement.spacedBy(6.du),
    ) {
        BasicText("actions for $name", Modifier.padding(start = 10.du, bottom = 4.du), style = ThorType.style(13, color = ThorColors.Mute), maxLines = 1, overflow = TextOverflow.Ellipsis)
        items.forEachIndexed { i, (label, act) ->
            MenuItem(label, act, Modifier.focusRequester(requesters[i]).onFocusChanged { if (it.isFocused) focusedIndex.value = i })
        }
        if (isDefault) {
            BasicText("This is the default scan folder.", Modifier.padding(start = 10.du, top = 4.du), style = ThorType.style(12, color = ThorColors.Mute))
        }
    }
}

@Composable
private fun MenuItem(label: String, onActivate: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    val shape = ThorShapes.GameRow.shape()
    Box(
        modifier
            .fillMaxWidth()
            .focusRing(shape)
            .rowBackground(shape, focused, selected = false)
            .onFocusChanged { focused.value = it.isFocused }
            .controllerClickable(interaction, onActivate)
            .padding(horizontal = 16.du, vertical = 10.du),
    ) {
        BasicText(label, style = ThorType.style(17, FontWeight.SemiBold, ThorColors.Ink), maxLines = 1)
    }
}
