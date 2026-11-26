package dev.catzeyea.thorbro.android.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers and mood rules, with values captured on a real Thor (same as the PC app's tests). */
class HealthTest {

    @Test
    fun `battery charging is status 2 only`() {
        val plugged = HealthParser.battery(level = 80, scale = 100, status = 3, present = true)!!
        assertEquals(80, plugged.percent)
        assertFalse(plugged.charging) // the Thor reports 3 while plugged in at its charge limit
        assertTrue(HealthParser.battery(50, 100, 2, true)!!.charging)
        assertNull(HealthParser.battery(50, 100, 2, present = false))
        assertNull(HealthParser.battery(150, 100, 2, true))
        assertEquals(50, HealthParser.battery(1, 2, 3, true)!!.percent)
    }

    @Test
    fun `proc stat, meminfo and thermal zones from a real Thor`() {
        val cpu = HealthParser.cpuLine("cpu  258585 18064 259019 14266376 2633 51842 7257 734 0 0")!!
        assertEquals(258585L + 18064 + 259019 + 14266376 + 2633 + 51842 + 7257 + 734, cpu.total)
        assertEquals(cpu.total - 14266376 - 2633, cpu.busy)
        assertNull(HealthParser.cpuLine("cpu0 1 2 3 4 5"))

        val ram = HealthParser.memInfo(listOf("MemTotal:       15621188 kB", "MemFree:         1954916 kB", "MemAvailable:   10717172 kB"))!!
        assertEquals(15621188L * 1024, ram.total)
        assertEquals(31, ram.percent)

        val temp = HealthParser.hottestCpu(listOf("cpu-1-0" to "30700", "cpu-0-1" to "35600", "gpuss-0" to "90000", "cpu-0-2" to "999999"))
        assertEquals(35.6, temp!!, 0.001)
        assertNull(HealthParser.hottestCpu(listOf("battery" to "22000")))
    }

    @Test
    fun `cpu load needs two fitting readings`() {
        assertEquals(25, HealthParser.cpuPercent(CpuTicks(100, 1000), CpuTicks(150, 1200)))
        assertNull(HealthParser.cpuPercent(null, CpuTicks(1, 2)))
        assertNull(HealthParser.cpuPercent(CpuTicks(100, 1000), CpuTicks(100, 1000)))
    }

    private val fine = HealthReading(
        battery = Battery(80, 3),
        internal = Space(499_000_000_000, 295_000_000_000),
        sd = Space(127_000_000_000, 25_000_000_000),
        ram = Ram(16_000_000_000, 10_000_000_000),
        cpuTempC = 35.6,
    )

    @Test
    fun `mood priority asleep, busy, tired, stuffed, fit`() {
        assertEquals(MoodKey.Asleep, Moods.moodFor(false, fine, 10).key)
        assertEquals(MoodKey.Fit, Moods.moodFor(null, fine, 10).key) // USB state unknown: never asleep
        assertEquals(MoodKey.Busy, Moods.moodFor(true, fine, 85).key)
        assertEquals(MoodKey.Busy, Moods.moodFor(true, fine.copy(cpuTempC = 71.0), 10).key)
        assertEquals(MoodKey.Tired, Moods.moodFor(true, fine.copy(battery = Battery(15, 3)), 10).key)
        assertEquals(MoodKey.Fit, Moods.moodFor(true, fine.copy(battery = Battery(15, 2)), 10).key) // charging
        val stuffed = Moods.moodFor(true, fine.copy(internal = Space(100, 5), sd = Space(100, 50)), 10)
        assertEquals(MoodKey.Stuffed, stuffed.key)
        assertTrue(stuffed.line.contains("SD card"))
        val fit = Moods.moodFor(true, fine, 10)
        assertEquals("I feel great!", fit.head)
        assertEquals("Plenty of battery, plenty of room. Nothing heavy running.", fit.line)
        assertEquals("All good here.", Moods.moodFor(true, HealthReading(), null).line)
    }

    @Test
    fun `RAM replaces the SD column without an SD card, unreadable values are dropped`() {
        assertEquals(listOf(Gauge.Battery, Gauge.Internal, Gauge.Sd, Gauge.Cpu, Gauge.Temp), Moods.columns(fine, 10))
        assertEquals(listOf(Gauge.Battery, Gauge.Internal, Gauge.Cpu, Gauge.Ram, Gauge.Temp), Moods.columns(fine.copy(sd = null), 10))
        assertEquals(listOf(Gauge.Battery, Gauge.Internal, Gauge.Sd, Gauge.Temp), Moods.columns(fine, null))
        assertEquals(emptyList<Gauge>(), Moods.columns(HealthReading(), null))
    }
}
