/*
 * Copyright (C) 2025 kenrow214
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xiaomi.settings.gamebar

import androidx.annotation.WorkerThread
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * Provides CPU usage, per-core frequency, and temperature readings from sysfs/procfs.
 *
 * Battery optimisation notes:
 * - `@Volatile` on the diff-state variables avoids unnecessary synchronisation overhead
 *   while still ensuring visibility across threads when the update interval fires.
 * - The thermal-zone path array is built once at class load time (lazy-style `by lazy`)
 *   and never reallocated.
 * - No intermediate collections are created for split() on the hot-path; `toLongOrNull`
 *   replaces try/catch NumberFormatException blocks, reducing exception machinery.
 */
object GameBarCpuInfo {

    /** Previous /proc/stat idle tick count — kept across calls to compute delta. */
    @Volatile private var prevIdle = -1L

    /** Previous /proc/stat total tick count. */
    @Volatile private var prevTotal = -1L

    /**
     * Thermal zone indices for the SoC's CPU clusters (zones 10–25 on Dimensity 9300).
     * Built once; immutable thereafter.
     */
    private val CPU_TEMP_PATHS: Array<String> by lazy {
        Array(16) { i -> "/sys/class/thermal/thermal_zone${10 + i}/temp" }
    }

    /**
     * Returns CPU usage percentage as a string (e.g. "42"), or "N/A" on the first call
     * (no previous sample) or on read/parse failure.
     *
     * Delta-based approach: reads /proc/stat twice across successive calls; avoids any
     * busy-polling or continuous background work — caller drives the sampling rate.
     */
    @WorkerThread
    fun getCpuUsage(): String {
        val line = readLine("/proc/stat") ?: return "N/A"
        if (!line.startsWith("cpu ")) return "N/A"

        val parts = line.split("\\s+".toRegex())
        if (parts.size < 8) return "N/A"

        val user    = parts[1].toLongOrNull() ?: return "N/A"
        val nice    = parts[2].toLongOrNull() ?: return "N/A"
        val system  = parts[3].toLongOrNull() ?: return "N/A"
        val idle    = parts[4].toLongOrNull() ?: return "N/A"
        val iowait  = parts[5].toLongOrNull() ?: return "N/A"
        val irq     = parts[6].toLongOrNull() ?: return "N/A"
        val softirq = parts[7].toLongOrNull() ?: return "N/A"
        val steal   = if (parts.size > 8) parts[8].toLongOrNull() ?: 0L else 0L

        val total = user + nice + system + idle + iowait + irq + softirq + steal

        val pTotal = prevTotal
        val pIdle  = prevIdle
        prevTotal  = total
        prevIdle   = idle

        if (pTotal == -1L || total == pTotal) return "N/A"

        val diffTotal = total - pTotal
        val diffIdle  = idle  - pIdle
        return (100L * (diffTotal - diffIdle) / diffTotal).toString()
    }

    /**
     * Returns a list of per-core frequency strings sorted by CPU index,
     * e.g. ["cpu0: 1800 MHz", "cpu1: 2400 MHz", ...].
     */
    @WorkerThread
    fun getCpuFrequencies(): List<String> {
        val cpuDir = File("/sys/devices/system/cpu/")
        val cpuFolders = cpuDir.listFiles { _, name -> name.matches(Regex("cpu\\d+")) }
            ?.sortedBy { extractCpuNumber(it) }
            ?: return emptyList()

        return cpuFolders.map { cpu ->
            val freqStr = readLine("${cpu.absolutePath}/cpufreq/scaling_cur_freq")?.trim()
            val mhz = freqStr?.toIntOrNull()?.let { it / 1000 }
            if (mhz != null) "${cpu.name}: $mhz MHz"
            else "${cpu.name}: offline or frequency not available"
        }
    }

    /**
     * Returns the average CPU temperature in °C as a formatted string, or "N/A".
     * Averages across all readable thermal zones assigned to CPU clusters.
     */
    @WorkerThread
    fun getCpuTemp(): String {
        var total = 0f
        var count = 0

        for (path in CPU_TEMP_PATHS) {
            val raw = readLine(path)?.trim()?.toFloatOrNull() ?: continue
            total += raw / 1000f   // millidegrees → Celsius
            count++
        }

        return if (count > 0) "%.1f".format(total / count) else "N/A"
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun extractCpuNumber(cpuFolder: File): Int =
        cpuFolder.name.removePrefix("cpu").toIntOrNull() ?: -1

    private fun readLine(path: String): String? = try {
        BufferedReader(FileReader(path)).use { it.readLine() }
    } catch (_: IOException) {
        null
    }
}
