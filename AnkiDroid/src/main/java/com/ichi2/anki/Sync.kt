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
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import anki.collection.Progress
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.web.HostNumFactory
import com.ichi2.async.Connection
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.getProgress
import com.ichi2.libanki.sync.*
import kotlinx.coroutines.*
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber

fun DeckPicker.handleNewSync(
    hkey: String,
    hostNum: Int,
    conflict: Connection.ConflictResolution?
) {
    val auth = SyncAuth.newBuilder().apply {
        this.hkey = hkey
        this.hostNumber = hostNum
    }.build()

    val col = CollectionHelper.getInstance().getCol(baseContext).newBackend
    val deckPicker = this

    lifecycleScope.launch {
        try {
            when (conflict) {
                Connection.ConflictResolution.FULL_DOWNLOAD -> handleDownload(col, auth, deckPicker)
                Connection.ConflictResolution.FULL_UPLOAD -> handleUpload(col, auth, deckPicker)
                null -> handleNormalSync(baseContext, col, auth, deckPicker)
            }
        } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
            // auth failed; log out
            AnkiDroidApp.getSharedPrefs(baseContext).edit {
                putString("hkey", "")
            }
            Timber.e("login failed")
            // FIXME: inform user
        } catch (exc: Exception) {
            Timber.e("exception in sync: $exc")
            // FIXME: inform user
        }
        deckPicker.refreshState()
    }
}

fun MyAccount.handleNewLogin(username: String, password: String) {
    val col = CollectionHelper.getInstance().getCol(baseContext).newBackend
    lifecycleScope.launch {
        val auth = try {
            runInBackgroundWithProgress(col, { }) {
                col.syncLogin(username, password)
            }
        } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
            Timber.e("login failed")
            // FIXME: inform user
            // auth failed; return empty values so preferences below logs us out
            SyncAuth.newBuilder().build()
        }
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        preferences.edit {
            putString("username", username)
            putString("hkey", auth.hkey)
        }
        finishWithAnimation(ActivityTransitionAnimation.Direction.FADE)
    }
}

private suspend fun handleNormalSync(
    context: Context,
    col: CollectionV16,
    auth: SyncAuth,
    deckPicker: DeckPicker
) {
    val output = runInBackgroundWithProgress(col, {
        if (it.hasNormalSync()) {
            it.normalSync.run { updateProgress("$added $removed") }
        }
    }) {
        col.syncCollection(auth)
    }

    // Save current host number
    HostNumFactory.getInstance(context).setHostNum(output.hostNumber)

    when (output.required) {
        SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
            // a successful sync returns this value
            deckPicker.showSyncLogMessage(R.string.sync_database_acknowledge, output.serverMessage)
            // kick off media sync - future implementations may want to run this in the
            // background instead
            handleMediaSync(col, auth)
        }

        SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD -> {
            handleDownload(col, auth, deckPicker)
            handleMediaSync(col, auth)
        }

        SyncCollectionResponse.ChangesRequired.FULL_UPLOAD -> {
            handleUpload(col, auth, deckPicker)
            handleMediaSync(col, auth)
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

private suspend fun handleDownload(
    col: CollectionV16,
    auth: SyncAuth,
    deckPicker: DeckPicker
) {
    runInBackgroundWithProgress(col, {
        if (it.hasFullSync()) {
            it.fullSync.run { updateProgress("downloaded $transferred/$total") }
        }
    }) {
        // TODO: backup
        col.close(save = true, downgrade = false, forFullSync = true)
        try {
            col.fullDownload(auth)
        } finally {
            col.reopen(afterFullSync = true)
        }
    }

    Timber.i("Full Download Completed")
    deckPicker.showSyncLogMessage(R.string.backup_full_sync_from_server, "")
}

private suspend fun handleUpload(
    col: CollectionV16,
    auth: SyncAuth,
    deckPicker: DeckPicker
) {
    runInBackgroundWithProgress(col, {
        if (it.hasFullSync()) {
            it.fullSync.run { updateProgress("uploaded $transferred/$total") }
        }
    }) {
        col.close(save = true, downgrade = false, forFullSync = true)
        try {
            col.fullUpload(auth)
        } finally {
            col.reopen(afterFullSync = true)
        }
    }

    Timber.i("Full Upload Completed")
    deckPicker.showSyncLogMessage(R.string.sync_log_uploading_message, "")
}

@Suppress("UNUSED_PARAMETER", "UNREACHABLE_CODE")
private suspend fun handleMediaSync(
    col: CollectionV16,
    auth: SyncAuth
) {
    runInBackgroundWithProgress(col, {
        if (it.hasMediaSync()) {
            it.mediaSync.run { updateProgress("media: $added $removed $checked") }
        }
    }) {
        col.syncMedia(auth)
    }
}

// FIXME: display/update a popup progress window instead of logging
fun updateProgress(text: String) {
    Timber.i("progress: $text")
}

suspend fun <T> runInBackgroundWithProgress(
    col: CollectionV16,
    onProgress: (Progress) -> Unit,
    op: suspend (CollectionV16) -> T
): T = coroutineScope {
    val monitor = launch { monitorProgress(col, onProgress) }
    try {
        withContext(Dispatchers.IO) {
            op(col)
        }
    } finally {
        monitor.cancel()
    }
}

suspend fun monitorProgress(col: CollectionV16, op: (Progress) -> Unit) {
    while (true) {
        try {
            val progress = col.getProgress()
            // on main thread, so op can update UI
            withContext(Dispatchers.Main) {
                op(progress)
            }
        } catch (exc: Exception) {
            Timber.e("exception in monitorProgress: $exc")
            return
        }
        delay(100)
    }
}
