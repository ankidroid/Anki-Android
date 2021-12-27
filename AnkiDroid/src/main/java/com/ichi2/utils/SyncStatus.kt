/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.libanki.Collection
import timber.log.Timber
import java.util.function.Supplier

enum class SyncStatus {
    INCONCLUSIVE, NO_ACCOUNT, NO_CHANGES, HAS_CHANGES, FULL_SYNC, BADGE_DISABLED;

    companion object {
        private var sPauseCheckingDatabase = false
        private var sMarkedInMemory = false

        @JvmStatic
        fun getSyncStatus(getCol: Supplier<Collection>): SyncStatus {
            val col: Collection
            col = try {
                getCol.get()
            } catch (e: Exception) {
                Timber.w(e)
                return INCONCLUSIVE
            }
            return getSyncStatus(col)
        }

        @JvmStatic
        fun getSyncStatus(col: Collection): SyncStatus {
            if (isDisabled) {
                return BADGE_DISABLED
            }
            if (!isLoggedIn) {
                return NO_ACCOUNT
            }
            if (col.schemaChanged()) {
                return FULL_SYNC
            }
            return if (hasDatabaseChanges()) {
                HAS_CHANGES
            } else {
                NO_CHANGES
            }
        }

        private val isDisabled: Boolean
            get() {
                val preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance())
                return !preferences.getBoolean("showSyncStatusBadge", true)
            }
        private val isLoggedIn: Boolean
            get() {
                val preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance())
                val hkey = preferences.getString("hkey", "")
                return hkey != null && hkey.length != 0
            }

        /** Whether data has been changed - to be converted to Rust  */
        fun hasDatabaseChanges(): Boolean {
            return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).getBoolean("changesSinceLastSync", false)
        }

        /** To be converted to Rust  */
        fun markDataAsChanged() {
            if (sPauseCheckingDatabase) {
                return
            }
            sMarkedInMemory = true
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).edit().putBoolean("changesSinceLastSync", true).apply()
        }

        /** To be converted to Rust  */
        @KotlinCleanup("Convert these to @RustCleanup")
        @JvmStatic
        fun markSyncCompleted() {
            sMarkedInMemory = false
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).edit().putBoolean("changesSinceLastSync", false).apply()
        }

        @JvmStatic
        fun ignoreDatabaseModification(runnable: Runnable) {
            sPauseCheckingDatabase = true
            try {
                runnable.run()
            } finally {
                sPauseCheckingDatabase = false
            }
        }

        /** Whether a change in data has been detected - used as a heuristic to stop slow operations  */
        fun hasBeenMarkedAsChangedInMemory(): Boolean {
            return sMarkedInMemory
        }
    }
}
