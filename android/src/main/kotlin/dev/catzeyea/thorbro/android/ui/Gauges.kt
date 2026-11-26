package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import dev.catzeyea.thorbro.android.health.MoodLimits
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import kotlin.math.roundToInt

// The gauges of the Start tab, drawn like the PC Start page (ui/js/status.js). Each drawing uses the
// PC's SVG coordinates and is scaled to its box, so the shapes match.

/** One status column: small label, drawing, big value + unit, small line. */
@Composable
fun GaugeColumn(label: String, value: String, unit: String, sub: String, modifier: Modifier = Modifier, drawing: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.du)) {
        Text(label, style = ThorType.label())
        Box(Modifier.heightIn(min = 104.du), contentAlignment = Alignment.CenterStart) { drawing() }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ThorType.style(30, FontWeight.Bold))
            Text(unit, style = ThorType.style(14, color = ThorColors.Mute))
        }
        if (sub.isNotEmpty()) Text(sub, style = ThorType.style(13, color = ThorColors.Mute))
    }
}

/** Draws [block] in a viewBox of [vbW] x [vbH] scaled into the canvas. */
private fun DrawScope.viewBox(vbW: Float, vbH: Float, block: DrawScope.() -> Unit) {
    val s = minOf(size.width / vbW, size.height / vbH)
    scale(s, s, pivot = Offset.Zero) { block() }
}

private val Sage = ThorColors.Sage
private val Ink = ThorColors.Ink
private val Dim = ThorColors.Ink.copy(alpha = 0.35f)

@Composable
fun BatteryDrawing(percent: Int, charging: Boolean) {
    Canvas(Modifier.size(176.du, 88.du)) {
        viewBox(196f, 96f) {
            drawRoundRect(Ink, Offset(3f, 5f), Size(170f, 82f), CornerRadius(18f), style = Stroke(5f))
            drawPath(Path().apply { moveTo(178f, 32f); lineTo(184f, 32f); quadraticTo(190f, 32f, 190f, 38f); lineTo(190f, 52f); quadraticTo(190f, 58f, 184f, 58f); lineTo(178f, 58f); close() }, Ink)
            val filled = if (percent > 0) maxOf(1, (percent / 10.0).roundToInt()) else 0
            for (i in 0 until 10) {
                val x = 14f + i * 15.4f
                if (i < filled) {
                    drawRoundRect(Sage, Offset(x, 16f), Size(11.4f, 58f), CornerRadius(4f))
                    drawRoundRect(Ink.copy(alpha = 0.45f), Offset(x + 2f, 19f), Size(7.4f, 4f), CornerRadius(2f))
                } else {
                    drawRoundRect(Dim, Offset(x, 16f), Size(11.4f, 58f), CornerRadius(4f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
                }
            }
            if (charging) {
                val bolt = Path().apply { moveTo(100f, 10f); lineTo(76f, 50f); lineTo(93f, 50f); lineTo(84f, 84f); lineTo(116f, 40f); lineTo(98f, 40f); lineTo(106f, 10f); close() }
                drawPath(bolt, ThorColors.Orange)
                drawPath(bolt, ThorColors.Bg, style = Stroke(3f, join = StrokeJoin.Round))
            }
        }
    }
}

/** [blocks] squares, 10 per row; the free part is drawn as outlines at the start, used ones filled. */
@Composable
fun BlocksDrawing(total: Long, free: Long, blocks: Int = 50) {
    val freeBlocks = ((free.toDouble() / total) * blocks).roundToInt()
    val rows = (blocks + 9) / 10
    Canvas(Modifier.size(130.du, (rows * 13).du)) {
        viewBox(130f, rows * 13f) {
            for (i in 0 until blocks) {
                val x = (i % 10) * 13f
                val y = (i / 10) * 13f
                val used = i >= freeBlocks
                if (used) {
                    drawRoundRect(Sage, Offset(x, y), Size(10f, 10f), CornerRadius(2f))
                } else {
                    drawRoundRect(Dim, Offset(x + 0.5f, y + 0.5f), Size(9f, 9f), CornerRadius(2f), style = Stroke(1.2f))
                }
            }
        }
    }
}

@Composable
fun CpuDrawing(history: List<Int>, pct: Int) {
    val color = if (pct >= MoodLimits.CPU_BUSY_PCT) ThorColors.Danger else Sage
    Canvas(Modifier.size(160.du, 60.du)) {
        viewBox(190f, 64f) {
            drawLine(Dim, Offset(2f, 54f), Offset(188f, 54f), strokeWidth = 2f)
            val points = history.ifEmpty { listOf(pct) }
            val path = Path()
            if (points.size == 1) {
                val y = 54f - points[0] * 0.5f
                path.moveTo(2f, y); path.lineTo(188f, y)
            } else {
                val step = 186f / (points.size - 1)
                points.forEachIndexed { i, p ->
                    val x = 2f + i * step
                    val y = 54f - p * 0.5f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun RamDrawing(pct: Int) {
    Canvas(Modifier.size(160.du, 66.du)) {
        viewBox(200f, 66f) {
            val stick = Path().apply {
                moveTo(4f, 8f); quadraticTo(4f, 2f, 10f, 2f); lineTo(190f, 2f); quadraticTo(196f, 2f, 196f, 8f)
                lineTo(196f, 50f); lineTo(112f, 50f); quadraticTo(104f, 58f, 96f, 50f); lineTo(4f, 50f); close()
            }
            drawPath(stick, Ink, style = Stroke(4f))
            val filled = ((pct / 100.0) * 6).roundToInt()
            for (i in 0 until 6) {
                val color: Color = if (i < filled) Sage else Dim
                drawRoundRect(color, Offset(18f + i * 30f, 12f), Size(22f, 26f), CornerRadius(4f), style = if (i < filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2f))
            }
            for (i in 0 until 22) {
                val x = 16f + i * 7.6f
                drawLine(Ink, Offset(x, 52f), Offset(x, 60f), strokeWidth = 2f)
            }
        }
    }
}

@Composable
fun TempDrawing(celsius: Double) {
    val hot = celsius >= MoodLimits.TEMP_HOT_C
    val mercury = if (hot) ThorColors.Danger else Sage
    Canvas(Modifier.size(58.du, 104.du)) {
        viewBox(58f, 112f) {
            drawRoundRect(Ink, Offset(12f, 4f), Size(22f, 84f), CornerRadius(11f), style = Stroke(4f))
            drawCircle(mercury, 16f, Offset(23f, 92f))
            drawCircle(Ink, 16f, Offset(23f, 92f), style = Stroke(4f))
            val top = (84 - (celsius.coerceIn(20.0, 80.0) - 20) * (74.0 / 60)).roundToInt().toFloat()
            drawRoundRect(mercury, Offset(18f, top), Size(10f, 94f - top), CornerRadius(5f))
            for (i in 0 until 8) {
                val y = 16f + i * 10f
                drawLine(Ink, Offset(40f, y), Offset(40f + if (i % 2 == 1) 7f else 12f, y), strokeWidth = 2f)
            }
        }
    }
}
