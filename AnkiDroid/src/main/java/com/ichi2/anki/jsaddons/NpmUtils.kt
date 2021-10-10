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

import android.annotation.SuppressLint
import android.content.Context
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import timber.log.Timber
import java.io.File
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashSet

object NpmUtils {
    // Update if api get updated
    const val ANKIDROID_JS_API = "0.0.1"
    const val ANKIDROID_JS_ADDON_KEYWORDS = "ankidroid-js-addon"
    const val REVIEWER_ADDON = "reviewer"
    const val NOTE_EDITOR_ADDON = "note-editor"

    /**
     * It parses npm package name from url, if url does not start with https://www.npmjs.com/package then it returns null
     *
     * Also return 'valid-ankidroid-js-addon-test' for url https://www.npmjs.com/package/valid-ankidroid-js-addon-test/v/1.0.0
     *
     * @param url url starts with https://www.npmjs.com/package
     * @return name of npm package
     */
    fun getAddonNameFromUrl(url: String): String? {
        var addonName: String

        // if url is npm package url
        if (!url.startsWith("https://www.npmjs.com/package/")) {
            return null
        }
        // get addon name removing package url
        addonName = url.replaceFirst("https://www.npmjs.com/package/".toRegex(), "")

        /**
         * if url contain version then there will be 'addon-name/v/version'
         * e.g. https://www.npmjs.com/package/ankidroid-js-addon-progress-bar/v/1.0.9
         *
         * also url may conatins '?' after package name
         * e.g. https://www.npmjs.com/package/ankidroid-js-addon-progress-bar?activeTab=versions
         *
         * for more view {@code NpmUtilsTest}
         */

        if (addonName.contains("/")) {
            val s = addonName.split("/".toRegex()).toTypedArray()
            addonName = s[0]
        } else if (addonName.contains("?")) {
            val s = addonName.split("\\?".toRegex()).toTypedArray()
            addonName = s[0]
        }

        return addonName
    }

    @SuppressLint("MutatingSharedPrefs")
    @JvmStatic
    fun getEnabledAddonsContent(context: Context): String {
        val content = StringBuilder()

        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        val preferences = AnkiDroidApp.getSharedPrefs(context)

        // set of enabled reviewer addons only
        val reviewerEnabledAddonSet = preferences.getStringSet(REVIEWER_ADDON, HashSet())
        // make a copy of prefs and modify it (ConcurrentModificationException)
        val newStrSet: MutableSet<String> = HashSet(reviewerEnabledAddonSet!!)

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
}
