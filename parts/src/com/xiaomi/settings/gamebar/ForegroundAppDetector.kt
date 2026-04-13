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

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Detects the currently foreground application using available APIs,
 * with a reflection-based fallback to [android.app.ActivityTaskManager].
 *
 * Converted to a Kotlin `object` — stateless utility, no instance needed.
 */
object ForegroundAppDetector {

    private const val TAG = "ForegroundAppDetector"

    /**
     * Returns the package name of the currently foreground app, or "Unknown"
     * if detection fails through all available strategies.
     *
     * Strategy order:
     * 1. [ActivityManager.getRunningTasks] (requires GET_TASKS permission)
     * 2. Reflection into [android.app.ActivityTaskManager] (internal API)
     */
    fun getForegroundPackageName(context: Context): String =
        tryGetRunningTasks(context)
            ?: tryReflectActivityTaskManager()
            ?: "Unknown"

    private fun tryGetRunningTasks(context: Context): String? = try {
        if (context.checkSelfPermission("android.permission.GET_TASKS")
            == PackageManager.PERMISSION_GRANTED
        ) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getRunningTasks(1)
                ?.firstOrNull()
                ?.topActivity
                ?.packageName
        } else {
            Log.w(TAG, "GET_TASKS permission not granted to this system app?")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "tryGetRunningTasks error: ", e)
        null
    }

    /**
     * Reflection fallback that accesses [android.app.ActivityTaskManager.getService]
     * and [IActivityTaskManager.getFocusedRootTaskInfo] via hidden APIs.
     * This is intentional for a system-privileged app — the call is guarded by a broad
     * catch so any API removal in future Android versions gracefully degrades.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun tryReflectActivityTaskManager(): String? = try {
        val atmClass = Class.forName("android.app.ActivityTaskManager")
        val getService = atmClass.getDeclaredMethod("getService").also { it.isAccessible = true }
        val atmService = getService.invoke(null) ?: return null

        @Suppress("UNCHECKED_CAST")
        val taskList = (atmService.javaClass
            .getMethod("getTasks", Int::class.java)
            .invoke(atmService, 1) as? List<*>)
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val firstTask = taskList[0] ?: return null
        val compName = firstTask.javaClass
            .getDeclaredMethod("getTopActivity")
            .invoke(firstTask)
            ?: return null

        compName.javaClass.getMethod("getPackageName").invoke(compName) as? String
    } catch (e: Exception) {
        Log.e(TAG, "tryReflectActivityTaskManager error: ", e)
        null
    }
}
