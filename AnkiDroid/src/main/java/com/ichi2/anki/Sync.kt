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

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.syncAuth
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.libanki.ChangeManager.notifySubscribersAllValuesChanged
import com.ichi2.libanki.createBackup
import com.ichi2.libanki.fullUploadOrDownload
import com.ichi2.libanki.syncCollection
import com.ichi2.libanki.syncLogin
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.preferences.VersatileTextWithASwitchPreference
import com.ichi2.utils.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
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
    FULL_UPLOAD;
}

data class SyncCompletion(val isSuccess: Boolean)
interface SyncCompletionListener {
    fun onMediaSyncCompleted(data: SyncCompletion)
}

fun DeckPicker.syncAuth(): SyncAuth? {
    val preferences = this.sharedPrefs()

    // Grab custom sync certificate from preferences (default is the empty string) and set it in CollectionManager
    val currentSyncCertificate = preferences.getString(SyncPreferences.CUSTOM_SYNC_CERTIFICATE, "") ?: ""
    CollectionManager.updateCustomCertificate(currentSyncCertificate)

    val hkey = preferences.getString(SyncPreferences.HKEY, null)
    val resolvedEndpoint = getEndpoint(this)
    return hkey?.let {
        syncAuth {
            this.hkey = hkey
            if (resolvedEndpoint != null) {
                this.endpoint = resolvedEndpoint
            }
        }
    }
}

fun getEndpoint(context: Context): String? {
    val preferences = context.sharedPrefs()
    val currentEndpoint = preferences.getString(SyncPreferences.CURRENT_SYNC_URI, null)
    val customEndpoint = if (preferences.getBoolean(SyncPreferences.CUSTOM_SYNC_ENABLED, false)) {
        preferences.getString(SyncPreferences.CUSTOM_SYNC_URI, null)
    } else {
        null
    }
    return currentEndpoint ?: customEndpoint
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
    AnkiDroidApp.instance.sharedPrefs().getString(SyncPreferences.HKEY, "")!!.isNotEmpty()

fun millisecondsSinceLastSync(preferences: SharedPreferences) = TimeManager.time.intTimeMS() - preferences.getLong("lastSyncTime", 0)

fun DeckPicker.handleNewSync(
    conflict: ConflictResolution?,
    syncMedia: Boolean
) {
    val auth = this.syncAuth() ?: return
    val deckPicker = this
    launchCatchingTask {
        try {
            when (conflict) {
                ConflictResolution.FULL_DOWNLOAD -> handleDownload(deckPicker, auth, deckPicker.mediaUsnOnConflict)
                ConflictResolution.FULL_UPLOAD -> handleUpload(deckPicker, auth, deckPicker.mediaUsnOnConflict)
                null -> {
                    handleNormalSync(deckPicker, auth, syncMedia)
                }
            }
        } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
            // auth failed; log out
            updateLogin(baseContext, "", "")
            throw exc
        }
        withCol { notetypes.clearCache() }
        notifySubscribersAllValuesChanged(deckPicker)
        setLastSyncTimeToNow()
        refreshState()
    }
}

fun MyAccount.handleNewLogin(username: String, password: String, resultLauncher: ActivityResultLauncher<String>) {
    val endpoint = getEndpoint(this)
    launchCatchingTask {
        val auth = try {
            withProgress(
                extractProgress = {
                    text = getString(R.string.sign_in)
                },
                onCancel = ::cancelSync
            ) {
                withCol {
                    syncLogin(username, password, endpoint)
                }
            }
        } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
            // auth failed; clear out login details
            updateLogin(baseContext, "", "")
            throw exc
        }
        updateLogin(baseContext, username, auth.hkey)
        setResult(RESULT_OK)
        MyAccount.checkNotificationPermission(this@handleNewLogin, resultLauncher)
        finish()
    }
}

private fun updateLogin(context: Context, username: String, hkey: String?) {
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

private suspend fun handleNormalSync(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    syncMedia: Boolean
) {
    var auth2 = auth
    val output = deckPicker.withProgress(
        extractProgress = {
            if (progress.hasNormalSync()) {
                text = progress.normalSync.run { "$added\n$removed" }
            }
        },
        onCancel = ::cancelSync,
        manualCancelButton = R.string.dialog_cancel
    ) {
        withCol {
            syncCollection(auth2, media = false) // media is synced by SyncMediaWorker
        }
    }

    if (output.hasNewEndpoint()) {
        deckPicker.sharedPrefs().edit {
            putString(SyncPreferences.CURRENT_SYNC_URI, output.newEndpoint)
        }
        auth2 = syncAuth { this.hkey = auth.hkey; endpoint = output.newEndpoint }
    }
    val mediaUsn = if (syncMedia) { output.serverMediaUsn } else { null }

    when (output.required) {
        // a successful sync returns this value
        SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
            // scheduler version may have changed
            withCol { _loadScheduler() }
            val message = if (syncMedia) R.string.col_synced_media_in_background else R.string.sync_database_acknowledge
            deckPicker.showSyncLogMessage(message, output.serverMessage)
            deckPicker.refreshState()
            if (syncMedia) {
                SyncMediaWorker.start(deckPicker, auth2)
            }
        }

        SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD -> {
            handleDownload(deckPicker, auth2, mediaUsn)
        }

        SyncCollectionResponse.ChangesRequired.FULL_UPLOAD -> {
            handleUpload(deckPicker, auth2, mediaUsn)
        }

        SyncCollectionResponse.ChangesRequired.FULL_SYNC -> {
            deckPicker.mediaUsnOnConflict = mediaUsn
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
    mediaUsn: Int?
) {
    deckPicker.withProgress(
        extractProgress = fullDownloadProgress(TR.syncDownloadingFromAnkiweb()),
        onCancel = ::cancelSync
    ) {
        withCol {
            try {
                createBackup(
                    BackupManager.getBackupDirectoryFromCollection(path),
                    force = true,
                    waitForCompletion = true
                )
                close(downgrade = false, forFullSync = true)
                fullUploadOrDownload(auth, upload = false, serverUsn = mediaUsn)
            } finally {
                reopen(afterFullSync = true)
            }
        }
        deckPicker.refreshState()
        if (mediaUsn != null) {
            SyncMediaWorker.start(deckPicker, auth)
        }
    }

    Timber.i("Full Download Completed")
    deckPicker.showSyncLogMessage(R.string.backup_one_way_sync_from_server, "")
}

private suspend fun handleUpload(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    mediaUsn: Int?
) {
    deckPicker.withProgress(
        extractProgress = fullDownloadProgress(TR.syncUploadingToAnkiweb()),
        onCancel = ::cancelSync
    ) {
        withCol {
            close(downgrade = false, forFullSync = true)
            try {
                fullUploadOrDownload(auth, upload = true, serverUsn = mediaUsn)
            } finally {
                reopen(afterFullSync = true)
            }
        }
        deckPicker.refreshState()
        if (mediaUsn != null) {
            SyncMediaWorker.start(deckPicker, auth)
        }
    }
    Timber.i("Full Upload Completed")
    deckPicker.showSyncLogMessage(R.string.sync_log_uploading_message, "")
}

fun cancelMediaSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortMediaSync()
}

/**
 * Whether media should be fetched on sync. Options from preferences are:
 * * Always
 * * Only if unmetered
 * * Never
 */
fun DeckPicker.shouldFetchMedia(preferences: SharedPreferences): Boolean {
    val always = getString(R.string.sync_media_always_value)
    val onlyIfUnmetered = getString(R.string.sync_media_only_unmetered_value)
    val shouldFetchMedia = preferences.getString(getString(R.string.sync_fetch_media_key), always)
    return shouldFetchMedia == always ||
        (shouldFetchMedia == onlyIfUnmetered && !NetworkUtils.isActiveNetworkMetered())
}

suspend fun monitorMediaSync(
    deckPicker: DeckPicker
) {
    val backend = CollectionManager.getBackend()
    val scope = CoroutineScope(Dispatchers.IO)
    var isAborted = false

    val dialog = withContext(Dispatchers.Main) {
        AlertDialog.Builder(deckPicker)
            .setTitle(TR.syncMediaLogTitle())
            .setMessage("")
            .setPositiveButton(R.string.dialog_continue) { _, _ ->
                scope.cancel()
            }
            .setNegativeButton(TR.syncAbortButton()) { _, _ ->
                isAborted = true
                cancelMediaSync(backend)
            }
            .show()
    }

    fun showMessage(msg: String) = deckPicker.showSnackbar(msg, Snackbar.LENGTH_SHORT)

    scope.launch {
        try {
            while (true) {
                // this will throw if the sync exited with an error
                val resp = backend.mediaSyncStatus()
                if (!resp.active) {
                    break
                }
                val text = resp.progress.run { "$added\n$removed\n$checked" }
                dialog.setMessage(text)
                delay(100)
            }
            showMessage(if (isAborted) TR.syncMediaAborted() else TR.syncMediaComplete())
        } catch (_: BackendInterruptedException) {
            showMessage(TR.syncMediaAborted())
        } catch (_: CancellationException) {
            // do nothing
        } catch (_: Exception) {
            showMessage(TR.syncMediaFailed())
        } finally {
            dialog.dismiss()
        }
    }
}

/**
 * Show a simple snackbar message or notification if the activity is not in foreground
 * @param messageResource String resource for message
 */
fun DeckPicker.showSyncLogMessage(@StringRes messageResource: Int, syncMessage: String?) {
    if (activityPaused) {
        val res = AnkiDroidApp.appResources
        showSimpleNotification(
            res.getString(R.string.app_name),
            res.getString(messageResource),
            Channel.SYNC
        )
    } else {
        if (syncMessage.isNullOrEmpty()) {
            showSnackbar(messageResource)
        } else {
            val res = AnkiDroidApp.appResources
            showSimpleMessageDialog(title = res.getString(messageResource), message = syncMessage)
        }
    }
}

fun Context.setLastSyncTimeToNow() {
    sharedPrefs().edit {
        putLong("lastSyncTime", TimeManager.time.intTimeMS())
    }
}

fun joinSyncMessages(dialogMessage: String?, syncMessage: String?): String? {
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
