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

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.window.TaskFpsCallback
import androidx.annotation.MainThread
import androidx.preference.PreferenceManager
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.lang.reflect.Field

/**
 * Measures the current frame rate using either the modern [TaskFpsCallback]
 * API (Android 13+) or a legacy sysfs file fallback.
 *
 * **Singleton** — obtain via [getInstance]. The single instance is tied to the
 * application context to prevent [Context] leaks.
 *
 * Battery optimisation notes:
 * - The [TaskFpsCallback] is unregistered the moment [stop] is called (e.g. when
 *   the overlay is hidden), preventing the SurfaceFlinger callback from ticking
 *   while the bar is invisible.
 * - The task-change poller ([taskCheckRunnable]) only runs while the callback is
 *   active and reschedules itself at [TASK_CHECK_INTERVAL_MS] — it never fires
 *   outside an active session.
 * - [reinitCallback] has a 500 ms debounce via [Handler.postDelayed] to avoid
 *   rapid re-registration during app transitions.
 * - [mCurrentFps] is annotated [@Volatile] so the update from the
 *   SurfaceFlinger callback thread is immediately visible on the UI thread without
 *   a lock.
 */
class GameBarFpsMeter private constructor(context: Context) {

    companion object {
        private const val STALENESS_THRESHOLD_MS = 2_000L
        private const val TASK_CHECK_INTERVAL_MS = 1_000L

        @Volatile private var instance: GameBarFpsMeter? = null

        fun getInstance(context: Context): GameBarFpsMeter =
            instance ?: synchronized(this) {
                instance ?: GameBarFpsMeter(context.applicationContext).also { instance = it }
            }
    }

    private val appContext: Context = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(appContext)
    private val handler = Handler(Looper.getMainLooper())

    /**
     * The most recently reported FPS. Written from the SurfaceFlinger callback
     * (potentially a non-main thread) and read from the UI update runnable on the
     * main thread — [Volatile] provides the necessary visibility guarantee without
     * a full lock.
     */
    @Volatile private var currentFps: Float = 0f

    private var callbackRegistered = false
    private var currentTaskId      = -1
    private var lastFpsUpdateTime  = System.currentTimeMillis()

    // Created once on API 33+ to avoid repeated class-loading overhead.
    // TaskFpsCallback is an abstract class — must use an anonymous object, not a lambda.
    private val taskFpsCallback: TaskFpsCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            object : TaskFpsCallback() {
                override fun onFpsReported(fps: Float) {
                    if (fps > 0f) {
                        currentFps        = fps
                        lastFpsUpdateTime = System.currentTimeMillis()
                    }
                }
            }
        } else null

    // ── Periodic task-switch check ────────────────────────────────────────────

    private val taskCheckRunnable = object : Runnable {
        override fun run() {
            val newTaskId = getFocusedTaskId()
            if (newTaskId > 0 && newTaskId != currentTaskId) {
                reinitCallback()
            } else if (System.currentTimeMillis() - lastFpsUpdateTime > STALENESS_THRESHOLD_MS) {
                // Callback went stale (e.g. task destroyed without notification) — re-init.
                reinitCallback()
            } else {
                handler.postDelayed(this, TASK_CHECK_INTERVAL_MS)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @MainThread
    fun start() {
        if (prefs.getString("game_bar_fps_method", "new") != "new") return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        stop()   // ensure clean state

        val taskId = getFocusedTaskId()
        if (taskId <= 0) return

        currentTaskId = taskId
        runCatching {
            windowManager.registerTaskFpsCallback(currentTaskId, Runnable::run, taskFpsCallback!!)
            callbackRegistered = true
        }
        lastFpsUpdateTime = System.currentTimeMillis()
        handler.postDelayed(taskCheckRunnable, TASK_CHECK_INTERVAL_MS)
    }

    @MainThread
    fun stop() {
        if (prefs.getString("game_bar_fps_method", "new") != "new") return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (callbackRegistered) {
            runCatching { windowManager.unregisterTaskFpsCallback(taskFpsCallback!!) }
            callbackRegistered = false
        }
        handler.removeCallbacks(taskCheckRunnable)
    }

    /**
     * Returns the current FPS value. Uses the legacy sysfs path for the "legacy"
     * pref value, or [currentFps] from the callback otherwise.
     */
    fun getFps(): Float = when (prefs.getString("game_bar_fps_method", "new")) {
        "legacy" -> readLegacyFps()
        else     -> currentFps
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun readLegacyFps(): Float = try {
        BufferedReader(FileReader("/sys/class/drm/sde-crtc-0/measured_fps")).use { br ->
            val line = br.readLine() ?: return -1f
            if (!line.startsWith("fps:")) return -1f
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 2) parts[1].trim().toFloatOrNull() ?: -1f else -1f
        }
    } catch (_: IOException) {
        -1f
    } catch (_: NumberFormatException) {
        -1f
    }

    /**
     * Obtains the task ID of the currently focused root task via hidden API reflection.
     * Returns -1 on API < 33 or on any reflection failure.
     */
    private fun getFocusedTaskId(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return -1
        return runCatching {
            val atmClass    = Class.forName("android.app.ActivityTaskManager")
            val getService  = atmClass.getDeclaredMethod("getService")
            val atmService  = getService.invoke(null) ?: return -1
            val taskInfo    = atmService.javaClass
                .getMethod("getFocusedRootTaskInfo")
                .invoke(atmService)
                ?: return -1
            // Try both field names used across AOSP versions
            taskInfo.javaClass.getFieldOrNull("taskId")?.getInt(taskInfo)
                ?: taskInfo.javaClass.getFieldOrNull("mTaskId")?.getInt(taskInfo)
                ?: -1
        }.getOrDefault(-1)
    }

    /** Helper: returns the [Field] or null without throwing [NoSuchFieldException]. */
    private fun Class<*>.getFieldOrNull(name: String): Field? = runCatching {
        getField(name)
    }.getOrNull()

    /** Stops the current callback and restarts after a 500 ms debounce. */
    @MainThread
    private fun reinitCallback() {
        stop()
        handler.postDelayed({ start() }, 500L)
    }
}
