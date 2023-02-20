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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.syncAuth
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.web.HostNumFactory
import com.ichi2.async.Connection
import com.ichi2.libanki.createBackup
import com.ichi2.libanki.sync.*
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.preferences.VersatileTextWithASwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber
import java.net.UnknownHostException

object SyncPreferences {
    const val HKEY = "hkey"
    const val USERNAME = "username"
    const val CURRENT_SYNC_URI = "currentSyncUri"
    const val CUSTOM_SYNC_URI = "syncBaseUrl"
    const val CUSTOM_SYNC_ENABLED = CUSTOM_SYNC_URI + VersatileTextWithASwitchPreference.SWITCH_SUFFIX

    // Used in the legacy schema path
    const val HOSTNUM = "hostNum"
}

fun DeckPicker.syncAuth(): SyncAuth? {
    val preferences = AnkiDroidApp.getSharedPrefs(this)
    val hkey = preferences.getString(SyncPreferences.HKEY, null)
    val currentEndpoint = preferences.getString(SyncPreferences.CURRENT_SYNC_URI, null)
    val customEndpoint = if (preferences.getBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, false)) {
        preferences.getString(SyncPreferences.CUSTOM_SYNC_URI, null)
    } else {
        null
    }
    val resolvedEndpoint = currentEndpoint ?: customEndpoint
    return hkey?.let {
        syncAuth {
            this.hkey = hkey
            if (resolvedEndpoint != null) {
                this.endpoint = resolvedEndpoint
            }
        }
    }
}

fun customSyncBase(preferences: SharedPreferences): String? {
    return if (preferences.getBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, false)) {
        val uri = preferences.getString(SyncPreferences.CUSTOM_SYNC_URI, null)
        if (uri.isNullOrEmpty()) {
            null
        } else {
            uri
        }
    } else {
        null
    }
}

suspend fun syncLogout(context: Context) {
    val preferences = AnkiDroidApp.getSharedPrefs(context)
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
fun isLoggedIn() = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).getString(SyncPreferences.HKEY, "")!!.isNotEmpty()

fun millisecondsSinceLastSync(preferences: SharedPreferences) = TimeManager.time.intTimeMS() - preferences.getLong("lastSyncTime", 0)

fun canSync(context: Context) = !ScopedStorageService.userMigrationIsInProgress(context)

fun DeckPicker.handleNewSync(
    conflict: Connection.ConflictResolution?,
    syncMedia: Boolean
) {
    val auth = this.syncAuth() ?: return
    val deckPicker = this
    launchCatchingTask {
        try {
            when (conflict) {
                Connection.ConflictResolution.FULL_DOWNLOAD -> handleDownload(deckPicker, auth, syncMedia)
                Connection.ConflictResolution.FULL_UPLOAD -> handleUpload(deckPicker, auth, syncMedia)
                null -> {
                    try {
                        handleNormalSync(deckPicker, auth, syncMedia)
                    } catch (exc: Exception) {
                        when (exc) {
                            is UnknownHostException, is BackendNetworkException -> {
                                showSnackbar(R.string.check_network) {
                                    setAction(R.string.sync_even_if_offline) {
                                        Connection.allowLoginSyncOnNoConnection = true
                                        sync()
                                    }
                                }
                                Timber.i("No network exception")
                            }
                            else -> throw exc
                        }
                    }
                }
            }
        } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
            // auth failed; log out
            updateLogin(baseContext, "", "")
            throw exc
        }
        refreshState()
    }
}

fun MyAccount.handleNewLogin(username: String, password: String) {
    launchCatchingTask {
        val auth = try {
            withProgress({}, onCancel = ::cancelSync) {
                withCol {
                    newBackend.syncLogin(username, password)
                }
            }
        } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
            // auth failed; clear out login details
            updateLogin(baseContext, "", "")
            throw exc
        }
        updateLogin(baseContext, username, auth.hkey)
        finishWithAnimation(ActivityTransitionAnimation.Direction.FADE)
    }
}

private fun updateLogin(context: Context, username: String, hkey: String?) {
    val preferences = AnkiDroidApp.getSharedPrefs(context)
    preferences.edit {
        putString(SyncPreferences.USERNAME, username)
        putString(SyncPreferences.HKEY, hkey)
    }
}

private fun cancelSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortSync()
}

private suspend fun handleNormalSync(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    syncMedia: Boolean
) {
    val output = deckPicker.withProgress(
        extractProgress = {
            if (progress.hasNormalSync()) {
                text = progress.normalSync.run { "$added\n$removed" }
            }
        },
        onCancel = ::cancelSync
    ) {
        withCol { newBackend.syncCollection(auth) }
    }

    if (output.hasNewEndpoint()) {
        AnkiDroidApp.getSharedPrefs(deckPicker).edit {
            putString(SyncPreferences.CURRENT_SYNC_URI, output.newEndpoint)
        }
    }
    // Save current host number (legacy)
    HostNumFactory.getInstance(deckPicker).hostNum = output.hostNumber

    when (output.required) {
        // a successful sync returns this value
        SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
            // scheduler version may have changed
            withCol { _loadScheduler() }
            deckPicker.showSyncLogMessage(R.string.sync_database_acknowledge, output.serverMessage)
            deckPicker.refreshState()
            // kick off media sync - future implementations may want to run this in the
            // background instead
            if (syncMedia) handleMediaSync(deckPicker, auth)
        }

        SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD -> {
            handleDownload(deckPicker, auth, syncMedia)
        }

        SyncCollectionResponse.ChangesRequired.FULL_UPLOAD -> {
            handleUpload(deckPicker, auth, syncMedia)
        }

        SyncCollectionResponse.ChangesRequired.FULL_SYNC -> {
            deckPicker.showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CONFLICT_RESOLUTION)
        }

        SyncCollectionResponse.ChangesRequired.NORMAL_SYNC,
        SyncCollectionResponse.ChangesRequired.UNRECOGNIZED,
        null -> {
            TODO("should never happen")
        }
    }
}

private fun fullDownloadProgress(title: String): ProgressContext.() -> Unit {
    return {
        if (progress.hasFullSync()) {
            text = title
            amount = progress.fullSync.run { Pair(transferred, total) }
        }
    }
}

private suspend fun handleDownload(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    syncMedia: Boolean
) {
    deckPicker.withProgress(
        extractProgress = fullDownloadProgress(TR.syncDownloadingFromAnkiweb()),
        onCancel = ::cancelSync
    ) {
        withCol {
            try {
                newBackend.createBackup(
                    BackupManager.getBackupDirectoryFromCollection(path),
                    force = true,
                    waitForCompletion = true
                )
                close(save = true, downgrade = false, forFullSync = true)
                newBackend.fullDownload(auth)
            } finally {
                reopen(afterFullSync = true)
            }
        }
        deckPicker.refreshState()
        if (syncMedia) handleMediaSync(deckPicker, auth)
    }

    Timber.i("Full Download Completed")
    deckPicker.showSyncLogMessage(R.string.backup_full_sync_from_server, "")
}

private suspend fun handleUpload(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    syncMedia: Boolean
) {
    deckPicker.withProgress(
        extractProgress = fullDownloadProgress(TR.syncUploadingToAnkiweb()),
        onCancel = ::cancelSync
    ) {
        withCol {
            close(save = true, downgrade = false, forFullSync = true)
            try {
                newBackend.fullUpload(auth)
            } finally {
                reopen(afterFullSync = true)
            }
        }
        deckPicker.refreshState()
        if (syncMedia) handleMediaSync(deckPicker, auth)
    }
    Timber.i("Full Upload Completed")
    deckPicker.showSyncLogMessage(R.string.sync_log_uploading_message, "")
}

// TODO: this needs a dedicated UI for media syncing, and needs to expose
// a way to interrupt the sync

private fun cancelMediaSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortMediaSync()
}

private suspend fun handleMediaSync(
    deckPicker: DeckPicker,
    auth: SyncAuth
) {
    val backend = CollectionManager.getBackend()
    // TODO: show this in a way that is clear it can be continued in background,
    // but also warn user that media files will not be available until it completes.
    // TODO: provide a way for users to abort later, and see it's still going
    val dialog = AlertDialog.Builder(deckPicker)
        .setTitle(TR.syncMediaLogTitle())
        .setMessage("")
        .setPositiveButton("Background") { _, _ -> }
        .setOnCancelListener { cancelMediaSync(backend) }
        .show()
    try {
        backend.withProgress(
            extractProgress = {
                if (progress.hasMediaSync()) {
                    text =
                        progress.mediaSync.run { "\n$added\n$removed\n$checked" }
                }
            },
            updateUi = {
                dialog.setMessage(text)
            }
        ) {
            withContext(Dispatchers.IO) {
                backend.syncMedia(auth)
            }
        }
    } finally {
        dialog.dismiss()
    }
}
