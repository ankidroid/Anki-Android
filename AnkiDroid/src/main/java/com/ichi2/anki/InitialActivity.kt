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

package com.ichi2.anki

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.CheckResult
import androidx.core.content.edit
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.setPreferencesUpToDate
import com.ichi2.utils.VersionUtils.pkgVersionName
import timber.log.Timber

/** Utilities for launching the first activity (currently the DeckPicker)  */
object InitialActivity {
    /** Returns null on success  */
    @CheckResult
    fun getStartupFailureType(context: Context): StartupFailure? {
        // A WebView failure means that we skip `AnkiDroidApp`, and therefore haven't loaded the collection
        if (AnkiDroidApp.webViewFailedToLoad()) {
            return StartupFailure.WEBVIEW_FAILED
        }

        // If we're OK, return null
        if (CollectionHelper.instance.getColSafe(context, reportException = false) != null) {
            return null
        }
        if (!AnkiDroidApp.isSdCardMounted) {
            return StartupFailure.SD_CARD_NOT_MOUNTED
        } else if (!CollectionHelper.isCurrentAnkiDroidDirAccessible(context)) {
            return StartupFailure.DIRECTORY_NOT_ACCESSIBLE
        }

        return when (CollectionHelper.lastOpenFailure) {
            CollectionHelper.CollectionOpenFailure.FILE_TOO_NEW -> StartupFailure.FUTURE_ANKIDROID_VERSION
            CollectionHelper.CollectionOpenFailure.CORRUPT -> StartupFailure.DB_ERROR
            CollectionHelper.CollectionOpenFailure.LOCKED -> StartupFailure.DATABASE_LOCKED
            CollectionHelper.CollectionOpenFailure.DISK_FULL -> StartupFailure.DISK_FULL
            null -> {
                // if getColSafe returned null, this should never happen
                null
            }
        }
    }

    /** @return Whether any preferences were upgraded
     */
    fun upgradePreferences(context: Context?, previousVersionCode: Long): Boolean {
        return PreferenceUpgradeService.upgradePreferences(context, previousVersionCode)
    }

    /**
     * @return Whether a fresh install occurred and a "fresh install" setup for preferences was performed
     * This only refers to a fresh install from the preferences perspective, not from the Anki data perspective.
     *
     * NOTE: A user can wipe app data, which will mean this returns true WITHOUT deleting their collection.
     * The above note will need to be reevaluated after scoped storage migration takes place
     *
     *
     * On the other hand, restoring an app backup can cause this to return true before the Anki collection is created
     * in practice, this doesn't occur due to CollectionHelper.getCol creating a new collection, and it's called before
     * this in the startup script
     */
    @CheckResult
    fun performSetupFromFreshInstallOrClearedPreferences(preferences: SharedPreferences): Boolean {
        if (!wasFreshInstall(preferences)) {
            Timber.d("Not a fresh install [preferences]")
            return false
        }
        Timber.i("Fresh install")
        setPreferencesUpToDate(preferences)
        setUpgradedToLatestVersion(preferences)
        return true
    }

    /**
     * true if the app was launched the first time
     * false if the app was launched for the second time after a successful initialisation
     * false if the app was launched after an update
     */
    fun wasFreshInstall(preferences: SharedPreferences) =
        "" == preferences.getString("lastVersion", "")

    /** Sets the preference stating that the latest version has been applied  */
    fun setUpgradedToLatestVersion(preferences: SharedPreferences) {
        Timber.i("Marked prefs as upgraded to latest version: %s", pkgVersionName)
        preferences.edit { putString("lastVersion", pkgVersionName) }
    }

    /** @return false: The app has been upgraded since the last launch OR the app was launched for the first time.
     * Implementation detail:
     * This is not called in the case of performSetupFromFreshInstall returning true.
     * So this should not use the default value
     */
    fun isLatestVersion(preferences: SharedPreferences): Boolean {
        return preferences.getString("lastVersion", "") == pkgVersionName
    }

    enum class StartupFailure {
        SD_CARD_NOT_MOUNTED, DIRECTORY_NOT_ACCESSIBLE, FUTURE_ANKIDROID_VERSION,
        DB_ERROR, DATABASE_LOCKED, WEBVIEW_FAILED, DISK_FULL
    }
}
