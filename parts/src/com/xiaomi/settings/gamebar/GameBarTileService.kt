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

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager
import com.xiaomi.settings.R

/**
 * Quick Settings tile that toggles the GameBar overlay on/off.
 *
 * [onStartListening] reflects the current preference state into the tile UI
 * each time the QS panel opens — no persistent listener is kept while the tile
 * is collapsed, which avoids phantom wake-ups.
 *
 * Battery note: `commit()` is used intentionally for the preference write in
 * [onClick] so the new state is immediately visible on the next [onStartListening]
 * call (which may occur before the async `apply()` flush completes).
 */
class GameBarTileService : TileService() {

    // Lazily obtained; safe because TileService lifecycle guarantees onCreate() before use.
    private val gameBar: GameBar by lazy { GameBar.getInstance(this) }

    override fun onStartListening() {
        val enabled = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("game_bar_enable", false)
        updateTileState(enabled)
    }

    override fun onClick() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val newState = !prefs.getBoolean("game_bar_enable", false)

        prefs.edit().putBoolean("game_bar_enable", newState).commit()
        updateTileState(newState)

        if (newState) {
            gameBar.applyPreferences()
            gameBar.show()
        } else {
            gameBar.hide()
        }
    }

    private fun updateTileState(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state              = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label              = getString(R.string.game_bar_tile_label)
        tile.contentDescription = getString(R.string.game_bar_tile_description)
        tile.updateTile()
    }
}
