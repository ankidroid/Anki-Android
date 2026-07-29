// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.ichi2.anki.R
import com.ichi2.anki.compat.CompatHelper.Companion.queryIntentActivitiesCompat
import com.ichi2.anki.compat.ResolveInfoFlagsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Checkbox list of every launchable app; checked apps are gated by the blocker.
 * Changes apply immediately (the accessibility service re-tunes via its
 * preference listener).
 */
class BlockedAppsFragment : Fragment(R.layout.fragment_blocked_apps) {
    private data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        var isBlocked: Boolean,
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().finish()
        }
        val list = view.findViewById<RecyclerView>(R.id.apps_list)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        list.layoutManager = LinearLayoutManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { loadLaunchableApps() }
            progress.isVisible = false
            list.adapter = AppsAdapter(apps)
        }
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val packageManager = requireContext().packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val blocked = BlockerPrefs.blockedApps
        return packageManager
            .queryIntentActivitiesCompat(launcherIntent, ResolveInfoFlagsCompat.EMPTY)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != requireContext().packageName }
            .distinctBy { it.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.packageName,
                    icon = it.loadIcon(packageManager),
                    isBlocked = it.packageName in blocked,
                )
            }.sortedWith(compareByDescending<AppEntry> { it.isBlocked }.thenBy { it.label.lowercase() })
            .toList()
    }

    private class AppsAdapter(
        private val apps: List<AppEntry>,
    ) : RecyclerView.Adapter<AppsAdapter.Holder>() {
        class Holder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val label: TextView = view.findViewById(R.id.app_label)
            val checkbox: MaterialCheckBox = view.findViewById(R.id.app_checkbox)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): Holder =
            Holder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_blocked_app, parent, false),
            )

        override fun getItemCount() = apps.size

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
            holder.checkbox.isChecked = app.isBlocked
            holder.itemView.setOnClickListener {
                app.isBlocked = !app.isBlocked
                holder.checkbox.isChecked = app.isBlocked
                persist(app)
            }
        }

        private fun persist(app: AppEntry) {
            Timber.i("Blocker: %s %s", if (app.isBlocked) "blocking" else "unblocking", app.packageName)
            val updated = BlockerPrefs.blockedApps.toMutableSet()
            if (app.isBlocked) updated += app.packageName else updated -= app.packageName
            BlockerPrefs.blockedApps = updated
        }
    }
}
