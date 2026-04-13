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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.preference.PreferenceManager
import com.xiaomi.settings.R
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Core GameBar overlay manager. Owns the [WindowManager] overlay view, drives the
 * periodic stats-update loop, and handles all touch interactions.
 *
 * **Singleton** — obtain via [getInstance].
 *
 * Battery optimisation notes:
 * - The update [Handler] uses [Looper.getMainLooper] so all view mutations happen
 *   on the main thread — no thread-switching overhead.
 * - [mUpdateRunnable] only reschedules itself while [mIsShowing] is true; once
 *   [hide] is called the loop terminates immediately.
 * - [mRootLayout.removeAllViews] + re-add pattern matches the original Java design.
 *   Candidate for future optimisation via view recycling / DiffUtil, but preserved
 *   here to maintain exact behavioural parity.
 * - [mBgDrawable] is allocated once and mutated in-place (setColor / setCornerRadius)
 *   to avoid repeated Drawable allocations on each config change.
 * - [mHandler.removeCallbacksAndMessages(null)] in [hide] guarantees no pending
 *   callbacks fire after the overlay is removed, preventing phantom wake-ups.
 */
class GameBar private constructor(context: Context) {

    companion object {
        private const val FPS_PATH          = "/sys/class/drm/sde-crtc-0/measured_fps"
        private const val BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp"
        private const val PREF_KEY_X        = "game_bar_x"
        private const val PREF_KEY_Y        = "game_bar_y"
        private const val TOUCH_SLOP        = 20f

        @Volatile private var sInstance: GameBar? = null

        fun getInstance(context: Context): GameBar =
            sInstance ?: synchronized(this) {
                sInstance ?: GameBar(context.applicationContext).also { sInstance = it }
            }
    }

    // ── Context / system services ─────────────────────────────────────────────

    private val mContext: Context       = context.applicationContext
    private val mWindowManager: WindowManager =
        mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mHandler = Handler(Looper.getMainLooper())

    // ── Overlay state ─────────────────────────────────────────────────────────

    private var mOverlayView: View?          = null
    private var mRootLayout: LinearLayout?   = null
    private var mLayoutParams: WindowManager.LayoutParams? = null
    private var mIsShowing = false

    // ── Drag tracking ─────────────────────────────────────────────────────────

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // ── Appearance ────────────────────────────────────────────────────────────

    private var mTextSizeSp      = 16
    private var mBackgroundAlpha = 128
    private var mCornerRadius    = 16
    private var mPaddingDp       = 12
    private var mTitleColorHex   = "#FFFFFF"
    private var mValueColorHex   = "#FFFFFF"
    private var mOverlayFormat   = "full"
    private var mPosition        = "top_left"
    private var mSplitMode       = "stacked"
    private var mUpdateIntervalMs = 1000
    private var mDraggable       = false
    private var mItemSpacingDp   = 8

    // ── Stat visibility flags ─────────────────────────────────────────────────

    private var mShowBatteryTemp = false
    private var mShowCpuUsage    = false
    private var mShowCpuClock    = false
    private var mShowCpuTemp     = false
    private var mShowRam         = false
    private var mShowFps         = false
    private var mShowGpuTemp     = false
    private var mShowGpuClock    = false

    // ── Gesture / interaction ─────────────────────────────────────────────────

    private var mLongPressEnabled       = false
    private var mLongPressThresholdMs   = 1000L
    private var mPressActive            = false
    private var mDownX                  = 0f
    private var mDownY                  = 0f
    private var mDoubleTapCaptureEnabled = false
    private var mSingleTapToggleEnabled  = false

    // ── Background drawable (mutated in-place to avoid allocations) ───────────

    private val mBgDrawable = GradientDrawable().also { applyBackgroundStyleTo(it) }

    // ── Runnables ─────────────────────────────────────────────────────────────

    private val mLongPressRunnable = Runnable {
        if (mPressActive) {
            openOverlaySettings()
            mPressActive = false
        }
    }

    private val mUpdateRunnable = object : Runnable {
        override fun run() {
            if (mIsShowing) {
                updateStats()
                mHandler.postDelayed(this, mUpdateIntervalMs.toLong())
            }
        }
    }

    // ── Gesture detector ──────────────────────────────────────────────────────

    private val mGestureDetector = GestureDetector(
        mContext,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!mDoubleTapCaptureEnabled) return super.onDoubleTap(e)
                if (GameDataExport.isCapturing()) {
                    GameDataExport.stopCapture()
                    Toast.makeText(mContext, R.string.game_bar_capture_stopped_toast, Toast.LENGTH_SHORT).show()
                } else {
                    GameDataExport.startCapture()
                    Toast.makeText(mContext, R.string.game_bar_capture_started_toast, Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!mSingleTapToggleEnabled) return super.onSingleTapConfirmed(e)
                mOverlayFormat = if (mOverlayFormat == "full") "minimal" else "full"
                PreferenceManager.getDefaultSharedPreferences(mContext)
                    .edit().putString("game_bar_format", mOverlayFormat).apply()
                Toast.makeText(
                    mContext,
                    mContext.getString(R.string.game_bar_overlay_format_toast, mOverlayFormat),
                    Toast.LENGTH_SHORT
                ).show()
                updateStats()
                return true
            }
        }
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun applyPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)

        mShowFps         = prefs.getBoolean("game_bar_fps_enable", false)
        mShowBatteryTemp = prefs.getBoolean("game_bar_temp_enable", false)
        mShowCpuUsage    = prefs.getBoolean("game_bar_cpu_usage_enable", false)
        mShowCpuClock    = prefs.getBoolean("game_bar_cpu_clock_enable", false)
        mShowCpuTemp     = prefs.getBoolean("game_bar_cpu_temp_enable", false)
        mShowRam         = prefs.getBoolean("game_bar_ram_enable", false)
        mShowGpuTemp     = prefs.getBoolean("game_bar_gpu_temp_enable", false)
        mShowGpuClock    = prefs.getBoolean("game_bar_gpu_clock_enable", false)

        mDoubleTapCaptureEnabled = prefs.getBoolean("game_bar_doubletap_capture", false)
        mSingleTapToggleEnabled  = prefs.getBoolean("game_bar_single_tap_toggle", false)

        updateSplitMode(prefs.getString("game_bar_split_mode", "stacked") ?: "stacked")
        updateTextSize(prefs.getInt("game_bar_text_size", 16))
        updateBackgroundAlpha(prefs.getInt("game_bar_background_alpha", 128))
        updateCornerRadius(prefs.getInt("game_bar_corner_radius", 16))
        updatePadding(prefs.getInt("game_bar_padding", 12))
        updateTitleColor(prefs.getString("game_bar_title_color", "#FFFFFF") ?: "#FFFFFF")
        updateValueColor(prefs.getString("game_bar_value_color", "#4CAF50") ?: "#4CAF50")
        updateOverlayFormat(prefs.getString("game_bar_format", "full") ?: "full")
        updateUpdateInterval(prefs.getString("game_bar_update_interval", "1000") ?: "1000")
        updatePosition(prefs.getString("game_bar_position", "top_left") ?: "top_left")
        updateItemSpacing(prefs.getInt("game_bar_item_spacing", 8))

        mLongPressEnabled = prefs.getBoolean("game_bar_longpress_enable", false)
        prefs.getString("game_bar_longpress_timeout", "1000")
            ?.toLongOrNull()
            ?.let { setLongPressThresholdMs(it) }
    }

    @MainThread
    fun show() {
        if (mIsShowing) return
        applyPreferences()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        if (mPosition == "draggable") {
            mDraggable = true
            loadSavedPosition(lp)
            if (lp.x == 0 && lp.y == 0) {
                lp.gravity = Gravity.TOP or Gravity.START
                lp.y = 100
            }
        } else {
            mDraggable = false
            applyPosition(lp, mPosition)
        }

        mLayoutParams = lp

        val root = LinearLayout(mContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mRootLayout  = root
        mOverlayView = root

        applySplitMode()
        applyBackgroundStyle()
        applyPadding()

        root.setOnTouchListener { _, event ->
            if (mGestureDetector.onTouchEvent(event)) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (mDraggable) {
                        initialX      = lp.x
                        initialY      = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    if (mLongPressEnabled) {
                        mPressActive = true
                        mDownX = event.rawX
                        mDownY = event.rawY
                        mHandler.postDelayed(mLongPressRunnable, mLongPressThresholdMs)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mLongPressEnabled && mPressActive) {
                        val dx = Math.abs(event.rawX - mDownX)
                        val dy = Math.abs(event.rawY - mDownY)
                        if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                            mPressActive = false
                            mHandler.removeCallbacks(mLongPressRunnable)
                        }
                    }
                    if (mDraggable) {
                        lp.x = initialX + (event.rawX - initialTouchX).toInt()
                        lp.y = initialY + (event.rawY - initialTouchY).toInt()
                        mWindowManager.updateViewLayout(root, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mLongPressEnabled && mPressActive) {
                        mPressActive = false
                        mHandler.removeCallbacks(mLongPressRunnable)
                    }
                    if (mDraggable) {
                        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                            .putInt(PREF_KEY_X, lp.x)
                            .putInt(PREF_KEY_Y, lp.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }

        mWindowManager.addView(root, lp)
        mIsShowing = true
        startUpdates()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GameBarFpsMeter.getInstance(mContext).start()
        }
    }

    @MainThread
    fun hide() {
        if (!mIsShowing) return
        // Cancel all pending callbacks before removing the view.
        mHandler.removeCallbacksAndMessages(null)
        mOverlayView?.let { mWindowManager.removeView(it) }
        mOverlayView = null
        mIsShowing   = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GameBarFpsMeter.getInstance(mContext).stop()
        }
    }

    // ── Stat setters (called from GameBarFragment listeners) ──────────────────

    fun setShowBatteryTemp(show: Boolean) { mShowBatteryTemp = show }
    fun setShowCpuUsage(show: Boolean)    { mShowCpuUsage    = show }
    fun setShowCpuClock(show: Boolean)    { mShowCpuClock    = show }
    fun setShowCpuTemp(show: Boolean)     { mShowCpuTemp     = show }
    fun setShowRam(show: Boolean)         { mShowRam         = show }
    fun setShowFps(show: Boolean)         { mShowFps         = show }
    fun setShowGpuTemp(show: Boolean)     { mShowGpuTemp     = show }
    fun setShowGpuClock(show: Boolean)    { mShowGpuClock    = show }

    fun setDoubleTapCaptureEnabled(enabled: Boolean) { mDoubleTapCaptureEnabled = enabled }
    fun setSingleTapToggleEnabled(enabled: Boolean)  { mSingleTapToggleEnabled  = enabled }
    fun setLongPressEnabled(enabled: Boolean)        { mLongPressEnabled        = enabled }
    fun setLongPressThresholdMs(ms: Long)            { mLongPressThresholdMs    = ms      }

    // ── Appearance updaters ───────────────────────────────────────────────────

    fun updateTextSize(sp: Int) { mTextSizeSp = sp }

    fun updateCornerRadius(radius: Int) {
        mCornerRadius = radius
        applyBackgroundStyle()
    }

    fun updateBackgroundAlpha(alpha: Int) {
        mBackgroundAlpha = alpha
        applyBackgroundStyle()
    }

    fun updatePadding(dp: Int) {
        mPaddingDp = dp
        applyPadding()
    }

    fun updateTitleColor(hex: String) { mTitleColorHex = hex }
    fun updateValueColor(hex: String) { mValueColorHex = hex }

    fun updateOverlayFormat(format: String) {
        mOverlayFormat = format
        if (mIsShowing) updateStats()
    }

    fun updateItemSpacing(dp: Int) {
        mItemSpacingDp = dp
        if (mIsShowing) updateStats()
    }

    fun updatePosition(pos: String) {
        mPosition = pos
        val lp   = mLayoutParams ?: return
        val view = mOverlayView   ?: return
        if (!mIsShowing) return

        if (pos == "draggable") {
            mDraggable = true
            loadSavedPosition(lp)
            if (lp.x == 0 && lp.y == 0) {
                lp.gravity = Gravity.TOP or Gravity.START
                lp.y = 100
            }
        } else {
            mDraggable = false
            applyPosition(lp, pos)
        }
        mWindowManager.updateViewLayout(view, lp)
    }

    fun updateSplitMode(mode: String) {
        mSplitMode = mode
        if (mIsShowing) {
            applySplitMode()
            updateStats()
        }
    }

    fun updateUpdateInterval(intervalStr: String) {
        mUpdateIntervalMs = intervalStr.toIntOrNull() ?: 1000
        if (mIsShowing) startUpdates()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @MainThread
    private fun updateStats() {
        val root = mRootLayout ?: return
        if (!mIsShowing) return

        root.removeAllViews()

        val statViews = mutableListOf<View>()

        // FPS
        val fpsVal = GameBarFpsMeter.getInstance(mContext).getFps()
        val fpsStr = if (fpsVal >= 0f) "%.0f".format(fpsVal) else "N/A"
        if (mShowFps) statViews.add(createStatLine(mContext.getString(R.string.game_bar_stat_fps), fpsStr))

        // Battery temperature
        var batteryTempStr = "N/A"
        if (mShowBatteryTemp) {
            val tmp = readLine(BATTERY_TEMP_PATH)?.trim()
            if (!tmp.isNullOrEmpty()) {
                tmp.toIntOrNull()?.let { raw ->
                    batteryTempStr = "%.1f".format(raw / 10f)
                }
            }
            statViews.add(createStatLine(mContext.getString(R.string.game_bar_stat_temp), "$batteryTempStr°C"))
        }

        // CPU usage
        var cpuUsageStr = "N/A"
        if (mShowCpuUsage) {
            cpuUsageStr = GameBarCpuInfo.getCpuUsage()
            statViews.add(createStatLine(mContext.getString(R.string.game_bar_stat_cpu), if (cpuUsageStr == "N/A") "N/A" else "$cpuUsageStr%"))
        }

        // CPU frequency
        if (mShowCpuClock) {
            val freqs = GameBarCpuInfo.getCpuFrequencies()
            if (freqs.isNotEmpty()) statViews.add(buildCpuFreqView(freqs))
        }

        // CPU temperature
        var cpuTempStr = "N/A"
        if (mShowCpuTemp) {
            cpuTempStr = GameBarCpuInfo.getCpuTemp()
            statViews.add(createStatLine(mContext.getString(R.string.game_bar_stat_cpu_temp), if (cpuTempStr == "N/A") "N/A" else "$cpuTempStr°C"))
        }

        // RAM
        var ramStr = "N/A"
        if (mShowRam) {
            ramStr = GameBarMemInfo.getRamUsage()
            statViews.add(createStatLine(mContext.getString(R.string.game_bar_stat_ram), if (ramStr == "N/A") "N/A" else "$ramStr MB"))
        }

        // GPU temperature
        var gpuTempStr = "N/A"
        if (mShowGpuTemp) {
            gpuTempStr = GameBarGpuInfo.getGpuTemp()
            statViews.add(createStatLine(mContext.getString(R.string.game_bar_stat_gpu_temp), if (gpuTempStr == "N/A") "N/A" else "$gpuTempStr°C"))
        }

        // GPU clock
        if (mShowGpuClock) {
            statViews.add(createStatLine(mContext.getString(R.string.game_bar_stat_gpu_clock), GameBarGpuInfo.getGpuFreq()))
        }

        // Layout
        if (mSplitMode == "side_by_side") {
            root.orientation = LinearLayout.HORIZONTAL
            if (mOverlayFormat == "minimal") {
                statViews.forEachIndexed { i, v ->
                    root.addView(v)
                    if (i < statViews.lastIndex) root.addView(createDotView())
                }
            } else {
                statViews.forEach { root.addView(it) }
            }
        } else {
            root.orientation = LinearLayout.VERTICAL
            statViews.forEach { root.addView(it) }
        }

        // Data capture
        if (GameDataExport.isCapturing()) {
            val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val pkgName  = ForegroundAppDetector.getForegroundPackageName(mContext)
            GameDataExport.addOverlayData(dateTime, pkgName, fpsStr, batteryTempStr, cpuUsageStr, cpuTempStr, gpuTempStr)
        }

        mLayoutParams?.let { mWindowManager.updateViewLayout(root, it) }
    }

    private fun buildCpuFreqView(freqs: List<String>): View {
        val spacingPx = dpToPx(mItemSpacingDp)
        val container = LinearLayout(mContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2) }
        }

        if (mOverlayFormat == "full") {
            container.addView(TextView(mContext).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp.toFloat())
                setTextColor(parseSafeColor(mTitleColorHex))
                text = mContext.getString(R.string.game_bar_stat_cpu_freq) + " "
            })
        }

        val verticalFreqs = LinearLayout(mContext).apply { orientation = LinearLayout.VERTICAL }
        for (freqLine in freqs) {
            val lineLayout = LinearLayout(mContext).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(spacingPx, spacingPx / 4, spacingPx, spacingPx / 4) }
            }
            lineLayout.addView(TextView(mContext).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp.toFloat())
                setTextColor(parseSafeColor(mValueColorHex))
                text = freqLine
            })
            verticalFreqs.addView(lineLayout)
        }
        container.addView(verticalFreqs)
        return container
    }

    private fun createStatLine(title: String, rawValue: String): LinearLayout {
        val spacingPx = dpToPx(mItemSpacingDp)
        return LinearLayout(mContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2) }

            if (mOverlayFormat == "full") {
                addView(TextView(mContext).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp.toFloat())
                    setTextColor(parseSafeColor(mTitleColorHex))
                    text = if (title.isEmpty()) "" else "$title "
                })
                addView(TextView(mContext).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp.toFloat())
                    setTextColor(parseSafeColor(mValueColorHex))
                    text = rawValue
                })
            } else {
                addView(TextView(mContext).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp.toFloat())
                    setTextColor(parseSafeColor(mValueColorHex))
                    text = rawValue
                })
            }
        }
    }

    private fun createDotView(): TextView = TextView(mContext).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp.toFloat())
        setTextColor(parseSafeColor(mValueColorHex))
        text = " . "
    }

    private fun applyBackgroundStyle() = applyBackgroundStyleTo(mBgDrawable)

    private fun applyBackgroundStyleTo(drawable: GradientDrawable) {
        drawable.setColor(Color.argb(mBackgroundAlpha, 0, 0, 0))
        drawable.cornerRadius = mCornerRadius.toFloat()
        mOverlayView?.background = mBgDrawable
    }

    private fun applyPadding() {
        mRootLayout?.let { layout ->
            val px = dpToPx(mPaddingDp)
            layout.setPadding(px, px, px, px)
        }
    }

    private fun applySplitMode() {
        mRootLayout?.orientation = if (mSplitMode == "side_by_side") {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
    }

    private fun loadSavedPosition(lp: WindowManager.LayoutParams) {
        val prefs  = PreferenceManager.getDefaultSharedPreferences(mContext)
        val savedX = prefs.getInt(PREF_KEY_X, Int.MIN_VALUE)
        val savedY = prefs.getInt(PREF_KEY_Y, Int.MIN_VALUE)
        if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE) {
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = savedX
            lp.y = savedY
        }
    }

    private fun applyPosition(lp: WindowManager.LayoutParams, pos: String) {
        when (pos) {
            "top_left"      -> { lp.gravity = Gravity.TOP or Gravity.START;   lp.x = 0; lp.y = 100 }
            "top_center"    -> { lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; lp.y = 100 }
            "top_right"     -> { lp.gravity = Gravity.TOP or Gravity.END;     lp.x = 0; lp.y = 100 }
            "bottom_left"   -> { lp.gravity = Gravity.BOTTOM or Gravity.START;   lp.x = 0; lp.y = 100 }
            "bottom_center" -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = 100 }
            "bottom_right"  -> { lp.gravity = Gravity.BOTTOM or Gravity.END;     lp.x = 0; lp.y = 100 }
            else            -> { lp.gravity = Gravity.TOP or Gravity.START;   lp.x = 0; lp.y = 100 }
        }
    }

    private fun startUpdates() {
        mHandler.removeCallbacksAndMessages(null)
        mHandler.post(mUpdateRunnable)
    }

    private fun openOverlaySettings() {
        runCatching {
            mContext.startActivity(
                android.content.Intent(mContext, GameBarSettingsActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun readLine(path: String): String? = try {
        BufferedReader(FileReader(path)).use { it.readLine() }
    } catch (_: IOException) {
        null
    }

    /** Parses a hex color string; returns [Color.WHITE] on parse failure. */
    private fun parseSafeColor(hex: String): Int = runCatching {
        Color.parseColor(hex)
    }.getOrDefault(Color.WHITE)

    private fun dpToPx(dp: Int): Int =
        (dp * mContext.resources.displayMetrics.density + 0.5f).toInt()
}
