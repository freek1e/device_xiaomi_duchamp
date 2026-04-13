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

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.xiaomi.settings.R

/**
 * Settings UI fragment for the GameBar overlay.
 *
 * Preference change listeners are registered once in [onCreatePreferences] using
 * Kotlin lambda syntax. Each listener is already scoped to this Fragment's
 * lifecycle by the PreferenceFragmentCompat machinery — no manual unregistration
 * is needed because listeners are attached to Preference objects that live in the
 * PreferenceScreen, which is destroyed with the fragment.
 *
 * Battery note: [onResume] starts or stops [GameBarMonitorService] to match the
 * current preference state, ensuring the service doesn't run when neither the
 * master switch nor the auto-enable switch is on.
 */
class GameBarFragment : PreferenceFragmentCompat() {

    private lateinit var gameBar: GameBar

    // ── Preference references ─────────────────────────────────────────────────

    private var masterSwitch: MainSwitchPreference?         by prefDelegate()
    private var autoEnableSwitch: SwitchPreferenceCompat?   by prefDelegate()
    private var fpsSwitch: SwitchPreferenceCompat?          by prefDelegate()
    private var batteryTempSwitch: SwitchPreferenceCompat?  by prefDelegate()
    private var cpuUsageSwitch: SwitchPreferenceCompat?     by prefDelegate()
    private var cpuClockSwitch: SwitchPreferenceCompat?     by prefDelegate()
    private var cpuTempSwitch: SwitchPreferenceCompat?      by prefDelegate()
    private var ramSwitch: SwitchPreferenceCompat?          by prefDelegate()
    private var gpuTempSwitch: SwitchPreferenceCompat?      by prefDelegate()
    private var gpuClockSwitch: SwitchPreferenceCompat?     by prefDelegate()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.game_bar_preferences, rootKey)

        gameBar = GameBar.getInstance(requireContext())

        // Find preferences
        masterSwitch       = findPreference("game_bar_enable")
        autoEnableSwitch   = findPreference("game_bar_auto_enable")
        fpsSwitch          = findPreference("game_bar_fps_enable")
        batteryTempSwitch  = findPreference("game_bar_temp_enable")
        cpuUsageSwitch     = findPreference("game_bar_cpu_usage_enable")
        cpuClockSwitch     = findPreference("game_bar_cpu_clock_enable")
        cpuTempSwitch      = findPreference("game_bar_cpu_temp_enable")
        ramSwitch          = findPreference("game_bar_ram_enable")
        gpuTempSwitch      = findPreference("game_bar_gpu_temp_enable")
        gpuClockSwitch     = findPreference("game_bar_gpu_clock_enable")

        // Navigation preferences
        findPreference<Preference>("game_bar_app_selector")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), GameBarAppSelectorActivity::class.java))
            true
        }
        findPreference<Preference>("game_bar_app_remover")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), GameBarAppRemoverActivity::class.java))
            true
        }

        // Capture controls
        findPreference<Preference>("game_bar_capture_start")?.setOnPreferenceClickListener {
            GameDataExport.startCapture()
            Toast.makeText(requireContext(), R.string.game_bar_logging_started_toast, Toast.LENGTH_SHORT).show()
            true
        }
        findPreference<Preference>("game_bar_capture_stop")?.setOnPreferenceClickListener {
            GameDataExport.stopCapture()
            Toast.makeText(requireContext(), R.string.game_bar_logging_stopped_toast, Toast.LENGTH_SHORT).show()
            true
        }
        findPreference<Preference>("game_bar_capture_export")?.setOnPreferenceClickListener {
            GameDataExport.exportDataToCsv()
            Toast.makeText(requireContext(), R.string.game_bar_logging_exported_toast, Toast.LENGTH_SHORT).show()
            true
        }

        // Master switch
        masterSwitch?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            if (enabled) {
                if (Settings.canDrawOverlays(requireContext())) {
                    gameBar.applyPreferences()
                    gameBar.show()
                    requireContext().startService(
                        Intent(requireContext(), GameBarMonitorService::class.java)
                    )
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.overlay_permission_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnPreferenceChangeListener false
                }
            } else {
                gameBar.hide()
                if (autoEnableSwitch?.isChecked != true) {
                    requireContext().stopService(
                        Intent(requireContext(), GameBarMonitorService::class.java)
                    )
                }
            }
            true
        }

        // Auto-enable switch
        autoEnableSwitch?.setOnPreferenceChangeListener { _, newValue ->
            val autoEnabled = newValue as Boolean
            if (autoEnabled) {
                requireContext().startService(
                    Intent(requireContext(), GameBarMonitorService::class.java)
                )
            } else {
                if (masterSwitch?.isChecked != true) {
                    requireContext().stopService(
                        Intent(requireContext(), GameBarMonitorService::class.java)
                    )
                }
            }
            true
        }

        // Stat visibility toggles
        fpsSwitch?.setOnPreferenceChangeListener  { _, v -> gameBar.setShowFps(v as Boolean); true }
        batteryTempSwitch?.setOnPreferenceChangeListener { _, v -> gameBar.setShowBatteryTemp(v as Boolean); true }
        cpuUsageSwitch?.setOnPreferenceChangeListener    { _, v -> gameBar.setShowCpuUsage(v as Boolean); true }
        cpuClockSwitch?.setOnPreferenceChangeListener    { _, v -> gameBar.setShowCpuClock(v as Boolean); true }
        cpuTempSwitch?.setOnPreferenceChangeListener     { _, v -> gameBar.setShowCpuTemp(v as Boolean); true }
        ramSwitch?.setOnPreferenceChangeListener         { _, v -> gameBar.setShowRam(v as Boolean); true }
        gpuTempSwitch?.setOnPreferenceChangeListener     { _, v -> gameBar.setShowGpuTemp(v as Boolean); true }
        gpuClockSwitch?.setOnPreferenceChangeListener    { _, v -> gameBar.setShowGpuClock(v as Boolean); true }

        // Gesture preferences
        findPreference<SwitchPreferenceCompat>("game_bar_doubletap_capture")
            ?.setOnPreferenceChangeListener { _, v -> gameBar.setDoubleTapCaptureEnabled(v as Boolean); true }
        findPreference<SwitchPreferenceCompat>("game_bar_single_tap_toggle")
            ?.setOnPreferenceChangeListener { _, v -> gameBar.setSingleTapToggleEnabled(v as Boolean); true }
        findPreference<SwitchPreferenceCompat>("game_bar_longpress_enable")
            ?.setOnPreferenceChangeListener { _, v -> gameBar.setLongPressEnabled(v as Boolean); true }
        findPreference<ListPreference>("game_bar_longpress_timeout")
            ?.setOnPreferenceChangeListener { _, v ->
                (v as? String)?.toLongOrNull()?.let { gameBar.setLongPressThresholdMs(it) }
                true
            }

        // Appearance preferences
        findPreference<SeekBarPreference>("game_bar_text_size")
            ?.setOnPreferenceChangeListener { _, v -> (v as? Int)?.let { gameBar.updateTextSize(it) }; true }
        findPreference<SeekBarPreference>("game_bar_background_alpha")
            ?.setOnPreferenceChangeListener { _, v -> (v as? Int)?.let { gameBar.updateBackgroundAlpha(it) }; true }
        findPreference<SeekBarPreference>("game_bar_corner_radius")
            ?.setOnPreferenceChangeListener { _, v -> (v as? Int)?.let { gameBar.updateCornerRadius(it) }; true }
        findPreference<SeekBarPreference>("game_bar_padding")
            ?.setOnPreferenceChangeListener { _, v -> (v as? Int)?.let { gameBar.updatePadding(it) }; true }
        findPreference<SeekBarPreference>("game_bar_item_spacing")
            ?.setOnPreferenceChangeListener { _, v -> (v as? Int)?.let { gameBar.updateItemSpacing(it) }; true }

        // Color / layout preferences
        findPreference<ListPreference>("game_bar_text_color")
            ?.setOnPreferenceChangeListener { _, _ -> true }   // handled by GameBar.applyPreferences()
        findPreference<ListPreference>("game_bar_title_color")
            ?.setOnPreferenceChangeListener { _, v -> (v as? String)?.let { gameBar.updateTitleColor(it) }; true }
        findPreference<ListPreference>("game_bar_value_color")
            ?.setOnPreferenceChangeListener { _, v -> (v as? String)?.let { gameBar.updateValueColor(it) }; true }
        findPreference<ListPreference>("game_bar_position")
            ?.setOnPreferenceChangeListener { _, v -> (v as? String)?.let { gameBar.updatePosition(it) }; true }
        findPreference<ListPreference>("game_bar_split_mode")
            ?.setOnPreferenceChangeListener { _, v -> (v as? String)?.let { gameBar.updateSplitMode(it) }; true }
        findPreference<ListPreference>("game_bar_format")
            ?.setOnPreferenceChangeListener { _, v -> (v as? String)?.let { gameBar.updateOverlayFormat(it) }; true }
        findPreference<ListPreference>("game_bar_update_interval")
            ?.setOnPreferenceChangeListener { _, v -> (v as? String)?.let { gameBar.updateUpdateInterval(it) }; true }
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsageStatsPermission(requireContext())) {
            requestUsageStatsPermission()
        }

        // Sync monitor service state with current switch values.
        val needMonitor = masterSwitch?.isChecked == true || autoEnableSwitch?.isChecked == true
        val intent = Intent(requireContext(), GameBarMonitorService::class.java)
        if (needMonitor) {
            requireContext().startService(intent)
        } else {
            requireContext().stopService(intent)
        }
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Trivial delegate that allows declaring nullable preference vars without
     * requiring an explicit backing field type annotation on every declaration.
     * The actual stored value is just a simple nullable reference.
     */
    private fun <T> prefDelegate() = object : kotlin.properties.ReadWriteProperty<Any?, T?> {
        private var value: T? = null
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T? = value
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T?) { this.value = value }
    }
}
