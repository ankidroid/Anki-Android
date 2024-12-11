/****************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.MenuItemCompat
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.SyncStatusResponse
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.MediaCheckDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.checkMedia
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.libanki.ChangeManager.notifySubscribersAllValuesChanged
import com.ichi2.libanki.createBackup
import com.ichi2.libanki.fullUploadOrDownload
import com.ichi2.libanki.syncCollection
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered
import com.ichi2.utils.SyncStatus
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber

/**
 * Class in charge of doing the sync work for another activity.
 */
class SyncHandler(
    private val activity: AnkiActivity,
    private val delegate: SyncHandlerDelegate,
) : SyncErrorDialogListener {


    /** Job running the sync media */
    private var syncMediaProgressJob: Job? = null

    /**
     * Flag to indicate whether the activity will perform a sync in its onResume.
     * Since syncing closes the database, this flag allows us to avoid doing any
     * work in onResume that might use the database and go straight to syncing.
     */
    var syncOnResume = false

    val loginForSyncLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            delegate.syncCallback {
                if (it.resultCode == RESULT_OK) {
                    syncOnResume = true
                }
            },
        )

    val notificationPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Timber.i("notification permission: %b", it)
        }

    /**
     * Part of [OptionsMenuState] related to sync
     */
    var syncMenuState: SyncIconState? = null

    fun setupMediaSyncMenuItem(menu: Menu) {
        // shouldn't be necessary, but `invalidateOptionsMenu()` is called way more than necessary
        syncMediaProgressJob?.cancel()

        val syncItem = menu.findItem(R.id.action_sync)
        val progressIndicator =
            syncItem.actionView
                ?.findViewById<LinearProgressIndicator>(R.id.progress_indicator)

        val workManager = WorkManager.getInstance(activity)
        val flow = workManager.getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)

        syncMediaProgressJob =
            activity.lifecycleScope.launch {
                flow.flowWithLifecycle(activity.lifecycle).collectLatest {
                    val workInfo = it.lastOrNull()
                    if (workInfo?.state == WorkInfo.State.RUNNING && progressIndicator?.isVisible == false) {
                        Timber.i("DeckPicker: Showing media sync progress indicator")
                        progressIndicator.isVisible = true
                    } else if (progressIndicator?.isVisible == true) {
                        Timber.i("DeckPicker: Hiding media sync progress indicator")
                        progressIndicator.isVisible = false
                    }
                }
            }
    }

    fun updateMenuFromState(menu: Menu) {
        syncMenuState?.run {
            updateSyncIconFromState(menu)
        }
    }

    fun updateSyncIconFromState(menu: Menu) {
        val state = syncMenuState ?: return
        val menuItem = menu.findItem(R.id.action_sync)
        val provider =
            MenuItemCompat.getActionProvider(menuItem) as? SyncActionProvider
                ?: return
        val tooltipText =
            when (state) {
                SyncIconState.Normal, SyncIconState.PendingChanges -> R.string.button_sync
                SyncIconState.OneWay -> R.string.sync_menu_title_one_way_sync
                SyncIconState.NotLoggedIn -> R.string.sync_menu_title_no_account
            }
        provider.setTooltipText(activity.getString(tooltipText))
        when (state) {
            SyncIconState.Normal -> {
                BadgeDrawableBuilder.removeBadge(provider)
            }
            SyncIconState.PendingChanges -> {
                BadgeDrawableBuilder(activity)
                    .withColor(activity.getColor(R.color.badge_warning))
                    .replaceBadge(provider)
            }
            SyncIconState.OneWay, SyncIconState.NotLoggedIn -> {
                BadgeDrawableBuilder(activity)
                    .withText('!')
                    .withColor(activity.getColor(R.color.badge_error))
                    .replaceBadge(provider)
            }
        }
    }

    suspend fun updateMenuState() {
        syncMenuState = fetchSyncStatus()
    }

    private suspend fun fetchSyncStatus(): SyncIconState {
        val auth = syncAuth()
        return when (SyncStatus.getSyncStatus(activity, auth)) {
            SyncStatus.BADGE_DISABLED, SyncStatus.NO_CHANGES, SyncStatus.ERROR -> SyncIconState.Normal
            SyncStatus.HAS_CHANGES -> SyncIconState.PendingChanges
            SyncStatus.NO_ACCOUNT -> SyncIconState.NotLoggedIn
            SyncStatus.ONE_WAY -> SyncIconState.OneWay
        }
    }

    /**
     * save [mediaUsnOnConflict] in [outState].
     */
    fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable("mediaUsnOnConflict", mediaUsnOnConflict)
    }

    /**
     * Restore [mediaUsnOnConflict] from [savedInstanceState].
     */
    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        mediaUsnOnConflict = savedInstanceState.getSerializableCompat("mediaUsnOnConflict")
    }

    /**
     * Schedules a background job to find missing, unused and invalid media files.
     * Shows a progress dialog while operation is running.
     * When check is finished a dialog box shows number of missing, unused and invalid media files.
     *
     * If has the storage permission, job is scheduled, otherwise storage permission is asked first.
     */
    override fun mediaCheck() {
        activity.launchCatchingTask {
            val mediaCheckResult = activity.checkMedia()
            delegate.showMediaCheckDialog(MediaCheckDialog.DIALOG_MEDIA_CHECK_RESULTS, mediaCheckResult)
        }
    }


    suspend fun automaticSync(runInBackground: Boolean = false) {
        /**
         * @return whether there are collection changes to be sync.
         *
         * It DOES NOT include if there are media to be synced.
         */
        suspend fun areThereChangesToSync(): Boolean {
            val auth = syncAuth() ?: return false
            val status =
                withContext(Dispatchers.IO) {
                    CollectionManager.getBackend().syncStatus(auth)
                }.required

            return when (status) {
                SyncStatusResponse.Required.NO_CHANGES,
                SyncStatusResponse.Required.UNRECOGNIZED,
                null,
                    -> false
                SyncStatusResponse.Required.FULL_SYNC,
                SyncStatusResponse.Required.NORMAL_SYNC,
                    -> true
            }
        }

        fun syncIntervalPassed(): Boolean {
            val lastSyncTime = activity.sharedPrefs().getLong("lastSyncTime", 0)
            val automaticSyncIntervalInMS = AUTOMATIC_SYNC_MINIMAL_INTERVAL_IN_MINUTES * 60 * 1000
            return TimeManager.time.intTimeMS() - lastSyncTime > automaticSyncIntervalInMS
        }

        val isAutoSyncEnabled = activity.sharedPrefs().getBoolean("automaticSyncMode", false)

        val isBlockedByMeteredConnection =
            !activity.sharedPrefs().getBoolean(activity.getString(R.string.metered_sync_key), false) &&
                    isActiveNetworkMetered()

        when {
            !isAutoSyncEnabled -> Timber.d("autoSync: not enabled")
            isBlockedByMeteredConnection -> Timber.d("autoSync: blocked by metered connection")
            !NetworkUtils.isOnline -> Timber.d("autoSync: offline")
            !runInBackground && !syncIntervalPassed() -> Timber.d("autoSync: interval not passed")
            !isLoggedIn() -> Timber.d("autoSync: not logged in")
            !areThereChangesToSync() -> {
                Timber.d("autoSync: no collection changes to sync. Syncing media if set")
                if (shouldFetchMedia(activity.sharedPrefs())) {
                    val auth = syncAuth() ?: return
                    SyncMediaWorker.start(activity, auth)
                }
                activity.setLastSyncTimeToNow()
            }
            else -> {
                if (runInBackground) {
                    Timber.i("autoSync: starting background")
                    val auth = syncAuth() ?: return
                    SyncWorker.start(activity, auth, shouldFetchMedia(activity.sharedPrefs()))
                } else {
                    Timber.i("autoSync: starting foreground")
                    sync()
                }
            }
        }
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     */
    override fun showSyncErrorDialog(dialogType: Int) {
        showSyncErrorDialog(dialogType, "")
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     * @param message text to show
     */
    override fun showSyncErrorDialog(
        dialogType: Int,
        message: String?,
    ) {
        val newFragment: AsyncDialogFragment = newInstance(dialogType, message)
        activity.showAsyncDialogFragment(newFragment, Channel.SYNC)
    }

    // Callback method to handle database integrity check
    override fun integrityCheck() {
        // #5852 - We were having issues with integrity checks where the users had run out of space.
        // display a dialog box if we don't have the space
        val status = CollectionIntegrityStorageCheck.createInstance(activity)
        if (status.shouldWarnOnIntegrityCheck()) {
            Timber.d("Displaying File Size confirmation")
            AlertDialog.Builder(activity).show {
                title(R.string.check_db_title)
                message(text = status.getWarningDetails(activity))
                positiveButton(R.string.integrity_check_continue_anyway) {
                    performIntegrityCheck()
                }
                negativeButton(R.string.dialog_cancel)
            }
        } else {
            performIntegrityCheck()
        }
    }

    private fun performIntegrityCheck() {
        Timber.i("performIntegrityCheck()")
        activity.handleDatabaseCheck()
    }

    /** In the conflict case, we need to store the USN received from the initial sync, and reuse
    it after the user has decided. */
    var mediaUsnOnConflict: Int? = null

    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     */
    override fun sync(conflict: ConflictResolution?) {
        val preferences = activity.sharedPrefs()

        val hkey = preferences.getString("hkey", "")
        if (hkey!!.isEmpty()) {
            Timber.w("User not logged in")
            delegate.onSyncStart()
            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
            return
        }

        MyAccount.checkNotificationPermission(activity, notificationPermissionLauncher)

        /** Nested private function that makes the connection to
         * the sync server and starts syncing the data */
        fun doSync() {
            handleNewSync(conflict, shouldFetchMedia(preferences))
        }

        // Warn the user in case the connection is metered
        val meteredSyncIsAllowed =
            preferences.getBoolean(activity.getString(R.string.metered_sync_key), false)
        if (!meteredSyncIsAllowed && isActiveNetworkMetered()) {
            AlertDialog.Builder(activity).show {
                message(R.string.metered_sync_data_warning)
                positiveButton(R.string.dialog_continue) { doSync() }
                negativeButton(R.string.dialog_cancel)
                checkBoxPrompt(R.string.button_do_not_show_again) { isCheckboxChecked ->
                    preferences.edit {
                        putBoolean(
                            activity.getString(R.string.metered_sync_key),
                            isCheckboxChecked,
                        )
                    }
                }
            }
        } else {
            doSync()
        }
    }

    override fun loginToSyncServer() {
        val myAccount = Intent(activity, MyAccount::class.java)
        myAccount.putExtra("notLoggedIn", true)
        loginForSyncLauncher.launch(myAccount)
    }

    enum class SyncIconState {
        Normal,
        PendingChanges,
        OneWay,
        NotLoggedIn,
    }

    private fun syncAuth(): SyncAuth? {
        val preferences = activity.sharedPrefs()

        // Grab custom sync certificate from preferences (default is the empty string) and set it in CollectionManager
        val currentSyncCertificate = preferences.getString(SyncPreferences.CUSTOM_SYNC_CERTIFICATE, "") ?: ""
        CollectionManager.updateCustomCertificate(currentSyncCertificate)

        val hkey = preferences.getString(SyncPreferences.HKEY, null)
        val resolvedEndpoint = getEndpoint(activity)
        return hkey?.let {
            anki.sync.syncAuth {
                this.hkey = hkey
                if (resolvedEndpoint != null) {
                    this.endpoint = resolvedEndpoint
                }
            }
        }
    }

    private fun handleNewSync(
        conflict: ConflictResolution?,
        syncMedia: Boolean,
    ) {
        val auth = this.syncAuth() ?: return
        activity.launchCatchingTask {
            try {
                when (conflict) {
                    ConflictResolution.FULL_DOWNLOAD -> handleDownload(auth, mediaUsnOnConflict)
                    ConflictResolution.FULL_UPLOAD -> handleUpload(auth, mediaUsnOnConflict)
                    null -> {
                        handleNormalSync(auth, syncMedia)
                    }
                }
            } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
                // auth failed; log out
                updateLogin(activity, "", "")
                throw exc
            }
            withCol { notetypes.clearCache() }
            notifySubscribersAllValuesChanged(activity)
            activity.setLastSyncTimeToNow()
            delegate.refreshState()
        }
    }

    private suspend fun handleNormalSync(
        auth: SyncAuth,
        syncMedia: Boolean,
    ) {
        var auth2 = auth
        val output =
            activity.withProgress(
                extractProgress = {
                    if (progress.hasNormalSync()) {
                        text = progress.normalSync.run { "$added\n$removed" }
                    }
                },
                onCancel = ::cancelSync,
                manualCancelButton = R.string.dialog_cancel,
            ) {
                withCol {
                    syncCollection(auth2, media = false) // media is synced by SyncMediaWorker
                }
            }

        if (output.hasNewEndpoint()) {
            activity.sharedPrefs().edit {
                putString(SyncPreferences.CURRENT_SYNC_URI, output.newEndpoint)
            }
            auth2 =
                anki.sync.syncAuth {
                    this.hkey = auth.hkey
                    endpoint = output.newEndpoint
                }
        }
        val mediaUsn =
            if (syncMedia) {
                output.serverMediaUsn
            } else {
                null
            }

        when (output.required) {
            // a successful sync returns this value
            SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
                // scheduler version may have changed
                withCol { _loadScheduler() }
                val message = if (syncMedia) R.string.col_synced_media_in_background else R.string.sync_database_acknowledge
                showSyncLogMessage(message, output.serverMessage)
                delegate.refreshState()
                if (syncMedia) {
                    SyncMediaWorker.start(activity, auth2)
                }
            }

            SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD -> {
                handleDownload(auth2, mediaUsn)
            }

            SyncCollectionResponse.ChangesRequired.FULL_UPLOAD -> {
                handleUpload(auth2, mediaUsn)
            }

            SyncCollectionResponse.ChangesRequired.FULL_SYNC -> {
                this.mediaUsnOnConflict = mediaUsn
                showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CONFLICT_RESOLUTION)
            }

            SyncCollectionResponse.ChangesRequired.NORMAL_SYNC,
            SyncCollectionResponse.ChangesRequired.UNRECOGNIZED,
            null,
                -> {
                TODO("should never happen")
            }
        }
    }

    private suspend fun handleDownload(
        auth: SyncAuth,
        mediaUsn: Int?,
    ) {
        activity.withProgress(
            extractProgress = fullDownloadProgress(TR.syncDownloadingFromAnkiweb()),
            onCancel = ::cancelSync,
        ) {
            withCol {
                try {
                    createBackup(
                        BackupManager.getBackupDirectoryFromCollection(path),
                        force = true,
                        waitForCompletion = true,
                    )
                    close(downgrade = false, forFullSync = true)
                    fullUploadOrDownload(auth, upload = false, serverUsn = mediaUsn)
                } finally {
                    reopen(afterFullSync = true)
                }
            }
            delegate.refreshState()
            if (mediaUsn != null) {
                SyncMediaWorker.start(activity, auth)
            }
        }

        Timber.i("Full Download Completed")
        showSyncLogMessage(R.string.backup_one_way_sync_from_server, "")
    }

    private suspend fun handleUpload(
        auth: SyncAuth,
        mediaUsn: Int?,
    ) {
        activity.withProgress(
            extractProgress = fullDownloadProgress(TR.syncUploadingToAnkiweb()),
            onCancel = ::cancelSync,
        ) {
            withCol {
                close(downgrade = false, forFullSync = true)
                try {
                    fullUploadOrDownload(auth, upload = true, serverUsn = mediaUsn)
                } finally {
                    reopen(afterFullSync = true)
                }
            }
            delegate.refreshState()
            if (mediaUsn != null) {
                SyncMediaWorker.start(activity, auth)
            }
        }
        Timber.i("Full Upload Completed")
        showSyncLogMessage(R.string.sync_log_uploading_message, "")
    }

    suspend fun monitorMediaSync() {
        val backend = CollectionManager.getBackend()
        val scope = CoroutineScope(Dispatchers.IO)
        var isAborted = false

        val dialog =
            withContext(Dispatchers.Main) {
                AlertDialog
                    .Builder(activity)
                    .setTitle(TR.syncMediaLogTitle())
                    .setMessage("")
                    .setPositiveButton(R.string.dialog_continue) { _, _ ->
                        scope.cancel()
                    }.setNegativeButton(TR.syncAbortButton()) { _, _ ->
                        isAborted = true
                        cancelMediaSync(backend)
                    }.show()
            }

        fun showMessage(msg: String) = activity.showSnackbar(msg, Snackbar.LENGTH_SHORT)

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

    fun fullDownloadProgress(title: String): ProgressContext.() -> Unit =
        {
            if (progress.hasFullSync()) {
                text = title
                amount = progress.fullSync.run { Pair(transferred, total) }
            }
        }

    /**
     * Whether media should be fetched on sync. Options from preferences are:
     * * Always
     * * Only if unmetered
     * * Never
     */
    private fun shouldFetchMedia(preferences: SharedPreferences): Boolean {
        val always = activity.getString(R.string.sync_media_always_value)
        val onlyIfUnmetered = activity.getString(R.string.sync_media_only_unmetered_value)
        val shouldFetchMedia = preferences.getString(activity.getString(R.string.sync_fetch_media_key), always)
        return shouldFetchMedia == always ||
                (shouldFetchMedia == onlyIfUnmetered && !isActiveNetworkMetered())
    }

    /**
     * Show a simple snackbar message or notification if the activity is not in foreground
     * @param messageResource String resource for message
     */
    fun showSyncLogMessage(
        @StringRes messageResource: Int,
        syncMessage: String?,
    ) {
        if (delegate.activityPaused()) {
            val res = AnkiDroidApp.appResources
            activity.showSimpleNotification(
                res.getString(R.string.app_name),
                res.getString(messageResource),
                Channel.SYNC,
            )
        } else {
            if (syncMessage.isNullOrEmpty()) {
                activity.showSnackbar(messageResource)
            } else {
                val res = AnkiDroidApp.appResources
                activity.showSimpleMessageDialog(title = res.getString(messageResource), message = syncMessage)
            }
        }
    }

    companion object {
        // For automatic syncing
        // 10 minutes in milliseconds..
        private const val AUTOMATIC_SYNC_MINIMAL_INTERVAL_IN_MINUTES: Long = 10
    }
}
