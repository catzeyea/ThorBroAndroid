package dev.catzeyea.thorbro.android.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.catzeyea.thorbro.android.health.CpuTicks
import dev.catzeyea.thorbro.android.health.HealthParser
import dev.catzeyea.thorbro.android.health.HealthReader
import dev.catzeyea.thorbro.android.health.HealthReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Health readings for the Start tab and the header ThorBro. Readings are only taken while the Start
 * tab is shown ([pollWhileVisible] runs in a LaunchedEffect there); other tabs keep the last values.
 */
class HealthController(private val context: Context) {

    companion object {
        const val POLL_MS = 4_000L
        /** The CPU load needs two readings; the second comes this much after the first. */
        const val CPU_SECOND_SAMPLE_MS = 1_000L
        /** An older CPU reading is not used for the load (it would be an average over minutes). */
        const val CPU_SAMPLE_MAX_AGE_MS = POLL_MS * 5 / 2
        const val CPU_HISTORY = 10
    }

    var reading by mutableStateOf<HealthReading?>(null)
        private set
    var cpuPct by mutableStateOf<Int?>(null)
        private set
    var cpuHistory by mutableStateOf<List<Int>>(emptyList())
        private set

    private var previous: Pair<CpuTicks, Long>? = null

    suspend fun pollWhileVisible() {
        while (true) {
            tick()
            delay(if (cpuPct == null && reading?.cpu != null) CPU_SECOND_SAMPLE_MS else POLL_MS)
        }
    }

    private suspend fun tick() {
        val next = withContext(Dispatchers.IO) { HealthReader.read(context) }
        val now = SystemClock.elapsedRealtime()
        val prev = previous
        previous = next.cpu?.let { it to now }
        val pct = if (prev == null || now - prev.second > CPU_SAMPLE_MAX_AGE_MS) null else HealthParser.cpuPercent(prev.first, next.cpu)
        cpuPct = pct
        cpuHistory = if (pct == null) cpuHistory.takeIf { next.cpu != null && prev != null } ?: emptyList() else (cpuHistory + pct).takeLast(CPU_HISTORY)
        reading = next
    }
}
