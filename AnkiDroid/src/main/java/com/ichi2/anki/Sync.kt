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

// This is a minimal example of integrating the new backend sync code into AnkiDroid.
// Work is required to make it robust: error handling, showing progress in the GUI instead
// of the console, keeping the screen on, preventing the user from interacting while syncing,
// etc.
//
// BackendFactory.defaultLegacySchema must be false to use this code.
//

package com.ichi2.anki

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.syncAuth
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.web.HostNumFactory
import com.ichi2.async.Connection
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.createBackup
import com.ichi2.libanki.sync.*
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber

fun DeckPicker.handleNewSync(
    hkey: String,
    hostNum: Int,
    conflict: Connection.ConflictResolution?
) {
    val auth = syncAuth {
        this.hkey = hkey
        this.hostNumber = hostNum
    }
    val deckPicker = this
    launchCatchingCollectionTask { col ->
        try {
            when (conflict) {
                Connection.ConflictResolution.FULL_DOWNLOAD -> handleDownload(deckPicker, col, auth)
                Connection.ConflictResolution.FULL_UPLOAD -> handleUpload(deckPicker, col, auth)
                null -> handleNormalSync(deckPicker, col, auth)
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
    launchCatchingCollectionTask { col ->
        val auth = try {
            runInBackgroundWithProgress(col.backend, {}, onCancel = ::cancelSync) {
                col.syncLogin(username, password)
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
        putString("username", username)
        putString("hkey", hkey)
    }
}

private fun cancelSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortSync()
}

private suspend fun handleNormalSync(
    deckPicker: DeckPicker,
    col: CollectionV16,
    auth: SyncAuth
) {
    val output = deckPicker.runInBackgroundWithProgress(
        col.backend,
        extractProgress = {
            if (progress.hasNormalSync()) {
                text = progress.normalSync.run { "$added\n$removed" }
            }
        },
        onCancel = ::cancelSync
    ) {
        col.syncCollection(auth)
    }

    // Save current host number
    HostNumFactory.getInstance(deckPicker).setHostNum(output.hostNumber)

    when (output.required) {
        SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
            // a successful sync returns this value
            deckPicker.showSyncLogMessage(R.string.sync_database_acknowledge, output.serverMessage)
            // kick off media sync - future implementations may want to run this in the
            // background instead
            handleMediaSync(deckPicker, col, auth)
        }

        SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD -> {
            handleDownload(deckPicker, col, auth)
            handleMediaSync(deckPicker, col, auth)
        }

        SyncCollectionResponse.ChangesRequired.FULL_UPLOAD -> {
            handleUpload(deckPicker, col, auth)
            handleMediaSync(deckPicker, col, auth)
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
    col: CollectionV16,
    auth: SyncAuth
) {
    deckPicker.runInBackgroundWithProgress(
        col.backend,
        extractProgress = fullDownloadProgress(col.tr.syncDownloadingFromAnkiweb()),
        onCancel = ::cancelSync
    ) {
        val helper = CollectionHelper.getInstance()
        helper.lockCollection()
        try {
            col.createBackup(
                BackupManager.getBackupDirectoryFromCollection(col.path),
                force = true,
                waitForCompletion = true
            )
            col.close(save = true, downgrade = false, forFullSync = true)
            col.fullDownload(auth)
        } finally {
            col.reopen(afterFullSync = true)
            helper.unlockCollection()
        }
    }

    Timber.i("Full Download Completed")
    deckPicker.showSyncLogMessage(R.string.backup_full_sync_from_server, "")
}

private suspend fun handleUpload(
    deckPicker: DeckPicker,
    col: CollectionV16,
    auth: SyncAuth
) {
    deckPicker.runInBackgroundWithProgress(
        col.backend,
        extractProgress = fullDownloadProgress(col.tr.syncUploadingToAnkiweb()),
        onCancel = ::cancelSync
    ) {
        val helper = CollectionHelper.getInstance()
        helper.lockCollection()
        col.close(save = true, downgrade = false, forFullSync = true)
        try {
            col.fullUpload(auth)
        } finally {
            col.reopen(afterFullSync = true)
            helper.unlockCollection()
        }
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
    col: CollectionV16,
    auth: SyncAuth
) {
    // TODO: show this in a way that is clear it can be continued in background,
    // but also warn user that media files will not be available until it completes.
    // TODO: provide a way for users to abort later, and see it's still going
    val dialog = AlertDialog.Builder(deckPicker)
        .setTitle(col.tr.syncMediaLogTitle())
        .setMessage("")
        .setPositiveButton("Background") { _, _ -> }
        .show()
    try {
        col.backend.withProgress(
            extractProgress = {
                if (progress.hasMediaSync()) {
                    text =
                        progress.mediaSync.run { "\n$added\n$removed\n$checked" }
                }
            },
            updateUi = {
                dialog.setMessage(text)
            },
        ) {
            runInBackground {
                col.syncMedia(auth)
            }
        }
    } finally {
        dialog.dismiss()
    }
}
