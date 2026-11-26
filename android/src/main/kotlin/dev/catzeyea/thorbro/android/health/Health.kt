package dev.catzeyea.thorbro.android.health

import kotlin.math.roundToInt

// Thor health for the Start tab, read on the device itself. Same values, rules and mood as the PC
// Start page (ThorBro src/status.rs + ui/js/status.js). Rule: a value that can't be read or makes
// no sense is null and is not shown; there are no placeholder numbers.

data class Battery(val percent: Int, val status: Int?) {
    /** BatteryManager.BATTERY_STATUS_CHARGING. Only status 2 counts (PC rule); "plugged in" is not enough. */
    val charging: Boolean get() = status == STATUS_CHARGING

    companion object {
        const val STATUS_CHARGING = 2
    }
}

data class Space(val total: Long, val free: Long) {
    val usedPercent: Int get() = (100.0 * (total - free) / total).roundToInt()
}

data class CpuTicks(val busy: Long, val total: Long)

data class Ram(val total: Long, val available: Long) {
    val used: Long get() = total - available
    val percent: Int get() = (100.0 * used / total).roundToInt()
}

/** One reading. Every field is null when it couldn't be read. */
data class HealthReading(
    val battery: Battery? = null,
    val internal: Space? = null,
    /** The first mounted SD card. Null also means "no SD card". */
    val sd: Space? = null,
    val cpu: CpuTicks? = null,
    val ram: Ram? = null,
    /** Hottest CPU thermal zone in °C. */
    val cpuTempC: Double? = null,
)

object HealthParser {

    fun battery(level: Int, scale: Int, status: Int, present: Boolean): Battery? {
        if (!present || level < 0) return null
        val s = if (scale <= 0) 100 else scale
        if (level > s) return null
        return Battery(((level.toDouble() / s) * 100).roundToInt(), status.takeIf { it >= 0 })
    }

    fun space(total: Long, free: Long): Space? = if (total <= 0 || free < 0 || free > total) null else Space(total, free)

    /** `cpu  user nice system idle iowait irq softirq steal ...` (first line of /proc/stat). */
    fun cpuLine(line: String): CpuTicks? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.firstOrNull() != "cpu") return null
        val values = parts.drop(1).map { it.toLongOrNull() ?: return null }
        if (values.size < 5) return null
        val total = values.take(8).sum()
        val idle = values[3] + values[4]
        return if (total > 0) CpuTicks(total - idle, total) else null
    }

    /** `MemTotal:` and `MemAvailable:` lines of /proc/meminfo (kB). */
    fun memInfo(lines: List<String>): Ram? {
        fun value(key: String) = lines.firstNotNullOfOrNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.firstOrNull() == key) parts.getOrNull(1)?.toLongOrNull() else null
        }
        val total = value("MemTotal:") ?: return null
        val available = value("MemAvailable:") ?: return null
        return if (total > 0 && available <= total) Ram(total * 1024, available * 1024) else null
    }

    /**
     * Hottest CPU zone from (zone type, temp in millidegrees) pairs. Only `cpu-0-*` / `cpu-1-*`
     * zones (as on the PC); values outside 0-150 °C are sensor errors.
     */
    fun hottestCpu(zones: List<Pair<String, String>>): Double? = zones
        .filter { (type, _) -> type.startsWith("cpu-0-") || type.startsWith("cpu-1-") }
        .mapNotNull { (_, temp) -> temp.trim().toLongOrNull()?.div(1000.0) }
        .filter { it in 0.0..150.0 }
        .maxOrNull()

    /** CPU load in % from two readings, or null if they don't fit together. */
    fun cpuPercent(previous: CpuTicks?, current: CpuTicks?): Int? {
        if (previous == null || current == null || current.total <= previous.total) return null
        val pct = (100.0 * (current.busy - previous.busy) / (current.total - previous.total)).roundToInt()
        return pct.coerceIn(0, 100)
    }
}

/** Mood thresholds (same as the PC, tune here). */
object MoodLimits {
    const val CPU_BUSY_PCT = 80
    const val TEMP_HOT_C = 70.0
    const val BATTERY_TIRED_PCT = 20
    const val STORAGE_FULL_PCT = 90

    /** Wording of the small lines under the gauges (not used for the mood). */
    const val TEMP_WARM_C = 55.0
    const val CPU_SOME_PCT = 40
}

enum class MoodKey { Asleep, Busy, Tired, Stuffed, Fit }

data class Mood(val key: MoodKey, val head: String, val line: String)

/** The gauge columns, in screen order. */
enum class Gauge { Battery, Internal, Sd, Cpu, Ram, Temp }

object Moods {

    private val HEADS = mapOf(
        MoodKey.Asleep to "Zzz…",
        MoodKey.Busy to "Working hard!",
        MoodKey.Tired to "I'm tired…",
        MoodKey.Stuffed to "I'm stuffed!",
        MoodKey.Fit to "I feel great!",
    )

    /**
     * First match wins: asleep (no PC) → busy → tired → stuffed → fit.
     * [pcConnected]: null = can't be read, then "asleep" is never used (decision 6).
     */
    fun moodFor(pcConnected: Boolean?, reading: HealthReading, cpuPct: Int?): Mood {
        fun mood(key: MoodKey, line: String) = Mood(key, HEADS.getValue(key), line)
        if (pcConnected == false) return mood(MoodKey.Asleep, "No PC connected. Plug me into your PC with USB.")
        val temp = reading.cpuTempC
        if (cpuPct != null && cpuPct >= MoodLimits.CPU_BUSY_PCT) return mood(MoodKey.Busy, "My CPU is at $cpuPct %. Give me a minute.")
        if (temp != null && temp >= MoodLimits.TEMP_HOT_C) {
            return mood(MoodKey.Busy, "I'm at ${temp.roundToInt()} °C. Give me a minute to cool down.")
        }
        val battery = reading.battery
        if (battery != null && battery.percent <= MoodLimits.BATTERY_TIRED_PCT && !battery.charging) {
            return mood(
                MoodKey.Tired,
                if (battery.percent <= 10) "Only ${battery.percent} % battery left. Plug me in now." else "Battery is at ${battery.percent} %. Plug me in soon.",
            )
        }
        val internal = reading.internal
        if (internal != null && internal.usedPercent >= MoodLimits.STORAGE_FULL_PCT) {
            val sd = reading.sd
            return mood(
                MoodKey.Stuffed,
                if (sd != null && sd.free > internal.free) {
                    "Internal storage is ${internal.usedPercent} % full. Move some games to the SD card?"
                } else {
                    "Internal storage is ${internal.usedPercent} % full. Clean out some games?"
                },
            )
        }
        // Fit: only say what is known.
        val parts = mutableListOf<String>()
        if (battery != null) parts += if (battery.charging) "charging up" else "plenty of battery"
        if (internal != null) parts += "plenty of room"
        val sentences = mutableListOf<String>()
        if (parts.isNotEmpty()) sentences += parts.joinToString(", ") + "."
        if (cpuPct != null) sentences += "nothing heavy running."
        val line = sentences.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }.ifEmpty { "All good here." }
        return mood(MoodKey.Fit, line)
    }

    /** With an SD card: battery, internal, SD, CPU, temp. Without: battery, internal, CPU, RAM, temp. */
    fun columns(reading: HealthReading, cpuPct: Int?): List<Gauge> = buildList {
        if (reading.battery != null) add(Gauge.Battery)
        if (reading.internal != null) add(Gauge.Internal)
        if (reading.sd != null) add(Gauge.Sd)
        if (cpuPct != null) add(Gauge.Cpu)
        if (reading.sd == null && reading.ram != null) add(Gauge.Ram)
        if (reading.cpuTempC != null) add(Gauge.Temp)
    }
}
