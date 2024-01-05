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
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.SyncPreferences
import com.ichi2.anki.preferences.sharedPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import timber.log.Timber

// TODO Remove BADGE_DISABLED from this enum, it doesn't belong here
enum class SyncStatus {
    NO_ACCOUNT, NO_CHANGES, HAS_CHANGES, FULL_SYNC, BADGE_DISABLED, ERROR;

    companion object {
        private var sPauseCheckingDatabase = false
        private var sMarkedInMemory = false

        suspend fun getSyncStatus(context: Context, auth: SyncAuth?): SyncStatus {
            if (isDisabled) {
                return BADGE_DISABLED
            }
            if (auth == null) {
                return NO_ACCOUNT
            }
            return try {
                // Use CollectionManager to ensure that this doesn't block 'deck count' tasks
                // throws if a .colpkg import or similar occurs just before this call
                val output = withContext(Dispatchers.IO) { CollectionManager.getBackend().syncStatus(auth) }
                if (output.hasNewEndpoint()) {
                    context.sharedPrefs().edit {
                        putString(SyncPreferences.CURRENT_SYNC_URI, output.newEndpoint)
                    }
                }
                syncStatusFromRequired(output.required)
            } catch (_: BackendNetworkException) {
                NO_CHANGES
            } catch (e: Exception) {
                Timber.d("error obtaining sync status: collection likely closed", e)
                ERROR
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
                val preferences = AnkiDroidApp.sharedPrefs()
                return !preferences.getBoolean("showSyncStatusBadge", true)
            }

        /** To be converted to Rust  */
        fun markDataAsChanged() {
            if (sPauseCheckingDatabase) {
                return
            }
            sMarkedInMemory = true
            AnkiDroidApp.sharedPrefs().edit { putBoolean("changesSinceLastSync", true) }
        }

        /** Whether a change in data has been detected - used as a heuristic to stop slow operations  */
        fun hasBeenMarkedAsChangedInMemory(): Boolean {
            return sMarkedInMemory
        }
    }
}
