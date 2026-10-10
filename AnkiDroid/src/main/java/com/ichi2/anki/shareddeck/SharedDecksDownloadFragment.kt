// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

package com.ichi2.anki.shareddeck

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CommonString
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.compat.CompatHelper.Companion.registerReceiverCompat
import com.ichi2.anki.shareddeck.SharedDecksActivity.Companion.DOWNLOAD_FILE
import com.ichi2.anki.shareddeck.SharedDecksActivity.Companion.HTTP_STATUS_TOO_MANY_REQUESTS
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.openUrl
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.create
import timber.log.Timber
import java.io.File
import java.net.URLConnection
import com.ichi2.anki.common.android.R as CommonR

/**
 * Used when a download is captured from AnkiWeb shared decks WebView.
 * Only for downloads started via [SharedDecksActivity].
 *
 * Only one download is supported at a time, since importing multiple decks
 * simultaneously is not supported.
 */
class SharedDecksDownloadFragment : Fragment() {
    private val viewModel: SharedDecksDownloadViewModel by viewModels()

    private val fileToBeDownloaded by lazy { arguments?.getSerializableCompat<DownloadFile>(DOWNLOAD_FILE)!! }

    private var downloadId: Long = 0

    private var fileName: String? = null

    private var completedFile: File? = null

    private var handler: Handler = Handler(Looper.getMainLooper())
    private var isProgressCheckerRunning = false

    /**
     * Android's DownloadManager - Used here to manage the functionality of downloading decks, one
     * at a time. Responsible for enqueuing a download and generating the corresponding download ID,
     * removing a download from the queue and providing cursor using a query related to the download ID.
     * Since only one download is supported at a time, the DownloadManager's queue is expected to
     * have a single request at a time.
     */
    private lateinit var downloadManager: DownloadManager

    var isDownloadInProgress = false

    private var downloadCancelConfirmationDialog: AlertDialog? = null
    private val onBackPressedCallback =
        object : OnBackPressedCallback(isDownloadInProgress) {
            override fun handleOnBackPressed() {
                Timber.i("back pressed")
                showCancelConfirmationDialog()
            }
        }

    companion object {
        const val DOWNLOAD_PROGRESS_CHECK_DELAY = 1000L

        const val EXTRA_IS_SHARED_DOWNLOAD = "extra_is_shared_download"

        /**
         * The folder on the app's external storage([Context.getExternalFilesDir]) where downloaded
         * decks will be temporarily stored before importing.
         *
         * Note: when changing this constant make sure to also change the associated entry in filepaths.xml
         * so our FileProvider can actually serve the file!
         */
        const val SHARED_DECKS_DOWNLOAD_FOLDER = "shared_decks"

        private val deckIdRegex = "download-deck/(\\d+)".toRegex()

        /**
         * Given the URI of a deck's download URL such as
         * https://ankiweb.net/svc/shared/download-deck/1104981491?t=eyJvcCI6InNkZCIsImlhdCI6MTc0MTUyNjQ0OSwianYiOjF9.hr4a_G-LAqMVBAp5_95l60_2lEtYxodGl4DrJ6dT2WI
         * returns the deck's id, in this case "1104981491" if it can be found.
         */
        @VisibleForTesting
        fun getDeckIdFromDownloadURL(downloadUrl: String) =
            deckIdRegex
                .find(downloadUrl)
                ?.groups
                ?.get(1)
                ?.value

        /**
         * Given the URI of a deck's download URL such as
         * https://ankiweb.net/svc/shared/download-deck/1104981491?t=eyJvcCI6InNkZCIsImlhdCI6MTc0MTUyNjQ0OSwianYiOjF9.hr4a_G-LAqMVBAp5_95l60_2lEtYxodGl4DrJ6dT2WI
         * returns the deck's page URL such as https://ankiweb.net/shared/info/1104981491
         * If the deck id can't be found, returns the ankiweb's shared deck's main page.
         */
        @VisibleForTesting
        fun Context.getDeckPageUri(deckDownloadURL: String): String {
            val deckId = getDeckIdFromDownloadURL(deckDownloadURL)
            return if (deckId != null) {
                getString(R.string.shared_deck_info) + deckId
            } else {
                getString(R.string.shared_decks_url)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(MaterialColors.getColor(this, CommonR.attr.appBarColor))
            setContent {
                AnkiDroidTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    SharedDecksDownloadScreen(
                        state = state,
                        onCancelClick = {
                            Timber.i("Cancel download button clicked")
                            showCancelConfirmationDialog()
                        },
                        onImportClick = {
                            Timber.i("Import deck button clicked")
                            completedFile?.let { openDownloadedDeck(requireContext(), it) }
                        },
                        onTryAgainClick = ::retryDownload,
                        onOpenInBrowserClick = ::openInBrowser,
                        onLogInClick = ::openLogin,
                        onSignUpClick = ::openSignUp,
                    )
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        downloadManager = (activity as SharedDecksActivity).downloadManager
        registerDownloadReceiver()
        downloadFile(fileToBeDownloaded)
    }

    override fun onDestroyView() {
        stopDownloadProgressChecker()
        removeCancelConfirmationDialog()
        super.onDestroyView()
    }

    /** Registers once per view, retaining the same context and receiver for cleanup. */
    private fun registerDownloadReceiver() {
        val context = requireContext()
        val receiver = onComplete
        context.registerReceiverCompat(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        viewLifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    context.unregisterReceiver(receiver)
                }
            },
        )
    }

    private fun retryDownload() {
        Timber.i("Try again button clicked, retry downloading of deck")
        downloadManager.remove(downloadId)
        downloadFile(fileToBeDownloaded)
    }

    private fun openInBrowser() {
        Timber.i("'Open in Browser' clicked")
        downloadManager.remove(downloadId)
        openUrl(requireContext().getDeckPageUri(fileToBeDownloaded.url).toUri())
        parentFragmentManager.popBackStack()
    }

    private fun openLogin() {
        Timber.i("'Log in' clicked")
        downloadManager.remove(downloadId)
        (requireActivity() as SharedDecksActivity).openAnkiWebLogin()
        parentFragmentManager.popBackStack()
    }

    private fun openSignUp() {
        Timber.i("'Sign up' clicked")
        downloadManager.remove(downloadId)
        (requireActivity() as SharedDecksActivity).openAnkiWebSignUp()
        parentFragmentManager.popBackStack()
    }

    /**
     * Set the request for downloading a deck, enqueue it in DownloadManager, store download ID and
     * file name, mark download to be in progress, set the title of the download screen and start
     * the download progress checker.
     */
    private fun downloadFile(fileToBeDownloaded: DownloadFile) {
        val externalFilesFolder = requireContext().getExternalFilesDir(null)
        if (externalFilesFolder == null) {
            showSnackbar(CommonString.external_storage_unavailable)
            parentFragmentManager.popBackStack()
            return
        }
        // ensure the "shared_decks" folder exists
        val decksDownloadFolder = File(externalFilesFolder, SHARED_DECKS_DOWNLOAD_FOLDER)
        if (!decksDownloadFolder.exists()) {
            decksDownloadFolder.mkdirs()
        }
        val currentFileName = fileToBeDownloaded.toFileName(extension = "apkg")

        val downloadRequest = generateDeckDownloadRequest(fileToBeDownloaded, currentFileName)

        completedFile = null
        // Store unique download ID to be used when onReceiveBroadcast() of AnkiBroadcastReceiver gets executed.
        downloadId = downloadManager.enqueue(downloadRequest)
        fileName = currentFileName
        isDownloadInProgress = true
        onBackPressedCallback.isEnabled = isDownloadInProgress
        Timber.d("Download ID -> $downloadId")
        Timber.d("File name -> $fileName")
        viewModel.onDownloadStarted(currentFileName)
        startDownloadProgressChecker()
    }

    private fun generateDeckDownloadRequest(
        fileToBeDownloaded: DownloadFile,
        currentFileName: String,
    ): DownloadManager.Request {
        val request: DownloadManager.Request = DownloadManager.Request(fileToBeDownloaded.url.toUri())
        request.setMimeType(fileToBeDownloaded.mimeType)

        val cookies = CookieManager.getInstance().getCookie(fileToBeDownloaded.url)

        request.addRequestHeader("Cookie", cookies)
        request.addRequestHeader("User-Agent", fileToBeDownloaded.userAgent)

        request.setTitle(currentFileName)

        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(
            context,
            null,
            "$SHARED_DECKS_DOWNLOAD_FOLDER/$currentFileName",
        )

        return request
    }

    /**
     * Opens the active download for import when it completes. Registered for the view's lifetime by
     * [registerDownloadReceiver].
     */
    private var onComplete: BroadcastReceiver =
        object : AnkiBroadcastReceiver() {
            override fun onReceiveBroadcast(
                context: Context,
                intent: Intent,
            ) {
                if (!isDownloadInProgress) return
                Timber.i("Download might be complete now, verify and continue with import")

                /**
                 * @return The completed download's file, or null if it cannot be imported.
                 */
                fun getImportableDeck(): File? {
                    if (fileName == null) {
                        // Send ACRA report
                        CrashReportService.sendExceptionReport(
                            "File name is null",
                            "SharedDecksDownloadFragment::getImportableDeck",
                        )
                        return null
                    }

                    // Return if mDownloadId does not match with the ID of the completed download.
                    if (downloadId != intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)) {
                        Timber.w("Download id did not match expected id. Ignoring this download completion")
                        return null
                    }

                    stopDownloadProgressChecker()

                    // Halt execution if file doesn't have extension as 'apkg' or 'colpkg'
                    if (!ImportUtils.isFileAValidDeck(fileName!!)) {
                        Timber.i("File does not have 'apkg' or 'colpkg' extension, abort the deck opening task")
                        onDownloadFinished(isSuccessful = false, isInvalidDeckFile = true)
                        return null
                    }

                    val query = DownloadManager.Query()
                    query.setFilterById(downloadId)
                    val cursor = downloadManager.query(query)

                    cursor.use {
                        // Return if cursor is empty.
                        if (!it.moveToFirst()) {
                            Timber.i("Empty cursor, cannot continue further with success check and deck import")
                            onDownloadFinished(isSuccessful = false)
                            return null
                        }

                        val columnStatusIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val columnReasonIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_REASON)

                        // Return if download was not successful.
                        if (it.getInt(columnStatusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                            Timber.i("Download could not be successful, update UI")
                            val reason = it.getIntOrNull(columnReasonIndex)
                            Timber.d("Status code -> ${it.getIntOrNull(columnStatusIndex)}, reason $reason")
                            if (reason == HTTP_STATUS_TOO_MANY_REQUESTS && !isLoggedInToAnkiWeb()) {
                                onLoginRequired()
                            } else {
                                onDownloadFinished(isSuccessful = false)
                            }
                            return null
                        }

                        // DownloadManager may add a suffix if the requested filename already exists.
                        val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        if (localUri == null) {
                            Timber.w("Completed download has no local URI")
                            onDownloadFinished(isSuccessful = false)
                            return null
                        }
                        return localUri.toUri().toFile()
                    }
                }

                val downloadedFile =
                    try {
                        getImportableDeck()
                    } catch (exception: Exception) {
                        Timber.w(exception)
                        onDownloadFinished(isSuccessful = false)
                        return
                    }

                if (downloadedFile == null) {
                    // Could be a retryable fault (we received notification of another file)
                    // Otherwise, onDownloadFinished should have been called
                    // to update the UI
                    return
                }

                completedFile = downloadedFile

                // the progress checker can stop before it sees 100%, so complete it here
                viewModel.onDownloadComplete()

                Timber.i("Opening downloaded deck for import")
                openDownloadedDeck(context, downloadedFile)

                Timber.d("Download finished")
                onDownloadFinished(isSuccessful = true)
            }
        }

    /**
     * Safely retrieves the integer value from the cursor at the specified column index.
     *
     * @param columnIndex The index of the column from which to retrieve the integer value.
     * @return The integer value from the cursor at the specified column index, or null if invalid or undefined.
     */
    private fun Cursor?.getIntOrNull(columnIndex: Int): Int? =
        try {
            if (columnIndex != -1) {
                this?.getInt(columnIndex)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * Check download progress and update status at intervals of 0.1 second.
     */
    private val downloadProgressChecker: Runnable by lazy {
        object : Runnable {
            override fun run() {
                if (!isVisible) {
                    stopDownloadProgressChecker()
                    return
                }
                checkDownloadProgress()

                // Keep checking download progress at intervals of 1 second.
                handler.postDelayed(this, DOWNLOAD_PROGRESS_CHECK_DELAY)
            }
        }
    }

    /**
     * Start checking for download progress.
     */
    private fun startDownloadProgressChecker() {
        Timber.d("Starting download progress checker")
        downloadProgressChecker.run()
        isProgressCheckerRunning = true
    }

    /**
     * Stop checking for download progress.
     */
    private fun stopDownloadProgressChecker() {
        Timber.d("Stopping download progress checker")
        handler.removeCallbacks(downloadProgressChecker)
        isProgressCheckerRunning = false
    }

    /**
     * Reads the current progress out of [DownloadManager] and reports it to the ViewModel.
     */
    private fun checkDownloadProgress() {
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)

        val cursor =
            try {
                downloadManager.query(query)
            } catch (_: IllegalArgumentException) {
                // 19812: column local_filename is not allowed in queries
                viewModel.onProgressUnavailable()
                return
            }

        cursor.use {
            // Return if cursor is empty.
            if (!it.moveToFirst()) {
                return
            }

            viewModel.onProgress(
                downloadedBytes = it.getLong(it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = it.getLong(it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                timeMillis = TimeManager.time.intTimeMS(),
            )

            val columnIndexForStatus = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val columnIndexForReason = it.getColumnIndex(DownloadManager.COLUMN_REASON)

            if (columnIndexForStatus == -1) {
                Timber.w("Column for status does not exist")
                return
            }

            if (columnIndexForReason == -1) {
                Timber.w("Column for reason does not exist")
                return
            }

            val waitingForNetwork =
                it.getInt(columnIndexForStatus) == DownloadManager.STATUS_PAUSED &&
                    it.getInt(columnIndexForReason) == DownloadManager.PAUSED_WAITING_FOR_NETWORK
            viewModel.onWaitingForNetwork(waitingForNetwork)
        }
    }

    /**
     * Open the completed download's file.
     */
    private fun openDownloadedDeck(
        context: Context,
        downloadedFile: File,
    ) {
        // A successful DownloadManager entry can outlive its file, e.g. after another import.
        if (!downloadedFile.isFile) {
            Timber.w("Downloaded shared deck no longer exists")
            onDownloadFinished(isSuccessful = false)
            return
        }

        val mimeType = URLConnection.guessContentTypeFromName(downloadedFile.name)
        val fileIntent = Intent(context, IntentHandler::class.java)
        fileIntent.action = Intent.ACTION_VIEW

        val fileUri =
            FileProvider.getUriForFile(
                context,
                context.packageName + ".apkgfileprovider",
                downloadedFile,
            )
        Timber.d("File URI -> $fileUri")
        fileIntent.setDataAndType(fileUri, mimeType)
        fileIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        fileIntent.putExtra(EXTRA_IS_SHARED_DOWNLOAD, true)
        try {
            context.startActivity(fileIntent)
        } catch (e: ActivityNotFoundException) {
            showThemedToast(context, CommonString.something_wrong, false)
            Timber.w(e)
        }
    }

    /**
     * Updates the UI after download completion or failure and marks the download as inactive.
     */
    private fun onDownloadFinished(
        isSuccessful: Boolean,
        isInvalidDeckFile: Boolean = false,
    ) {
        if (!isSuccessful) {
            if (isInvalidDeckFile) {
                Timber.i("File is not a valid deck, hence return from the download screen")
                if (isVisible) {
                    context?.let { showThemedToast(it, CommonString.import_log_no_apkg, false) }
                    // Go back if file is not a deck and cannot be imported
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            } else {
                Timber.i("Download failed, offer a retry")
                if (isVisible) {
                    context?.let { showThemedToast(it, CommonString.something_wrong, false) }
                }
                viewModel.onDownloadFailed()
            }
        }

        isDownloadInProgress = false
        onBackPressedCallback.isEnabled = isDownloadInProgress

        // If the cancel confirmation dialog is being shown and the download is no longer in progress, then remove the dialog.
        removeCancelConfirmationDialog()
    }

    private fun onLoginRequired() {
        Timber.i("AnkiWeb wants a login before more downloads")
        viewModel.onLoginRequired()
        isDownloadInProgress = false
        onBackPressedCallback.isEnabled = isDownloadInProgress
        removeCancelConfirmationDialog()
    }

    private fun showCancelConfirmationDialog() {
        Timber.i("displaying cancel download confirmation dialog")
        downloadCancelConfirmationDialog =
            AlertDialog.Builder(requireContext()).create {
                setTitle(CommonString.cancel_download_question_title)
                setPositiveButton(CommonString.dialog_yes) { _, _ ->
                    Timber.i("cancelling download")
                    downloadManager.remove(downloadId)
                    isDownloadInProgress = false
                    onBackPressedCallback.isEnabled = isDownloadInProgress
                    parentFragmentManager.popBackStack()
                }
                setNegativeButton(CommonString.dialog_no) { _, _ ->
                    Timber.i("dismissed cancel download confirmation dialog")
                    downloadCancelConfirmationDialog?.dismiss()
                }
            }
        downloadCancelConfirmationDialog?.show()
    }

    private fun removeCancelConfirmationDialog() {
        downloadCancelConfirmationDialog?.dismiss()
    }
}
