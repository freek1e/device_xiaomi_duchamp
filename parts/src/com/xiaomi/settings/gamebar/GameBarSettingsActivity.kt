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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.xiaomi.settings.R

/**
 * Entry point Activity for the GameBar settings screen.
 *
 * Requests SYSTEM_ALERT_WINDOW (overlay) permission on first launch if it hasn't
 * been granted yet. Without this permission the overlay cannot be displayed.
 */
class GameBarSettingsActivity : CollapsingToolbarBaseActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_bar)
        setTitle(getString(R.string.game_bar_title))

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")   // required for API compatibility below API 30
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    @Deprecated("Deprecated in API 30 — use ActivityResultContracts when minSdk ≥ 30")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            val msgRes = if (Settings.canDrawOverlays(this)) {
                R.string.overlay_permission_granted
            } else {
                R.string.overlay_permission_denied
            }
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
        }
    }
}
