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
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.libanki.Collection

// TODO Remove BADGE_DISABLED from this enum, it doesn't belong here
enum class SyncStatus {
    NO_ACCOUNT, NO_CHANGES, HAS_CHANGES, FULL_SYNC, BADGE_DISABLED;

    companion object {
        private var sPauseCheckingDatabase = false
        private var sMarkedInMemory = false

        fun getSyncStatus(col: Collection, context: Context, auth: SyncAuth?): SyncStatus {
            if (isDisabled) {
                return BADGE_DISABLED
            }
            if (auth == null) {
                return NO_ACCOUNT
            }
            val output = col.syncStatus(auth)
            if (output.hasNewEndpoint()) {
                context.sharedPrefs().edit {
                    putString(SyncPreferences.CURRENT_SYNC_URI, output.newEndpoint)
                }
            }
            return syncStatusFromRequired(output.required)
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
                val preferences = AnkiDroidApp.instance.sharedPrefs()
                return !preferences.getBoolean("showSyncStatusBadge", true)
            }

        /** To be converted to Rust  */
        fun markDataAsChanged() {
            if (sPauseCheckingDatabase) {
                return
            }
            sMarkedInMemory = true
            AnkiDroidApp.instance.sharedPrefs().edit { putBoolean("changesSinceLastSync", true) }
        }

        /** Whether a change in data has been detected - used as a heuristic to stop slow operations  */
        fun hasBeenMarkedAsChangedInMemory(): Boolean {
            return sMarkedInMemory
        }
    }
}
