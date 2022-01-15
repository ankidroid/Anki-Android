/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.jsaddons

import android.content.SharedPreferences
import com.ichi2.anki.jsaddons.NpmUtils.ANKIDROID_JS_ADDON_KEYWORDS
import com.ichi2.anki.jsaddons.NpmUtils.NOTE_EDITOR_ADDON

/**
 * This class used in NpmPackageDownloader by ObjectMapper to map npm package.json to AddonModel
 * When package.json fetched from https://registry.npmjs.org/some-addon/latest,
 * ObjectMapper (jackson) readvalue map package.json to this class
 * All the required fields in package.json mapped to variables in this class.
 * The most important fields in package.json are
 * ankiDroidJsApi, addonType and keywords --> this distinguish from other npm packages
 */

class AddonModel {
    val name: String? = null // name of npm package, it unique for each package listed on npm
    val addonTitle: String? = null // for showing in AnkiDroid
    val icon: String? = null // only required for note editor (single character recommended)
    val version: String? = null
    val description: String? = null
    val main: String? = null
    val ankidroidJsApi: String? = null
    val addonType: String? = null
    val keywords: Array<String>? = null
    val author: Map<String, String>? = null
    val license: String? = null
    val homepage: String? = null
    val dist: Map<String, String>? = null

    /**
     * @param jsAddonKey  REVIEWER_ADDON_KEY
     * @param remove    true for removing from prefs
     *
     *
     * https://stackoverflow.com/questions/19949182/android-sharedpreferences-string-set-some-items-are-removed-after-app-restart/19949833
     */
    fun updatePrefs(preferences: SharedPreferences, jsAddonKey: String?, remove: Boolean) {
        val reviewerEnabledAddonSet = preferences.getStringSet(jsAddonKey, HashSet())
        val newStrSet: MutableSet<String> = reviewerEnabledAddonSet?.toHashSet()!!

        if (remove) {
            newStrSet.remove(addonTitle)
        } else {
            if (addonTitle != null) {
                newStrSet.add(addonTitle)
            }
        }

        preferences.edit().putStringSet(jsAddonKey, newStrSet).apply()
    }
}

/**
 * Check if npm package is valid or not by fields ankidroidJsApi, keywords (ankidroid-js-addon) and
 * addon_type (reviewer or note editor) in addonModel
 *
 * @return true for valid addon else false
 */
fun AddonModel.isValidAnkiDroidAddon(): Boolean {
    // either fields not present in package.json or failed to parse the fields
    if (name.isNullOrBlank() || addonTitle.isNullOrBlank() || main.isNullOrBlank() ||
        ankidroidJsApi.isNullOrBlank() || addonType.isNullOrBlank() || homepage.isNullOrBlank() ||
        keywords.isNullOrEmpty()
    ) {
        return false
    }

    // check if name is safe and valid
    if (!validateName(name)) {
        return false
    }

    // if addon type is note editor then it must have icon
    if (addonType == NOTE_EDITOR_ADDON && icon.isNullOrBlank()) {
        return false
    }

    // check if ankidroid-js-addon present or not in mapped addonModel
    val jsAddonKeywordsPresent = keywords.any { it == ANKIDROID_JS_ADDON_KEYWORDS }

    // addon package.json should have js_api_version, ankidroid-js-addon keywords and addon type
    return (
        ankidroidJsApi == NpmUtils.ANKIDROID_JS_API && jsAddonKeywordsPresent &&
            (addonType == NpmUtils.REVIEWER_ADDON || addonType == NOTE_EDITOR_ADDON)
        )
}
