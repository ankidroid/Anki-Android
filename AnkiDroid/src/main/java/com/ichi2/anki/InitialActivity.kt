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
import android.os.AsyncTask
import androidx.annotation.CheckResult
import com.ichi2.anki.CollectionHelper.DatabaseVersion
import com.ichi2.anki.exception.OutOfSpaceException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustBackendFailedException
import timber.log.Timber
import java.lang.ref.WeakReference

/** Utilities for launching the first activity (currently the DeckPicker)  */
object InitialActivity {
    @JvmStatic
    @CheckResult
    fun getStartupFailureType(context: Context): StartupFailure {
        if (!AnkiDroidApp.isSdCardMounted()) {
            return StartupFailure.SD_CARD_NOT_MOUNTED
        } else if (!CollectionHelper.isCurrentAnkiDroidDirAccessible(context)) {
            return StartupFailure.DIRECTORY_NOT_ACCESSIBLE
        }
        val databaseVersion = isFutureAnkiDroidVersion(context)
        return when (databaseVersion) {
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
            else -> try {
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
    fun downgradeBackend(deckPicker: DeckPicker) {
        // Note: This method does not require a backend pointer or an open collection
        Timber.i("Downgrading backend")
        PerformDowngradeTask(WeakReference<DeckPicker>(deckPicker)).execute()
    }

    @Throws(OutOfSpaceException::class, RustBackendFailedException::class)
    internal fun downgradeCollection(deckPicker: DeckPicker?, backupManager: BackupManager) {
        requireNotNull(deckPicker) { "deckPicker was null" }
        val collectionPath = CollectionHelper.getCollectionPath(deckPicker)
        require(backupManager.performDowngradeBackupInForeground(collectionPath)) { "backup failed" }
        Timber.d("Downgrading database to V11: '%s'", collectionPath)
        BackendFactory.createInstance().backend.downgradeBackend(collectionPath)
    }

    // I disapprove, but it's best to keep consistency with the rest of the app
    private class PerformDowngradeTask(deckPicker: WeakReference<DeckPicker>) : AsyncTask<Void?, Void?, Void?>() {
        private val mDeckPicker: WeakReference<DeckPicker>
        private var mException: Exception? = null
        protected override fun doInBackground(vararg params: Void?): Void? {
            // It would be great if we could catch the OutOfSpaceException here
            try {
                val deckPicker: DeckPicker? = mDeckPicker.get()
                deckPicker?.backupManager?.let { downgradeCollection(deckPicker, it) }
            } catch (e: Exception) {
                Timber.w(e)
                mException = e
            }
            return null
        }

        override fun onPreExecute() {
            super.onPreExecute()
            val d: DeckPicker? = mDeckPicker.get()
            if (d != null) {
                d.showProgressBar()
            }
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            val d: DeckPicker = mDeckPicker.get() ?: return
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

        init {
            mDeckPicker = deckPicker
        }
    }

    enum class StartupFailure {
        SD_CARD_NOT_MOUNTED, DIRECTORY_NOT_ACCESSIBLE, FUTURE_ANKIDROID_VERSION,

        /** A downgrade of the AnkiDroid database is required (and possible)  */
        DATABASE_DOWNGRADE_REQUIRED, DB_ERROR, DATABASE_LOCKED
    }
}
