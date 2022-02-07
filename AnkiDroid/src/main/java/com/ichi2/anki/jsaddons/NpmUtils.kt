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

import android.content.Context
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.jsaddons.NpmUtils.REVIEWER_ADDON
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern

object NpmUtils {
    // Update if api get updated
    const val ANKIDROID_JS_API = "0.0.1"
    const val ANKIDROID_JS_ADDON_KEYWORDS = "ankidroid-js-addon"
    const val REVIEWER_ADDON = "reviewer"
    const val NOTE_EDITOR_ADDON = "note-editor"
}

// https://github.com/npm/validate-npm-package-name/blob/main/index.js
// https://github.com/lassjs/is-valid-npm-name/blob/master/index.js
fun validateName(name: String): Boolean {
    if (name.isNullOrBlank()) {
        return false
    }

    // first trim it
    if (name.trim() != name) {
        return false
    }

    // name can no longer contain more than 214 characters
    if (name.length > 214) {
        return false
    }

    // name cannot start with a period or an underscore
    if (name.startsWith(".") || name.startsWith("_")) {
        return false
    }

    // name can no longer contain capital letters
    if (name.lowercase(Locale.getDefault()) != name) {
        return false
    }

    // must have @
    // must have @ at beginning of string
    if (name.startsWith("@")) {
        // must have only one @
        if (name.indexOf('@') != name.lastIndexOf('@')) {
            return false
        }

        // must have /
        if (!name.contains('/')) {
            return false
        }

        // must have only one /
        if (name.indexOf('/') != name.lastIndexOf('/')) {
            return false
        }

        // validate scope
        val arr = name.split('/')
        val scope = arr[0].removePrefix("@")
        val isValidScopeName = validateName(scope)

        if (!isValidScopeName) {
            return isValidScopeName
        }

        // validate name again
        return validateName(arr[1])
    }

    // no non-URL-safe characters
    if (URLEncoder.encode(name, "UTF-8") != name) {
        return false
    }

    // name can no longer contain special characters ("~\'!()*")'
    val special = Pattern.compile("[~\'!()*]")
    if (special.matcher(name).find()) {
        return false
    }

    return true
}

fun NpmUtils.getEnabledAddonsContent(context: Context): String {
    val content = StringBuilder()

    val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
    val preferences = AnkiDroidApp.getSharedPrefs(context)

    // if preferences for addons in settings toggle off then return empty string
    if (!preferences.getBoolean("javascript_addons_support_prefs", false)) {
        return ""
    }

    // set of enabled reviewer addons only
    val reviewerEnabledAddonSet = preferences.getStringSet(REVIEWER_ADDON, HashSet())

    // make a copy of prefs and modify it (ConcurrentModificationException)
    val newStrSet: MutableSet<String> = reviewerEnabledAddonSet?.toHashSet()!!

    for (enabledAddon in reviewerEnabledAddonSet) {
        try {
            // AnkiDroid/addons/js-addons/package/index.js
            // here enabledAddon is id of npm package which may not contain ../ or other bad path
            val joinedPath: StringJoiner = StringJoiner("/")
                .add(currentAnkiDroidDirectory)
                .add("addons")
                .add(enabledAddon)
                .add("package")
                .add("index.js")

            val indexJsPath: String = joinedPath.toString()

            // user removed content from folder and prefs not updated then remove it
            if (!File(indexJsPath).exists()) {
                newStrSet.remove(enabledAddon)
                Timber.v("indexJsPath:: %s", indexJsPath)
            }

            // <script src="../addons/some-addons/package/index.js"></script>
            val scriptSrcTag = "<script src='$indexJsPath'></script>\n"
            content.append(scriptSrcTag)
        } catch (e: ArrayIndexOutOfBoundsException) {
            Timber.w(e, "AbstractFlashcardViewer::Exception")
        }
    }

    // update prefs for file exits in addons folder
    preferences.edit().putStringSet(REVIEWER_ADDON, newStrSet).apply()
    return content.toString()
}
