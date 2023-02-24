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

import android.content.Context
import androidx.core.content.edit
import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.SyncPreferences
import com.ichi2.anki.servicelayer.ScopedStorageService.userMigrationIsInProgress
import com.ichi2.libanki.Collection
import net.ankiweb.rsdroid.BackendFactory

enum class SyncStatus {
    INCONCLUSIVE, NO_ACCOUNT, NO_CHANGES, HAS_CHANGES, FULL_SYNC, BADGE_DISABLED,

    /**
     * Scope storage migration is ongoing. Sync should be disabled.
     */
    ONGOING_MIGRATION;

    companion object {
        private var sPauseCheckingDatabase = false
        private var sMarkedInMemory = false

        fun getSyncStatus(col: Collection, context: Context, auth: SyncAuth?): SyncStatus {
            if (userMigrationIsInProgress(context)) {
                return ONGOING_MIGRATION
            }
            if (isDisabled) {
                return BADGE_DISABLED
            }
            if (auth == null) {
                return NO_ACCOUNT
            }
            if (!BackendFactory.defaultLegacySchema) {
                val output = col.newBackend.backend.syncStatus(auth)
                if (output.hasNewEndpoint()) {
                    AnkiDroidApp.getSharedPrefs(context).edit {
                        putString(SyncPreferences.CURRENT_SYNC_URI, output.newEndpoint)
                    }
                }
                return syncStatusFromRequired(output.required)
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

        private fun syncStatusFromRequired(required: SyncStatusResponse.Required?): SyncStatus {
            return when (required) {
                SyncStatusResponse.Required.NO_CHANGES -> NO_CHANGES
                SyncStatusResponse.Required.NORMAL_SYNC -> HAS_CHANGES
                SyncStatusResponse.Required.FULL_SYNC -> FULL_SYNC
                SyncStatusResponse.Required.UNRECOGNIZED, null -> TODO("unexpected required response")
            }
        }

        private val isDisabled: Boolean
            get() {
                val preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance)
                return !preferences.getBoolean("showSyncStatusBadge", true)
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
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit { putBoolean("changesSinceLastSync", true) }
        }

        /** To be converted to Rust  */
        @KotlinCleanup("Convert these to @RustCleanup")
        fun markSyncCompleted() {
            sMarkedInMemory = false
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit { putBoolean("changesSinceLastSync", false) }
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
