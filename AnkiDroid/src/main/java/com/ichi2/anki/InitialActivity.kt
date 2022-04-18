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
import android.os.*
import androidx.annotation.CheckResult
import com.ichi2.anki.CollectionHelper.DatabaseVersion
import com.ichi2.anki.exception.OutOfSpaceException
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.setPreferencesUpToDate
import com.ichi2.utils.VersionUtils.pkgVersionName
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustBackendFailedException
import timber.log.Timber
import java.lang.ref.WeakReference

/** Utilities for launching the first activity (currently the DeckPicker)  */
object InitialActivity {
    /** Returns null on success  */
    @JvmStatic
    @CheckResult
    fun getStartupFailureType(context: Context): StartupFailure? {

        // A WebView failure means that we skip `AnkiDroidApp`, and therefore haven't loaded the collection
        if (AnkiDroidApp.webViewFailedToLoad()) {
            return StartupFailure.WEBVIEW_FAILED
        }

        // If we're OK, return null
        if (CollectionHelper.getInstance().getColSafe(context) != null) {
            return null
        }
        if (!AnkiDroidApp.isSdCardMounted()) {
            return StartupFailure.SD_CARD_NOT_MOUNTED
        } else if (!CollectionHelper.isCurrentAnkiDroidDirAccessible(context)) {
            return StartupFailure.DIRECTORY_NOT_ACCESSIBLE
        }

        return when (isFutureAnkiDroidVersion(context)) {
            DatabaseVersion.FUTURE_NOT_DOWNGRADABLE -> StartupFailure.FUTURE_ANKIDROID_VERSION
            DatabaseVersion.FUTURE_DOWNGRADABLE -> StartupFailure.DATABASE_DOWNGRADE_REQUIRED
            DatabaseVersion.UNKNOWN, DatabaseVersion.USABLE -> try {
                CollectionHelper.getInstance().getCol(context)
                StartupFailure.DB_ERROR
            } catch (e: BackendDbLockedException) {
                StartupFailure.DATABASE_LOCKED
            } catch (ignored: Exception) {
                StartupFailure.DB_ERROR
            }
        }
    }

    private fun isFutureAnkiDroidVersion(context: Context): DatabaseVersion {
        return try {
            CollectionHelper.isFutureAnkiDroidVersion(context)
        } catch (e: Exception) {
            Timber.w(e, "Could not determine if future AnkiDroid version - assuming not")
            DatabaseVersion.UNKNOWN
        }
    }

    /**
     * Downgrades the database at the currently selected collection path from V16 to V11 in a background task
     */
    // #7108: AsyncTask
    @Suppress("deprecation")
    @JvmStatic
    fun downgradeBackend(deckPicker: DeckPicker) {
        // Note: This method does not require a backend pointer or an open collection
        Timber.i("Downgrading backend")
        PerformDowngradeTask(WeakReference(deckPicker)).execute()
    }

    @Throws(OutOfSpaceException::class, RustBackendFailedException::class)
    internal fun downgradeCollection(deckPicker: DeckPicker?, backupManager: BackupManager) {
        requireNotNull(deckPicker) { "deckPicker was null" }
        val collectionPath = CollectionHelper.getCollectionPath(deckPicker)
        require(backupManager.performDowngradeBackupInForeground(collectionPath)) { "backup failed" }
        Timber.d("Downgrading database to V11: '%s'", collectionPath)
        BackendFactory.createInstance().backend.downgradeBackend(collectionPath)
    }

    /** @return Whether any preferences were upgraded
     */
    @JvmStatic
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
    @JvmStatic
    @CheckResult
    fun performSetupFromFreshInstallOrClearedPreferences(preferences: SharedPreferences): Boolean {
        val lastVersionWasSet = "" != preferences.getString("lastVersion", "")
        if (lastVersionWasSet) {
            Timber.d("Not a fresh install [preferences]")
            return false
        }
        Timber.i("Fresh install")
        setPreferencesUpToDate(preferences)
        setUpgradedToLatestVersion(preferences)
        return true
    }

    /** Sets the preference stating that the latest version has been applied  */
    @JvmStatic
    fun setUpgradedToLatestVersion(preferences: SharedPreferences) {
        Timber.i("Marked prefs as upgraded to latest version: %s", pkgVersionName)
        preferences.edit().putString("lastVersion", pkgVersionName).apply()
    }

    /** @return false: The app has been upgraded since the last launch OR the app was launched for the first time.
     * Implementation detail:
     * This is not called in the case of performSetupFromFreshInstall returning true.
     * So this should not use the default value
     */
    @JvmStatic
    fun isLatestVersion(preferences: SharedPreferences): Boolean {
        return preferences.getString("lastVersion", "") == pkgVersionName
    }

    // I disapprove, but it's best to keep consistency with the rest of the app
    // #7108: AsyncTask
    @Suppress("deprecation")
    private class PerformDowngradeTask(private val deckPicker: WeakReference<DeckPicker>) : AsyncTask<Void?, Void?, Void?>() {
        private var mException: Exception? = null
        override fun doInBackground(vararg p0: Void?): Void? {
            // It would be great if we could catch the OutOfSpaceException here
            try {
                val deckPicker = deckPicker.get()
                downgradeCollection(deckPicker, deckPicker!!.backupManager!!)
            } catch (e: Exception) {
                Timber.w(e)
                mException = e
            }
            return null
        }

        override fun onPreExecute() {
            super.onPreExecute()
            val d = deckPicker.get()
            d?.showProgressBar()
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            val d = deckPicker.get() ?: return
            d.hideProgressBar()
            if (mException != null) {
                if (mException is OutOfSpaceException) {
                    d.displayDowngradeFailedNoSpace()
                } else {
                    d.displayDatabaseFailure()
                }
                return
            }
            Timber.i("Database downgrade successful - starting up")
            // no exception - continue
            d.handleStartup()
            // This call should probably be in handleStartup - but it's also called there onRefresh
            // TODO: PERF: to fix the above, add test to ensure that this is only called once on each startup path
            d.refreshState()
        }
    }

    enum class StartupFailure {
        SD_CARD_NOT_MOUNTED, DIRECTORY_NOT_ACCESSIBLE, FUTURE_ANKIDROID_VERSION,

        /** A downgrade of the AnkiDroid database is required (and possible)  */
        DATABASE_DOWNGRADE_REQUIRED, DB_ERROR, DATABASE_LOCKED, WEBVIEW_FAILED
    }
}
