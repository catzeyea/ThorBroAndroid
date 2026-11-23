package dev.catzeyea.thorbro.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.ui.navigation.ControllerKeys
import dev.catzeyea.thorbro.android.ui.theme.Corners
import dev.catzeyea.thorbro.android.ui.theme.HardShadow
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du

// Building blocks for all screens. Colors, corners and sizes come from ui/theme/ThorBroTheme.kt.

// ---------------------------------------------------------------------------------------------
// Modifiers
// ---------------------------------------------------------------------------------------------

/**
 * Draws the focus ring: a cream outline of [shape], [ThorDimens.FocusRingOffset] outside the element.
 * Put it early in the modifier chain so the ring is drawn on top of the element.
 */
fun Modifier.focusRing(visible: Boolean, shape: Shape): Modifier = drawWithContent {
    drawContent()
    if (visible) {
        val width = ThorDimens.FocusRingWidth.toPx()
        // The stroke is centered on the outline, so grow by offset + half the stroke width.
        val grow = ThorDimens.FocusRingOffset.toPx() + width / 2
        val outline = shape.createOutline(
            Size(size.width + 2 * grow, size.height + 2 * grow),
            layoutDirection,
            this,
        )
        translate(-grow, -grow) { drawOutline(outline, ThorColors.Focus, style = Stroke(width)) }
    }
}

/** A hard drop shadow (no blur): [shape] again, moved by the shadow's offset. Draws behind the element. */
@Composable
fun Modifier.hardShadow(shadow: HardShadow, shape: Shape): Modifier {
    val x = shadow.x.du
    val y = shadow.y.du
    return drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(x.toPx(), y.toPx()) { drawOutline(outline, shadow.color) }
    }
}

/** Panel look: hard shadow, fill and 2dp outline. */
@Composable
fun Modifier.panel(
    corners: Corners,
    background: Color = ThorColors.Panel,
    border: Color = ThorColors.InkLine,
    shadow: HardShadow? = null,
): Modifier {
    val shape = corners.shape()
    return this
        .then(if (shadow != null) Modifier.hardShadow(shadow, shape) else Modifier)
        .background(background, shape)
        .border(ThorDimens.Outline, border, shape)
}

/**
 * Makes an element focusable and activatable by controller, keyboard and touch.
 *
 * Unlike Modifier.clickable, the element can hold focus in touch mode too. So the focus ring is always
 * somewhere on screen, D-pad navigation starts from a known place, and initial focus works right after
 * launch. A tap moves focus to the element, so the ring follows the finger as well.
 *
 * [onActivate] runs when a confirm key (A, D-pad center, Enter) is released; it only runs if the same
 * element also got the key press, so a press that moved focus elsewhere cannot trigger the next element.
 * [onTap] runs on a touch tap (defaults to [onActivate]).
 */
@Composable
fun Modifier.controllerClickable(
    interactionSource: MutableInteractionSource,
    onActivate: () -> Unit,
    onTap: () -> Unit = onActivate,
): Modifier {
    val activate by rememberUpdatedState(onActivate)
    val tap by rememberUpdatedState(onTap)
    val requester = remember { FocusRequester() }
    var pressed by remember { mutableStateOf(false) }
    return this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick { activate(); true }
        }
        .onKeyEvent { event ->
            if (!ControllerKeys.isConfirm(event.nativeKeyEvent.keyCode)) return@onKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> pressed = true
                KeyEventType.KeyUp -> if (pressed) {
                    pressed = false
                    activate()
                }
            }
            // Consume down and up, so Android does not also send a fallback key for the A button.
            true
        }
        .onFocusChanged { if (!it.isFocused) pressed = false }
        .focusRequester(requester)
        .focusable(interactionSource = interactionSource)
        .pointerInput(Unit) {
            detectTapGestures(onTap = {
                requester.requestFocus()
                tap()
            })
        }
}

/**
 * A focusable element without an action (a text card). It lets the D-pad reach and scroll to text
 * that would otherwise be out of reach, and shows the focus ring while focused.
 */
@Composable
fun Modifier.focusableCard(shape: Shape, interactionSource: MutableInteractionSource): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val requester = remember { FocusRequester() }
    return this
        .focusRing(focused, shape)
        .focusRequester(requester)
        .focusable(interactionSource = interactionSource)
        .pointerInput(Unit) { detectTapGestures(onTap = { requester.requestFocus() }) }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

enum class ButtonKind {
    /** Big orange main action with an "A" badge (Connect to PC, Play on Thor). */
    Main,

    /** Outlined button (Open Tailscale). */
    Secondary,

    /** Smaller, quieter outlined button (Open Moonlight only, links). */
    Small,
}

/**
 * A ThorBro button. [fontSize] is in design units; null uses the kind's default size.
 * [showABadge] draws the "A" controller glyph inside a main button.
 */
@Composable
fun ThorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    showABadge: Boolean = kind == ButtonKind.Main,
    fontSize: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val corners = when (kind) {
        ButtonKind.Main -> ThorShapes.ButtonMain
        ButtonKind.Secondary -> ThorShapes.ButtonSecondary
        ButtonKind.Small -> ThorShapes.ButtonSmall
    }
    val shape = corners.shape()
    val size = fontSize ?: when (kind) {
        ButtonKind.Main -> 24
        ButtonKind.Secondary -> 18
        ButtonKind.Small -> 16
    }
    val textColor = if (kind == ButtonKind.Main) ThorColors.OnAccent else ThorColors.Ink
    val weight = when (kind) {
        ButtonKind.Main -> FontWeight.Bold
        ButtonKind.Secondary -> FontWeight.SemiBold
        ButtonKind.Small -> FontWeight.Medium
    }
    val (padH, padV) = when (kind) {
        ButtonKind.Main -> 34.du to 18.du
        ButtonKind.Secondary -> 26.du to 14.du
        ButtonKind.Small -> 20.du to 12.du
    }
    val surface = when (kind) {
        ButtonKind.Main -> Modifier
            .hardShadow(ThorShapes.OrangeButtonShadow, shape)
            .background(ThorColors.Orange, shape)
            .border(ThorDimens.Outline, ThorColors.Ink, shape)
        ButtonKind.Secondary -> Modifier.border(ThorDimens.Outline, ThorColors.InkLine, shape)
        ButtonKind.Small -> Modifier.border(ThorDimens.Outline, ThorColors.InkSoft, shape)
    }
    val minHeight = if (kind == ButtonKind.Main) ThorDimens.MinMainAction else ThorDimens.MinTarget

    Row(
        modifier
            .focusRing(focused, shape)
            .then(surface)
            .controllerClickable(interaction, onClick)
            .heightIn(min = minHeight)
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.du),
    ) {
        if (showABadge) {
            ButtonGlyph("A", diameter = (size + 6).du, fontSize = (size * 2) / 3, color = ThorColors.OnAccent)
        }
        Text(text, style = ThorType.style(size, weight, textColor))
    }
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

/** A round controller button glyph ("A", "B"). */
@Composable
fun ButtonGlyph(letter: String, diameter: Dp, fontSize: Int, color: Color = ThorColors.Ink) {
    Box(
        Modifier.size(diameter).border(ThorDimens.Outline, color, ThorShapes.Circle),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = ThorType.style(fontSize, FontWeight.Bold, color), textAlign = TextAlign.Center)
    }
}

/** A shoulder button chip ("L1", "R1"). */
@Composable
fun KeyChip(label: String, corners: Corners, border: Color, textColor: Color, weight: FontWeight) {
    val shape = corners.shape()
    Box(Modifier.border(ThorDimens.Outline, border, shape).padding(horizontal = 9.du, vertical = 3.du)) {
        Text(label, style = ThorType.style(13, weight, textColor))
    }
}

/** The numbered circle of a step card. */
@Composable
fun StepNumber(number: Int, fill: Color, diameter: Int = 52, fontSize: Int = 24) {
    Box(
        Modifier
            .size(diameter.du)
            .background(fill, ThorShapes.Circle)
            .border(ThorDimens.Outline, ThorColors.Ink, ThorShapes.Circle),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = ThorType.style(fontSize, FontWeight.Bold, ThorColors.OnAccent))
    }
}

/** A small sage label, e.g. "✓ profile received". */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier) {
    val shape = ThorShapes.StatusChip.shape()
    Box(modifier.background(ThorColors.Sage, shape).padding(horizontal = 14.du, vertical = 6.du)) {
        Text(text, style = ThorType.style(15, FontWeight.SemiBold, ThorColors.OnAccent))
    }
}

/** "Label .......... value" with an optional hairline below. */
@Composable
fun InfoRow(label: String, value: String, divider: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        val y = size.height - 0.5f
                        drawLine(ThorColors.InkHairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }.padding(bottom = 10.du)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(12.du),
    ) {
        Text(label, style = ThorType.style(16, color = ThorColors.Mute))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = ThorType.style(16, color = ThorColors.Ink),
            textAlign = TextAlign.End,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom hint bar
// ---------------------------------------------------------------------------------------------

/** One entry of the hint bar: controller [keys] (e.g. "A", or "L1" + "R1") and what they do. */
data class Hint(val keys: List<String>, val label: String)

/** A short message at the right end of the hint bar (e.g. "Tailscale is not installed"). */
data class Notice(val text: String, val isError: Boolean, val id: Long)

@Composable
fun HintBar(hints: List<Hint>, notice: Notice?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(30.du),
    ) {
        hints.forEach { hint ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.du)) {
                hint.keys.forEachIndexed { index, key ->
                    if (key.length == 1) {
                        ButtonGlyph(key, diameter = 28.du, fontSize = 14)
                    } else {
                        KeyChip(
                            key,
                            if (index == 0) ThorShapes.ChipLeft else ThorShapes.ChipRight,
                            border = ThorColors.Ink,
                            textColor = ThorColors.Ink,
                            weight = FontWeight.Bold,
                        )
                    }
                }
                Text(hint.label, style = ThorType.hint())
            }
        }
        if (notice != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                val shape = ThorShapes.StatusChip.shape()
                val color = if (notice.isError) ThorColors.Danger else ThorColors.Sage
                Text(
                    notice.text,
                    modifier = Modifier.border(ThorDimens.Outline, color, shape).padding(horizontal = 12.du, vertical = 4.du),
                    style = ThorType.style(15, FontWeight.SemiBold, color),
                )
            }
        }
    }
}
