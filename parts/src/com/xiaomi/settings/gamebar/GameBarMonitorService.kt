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

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.preference.PreferenceManager

/**
 * Background service that polls the foreground app every [MONITOR_INTERVAL_MS]
 * and shows/hides the GameBar overlay accordingly.
 *
 * Battery optimisation notes:
 * - Polling interval is 2 s — a reasonable minimum that avoids excessive wakeups
 *   while still reacting quickly to app switches during active gaming sessions.
 * - The [Handler] is bound to the **main** [Looper] explicitly (not the implicit
 *   thread looper), ensuring UI operations from [GameBar] run on the correct thread.
 * - [onDestroy] removes all pending callbacks to guarantee the runnable doesn't
 *   fire after the service has been stopped, preventing phantom wakes.
 * - Consider migrating to an [android.app.usage.UsageStatsManager]-based approach
 *   or a system-level callback in a future iteration to eliminate polling entirely.
 */
class GameBarMonitorService : Service() {

    companion object {
        private const val MONITOR_INTERVAL_MS = 2_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val monitorRunnable = object : Runnable {
        override fun run() {
            monitorForegroundApp()
            handler.postDelayed(this, MONITOR_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(monitorRunnable)
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Critical: remove all pending callbacks so the service truly stops ticking.
        handler.removeCallbacksAndMessages(null)
    }

    @androidx.annotation.MainThread
    private fun monitorForegroundApp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val gameBar = GameBar.getInstance(this)

        // Master switch overrides auto-enable: always show if master is on.
        if (prefs.getBoolean("game_bar_enable", false)) {
            gameBar.applyPreferences()
            gameBar.show()
            return
        }

        if (!prefs.getBoolean("game_bar_auto_enable", false)) {
            gameBar.hide()
            return
        }

        val foreground = ForegroundAppDetector.getForegroundPackageName(this)
        val autoApps   = prefs.getStringSet(
            GameBarAppSelectorFragment.PREF_AUTO_APPS, emptySet()
        ) ?: emptySet()

        if (foreground in autoApps) {
            gameBar.applyPreferences()
            gameBar.show()
        } else {
            gameBar.hide()
        }
    }
}
