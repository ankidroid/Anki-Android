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
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.KeyEvent
import android.view.WindowManager
import androidx.annotation.StringRes
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
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.utils.*
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber
import java.net.UnknownHostException
import kotlin.math.abs

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
    val preferences = AnkiDroidApp.getSharedPrefs(context)
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
    val endpoint = getEndpoint(this)
    launchCatchingTask {
        val auth = try {
            withProgress({}, onCancel = ::cancelSync) {
                withCol {
                    newBackend.syncLogin(username, password, endpoint)
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

fun DeckPicker.createSyncListener() = object : Connection.CancellableTaskListener {
    private var mCurrentMessage: String? = null
    private var mCountUp: Long = 0
    private var mCountDown: Long = 0
    private var mDialogDisplayFailure = false
    override fun onDisconnected() {
        showSyncLogMessage(R.string.youre_offline, "")
    }

    override fun onCancelled() {
        showSyncLogMessage(R.string.sync_cancelled, "")
        if (!mDialogDisplayFailure) {
            mProgressDialog!!.dismiss()
            // update deck list in case sync was cancelled during media sync and main sync was actually successful
            updateDeckList()
        }
        // reset our display failure fate, just in case it is re-used
        mDialogDisplayFailure = false
    }

    override fun onPreExecute() {
        mCountUp = 0
        mCountDown = 0
        val syncStartTime = TimeManager.time.intTimeMS()
        if (mProgressDialog == null || !mProgressDialog!!.isShowing) {
            try {
                mProgressDialog = StyledProgressDialog.show(
                    this@createSyncListener,
                    resources.getString(R.string.sync_title),
                    """
                                ${resources.getString(R.string.sync_title)}
                                ${resources.getString(R.string.sync_up_down_size, mCountUp, mCountDown)}
                    """.trimIndent(),
                    false
                )
            } catch (e: WindowManager.BadTokenException) {
                // If we could not show the progress dialog to start even, bail out - user will get a message
                Timber.w(e, "Unable to display Sync progress dialog, Activity not valid?")
                mDialogDisplayFailure = true
                Connection.cancel()
                return
            }

            // Override the back key so that the user can cancel a sync which is in progress
            mProgressDialog!!.setOnKeyListener { _: DialogInterface?, keyCode: Int, event: KeyEvent ->
                // Make sure our method doesn't get called twice
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && Connection.isCancellable &&
                    !Connection.isCancelled
                ) {
                    // If less than 2s has elapsed since sync started then don't ask for confirmation
                    if (TimeManager.time.intTimeMS() - syncStartTime < 2000) {
                        Connection.cancel()
                        @Suppress("Deprecation")
                        mProgressDialog!!.setMessage(getString(R.string.sync_cancel_message))
                        return@setOnKeyListener true
                    }
                    // Show confirmation dialog to check if the user wants to cancel the sync
                    AlertDialog.Builder(mProgressDialog!!.context).show {
                        message(R.string.cancel_sync_confirm)
                        cancelable(false)
                        positiveButton(R.string.dialog_ok) {
                            @Suppress("Deprecation")
                            mProgressDialog!!.setMessage(getString(R.string.sync_cancel_message))
                            Connection.cancel()
                        }
                        negativeButton(R.string.continue_sync)
                    }
                    return@setOnKeyListener true
                } else {
                    return@setOnKeyListener false
                }
            }
        }

        // Store the current time so that we don't bother the user with a sync prompt for another 10 minutes
        // Note: getLs() in Libanki doesn't take into account the case when no changes were found, or sync cancelled
        AnkiDroidApp.getSharedPrefs(baseContext).edit { putLong("lastSyncTime", syncStartTime) }
    }

    override fun onProgressUpdate(vararg values: Any?) {
        val res = resources
        if (values[0] is Int) {
            val id = values[0] as Int
            if (id != 0) {
                mCurrentMessage = res.getString(id)
            }
            if (values.size >= 3) {
                mCountUp = values[1] as Long
                mCountDown = values[2] as Long
            }
        } else if (values[0] is String) {
            mCurrentMessage = values[0] as String
            if (values.size >= 3) {
                mCountUp = values[1] as Long
                mCountDown = values[2] as Long
            }
        }
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            @Suppress("Deprecation")
            mProgressDialog!!.setMessage(
                """
    $mCurrentMessage
    
                """.trimIndent() +
                    res
                        .getString(R.string.sync_up_down_size, mCountUp / 1024, mCountDown / 1024)
            )
        }
    }

    override fun onPostExecute(data: Connection.Payload) {
        mPullToSyncWrapper.isRefreshing = false
        var dialogMessage: String? = ""
        Timber.d("Sync Listener onPostExecute()")
        val res = resources
        try {
            if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                mProgressDialog!!.dismiss()
            }
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Could not dismiss mProgressDialog. The Activity must have been destroyed while the AsyncTask was running")
            CrashReportService.sendExceptionReport(e, "DeckPicker.onPostExecute", "Could not dismiss mProgressDialog")
        }
        val syncMessage = data.message
        Timber.i("Sync Listener: onPostExecute: Data: %s", data.toString())
        if (!data.success) {
            val result = data.result
            val resultType = data.resultType
            if (resultType != null) {
                when (resultType) {
                    Syncer.ConnectionResultType.BAD_AUTH -> {
                        // delete old auth information
                        AnkiDroidApp.getSharedPrefs(baseContext).edit {
                            putString("username", "")
                            putString("hkey", "")
                        }
                        // then show not logged in dialog
                        showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
                    }
                    Syncer.ConnectionResultType.NO_CHANGES -> {
                        SyncStatus.markSyncCompleted()
                        // show no changes message, use false flag so we don't show "sync error" as the Dialog title
                        showSyncLogMessage(R.string.sync_no_changes_message, "")
                    }
                    Syncer.ConnectionResultType.CLOCK_OFF -> {
                        val diff = result[0] as Long
                        dialogMessage = when {
                            diff >= 86100 -> {
                                // The difference if more than a day minus 5 minutes acceptable by ankiweb error
                                res.getString(
                                    R.string.sync_log_clocks_unsynchronized,
                                    diff,
                                    res.getString(R.string.sync_log_clocks_unsynchronized_date)
                                )
                            }
                            abs(diff % 3600.0 - 1800.0) >= 1500.0 -> {
                                // The difference would be within limit if we adjusted the time by few hours
                                // It doesn't work for all timezones, but it covers most and it's a guess anyway
                                res.getString(
                                    R.string.sync_log_clocks_unsynchronized,
                                    diff,
                                    res.getString(R.string.sync_log_clocks_unsynchronized_tz)
                                )
                            }
                            else -> {
                                res.getString(R.string.sync_log_clocks_unsynchronized, diff, "")
                            }
                        }
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.FULL_SYNC -> if (col.isEmpty) {
                        // don't prompt user to resolve sync conflict if local collection empty
                        sync(Connection.ConflictResolution.FULL_DOWNLOAD)
                        // TODO: Also do reverse check to see if AnkiWeb collection is empty if Anki Desktop
                        // implements it
                    } else {
                        // If can't be resolved then automatically then show conflict resolution dialog
                        showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CONFLICT_RESOLUTION)
                    }
                    Syncer.ConnectionResultType.BASIC_CHECK_FAILED -> {
                        dialogMessage = res.getString(R.string.sync_basic_check_failed, res.getString(R.string.check_db))
                        showSyncErrorDialog(
                            SyncErrorDialog.DIALOG_SYNC_BASIC_CHECK_ERROR,
                            joinSyncMessages(dialogMessage, syncMessage)
                        )
                    }
                    Syncer.ConnectionResultType.DB_ERROR -> showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CORRUPT_COLLECTION, syncMessage)
                    Syncer.ConnectionResultType.OVERWRITE_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_overwrite_error)
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.REMOTE_DB_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_remote_db_error)
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.SD_ACCESS_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_write_access_error_on_storage)
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.FINISH_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_log_finish_error)
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.CONNECTION_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_connection_error)
                        if (result[0] is Exception) {
                            dialogMessage += """
                                    
                                    
                                    ${(result[0] as Exception).localizedMessage}
                            """.trimIndent()
                        }
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.IO_EXCEPTION -> handleDbError()
                    Syncer.ConnectionResultType.GENERIC_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_generic_error)
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.OUT_OF_MEMORY_ERROR -> {
                        dialogMessage = res.getString(R.string.error_insufficient_memory)
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.SANITY_CHECK_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_sanity_failed)
                        showSyncErrorDialog(
                            SyncErrorDialog.DIALOG_SYNC_SANITY_ERROR,
                            joinSyncMessages(dialogMessage, syncMessage)
                        )
                    }
                    Syncer.ConnectionResultType.SERVER_ABORT -> // syncMsg has already been set above, no need to fetch it here.
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    Syncer.ConnectionResultType.MEDIA_SYNC_SERVER_ERROR -> {
                        dialogMessage = res.getString(R.string.sync_media_error_check)
                        showSyncErrorDialog(
                            SyncErrorDialog.DIALOG_MEDIA_SYNC_ERROR,
                            joinSyncMessages(dialogMessage, syncMessage)
                        )
                    }
                    Syncer.ConnectionResultType.CUSTOM_SYNC_SERVER_URL -> {
                        val url = if (result.isNotEmpty() && result[0] is CustomSyncServerUrlException) (result[0] as CustomSyncServerUrlException).url else "unknown"
                        dialogMessage = res.getString(R.string.sync_error_invalid_sync_server, url)
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                    Syncer.ConnectionResultType.NETWORK_ERROR -> {
                        showSnackbar(R.string.check_network) {
                            setAction(R.string.sync_even_if_offline) {
                                Connection.allowLoginSyncOnNoConnection = true
                                sync()
                            }
                        }
                    }
                    else -> {
                        if (result.isNotEmpty() && result[0] is Int) {
                            val code = result[0] as Int
                            dialogMessage = getMessageFromSyncErrorCode(resources, code)
                            if (dialogMessage == null) {
                                dialogMessage = res.getString(
                                    R.string.sync_log_error_specific,
                                    code.toString(),
                                    result[1]
                                )
                            }
                        } else {
                            dialogMessage = res.getString(R.string.sync_log_error_specific, (-1).toString(), resultType)
                        }
                        showSyncErrorMessage(
                            joinSyncMessages(
                                dialogMessage,
                                syncMessage
                            )
                        )
                    }
                }
            } else {
                dialogMessage = res.getString(R.string.sync_generic_error)
                showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
            }
        } else {
            Timber.i("Sync was successful")
            if (data.data[2] != null && "" != data.data[2]) {
                Timber.i("Syncing had additional information")
                // There was a media error, so show it
                // Note: Do not log this data. May contain user email.
                val message = res.getString(R.string.sync_database_acknowledge) + "\n\n" + data.data[2]
                showSimpleMessageDialog(message)
            } else if (data.data.isNotEmpty() && data.data[0] is Connection.ConflictResolution) {
                // A full sync occurred
                when (data.data[0] as Connection.ConflictResolution) {
                    Connection.ConflictResolution.FULL_UPLOAD -> {
                        Timber.i("Full Upload Completed")
                        showSyncLogMessage(R.string.sync_log_uploading_message, syncMessage)
                    }
                    Connection.ConflictResolution.FULL_DOWNLOAD -> {
                        Timber.i("Full Download Completed")
                        showSyncLogMessage(R.string.backup_full_sync_from_server, syncMessage)
                    }
                }
            } else {
                Timber.i("Regular sync completed successfully")
                showSyncLogMessage(R.string.sync_database_acknowledge, syncMessage)
            }
            // Mark sync as completed - then refresh the sync icon
            SyncStatus.markSyncCompleted()
            invalidateOptionsMenu()
            updateDeckList()
            WidgetStatus.update(this@createSyncListener)
            if (fragmented) {
                try {
                    loadStudyOptionsFragment(false)
                } catch (e: IllegalStateException) {
                    // Activity was stopped or destroyed when the sync finished. Losing the
                    // fragment here is fine since we build a fresh fragment on resume anyway.
                    Timber.w(e, "Failed to load StudyOptionsFragment after sync.")
                }
            }
        }
    }
}

/**
 * Show simple error dialog with just the message and OK button. Reload the activity when dialog closed.
 */
private fun DeckPicker.showSyncErrorMessage(message: String?) {
    val title = resources.getString(R.string.sync_error)
    showSimpleMessageDialog(title = title, message = message, reload = true)
}

/**
 * Show a simple snackbar message or notification if the activity is not in foreground
 * @param messageResource String resource for message
 */
fun DeckPicker.showSyncLogMessage(@StringRes messageResource: Int, syncMessage: String?) {
    if (mActivityPaused) {
        val res = AnkiDroidApp.appResources
        showSimpleNotification(
            res.getString(R.string.app_name),
            res.getString(messageResource),
            Channel.SYNC
        )
    } else {
        if (syncMessage.isNullOrEmpty()) {
            if (messageResource == R.string.youre_offline && !Connection.allowLoginSyncOnNoConnection) {
                // #6396 - Add a temporary "Try Anyway" button until we sort out `isOnline`
                showSnackbar(messageResource) {
                    setAction(R.string.sync_even_if_offline) {
                        Connection.allowLoginSyncOnNoConnection = true
                        sync()
                    }
                }
            } else {
                showSnackbar(messageResource)
            }
        } else {
            val res = AnkiDroidApp.appResources
            showSimpleMessageDialog(title = res.getString(messageResource), message = syncMessage)
        }
    }
}

fun getMessageFromSyncErrorCode(res: Resources, code: Int): String? = when (code) {
    407 -> res.getString(R.string.sync_error_407_proxy_required)
    409 -> res.getString(R.string.sync_error_409)
    413 -> res.getString(R.string.sync_error_413_collection_size)
    500 -> res.getString(R.string.sync_error_500_unknown)
    501 -> res.getString(R.string.sync_error_501_upgrade_required)
    502 -> res.getString(R.string.sync_error_502_maintenance)
    503 -> res.getString(R.string.sync_too_busy)
    504 -> res.getString(R.string.sync_error_504_gateway_timeout)
    else -> null
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
