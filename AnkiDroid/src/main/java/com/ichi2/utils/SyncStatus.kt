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

import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.libanki.Collection
import net.ankiweb.rsdroid.BackendFactory

enum class SyncStatus {
    /**
     * Whether the user has not logged-in with an account.
     */
    NO_ACCOUNT,

    /**
     * There has been no change on this device since the last sync.
     * Hence a sync would only serves to download more data (or do a full sync if requested by the sync server)
     */
    NO_CHANGES,

    /**
     * There has been local change since the latest sync. Those changes don't require a full sync.
     * So we expect next sync to upload some data (unless the sync server requires a full sync)
     */
    HAS_CHANGES,

    /**
     * Next sync will be a full sync. This can occur either:
     * * the user did changes on this device that requires a full sync
     * * since last sync, the sync server let the user know a full sync was required.
     */
    FULL_SYNC,

    /**
     * Whether the user settings ask not to show badge on top of the sync icon
     */
    BADGE_DISABLED;

    companion object {
        private var sPauseCheckingDatabase = false
        private var sMarkedInMemory = false

        fun getSyncStatus(col: Collection, auth: SyncAuth?): SyncStatus =
            if (syncIconBadgeIsDisabledInPreferences) {
                BADGE_DISABLED
            } else if (auth == null) {
                NO_ACCOUNT
            } else if (!BackendFactory.defaultLegacySchema) {
                syncStatusFromRequired(col.newBackend.backend.syncStatus(auth).required)
            } else if (col.schemaChanged()) {
                FULL_SYNC
            } else if (hasDatabaseChanges()) {
                HAS_CHANGES
            } else {
                NO_CHANGES
            }

        /**
         * @return What will the next sync upload will need to do.
         * This assumes that the user is signed-in.
         */
        private fun syncStatusFromRequired(required: SyncStatusResponse.Required?): SyncStatus {
            return when (required) {
                SyncStatusResponse.Required.NO_CHANGES -> NO_CHANGES
                SyncStatusResponse.Required.NORMAL_SYNC -> HAS_CHANGES
                SyncStatusResponse.Required.FULL_SYNC -> FULL_SYNC
                SyncStatusResponse.Required.UNRECOGNIZED, null -> TODO("unexpected required response")
            }
        }

        private val syncIconBadgeIsDisabledInPreferences: Boolean
            get() {
                val preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance)
                return !preferences.getBoolean("showSyncStatusBadge", true)
            }

        val isLoggedIn: Boolean
            get() {
                val preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance)
                val hkey = preferences.getString("hkey", "")
                return hkey != null && hkey.isNotEmpty()
            }

        /** Whether data has been changed - to be converted to Rust  */
        fun hasDatabaseChanges(): Boolean {
            return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).getBoolean("changesSinceLastSync", false)
        }

        /** To be converted to Rust  */
        fun markDataAsChanged() {
            if (sPauseCheckingDatabase) {
                return
            }
            sMarkedInMemory = true
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit().putBoolean("changesSinceLastSync", true).apply()
        }

        /** To be converted to Rust  */
        @KotlinCleanup("Convert these to @RustCleanup")
        fun markSyncCompleted() {
            sMarkedInMemory = false
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit().putBoolean("changesSinceLastSync", false).apply()
        }

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
