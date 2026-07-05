// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mani <infinyte01@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.SharedPreferences
import androidx.core.content.edit

data class AddonModel(
    val name: String,
    val addonTitle: String,
    val icon: String,
    val version: String,
    val description: String,
    val main: String,
    val ankidroidJsApi: String,
    val addonType: String,
    val keywords: List<String>,
    val author: Map<String, String>,
    val license: String,
    val homepage: String,
    /** Tarball location from the npm registry API; null for locally installed addons */
    val dist: DistInfo?,
) {
    /**
     * Update preferences for addons with boolean remove, the preferences will be used to store the information about
     * enabled and disabled addon. So, that other method will return content of script to reviewer or note editor
     *
     * @param preferences
     * @param jsAddonKey  REVIEWER_ADDON_KEY
     * @param remove    true for removing from prefs
     *
     * Android returns a reference to StringSet in SharedPreferences but does not mark it as modified if any changes made,
     * so commit/apply won't persist it. It needs to make a copy and set the new copy in to persist any StringSet changes
     * in SharedPreferences.
     * https://stackoverflow.com/questions/19949182/android-sharedpreferences-string-set-some-items-are-removed-after-app-restart/19949833
     */
    fun updatePrefs(
        preferences: SharedPreferences,
        jsAddonKey: String,
        remove: Boolean,
    ) {
        val reviewerEnabledAddonSet = preferences.getStringSet(jsAddonKey, HashSet())
        val newStrSet: MutableSet<String> = reviewerEnabledAddonSet?.toHashSet()!!

        if (remove) {
            newStrSet.remove(name)
        } else {
            newStrSet.add(name)
        }

        preferences.edit { putStringSet(jsAddonKey, newStrSet) }
    }
}
