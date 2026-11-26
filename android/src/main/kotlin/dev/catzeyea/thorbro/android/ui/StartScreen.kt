package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import dev.catzeyea.thorbro.android.R
import dev.catzeyea.thorbro.android.health.Gauge
import dev.catzeyea.thorbro.android.health.HealthReading
import dev.catzeyea.thorbro.android.health.MoodKey
import dev.catzeyea.thorbro.android.health.MoodLimits
import dev.catzeyea.thorbro.android.health.Moods
import dev.catzeyea.thorbro.android.ui.navigation.OnControllerKeys
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du
import java.util.Locale
import kotlin.math.roundToInt

/** Start tab: ThorBro's mood and the device health, like the PC Start page. Nothing here is focusable. */
@Composable
fun StartScreen(state: AppUiState, health: HealthController) {
    OnControllerKeys { false }
    LaunchedEffect(Unit) { health.pollWhileVisible() }
    val reading = health.reading ?: return
    val cpuPct = health.cpuPct
    val mood = Moods.moodFor(state.pcConnected, reading, cpuPct)

    Column(Modifier.fillMaxSize().padding(top = 4.du), verticalArrangement = Arrangement.spacedBy(44.du)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.du)) {
            Image(painterResource(moodImage(mood.key)), contentDescription = null, modifier = Modifier.size(170.du))
            Column(verticalArrangement = Arrangement.spacedBy(6.du)) {
                Text(mood.head, style = ThorType.style(46, FontWeight.Bold))
                Text(mood.line, style = ThorType.style(17, color = ThorColors.Body))
            }
        }
        val columns = Moods.columns(reading, cpuPct)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.du)) {
            columns.forEachIndexed { index, gauge ->
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (index > 0) Modifier.dashedLeftLine() else Modifier)
                        .padding(start = if (index > 0) 24.du else 0.du),
                ) { GaugeFor(gauge, reading, cpuPct, health.cpuHistory) }
            }
        }
    }
}

private fun moodImage(key: MoodKey) = when (key) {
    MoodKey.Asleep -> R.drawable.thorbro_mood_wait_off
    MoodKey.Busy -> R.drawable.thorbro_mood_busy
    MoodKey.Tired -> R.drawable.thorbro_mood_tired
    MoodKey.Stuffed -> R.drawable.thorbro_mood_stuffed
    MoodKey.Fit -> R.drawable.thorbro_mood_done
}

private fun Modifier.dashedLeftLine() = drawBehind {
    drawLine(
        ThorColors.InkHairline,
        Offset(0f, 0f),
        Offset(0f, size.height),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
    )
}

/** Gigabytes as Android's storage screen counts them (1 GB = 10^9 bytes), one decimal below 10 GB. */
fun formatGB(bytes: Long): String {
    val tenths = (bytes / 1e8).roundToInt() / 10.0
    return if (tenths >= 10) "${tenths.roundToInt()}" else String.format(Locale.ROOT, "%.1f", tenths)
}

@Composable
private fun GaugeFor(gauge: Gauge, reading: HealthReading, cpuPct: Int?, history: List<Int>) {
    when (gauge) {
        Gauge.Battery -> {
            val battery = reading.battery ?: return
            GaugeColumn("battery", "${battery.percent}", if (battery.charging) " % · charging" else " %", "") {
                BatteryDrawing(battery.percent, battery.charging)
            }
        }
        Gauge.Internal, Gauge.Sd -> {
            val space = (if (gauge == Gauge.Sd) reading.sd else reading.internal) ?: return
            GaugeColumn(
                if (gauge == Gauge.Sd) "SD card" else "internal storage",
                "${formatGB(space.free)} GB",
                " free",
                "each block is about ${formatGB(space.total / 50)} GB",
            ) { BlocksDrawing(space.total, space.free) }
        }
        Gauge.Cpu -> {
            val pct = cpuPct ?: return
            val ram = reading.ram
            val sub = when {
                reading.sd != null && ram != null -> "RAM ${ram.percent} %"
                pct >= MoodLimits.CPU_BUSY_PCT -> "working hard"
                pct >= MoodLimits.CPU_SOME_PCT -> "doing some work"
                else -> "nothing heavy running"
            }
            GaugeColumn("cpu", "$pct", " %", sub) { CpuDrawing(history, pct) }
        }
        Gauge.Ram -> {
            val ram = reading.ram ?: return
            GaugeColumn("ram", "${ram.percent}", " %", "${formatGB(ram.used)} of ${formatGB(ram.total)} GB used") { RamDrawing(ram.percent) }
        }
        Gauge.Temp -> {
            val c = reading.cpuTempC ?: return
            val sub = when {
                c >= MoodLimits.TEMP_HOT_C -> "running hot"
                c >= MoodLimits.TEMP_WARM_C -> "warm"
                else -> "cool and calm"
            }
            GaugeColumn("temperature", "${c.roundToInt()}", " °C", sub) { TempDrawing(c) }
        }
    }
}
