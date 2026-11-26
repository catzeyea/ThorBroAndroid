package dev.catzeyea.thorbro.android.health

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import dev.catzeyea.thorbro.android.device.Storages
import java.io.File

/**
 * Reads [HealthReading] on the device (no adb). Sources, all checked on a real Thor (Android 13):
 * - battery: the sticky ACTION_BATTERY_CHANGED broadcast (level, scale, status, present)
 * - internal storage / SD card: StatFs on the storage roots (same numbers as `df` on the PC)
 * - CPU: first line of /proc/stat (readable for apps on the Thor firmware; stock Android blocks it)
 * - RAM: /proc/meminfo MemTotal + MemAvailable
 * - temperature: CPU zones in /sys/class/thermal
 * Anything that fails is null and its gauge is not shown. Call off the main thread.
 */
object HealthReader {

    private const val THERMAL = "/sys/class/thermal"

    fun read(context: Context): HealthReading = HealthReading(
        battery = runCatching { battery(context) }.getOrNull(),
        internal = runCatching { space(Environment.getExternalStorageDirectory()) }.getOrNull(),
        sd = runCatching { Storages.list(context).firstOrNull { it.removable }?.let { space(it.root) } }.getOrNull(),
        cpu = runCatching { File("/proc/stat").bufferedReader().use { it.readLine() }?.let(HealthParser::cpuLine) }.getOrNull(),
        ram = runCatching { HealthParser.memInfo(File("/proc/meminfo").readLines().take(8)) }.getOrNull(),
        cpuTempC = runCatching { cpuTemp() }.getOrNull(),
    )

    private fun battery(context: Context): Battery? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return HealthParser.battery(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false),
        )
    }

    private fun space(root: File): Space? {
        val stat = StatFs(root.path)
        return HealthParser.space(stat.totalBytes, stat.availableBytes)
    }

    private fun cpuTemp(): Double? {
        val zones = File(THERMAL).listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        val pairs = zones.mapNotNull { zone ->
            val type = runCatching { File(zone, "type").readText().trim() }.getOrNull() ?: return@mapNotNull null
            if (!type.startsWith("cpu-0-") && !type.startsWith("cpu-1-")) return@mapNotNull null
            val temp = runCatching { File(zone, "temp").readText().trim() }.getOrNull() ?: return@mapNotNull null
            type to temp
        }
        return HealthParser.hottestCpu(pairs)
    }
}
