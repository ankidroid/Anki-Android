/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.ichi2.anki.SharedDecksActivity.Companion.DOWNLOAD_FILE
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.create
import timber.log.Timber
import java.io.File
import java.net.URLConnection
import kotlin.math.abs

/**
 * Used when a download is captured from AnkiWeb shared decks WebView.
 * Only for downloads started via [SharedDecksActivity].
 *
 * Only one download is supported at a time, since importing multiple decks
 * simultaneously is not supported.
 */
class SharedDecksDownloadFragment : Fragment(R.layout.fragment_shared_decks_download) {

    private var downloadId: Long = 0

    private var fileName: String? = null

    private var handler: Handler = Handler(Looper.getMainLooper())
    private var isProgressCheckerRunning = false

    private lateinit var cancelButton: Button
    private lateinit var tryAgainButton: Button
    private lateinit var importDeckButton: Button
    private lateinit var downloadPercentageText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var checkNetworkInfoText: TextView

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

    companion object {
        const val DOWNLOAD_PROGRESS_CHECK_DELAY = 1000L

        const val DOWNLOAD_STARTED_PROGRESS_PERCENTAGE = "0"
        const val DOWNLOAD_COMPLETED_PROGRESS_PERCENTAGE = "100"

        const val EXTRA_IS_SHARED_DOWNLOAD = "extra_is_shared_download"

        /**
         * The folder on the app's external storage([Context.getExternalFilesDir]) where downloaded
         * decks will be temporarily stored before importing.
         *
         * Note: when changing this constant make sure to also change the associated entry in filepaths.xml
         * so our FileProvider can actually serve the file!
         */
        const val SHARED_DECKS_DOWNLOAD_FOLDER = "shared_decks"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        downloadPercentageText = view.findViewById(R.id.download_percentage)
        downloadProgressBar = view.findViewById(R.id.download_progress)
        cancelButton = view.findViewById(R.id.cancel_shared_decks_download)
        importDeckButton = view.findViewById(R.id.import_shared_deck_button)
        tryAgainButton = view.findViewById(R.id.try_again_deck_download)
        checkNetworkInfoText = view.findViewById(R.id.check_network_info_text)

        val fileToBeDownloaded = arguments?.getSerializableCompat<DownloadFile>(DOWNLOAD_FILE)!!
        downloadManager = (activity as SharedDecksActivity).downloadManager

        downloadFile(fileToBeDownloaded)

        cancelButton.setOnClickListener {
            Timber.i("Cancel download button clicked which would lead to showing of confirmation dialog")
            showCancelConfirmationDialog()
        }

        importDeckButton.setOnClickListener {
            Timber.i("Import deck button clicked")
            openDownloadedDeck(context)
        }

        tryAgainButton.setOnClickListener {
            Timber.i("Try again button clicked, retry downloading of deck")
            downloadManager.remove(downloadId)
            downloadFile(fileToBeDownloaded)
            cancelButton.visibility = View.VISIBLE
            tryAgainButton.visibility = View.GONE
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
        activity?.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        val currentFileName = URLUtil.guessFileName(
            fileToBeDownloaded.url,
            fileToBeDownloaded.contentDisposition,
            fileToBeDownloaded.mimeType
        )

        val downloadRequest = generateDeckDownloadRequest(fileToBeDownloaded, currentFileName)

        // Store unique download ID to be used when onReceive() of BroadcastReceiver gets executed.
        downloadId = downloadManager.enqueue(downloadRequest)
        fileName = currentFileName
        isDownloadInProgress = true
        Timber.d("Download ID -> $downloadId")
        Timber.d("File name -> $fileName")
        view?.findViewById<TextView>(R.id.downloading_title)?.text = getString(R.string.downloading_file, fileName)
        startDownloadProgressChecker()
    }

    private fun generateDeckDownloadRequest(fileToBeDownloaded: DownloadFile, currentFileName: String): DownloadManager.Request {
        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(fileToBeDownloaded.url))
        request.setMimeType(fileToBeDownloaded.mimeType)

        val cookies = CookieManager.getInstance().getCookie(fileToBeDownloaded.url)

        request.addRequestHeader("Cookie", cookies)
        request.addRequestHeader("User-Agent", fileToBeDownloaded.userAgent)

        request.setTitle(currentFileName)

        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(
            context,
            null,
            "$SHARED_DECKS_DOWNLOAD_FOLDER/$currentFileName"
        )

        return request
    }

    /**
     * Registered in downloadFile() method.
     * When onReceive() is called, open the deck file in AnkiDroid to import it.
     */
    private var onComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            Timber.i("Download might be complete now, verify and continue with import")

            /**
             * @return Whether the data in the received data is an importable deck
             */
            fun verifyDeckIsImportable(): Boolean {
                if (fileName == null) {
                    // Send ACRA report
                    CrashReportService.sendExceptionReport(
                        "File name is null",
                        "SharedDecksDownloadFragment::verifyDeckIsImportable"
                    )
                    return false
                }

                // Return if mDownloadId does not match with the ID of the completed download.
                if (downloadId != intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)) {
                    Timber.w(
                        "Download ID did not match with the ID of the completed download. " +
                            "Download completion related to some other download might have been received. " +
                            "Deck download might still be going on, when it completes then the method would be called again."
                    )
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

                    val columnIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    // Return if download was not successful.
                    if (it.getInt(columnIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                        Timber.i("Download could not be successful, update UI and unregister receiver")
                        Timber.d("Status code -> ${it.getInt(columnIndex)}")
                        checkDownloadStatusAndUnregisterReceiver(isSuccessful = false)
                        return false
                    }
                }
                return true
            }

            val verified = try {
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

            if (isVisible) {
                // Setting these since progress checker can stop before progress is updated to represent 100%
                downloadPercentageText.text = getString(R.string.percentage, DOWNLOAD_COMPLETED_PROGRESS_PERCENTAGE)
                downloadProgressBar.progress = DOWNLOAD_COMPLETED_PROGRESS_PERCENTAGE.toInt()

                // Remove cancel button and show import deck button
                cancelButton.visibility = View.GONE
                importDeckButton.visibility = View.VISIBLE
            }

            Timber.i("Opening downloaded deck for import")
            openDownloadedDeck(context)

            Timber.d("Checking download status and unregistering receiver")
            checkDownloadStatusAndUnregisterReceiver(isSuccessful = true)
        }
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
        downloadPercentageText.text = getString(R.string.percentage, DOWNLOAD_STARTED_PROGRESS_PERCENTAGE)
        downloadProgressBar.progress = DOWNLOAD_STARTED_PROGRESS_PERCENTAGE.toInt()
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
     * Checks download progress and sets the current progress in ProgressBar.
     */
    private fun checkDownloadProgress() {
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)

        val cursor = downloadManager.query(query)

        cursor.use {
            // Return if cursor is empty.
            if (!it.moveToFirst()) {
                return
            }

            // Calculate download progress and display it in the ProgressBar.
            val downloadedBytes = it.getLong(it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            // Taking absolute value to prevent case of -0.0 % being shown.
            val downloadProgress: Float = abs(downloadedBytes * 1f / totalBytes * 100)
            val downloadProgressIntValue = downloadProgress.toInt()
            val percentageValue = if (downloadProgressIntValue == 0 || downloadProgressIntValue == 100) {
                // Show 0 % and 100 % instead of 0.0 % and 100.0 %
                downloadProgressIntValue.toString()
            } else {
                // Show download progress percentage up to 1 decimal place.
                "%.1f".format(downloadProgress)
            }
            downloadPercentageText.text = getString(R.string.percentage, percentageValue)
            downloadProgressBar.progress = downloadProgress.toInt()

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

            // Display message if download is waiting for network connection
            if (it.getInt(columnIndexForStatus) == DownloadManager.STATUS_PAUSED &&
                it.getInt(columnIndexForReason) == DownloadManager.PAUSED_WAITING_FOR_NETWORK
            ) {
                checkNetworkInfoText.visibility = View.VISIBLE
            } else {
                checkNetworkInfoText.visibility = View.GONE
            }
        }
    }

    /**
     * Open the downloaded deck using 'mFileName'.
     */
    private fun openDownloadedDeck(context: Context?) {
        val mimeType = URLConnection.guessContentTypeFromName(fileName)
        val fileIntent = Intent(context, IntentHandler::class.java)
        fileIntent.action = Intent.ACTION_VIEW

        val fileUri = context?.let {
            val sharedDecksPath = File(it.getExternalFilesDir(null), SHARED_DECKS_DOWNLOAD_FOLDER)
            FileProvider.getUriForFile(
                it,
                it.applicationContext?.packageName + ".apkgfileprovider",
                File(sharedDecksPath, fileName.toString())
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
    @Suppress("deprecation") // onBackPressed
    private fun checkDownloadStatusAndUnregisterReceiver(isSuccessful: Boolean, isInvalidDeckFile: Boolean = false) {
        if (isVisible && !isSuccessful) {
            if (isInvalidDeckFile) {
                Timber.i("File is not a valid deck, hence return from the download screen")
                context?.let { showThemedToast(it, R.string.import_log_no_apkg, false) }
                // Go back if file is not a deck and cannot be imported
                activity?.onBackPressed()
            } else {
                Timber.i("Download failed, update UI and provide option to retry")
                context?.let { showThemedToast(it, R.string.something_wrong, false) }
                // Update UI if download could not be successful
                tryAgainButton.visibility = View.VISIBLE
                cancelButton.visibility = View.GONE
                downloadPercentageText.text = getString(R.string.download_failed)
                downloadProgressBar.progress = DOWNLOAD_STARTED_PROGRESS_PERCENTAGE.toInt()
            }
        }

        unregisterReceiver()
        isDownloadInProgress = false

        // If the cancel confirmation dialog is being shown and the download is no longer in progress, then remove the dialog.
        removeCancelConfirmationDialog()
    }

    @Suppress("deprecation") // onBackPressed
    fun showCancelConfirmationDialog() {
        downloadCancelConfirmationDialog = AlertDialog.Builder(requireContext()).create {
            setTitle(R.string.cancel_download_question_title)
            setPositiveButton(R.string.dialog_yes) { _, _ ->
                downloadManager.remove(downloadId)
                unregisterReceiver()
                isDownloadInProgress = false
                activity?.onBackPressed()
            }
            setNegativeButton(R.string.dialog_no) { _, _ ->
                downloadCancelConfirmationDialog?.dismiss()
            }
        }
        downloadCancelConfirmationDialog?.show()
    }

    private fun removeCancelConfirmationDialog() {
        downloadCancelConfirmationDialog?.dismiss()
    }
}
