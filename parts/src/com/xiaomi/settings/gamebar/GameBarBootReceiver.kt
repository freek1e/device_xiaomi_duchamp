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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

/**
 * BroadcastReceiver that restores the GameBar overlay and auto-enable monitor
 * after device boot.
 *
 * Registered for [Intent.ACTION_BOOT_COMPLETED] and
 * [Intent.ACTION_LOCKED_BOOT_COMPLETED] in the manifest so the bar can
 * resume on both encrypted-storage-available and direct-boot contexts.
 *
 * Battery note: [onReceive] executes on the main thread and must complete
 * quickly — heavy work is delegated to [GameBar] and [GameBarMonitorService].
 * No wake lock is held here; the system's BroadcastReceiver window is sufficient.
 */
class GameBarBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> restoreOverlayState(context)
        }
    }

    private fun restoreOverlayState(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val mainEnabled = prefs.getBoolean("game_bar_enable", false)
        val autoEnabled = prefs.getBoolean("game_bar_auto_enable", false)

        if (mainEnabled) {
            GameBar.getInstance(context).run {
                applyPreferences()
                show()
            }
        }

        if (autoEnabled) {
            context.startService(Intent(context, GameBarMonitorService::class.java))
        }
    }
}
