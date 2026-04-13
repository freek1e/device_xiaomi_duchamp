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
import java.io.FileReader
import java.io.IOException

/**
 * Provides GPU temperature and frequency readings from sysfs for the Dimensity 9300 (Duchamp).
 * All methods are stateless and thread-safe.
 */
object GameBarGpuInfo {

    private val GPU_TEMP_PATHS = arrayOf(
        "/sys/class/thermal/thermal_zone30/temp",
        "/sys/class/thermal/thermal_zone31/temp"
    )

    private const val GPU_FREQ_PATH = "/sys/class/devfreq/13000000.mali/cur_freq"

    /**
     * Returns the average GPU temperature in °C as a formatted string, or "N/A".
     *
     * Optimisation: invalid sensor values (≤ 0 or > 120 000 millidegrees) are filtered
     * before averaging, matching the Java original's sanity check.
     */
    @WorkerThread
    fun getGpuTemp(): String {
        var total = 0f
        var count = 0

        for (path in GPU_TEMP_PATHS) {
            val raw = readLine(path)?.trim()?.toFloatOrNull() ?: continue
            if (raw <= 0f || raw > 120_000f) continue   // filter bogus sensor readings
            total += raw / 1000f
            count++
        }

        return if (count > 0) "%.1f".format(total / count) else "N/A"
    }

    /**
     * Returns the current GPU clock frequency in MHz, or "N/A".
     * Returns "0 MHz" if the GPU is idle/gated (reported frequency == 0).
     */
    @WorkerThread
    fun getGpuFreq(): String {
        val hz = readLine(GPU_FREQ_PATH)?.trim()?.toLongOrNull() ?: return "N/A"
        if (hz <= 0L) return "0 MHz"
        return "${hz / 1_000_000L} MHz"
    }

    /** Reads the first line of [path], returning null on any I/O failure. */
    private fun readLine(path: String): String? = try {
        BufferedReader(FileReader(path)).use { it.readLine() }
    } catch (_: IOException) {
        null
    }
}
