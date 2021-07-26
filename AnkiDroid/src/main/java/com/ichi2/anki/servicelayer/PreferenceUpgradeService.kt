/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.servicelayer

import android.content.Context
import android.content.SharedPreferences
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.reviewer.FullScreenMode
import timber.log.Timber

object PreferenceUpgradeService {
    /**
     * The latest package version number that included changes to the preferences that requires handling. All
     * collections being upgraded to (or after) this version must update preferences.
     *
     * #9309 Do not modify this variable - it no longer works.
     *
     * Instead, add an unconditional check for the old preference before the call to
     * "needsPreferenceUpgrade", and perform the upgrade.
     */
    const val CHECK_PREFERENCES_AT_VERSION = 20500225

    @JvmStatic
    fun upgradePreferences(context: Context?, previousVersionCode: Long): Boolean =
        upgradePreferences(AnkiDroidApp.getSharedPrefs(context), previousVersionCode)

    /** @return Whether any preferences were upgraded */
    internal fun upgradePreferences(preferences: SharedPreferences, previousVersionCode: Long): Boolean {
        var hasUpgradedPreferences = false

        // perform any new preference upgrades here and set hasUpgradedPreferences

        if (!needsLegacyPreferenceUpgrade(previousVersionCode)) {
            return hasUpgradedPreferences
        }
        hasUpgradedPreferences = true

        Timber.i("running upgradePreferences()")
        // clear all prefs if super old version to prevent any errors
        if (previousVersionCode < 20300130) {
            Timber.i("Old version of Anki - Clearing preferences")
            preferences.edit().clear().apply()
        }
        // when upgrading from before 2.5alpha35
        if (previousVersionCode < 20500135) {
            Timber.i("Old version of Anki - Fixing Zoom")
            // Card zooming behaviour was changed the preferences renamed
            val oldCardZoom = preferences.getInt("relativeDisplayFontSize", 100)
            val oldImageZoom = preferences.getInt("relativeImageSize", 100)
            preferences.edit().putInt("cardZoom", oldCardZoom).apply()
            preferences.edit().putInt("imageZoom", oldImageZoom).apply()
            if (!preferences.getBoolean("useBackup", true)) {
                preferences.edit().putInt("backupMax", 0).apply()
            }
            preferences.edit().remove("useBackup").apply()
            preferences.edit().remove("intentAdditionInstantAdd").apply()
        }
        FullScreenMode.upgradeFromLegacyPreference(preferences)
        return hasUpgradedPreferences
    }

    internal fun needsLegacyPreferenceUpgrade(previous: Long): Boolean = previous < CHECK_PREFERENCES_AT_VERSION
}
