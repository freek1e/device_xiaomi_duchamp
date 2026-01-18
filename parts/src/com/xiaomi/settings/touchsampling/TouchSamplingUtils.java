/*
 * Copyright (C) 2024 The LineageOS Project
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

package com.xiaomi.settings.touchsampling;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import com.xiaomi.settings.utils.FileUtils;

import java.util.List;

public final class TouchSamplingUtils {
    private static final String TAG = "TouchSamplingUtils";
    public static final String HTSR_FILE = "/sys/devices/platform/goodix_ts.0/goodix_ts_report_rate";
    public static final String SCONFIG_FILE = "/sys/class/thermal/thermal_message/sconfig";

    public static void restoreSamplingValue(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                TouchSamplingSettingsFragment.SHAREDHTSR, Context.MODE_PRIVATE);
        int htsrState = sharedPref.getInt(TouchSamplingSettingsFragment.SHAREDHTSR, 0);
        FileUtils.writeLine(HTSR_FILE, Integer.toString(htsrState));
    }
    /**
     * Returns the package name of the current foreground app.
     */
    private static String getForegroundApp(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                return tasks.get(0).topActivity.getPackageName();
            }
        }
        return null;
    }
}
