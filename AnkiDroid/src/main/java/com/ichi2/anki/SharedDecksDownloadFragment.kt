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
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import timber.log.Timber
import java.io.File
import java.net.URLConnection

class SharedDecksDownloadFragment : Fragment() {

    private var mDownloadId: Long = 0

    private var mFileName: String = ""

    private val mPendingDownloads: MutableList<Pair<DownloadManager.Request, String>> = mutableListOf()

    private var mHandler: Handler = Handler(Looper.getMainLooper())
    private var mIsProgressCheckerRunning = false

    private var mIsPreviousDownloadOngoing = false

    private lateinit var mCloseButton: Button
    private lateinit var mCancelButton: Button
    private lateinit var mTryAgainButton: Button
    private lateinit var mImportDeckButton: Button
    private lateinit var mDownloadPercentageText: TextView
    private lateinit var mDownloadProgressBar: ProgressBar

    private lateinit var mDownloadManager: DownloadManager

    private var mCurrentDownloadRequest: DownloadManager.Request? = null

    companion object {
        const val DOWNLOAD_PROGRESS_CHECK_DELAY = 1000L
        const val DOWNLOAD_COMPLETED_PROGRESS_PERCENTAGE = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shared_decks_download, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val downloadFile = arguments?.getSerializable("DownloadFile") as DownloadFile
        mDownloadManager = downloadFile.mDownloadManager

        downloadFile(downloadFile.mUrl, downloadFile.mUserAgent, downloadFile.mContentDisposition, downloadFile.mMimeType)

        mCloseButton = view.findViewById(R.id.close_shared_decks_download)

        mCloseButton.setOnClickListener {
            activity?.onBackPressed()
        }

        mCancelButton = view.findViewById(R.id.cancel_shared_decks_download)

        mCancelButton.setOnClickListener {
            mDownloadManager.remove(mDownloadId)
            activity?.onBackPressed()
        }

        mImportDeckButton = view.findViewById(R.id.import_shared_deck_button)

        mImportDeckButton.setOnClickListener {
            openDownloadedDeck(context)
        }

        mTryAgainButton = view.findViewById(R.id.try_again_deck_download)

        mTryAgainButton.setOnClickListener {
            mDownloadManager.remove(mDownloadId)
            downloadFile(downloadFile.mUrl, downloadFile.mUserAgent, downloadFile.mContentDisposition, downloadFile.mMimeType)
            mCancelButton.visibility = View.VISIBLE
            mTryAgainButton.visibility = View.GONE
        }

        mDownloadPercentageText = view.findViewById(R.id.download_percentage)
        mDownloadProgressBar = view.findViewById(R.id.download_progress)
    }

    /**
     * Method to set the request for downloading a deck.
     */
    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        if (!mIsPreviousDownloadOngoing) {
            // Register broadcast receiver for download completion. 
            Timber.i("Registering broadcast receiver for download completion")
            activity?.registerReceiver(mOnComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))
        request.setMimeType(mimeType)

        val cookies = CookieManager.getInstance().getCookie(url)

        request.addRequestHeader("Cookie", cookies)
        request.addRequestHeader("User-Agent", userAgent)

        val currentFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

        request.setTitle(currentFileName)

        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, currentFileName)

        /*
            If some download is already going on, then put the new download in the list of pending downloads.
            Otherwise, enqueue the new download so that it gets started.
         */
        if (mIsPreviousDownloadOngoing) {
            UIUtils.showThemedToast(context, R.string.previous_download_running_message, false)
            mPendingDownloads.add(Pair(request, currentFileName))
        } else {
            // Store unique download ID to be used when onReceive() of BroadcastReceiver gets executed.
            mDownloadId = mDownloadManager.enqueue(request)
            mFileName = currentFileName
            Timber.i("Download ID -> $mDownloadId")
            Timber.i("File name -> $mFileName")
            mCurrentDownloadRequest = request
            mIsPreviousDownloadOngoing = true
            view?.findViewById<TextView>(R.id.downloading_title)?.text = getString(R.string.downloading_file, mFileName)
            startDownloadProgressChecker()
        }
    }

    /**
     * When the deck download is completed successfully, open the .apkg file in AnkiDroid to import it.
     */
    private var mOnComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Return if mDownloadId does not match with the ID of the completed download.
            if (mDownloadId != intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)) {
                return
            }

            stopDownloadProgressChecker()

            // Return if file doesn't have extension as .apkg or .colpkg
            if (!(mFileName.endsWith(".apkg") || mFileName.endsWith(".colpkg"))) {
                continuePendingDownloadsOrUnregisterReceiver(isInvalidDeckFile = true)
                return
            }

            val action = intent.action

            // Return if download is not complete.
            if (action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                continuePendingDownloadsOrUnregisterReceiver()
                return
            }

            val query = DownloadManager.Query()
            query.setFilterById(mDownloadId)
            val cursor = mDownloadManager.query(query)

            cursor.use {
                // Return if cursor is empty.
                if (!it.moveToFirst()) {
                    continuePendingDownloadsOrUnregisterReceiver()
                    return
                }

                val columnIndex: Int = it.getColumnIndex(DownloadManager.COLUMN_STATUS)

                // Return if download was not successful.
                if (it.getInt(columnIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                    continuePendingDownloadsOrUnregisterReceiver()
                    return
                }
            }

            if (isVisible) {
                // Setting these since progress checker can stop before progress is updated to represent 100%
                mDownloadPercentageText.text = getString(R.string.percentage, DOWNLOAD_COMPLETED_PROGRESS_PERCENTAGE)
                mDownloadProgressBar?.progress = DOWNLOAD_COMPLETED_PROGRESS_PERCENTAGE

                // Remove close button and show import deck button
                mCloseButton.visibility = View.GONE
                mImportDeckButton.visibility = View.VISIBLE

                // Remove the cancel download button
                mCancelButton.visibility = View.GONE
            }

            openDownloadedDeck(context)

            continuePendingDownloadsOrUnregisterReceiver(true)
        }
    }

    /**
     * Unregister the mOnComplete broadcast receiver.
     */
    private fun unregisterReceiver() {
        activity?.unregisterReceiver(mOnComplete)
    }

    /**
     * Checks download progress and updates status, then re-schedules itself.
     */
    private val mDownloadProgressChecker: Runnable by lazy {
        object : Runnable {
            override fun run() {
                checkDownloadProgress()

                // Keep checking download progress at intervals of 1 second.
                mHandler.postDelayed(this, DOWNLOAD_PROGRESS_CHECK_DELAY)
            }
        }
    }

    /**
     * Start checking for download progress.
     */
    private fun startDownloadProgressChecker() {
        if (!mIsProgressCheckerRunning) {
            mDownloadProgressChecker.run()
            mIsProgressCheckerRunning = true
        }
    }

    /**
     * Stop checking for download progress.
     */
    private fun stopDownloadProgressChecker() {
        mHandler.removeCallbacks(mDownloadProgressChecker)
        mIsProgressCheckerRunning = false
    }

    /**
     * Checks download progress and sets the current progress in ProgressBar.
     */
    private fun checkDownloadProgress() {
        val query = DownloadManager.Query()
        query.setFilterById(mDownloadId)

        val cursor = mDownloadManager.query(query)

        cursor.use {
            // Return if cursor is empty.
            if (!it.moveToFirst()) {
                it.close()
                return
            }

            do {
                // Calculate download progress and display it in the ProgressBar.
                val downloadedBytes = it.getLong(it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val totalBytes = it.getInt(it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val downloadProgress: Float = downloadedBytes * 1f / totalBytes * 100
                view?.findViewById<TextView>(R.id.download_percentage)?.text = getString(R.string.percentage, downloadProgress.toInt())
                view?.findViewById<ProgressBar>(R.id.download_progress)?.progress = downloadProgress.toInt()
            } while (it.moveToNext())
        }
    }

    /**
     * Opens the downloaded deck using its file name.
     */
    private fun openDownloadedDeck(context: Context?) {
        val mimeType = URLConnection.guessContentTypeFromName(mFileName)
        val fileIntent = Intent(Intent.ACTION_VIEW)

        val fileUri = context?.let {
            FileProvider.getUriForFile(
                it,
                it.applicationContext?.packageName + ".apkgfileprovider",
                File(it.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), mFileName)
            )
        }
        Timber.i("File URI -> $fileUri")
        fileIntent.setDataAndType(fileUri, mimeType)
        fileIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context?.startActivity(fileIntent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e)
        }
    }

    /**
     * Handle download error scenarios.
     *
     * If there are any pending downloads, continue with them.
     * Else, set mIsPreviousDownloadOngoing as false and unregister mOnComplete broadcast receiver.
     */
    private fun continuePendingDownloadsOrUnregisterReceiver(isSuccessful: Boolean = false, isInvalidDeckFile: Boolean = false) {
        if (isVisible && !isSuccessful) {
            if (isInvalidDeckFile) {
                UIUtils.showThemedToast(activity, R.string.cannot_import, false)
                // Go back if file is not a deck and cannot be imported
                activity?.onBackPressed()
            } else {
                UIUtils.showThemedToast(activity, R.string.something_wrong, false)
                // Update UI if download could not be successful
                mTryAgainButton.visibility = View.VISIBLE
                mCancelButton.visibility = View.GONE
                mDownloadPercentageText.text = getString(R.string.download_failed)
                mDownloadProgressBar.progress = 0
            }
        }

        if (mPendingDownloads.isNotEmpty()) {
            mDownloadId = mDownloadManager.enqueue(mPendingDownloads[0].first)
            mFileName = mPendingDownloads[0].second
            Timber.i("Download ID -> $mDownloadId")
            Timber.i("File name -> $mFileName")
            mCurrentDownloadRequest = mPendingDownloads[0].first
            mPendingDownloads.removeAt(0)
            startDownloadProgressChecker()
        } else {
            mIsPreviousDownloadOngoing = false
            unregisterReceiver()
        }
    }
}
