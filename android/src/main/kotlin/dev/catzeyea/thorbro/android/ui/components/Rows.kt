package dev.catzeyea.thorbro.android.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du

/**
 * Fill and outline that follow focus (or [selected]) without a recomposition: [focused] is written
 * by `onFocusChanged` and only read while drawing, like [focusRing].
 */
fun Modifier.focusFill(shape: Shape, focused: MutableState<Boolean>, selected: Boolean, fill: Color = ThorColors.Sage): Modifier =
    drawBehind {
        if (focused.value || selected) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, fill)
            drawOutline(outline, ThorColors.Ink, style = Stroke(ThorDimens.Outline.toPx()))
        }
    }

/** Rows are smaller than the 48 dp touch minimum (the design shows about 7 rows), but stay tappable. */
private val MIN_ROW_HEIGHT = 40.dp

/**
 * A row in a list (files, places, saves): title, optional second line and a value on the right.
 * Focused (or [selected]) rows are sage with dark text, the focus ring around them. Everything that
 * changes with focus is decided at draw time, so a row that gets focus is drawn complete in the same
 * frame.
 */
@Composable
fun ListRow(
    title: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
    onFocused: () -> Unit = {},
    onTap: () -> Unit = onActivate,
    titleSize: Int = 20,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    val shape = ThorShapes.GameRow.shape()
    val highlighted = { focused.value || selected }
    val ink = { if (highlighted()) ThorColors.OnAccent else ThorColors.Ink }
    val soft = { if (highlighted()) ThorColors.OnAccent else ThorColors.Mute }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = max(46.du, MIN_ROW_HEIGHT))
            .focusRing(shape)
            .focusFill(shape, focused, selected)
            .onFocusChanged {
                focused.value = it.isFocused
                if (it.isFocused) onFocused()
            }
            .controllerClickable(interaction, onActivate, onTap)
            .padding(horizontal = 20.du, vertical = 6.du),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.du),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.du)) {
            BasicText(
                title,
                style = ThorType.style(titleSize, FontWeight.Medium),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                BasicText(subtitle, style = ThorType.style(14), color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) BasicText(trailing, style = ThorType.style(16, FontWeight.Medium), color = ink, maxLines = 1)
    }
}
