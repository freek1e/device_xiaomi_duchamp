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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaomi.settings.R

/**
 * Fragment that lists apps enrolled in GameBar auto-enable.
 * Tapping an app removes it from the list and from persistent preferences.
 *
 * Uses [notifyItemRemoved] for precise RecyclerView updates (no full rebind).
 */
class GameBarAppRemoverFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GameBarAutoAppsAdapter
    private lateinit var packageManager: PackageManager
    private val autoAppsList: MutableList<ApplicationInfo> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.game_bar_app_selector, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView   = view.findViewById(R.id.app_list)
        packageManager = requireContext().packageManager
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadAutoApps()
    }

    private fun loadAutoApps() {
        autoAppsList.clear()
        for (pkg in getSavedAutoApps()) {
            runCatching { packageManager.getApplicationInfo(pkg, 0) }
                .onSuccess { autoAppsList.add(it) }
                // silently skip packages that are no longer installed
        }

        adapter = GameBarAutoAppsAdapter(packageManager, autoAppsList) { appInfo ->
            val position = autoAppsList.indexOf(appInfo)
            if (position >= 0) {
                removeAppFromAutoList(appInfo.packageName)
                autoAppsList.removeAt(position)
                adapter.notifyItemRemoved(position)
                Toast.makeText(
                    requireContext(),
                    "${appInfo.loadLabel(packageManager)} removed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        recyclerView.adapter = adapter
    }

    private fun getSavedAutoApps(): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getStringSet(GameBarAppSelectorFragment.PREF_AUTO_APPS, emptySet()) ?: emptySet()

    private fun removeAppFromAutoList(packageName: String) {
        val updated = HashSet(getSavedAutoApps()).also { it.remove(packageName) }
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putStringSet(GameBarAppSelectorFragment.PREF_AUTO_APPS, updated)
            .apply()
    }
}
