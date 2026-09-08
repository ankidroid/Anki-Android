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
import android.view.View
import android.webkit.CookieManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.compat.CompatHelper.Companion.registerReceiverCompat
import com.ichi2.anki.databinding.FragmentSharedDecksDownloadBinding
import com.ichi2.anki.shareddeck.SharedDecksActivity.Companion.DOWNLOAD_FILE
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.create
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber
import java.io.File
import java.net.URLConnection

/**
 * Used when a download is captured from AnkiWeb shared decks WebView.
 * Only for downloads started via [SharedDecksActivity].
 *
 * Only one download is supported at a time, since importing multiple decks
 * simultaneously is not supported.
 */
class SharedDecksDownloadFragment : Fragment(R.layout.fragment_shared_decks_download) {
    private val binding by viewBinding(FragmentSharedDecksDownloadBinding::bind)
    private val viewModel: SharedDecksDownloadViewModel by viewModels()

    private var downloadId: Long = 0

    private var fileName: String? = null

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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupEdgeToEdge(view)
        viewModel.uiState.launchCollectionInLifecycleScope(::render)

        val fileToBeDownloaded = arguments?.getSerializableCompat<DownloadFile>(DOWNLOAD_FILE)!!
        downloadManager = (activity as SharedDecksActivity).downloadManager

        downloadFile(fileToBeDownloaded)

        binding.cancelDownloadButton.setOnClickListener {
            Timber.i("Cancel download button clicked")
            showCancelConfirmationDialog()
        }

        binding.importSharedDeckButton.setOnClickListener {
            Timber.i("Import deck button clicked")
            openDownloadedDeck(context)
        }

        binding.openInWebBrowserButton.setOnClickListener {
            Timber.i("'Open in Browser' clicked")
            downloadManager.remove(downloadId)
            openUrl(requireContext().getDeckPageUri(fileToBeDownloaded.url).toUri())
            parentFragmentManager.popBackStack()
        }

        binding.tryDownloadAgainButton.setOnClickListener {
            Timber.i("Try again button clicked, retry downloading of deck")
            downloadManager.remove(downloadId)
            downloadFile(fileToBeDownloaded)
        }
    }

    private fun render(state: SharedDecksDownloadUiState) {
        binding.downloadingTitle.text = state.fileName?.let { getString(R.string.downloading_file, it) }
        binding.downloadPercentageText.text =
            when {
                state.phase == DownloadPhase.Failed -> getString(R.string.download_failed)
                // 19812: DownloadManager could not be queried, so all we can say is that it is running
                state.percent == null -> TR.syncDownloadingFromAnkiweb()
                else -> getString(R.string.percentage, formatDownloadPercent(state.percent))
            }
        binding.downloadProgressBar.progress = state.percent?.toInt() ?: 0
        binding.checkNetworkInfoText.isVisible = state.isWaitingForNetwork
        binding.cancelDownloadButton.isVisible = state.phase == DownloadPhase.Downloading
        binding.importSharedDeckButton.isVisible = state.phase == DownloadPhase.Complete
        binding.tryDownloadAgainButton.isVisible = state.phase == DownloadPhase.Failed
        binding.openInWebBrowserButton.isVisible = state.phase == DownloadPhase.Failed
    }

    /** Applies edge-to-edge insets for the screen */
    private fun setupEdgeToEdge(view: View) {
        // systemBars (not just statusBars) so a landscape 3-button navigation bar,
        // which is a side inset, is also cleared
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            // top inset on the root only: the strip behind the status bar shows the
            // root's app bar color, while the other sides keep the content background
            root.updatePadding(top = bars.top)
            binding.downloadContent.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    /**
     * Register broadcast receiver for listening to download completion.
     * Set the request for downloading a deck, enqueue it in DownloadManager, store download ID and
     * file name, mark download to be in progress, set the title of the download screen and start
     * the download progress checker.
     */
    private fun downloadFile(fileToBeDownloaded: DownloadFile) {
        val externalFilesFolder = requireContext().getExternalFilesDir(null)
        if (externalFilesFolder == null) {
            showSnackbar(R.string.external_storage_unavailable)
            parentFragmentManager.popBackStack()
            return
        }
        // ensure the "shared_decks" folder exists
        val decksDownloadFolder = File(externalFilesFolder, SHARED_DECKS_DOWNLOAD_FOLDER)
        if (!decksDownloadFolder.exists()) {
            decksDownloadFolder.mkdirs()
        }
        // Register broadcast receiver for download completion.
        Timber.d("Registering broadcast receiver for download completion")
        activity?.registerReceiverCompat(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )

        val currentFileName = fileToBeDownloaded.toFileName(extension = "apkg")

        val downloadRequest = generateDeckDownloadRequest(fileToBeDownloaded, currentFileName)

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
     * Registered in downloadFile() method.
     * When [AnkiBroadcastReceiver.onReceiveBroadcast] is called, open the deck file in AnkiDroid to import it.
     */
    private var onComplete: BroadcastReceiver =
        object : AnkiBroadcastReceiver() {
            override fun onReceiveBroadcast(
                context: Context,
                intent: Intent,
            ) {
                Timber.i("Download might be complete now, verify and continue with import")

                /**
                 * @return Whether the data in the received data is an importable deck
                 */
                fun verifyDeckIsImportable(): Boolean {
                    if (fileName == null) {
                        // Send ACRA report
                        CrashReportService.sendExceptionReport(
                            "File name is null",
                            "SharedDecksDownloadFragment::verifyDeckIsImportable",
                        )
                        return false
                    }

                    // Return if mDownloadId does not match with the ID of the completed download.
                    if (downloadId != intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)) {
                        Timber.w("Download id did not match expected id. Ignoring this download completion")
                        return false
                    }

                    stopDownloadProgressChecker()

                    // Halt execution if file doesn't have extension as 'apkg' or 'colpkg'
                    if (!ImportUtils.isFileAValidDeck(fileName!!)) {
                        Timber.i("File does not have 'apkg' or 'colpkg' extension, abort the deck opening task")
                        checkDownloadStatusAndUnregisterReceiver(isSuccessful = false, isInvalidDeckFile = true)
                        return false
                    }

                    val query = DownloadManager.Query()
                    query.setFilterById(downloadId)
                    val cursor = downloadManager.query(query)

                    cursor.use {
                        // Return if cursor is empty.
                        if (!it.moveToFirst()) {
                            Timber.i("Empty cursor, cannot continue further with success check and deck import")
                            checkDownloadStatusAndUnregisterReceiver(isSuccessful = false)
                            return false
                        }

                        val columnStatusIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val columnReasonIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_REASON)

                        // Return if download was not successful.
                        if (it.getInt(columnStatusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                            Timber.i("Download could not be successful, update UI and unregister receiver")
                            Timber.d("Status code -> ${it.getIntOrNull(columnStatusIndex)}, reason ${it.getIntOrNull(columnReasonIndex)}")
                            checkDownloadStatusAndUnregisterReceiver(isSuccessful = false)
                            return false
                        }
                    }
                    return true
                }

                val verified =
                    try {
                        verifyDeckIsImportable()
                    } catch (exception: Exception) {
                        Timber.w(exception)
                        checkDownloadStatusAndUnregisterReceiver(isSuccessful = false)
                        return
                    }

                if (!verified) {
                    // Could be a retryable fault (we received notification of another file)
                    // Otherwise, checkDownloadStatusAndUnregisterReceiver should have been called
                    // to update the UI
                    return
                }

                // the progress checker can stop before it sees 100%, so complete it here
                viewModel.onDownloadComplete()

                Timber.i("Opening downloaded deck for import")
                openDownloadedDeck(context)

                Timber.d("Checking download status and unregistering receiver")
                checkDownloadStatusAndUnregisterReceiver(isSuccessful = true)
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
     * Unregister the mOnComplete broadcast receiver.
     */
    private fun unregisterReceiver() {
        Timber.d("Unregistering receiver")
        try {
            activity?.unregisterReceiver(onComplete)
        } catch (exception: IllegalArgumentException) {
            // This might throw an exception in cases where the receiver is already in unregistered state.
            // Log the exception in such cases, there is nothing else to do.
            Timber.w(exception)
            return
        }
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
     * Open the downloaded deck using 'mFileName'.
     */
    private fun openDownloadedDeck(context: Context?) {
        val mimeType = URLConnection.guessContentTypeFromName(fileName)
        val fileIntent = Intent(context, IntentHandler::class.java)
        fileIntent.action = Intent.ACTION_VIEW

        val fileUri =
            context?.let {
                val sharedDecksPath = File(it.getExternalFilesDir(null), SHARED_DECKS_DOWNLOAD_FOLDER)
                FileProvider.getUriForFile(
                    it,
                    it.applicationContext?.packageName + ".apkgfileprovider",
                    File(sharedDecksPath, fileName.toString()),
                )
            }
        Timber.d("File URI -> $fileUri")
        fileIntent.setDataAndType(fileUri, mimeType)
        fileIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        fileIntent.putExtra(EXTRA_IS_SHARED_DOWNLOAD, true)
        try {
            context?.startActivity(fileIntent)
        } catch (e: ActivityNotFoundException) {
            context?.let { showThemedToast(it, R.string.something_wrong, false) }
            Timber.w(e)
        }
    }

    /**
     * Handle download error scenarios.
     *
     * If there are any pending downloads, continue with them.
     * Else, set mIsPreviousDownloadOngoing as false and unregister mOnComplete broadcast receiver.
     */
    private fun checkDownloadStatusAndUnregisterReceiver(
        isSuccessful: Boolean,
        isInvalidDeckFile: Boolean = false,
    ) {
        if (!isSuccessful) {
            if (isInvalidDeckFile) {
                Timber.i("File is not a valid deck, hence return from the download screen")
                if (isVisible) {
                    context?.let { showThemedToast(it, R.string.import_log_no_apkg, false) }
                    // Go back if file is not a deck and cannot be imported
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            } else {
                Timber.i("Download failed, offer a retry")
                if (isVisible) {
                    context?.let { showThemedToast(it, R.string.something_wrong, false) }
                }
                viewModel.onDownloadFailed()
            }
        }

        unregisterReceiver()
        isDownloadInProgress = false
        onBackPressedCallback.isEnabled = isDownloadInProgress

        // If the cancel confirmation dialog is being shown and the download is no longer in progress, then remove the dialog.
        removeCancelConfirmationDialog()
    }

    private fun showCancelConfirmationDialog() {
        Timber.i("displaying cancel download confirmation dialog")
        downloadCancelConfirmationDialog =
            AlertDialog.Builder(requireContext()).create {
                setTitle(R.string.cancel_download_question_title)
                setPositiveButton(R.string.dialog_yes) { _, _ ->
                    Timber.i("cancelling download")
                    downloadManager.remove(downloadId)
                    unregisterReceiver()
                    isDownloadInProgress = false
                    onBackPressedCallback.isEnabled = isDownloadInProgress
                    parentFragmentManager.popBackStack()
                }
                setNegativeButton(R.string.dialog_no) { _, _ ->
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
