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
 * Reads RAM usage from /proc/meminfo without allocating intermediate collections.
 * All methods are stateless — safe to call from any thread.
 */
object GameBarMemInfo {

    /**
     * Returns used RAM in MB, or "N/A" on failure.
     *
     * Optimisation notes:
     * - Early-exit loop: stops reading as soon as both MemTotal and MemAvailable are found,
     *   avoiding iteration over the rest of /proc/meminfo (~40+ lines).
     * - No regex, no split() on hot path — manual indexOf parse avoids GC pressure.
     */
    @WorkerThread
    fun getRamUsage(): String {
        var memTotal = 0L
        var memAvailable = 0L

        try {
            BufferedReader(FileReader("/proc/meminfo")).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val l = line!!
                    when {
                        l.startsWith("MemTotal:") -> memTotal = parseMemValue(l)
                        l.startsWith("MemAvailable:") -> memAvailable = parseMemValue(l)
                    }
                    // Early exit once we have both values
                    if (memTotal > 0L && memAvailable > 0L) break
                }
            }
        } catch (_: IOException) {
            return "N/A"
        }

        if (memTotal == 0L) return "N/A"
        val usedMb = (memTotal - memAvailable) / 1024L
        return usedMb.toString()
    }

    /**
     * Parses the numeric kB value from a /proc/meminfo line such as "MemTotal: 7956320 kB".
     * Uses manual index-based parsing instead of split() to avoid array allocation on every call.
     */
    private fun parseMemValue(line: String): Long {
        val colon = line.indexOf(':')
        if (colon < 0) return 0L
        val trimmed = line.substring(colon + 1).trim()
        // value is everything up to the first space (before "kB")
        val spaceIdx = trimmed.indexOf(' ')
        val numStr = if (spaceIdx > 0) trimmed.substring(0, spaceIdx) else trimmed
        return numStr.toLongOrNull() ?: 0L
    }
}
