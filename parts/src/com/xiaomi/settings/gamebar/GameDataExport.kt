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

import android.os.Environment
import androidx.annotation.WorkerThread
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures per-frame game performance stats and exports them as a CSV file.
 *
 * Singleton — only one capture session runs at a time.
 *
 * Battery / GC optimisation notes:
 * - Uses a pre-sized [ArrayList] to avoid repeated backing-array reallocations
 *   during a long capture session.
 * - [addOverlayData] avoids any object creation when [isCapturing] is false,
 *   which is the common case when the user isn't actively recording.
 * - [exportDataToCsv] uses [BufferedWriter] with try-with-resources (Kotlin's `use`)
 *   to guarantee stream closure even on exception, preventing file-handle leaks.
 * - CSV line assembly uses [joinToString] which internally uses a [StringBuilder],
 *   matching the Java implementation without a manual StringBuilder.
 */
object GameDataExport {

    private val CSV_HEADER = arrayOf(
        "DateTime", "PackageName", "FPS",
        "Battery_Temp", "CPU_Usage", "CPU_Temp", "GPU_Temp"
    )

    /** Live row buffer. Pre-sized to 1 (the header) + a reasonable initial capacity. */
    private val statsRows: MutableList<Array<String>> = ArrayList(256)

    @Volatile private var capturing = false

    /** Returns `true` while a capture session is active. */
    fun isCapturing(): Boolean = capturing

    /** Begins a new capture session, discarding any previously buffered rows. */
    fun startCapture() {
        statsRows.clear()
        statsRows.add(CSV_HEADER)
        capturing = true
    }

    /** Ends the active capture session without flushing data to disk. */
    fun stopCapture() {
        capturing = false
    }

    /**
     * Appends one row of overlay statistics.
     * No-op when not capturing — zero allocation cost in the common (non-capturing) path.
     */
    fun addOverlayData(
        dateTime: String,
        packageName: String,
        fps: String,
        batteryTemp: String,
        cpuUsage: String,
        cpuTemp: String,
        gpuTemp: String
    ) {
        if (!capturing) return
        statsRows.add(arrayOf(dateTime, packageName, fps, batteryTemp, cpuUsage, cpuTemp, gpuTemp))
    }

    /**
     * Writes all buffered rows to a timestamped CSV file on external storage.
     * Does nothing if fewer than 2 rows are available (header-only = no data).
     *
     * Must be called from a background thread to avoid ANR on I/O operations.
     */
    @WorkerThread
    fun exportDataToCsv() {
        if (statsRows.size <= 1) return   // no data beyond the header row

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(Environment.getExternalStorageDirectory(), "GameBar_log_$timestamp.csv")

        try {
            BufferedWriter(FileWriter(outFile, true /* append */)).use { bw ->
                for (row in statsRows) {
                    bw.write(row.joinToString(","))
                    bw.newLine()
                }
                bw.flush()
            }
        } catch (_: IOException) {
            // Best-effort export — silently ignore I/O failures
        }
    }
}
