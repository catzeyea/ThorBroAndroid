package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.catzeyea.thorbro.android.files.ScanResult
import dev.catzeyea.thorbro.android.ui.components.controllerClickable
import dev.catzeyea.thorbro.android.ui.components.focusFill
import dev.catzeyea.thorbro.android.ui.components.focusRing
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
    val selected = scan.selectedItems
    val inPlace = result.group(ScanGroup.InPlace).size
    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(24.du)) {
        PreviewList(scan, files, Modifier.weight(1f).fillMaxHeight())
        Column(
            Modifier
                .width(400.du)
                .fillMaxHeight()
                .panel(ThorShapes.SelectedGameCard, background = ThorColors.PanelSelected, border = ThorColors.Ink, shadow = ThorShapes.MainCardShadow)
                .padding(26.du),
            verticalArrangement = Arrangement.spacedBy(12.du),
        ) {
            Text("nothing has moved yet", style = ThorType.label())
            when {
                result.toMove.isEmpty() -> {
                    Text("Nothing to move", style = ThorType.style(28, FontWeight.Bold))
                    Text(
                        if (inPlace > 0) {
                            "Nothing to move: $inPlace ${if (inPlace == 1) "file is" else "files are"} already in their library folder."
                        } else {
                            "No file was recognized. Unclear files are not touched."
                        },
                        style = ThorType.body(),
                    )
                    Spacer(Modifier.weight(1f))
                    ThorButton("Close", actions.onCloseSubScreen, Modifier.focusEntryButton(entry, files), kind = ButtonKind.Secondary)
                }
                selected.isEmpty() -> {
                    Text("No file switched on", style = ThorType.style(28, FontWeight.Bold))
                    Text("Switch files on with A to move them. Unclear files are not touched.", style = ThorType.body())
                    Spacer(Modifier.weight(1f))
                    ThorButton("Cancel", actions.onCloseSubScreen, Modifier.focusEntryButton(entry, files), kind = ButtonKind.Secondary, fontSize = 18)
                }
                else -> {
                    Text("Move ${selected.size} ${if (selected.size == 1) "file" else "files"}?", style = ThorType.style(28, FontWeight.Bold))
                    Text(
                        "${FileNames.human(selected.sumOf { it.size })} in total. Nothing is overwritten, every move is written to " +
                            "ThorBro/logs, and Undo on the result screen puts the files back. Unclear files are not touched.",
                        style = ThorType.body(),
                    )
                    if (scan.copies > 0) {
                        Text(
                            "${scan.copies} of them go to another storage: they are copied, checked, and only then deleted here. That takes longer.",
                            style = ThorType.style(15, FontWeight.SemiBold, ThorColors.Orange, lineHeight = 1.4f),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    ThorButton(
                        "Move ${selected.size} ${if (selected.size == 1) "file" else "files"}",
                        files::confirmMove,
                        Modifier.focusEntryButton(entry, files),
                        kind = ButtonKind.Main,
                        fontSize = 22,
                    )
                    ThorButton("Cancel", actions.onCloseSubScreen, Modifier.onFocusChanged { if (it.isFocused) files.previewFocusOnRow = false }, kind = ButtonKind.Secondary, fontSize = 18)
                }
            }
        }
    }
}

/** The first button of the preview: gets focus when the preview opens, and tells the hint bar where focus is. */
private fun Modifier.focusEntryButton(entry: FocusRequester, files: FilesController) =
    focusRequester(entry).onFocusChanged { if (it.isFocused) files.previewFocusOnRow = false }

/** One line of the preview list: a group heading or a file. */
private sealed interface PreviewLine {
    data class Heading(val group: ScanGroup, val count: Int, val target: String) : PreviewLine
    data class FileLine(val item: ScanItem) : PreviewLine
}

private fun previewLines(result: ScanResult): List<PreviewLine> {
    val library = result.library
    val lines = mutableListOf<PreviewLine>()
    for (group in ScanGroup.entries) {
        val items = result.group(group)
        if (items.isEmpty()) continue
        val target = when (group) {
            ScanGroup.Review -> "stays in ${result.folder.name} (Review)"
            ScanGroup.Skipped -> "ignored this time"
            ScanGroup.InPlace -> "nothing to do"
            else -> items.mapNotNull { it.target }.distinct().joinToString(", ") { t -> library?.let { relativeTarget(it.path, t) } ?: t.path }
        }
        lines += PreviewLine.Heading(group, items.size, target)
        items.forEach { lines += PreviewLine.FileLine(it) }
    }
    return lines
}

/**
 * The scan result, grouped. Every file row can take focus (so the D-pad scrolls through the list);
 * A switches a file that can move on or off. Files that can't move have no switch.
 */
@Composable
private fun PreviewList(scan: ScanState.Preview, files: FilesController, modifier: Modifier) {
    val result = scan.result
    val lines = remember(result) { previewLines(result) }
    Column(modifier.panel(ThorShapes.GamesList, shadow = ThorShapes.PcCardShadow).padding(horizontal = 18.du, vertical = 18.du)) {
        Text("scan result for ${result.folder.name}", style = ThorType.label(), modifier = Modifier.padding(start = 8.du))
        Text("${result.items.size} files found", style = ThorType.style(30, FontWeight.Bold), modifier = Modifier.padding(start = 8.du, bottom = 4.du))
        if (result.library == null) {
            Text("No game library (ROMs folder) was found, so nothing can be sorted.", style = ThorType.small(), modifier = Modifier.padding(start = 8.du))
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(ThorDimens.ScrollBleed),
            verticalArrangement = Arrangement.spacedBy(2.du),
        ) {
            itemsIndexed(lines, key = { index, line -> if (line is PreviewLine.FileLine) line.item.file.path else "h$index" }) { _, line ->
                when (line) {
                    is PreviewLine.Heading -> PreviewHeading(line)
                    is PreviewLine.FileLine -> {
                        val movable = line.item.target != null
                        PreviewFileRow(
                            line.item,
                            checked = if (movable) line.item.file in scan.selected else null,
                            onToggle = { files.toggle(line.item.file) },
                            onFocused = { files.previewFocusOnRow = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeading(line: PreviewLine.Heading) {
    val color = when (line.group) {
        ScanGroup.Review, ScanGroup.Skipped -> ThorColors.Orange
        else -> ThorColors.Sage
    }
    Row(Modifier.padding(top = 12.du, start = 8.du, bottom = 2.du), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.du)) {
        Text("${line.group.title} · ${line.count}", style = ThorType.style(20, FontWeight.Bold, if (line.group == ScanGroup.InPlace) ThorColors.Sage else ThorColors.Ink))
        Text("→ ${line.target}", style = ThorType.style(15, color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** [checked]: null = this file can't move (no switch). */
@Composable
private fun PreviewFileRow(item: ScanItem, checked: Boolean?, onToggle: () -> Unit, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    val shape = ThorShapes.GameRow.shape()
    val ink = { if (focused.value) ThorColors.OnAccent else ThorColors.Ink }
    val soft = { if (focused.value) ThorColors.OnAccent else ThorColors.Mute }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .focusFill(shape, focused, selected = false)
            .onFocusChanged {
                focused.value = it.isFocused
                if (it.isFocused) onFocused()
            }
            .controllerClickable(interaction, onActivate = { if (checked != null) onToggle() })
            .padding(horizontal = 10.du, vertical = 5.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.du),
    ) {
        if (checked != null) CheckBox(checked) else Spacer(Modifier.width(22.du))
        Column(Modifier.weight(1f)) {
            BasicText(item.file.name, style = ThorType.style(15), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(
                if (checked == false && !item.onByDefault) "${item.reason} · off: not sure" else item.reason,
                style = ThorType.style(13),
                color = soft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.size > 0) BasicText(FileNames.human(item.size), style = ThorType.style(15), color = ink)
    }
}

/** A small checkbox, drawn (no Material checkbox, it has its own focus handling). */
@Composable
fun CheckBox(checked: Boolean) {
    Canvas(Modifier.size(22.du)) {
        val stroke = ThorDimens.Outline.toPx()
        val radius = CornerRadius(4.dp.toPx())
        if (checked) {
            drawRoundRect(ThorColors.Sage, cornerRadius = radius)
            val check = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.52f)
                lineTo(size.width * 0.42f, size.height * 0.72f)
                lineTo(size.width * 0.78f, size.height * 0.30f)
            }
            drawPath(check, ThorColors.OnAccent, style = Stroke(stroke * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawRoundRect(ThorColors.Ink, cornerRadius = radius, style = Stroke(stroke))
    }
}

/** "ROMs/switch/update" for a target inside the library. */
private fun relativeTarget(libraryPath: File, target: File): String =
    "${libraryPath.name}/${target.relativeTo(libraryPath).path}"

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
