/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

package com.ichi2.anki

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.core.content.edit
import anki.sync.SyncAuth
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.libanki.MediaCheckResult
import com.ichi2.libanki.createBackup
import com.ichi2.libanki.fullUploadOrDownload
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.preferences.VersatileTextWithASwitchPreference
import net.ankiweb.rsdroid.Backend
import timber.log.Timber

object SyncPreferences {
    const val HKEY = "hkey"
    const val USERNAME = "username"
    const val CURRENT_SYNC_URI = "currentSyncUri"
    const val CUSTOM_SYNC_URI = "syncBaseUrl"
    const val CUSTOM_SYNC_ENABLED = CUSTOM_SYNC_URI + VersatileTextWithASwitchPreference.SWITCH_SUFFIX
    const val CUSTOM_SYNC_CERTIFICATE = "customSyncCertificate"

    // Used in the legacy schema path
    const val HOSTNUM = "hostNum"
}

enum class ConflictResolution {
    FULL_DOWNLOAD,
    FULL_UPLOAD,
}

data class SyncCompletion(
    val isSuccess: Boolean,
)

interface SyncCompletionListener {
    fun onMediaSyncCompleted(data: SyncCompletion)
}

interface SyncHandlerDelegate {
    fun onSyncStart()

    fun refreshState()

    fun activityPaused(): Boolean

    fun syncCallback(callback: (result: ActivityResult) -> Unit): ActivityResultCallback<ActivityResult>

    fun showMediaCheckDialog(
        dialogType: Int,
        checkList: MediaCheckResult,
    )
}

fun getEndpoint(context: Context): String? {
    val preferences = context.sharedPrefs()
    val currentEndpoint = preferences.getString(SyncPreferences.CURRENT_SYNC_URI, null)
    val customEndpoint =
        if (preferences.getBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, false)) {
            preferences.getString(SyncPreferences.CUSTOM_SYNC_URI, null)
        } else {
            null
        }
    return currentEndpoint ?: customEndpoint
}

fun customSyncBase(preferences: SharedPreferences): String? =
    if (preferences.getBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, false)) {
        val uri = preferences.getString(SyncPreferences.CUSTOM_SYNC_URI, null)
        if (uri.isNullOrEmpty()) {
            null
        } else {
            uri
        }
    } else {
        null
    }

suspend fun syncLogout(context: Context) {
    val preferences = context.sharedPrefs()
    preferences.edit {
        remove(SyncPreferences.HKEY)
        remove(SyncPreferences.USERNAME)
        remove(SyncPreferences.CURRENT_SYNC_URI)
        remove(SyncPreferences.HOSTNUM)
    }
    withCol {
        media.forceResync()
    }
}

/**
 * Whether the user has a sync account.
 * Returning true does not guarantee that the user actually synced recently,
 * or even that the ankiweb account is still valid.
 */
fun isLoggedIn() =
    AnkiDroidApp.instance
        .sharedPrefs()
        .getString(SyncPreferences.HKEY, "")!!
        .isNotEmpty()

fun millisecondsSinceLastSync(preferences: SharedPreferences) = TimeManager.time.intTimeMS() - preferences.getLong("lastSyncTime", 0)

fun updateLogin(
    context: Context,
    username: String,
    hkey: String?,
) {
    val preferences = context.sharedPrefs()
    preferences.edit {
        putString(SyncPreferences.USERNAME, username)
        putString(SyncPreferences.HKEY, hkey)
    }
}

fun cancelSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortSync()
}

fun cancelMediaSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortMediaSync()
}

fun Context.setLastSyncTimeToNow() {
    sharedPrefs().edit {
        putLong("lastSyncTime", TimeManager.time.intTimeMS())
    }
}

fun joinSyncMessages(
    dialogMessage: String?,
    syncMessage: String?,
): String? {
    // If both strings have text, separate them by a new line, otherwise return whichever has text
    return if (!dialogMessage.isNullOrEmpty() && !syncMessage.isNullOrEmpty()) {
        """
        $dialogMessage
        
        $syncMessage
        """.trimIndent()
    } else if (!dialogMessage.isNullOrEmpty()) {
        dialogMessage
    } else {
        syncMessage
    }
}
