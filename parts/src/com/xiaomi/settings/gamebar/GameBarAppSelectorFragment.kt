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
 * Fragment that shows all user-installed apps not yet enrolled in the GameBar
 * auto-enable list. Tapping an app adds it and removes it from the displayed list.
 *
 * Battery / GC optimisation notes:
 * - [loadApps] filters the installed app list once, before building the adapter,
 *   so no redundant re-filtering occurs while the list is displayed.
 * - Uses [notifyItemRemoved] instead of [notifyDataSetChanged] to avoid full
 *   re-layout of the entire RecyclerView on every tap.
 */
class GameBarAppSelectorFragment : Fragment() {

    companion object {
        const val PREF_AUTO_APPS = "game_bar_auto_apps"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GameBarAppsAdapter
    private lateinit var packageManager: PackageManager
    private val allApps: MutableList<ApplicationInfo> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.game_bar_app_selector, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView    = view.findViewById(R.id.app_list)
        packageManager  = requireContext().packageManager
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadApps()
    }

    private fun loadApps() {
        val autoApps = getSavedAutoApps()
        val myPkg    = requireContext().packageName

        allApps.clear()
        packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filterTo(allApps) { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    app.packageName != myPkg &&
                    app.packageName !in autoApps
            }

        adapter = GameBarAppsAdapter(packageManager, allApps) { appInfo ->
            val position = allApps.indexOf(appInfo)
            if (position >= 0) {
                addAppToAutoList(appInfo.packageName)
                allApps.removeAt(position)
                adapter.notifyItemRemoved(position)  // precise notification — no full rebind
                Toast.makeText(
                    requireContext(),
                    "${appInfo.loadLabel(packageManager)} added.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        recyclerView.adapter = adapter
    }

    private fun getSavedAutoApps(): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getStringSet(PREF_AUTO_APPS, emptySet()) ?: emptySet()

    private fun addAppToAutoList(packageName: String) {
        val updated = HashSet(getSavedAutoApps()).also { it.add(packageName) }
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putStringSet(PREF_AUTO_APPS, updated)
            .apply()
    }
}
