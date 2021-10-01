/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
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

package com.ichi2.anki.jsaddons

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.web.HttpFetcher
import com.ichi2.async.ProgressSenderAndCancelListener
import com.ichi2.async.TaskDelegate
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import org.apache.commons.compress.archivers.ArchiveException
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.UnknownHostException

class NpmPackageDownloader {
    /**
     * Show/hide download button in webview
     * for valid addon npm package - show button
     * for invalid addon npm package - hide button
     */
    class InstallButtonTask(private val context: Context, private val npmPackageName: String) : TaskDelegate<Void?, NetworkResult<String?>?>() {

        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): NetworkResult<String?>? {
            val url = URL(context.getString(R.string.npmjs_registry, npmPackageName))
            return getTarBallUrl(url)
        }

        /**
         * Here Jackson used to map package.json from given url https://www.npmjs.org/package/addon-name to AddonModel
         * For valid package it readValue url and gets tarball url from mapped AddonModel
         *
         * @param url npmjs.org package registry url http://registry.npmjs.org/ankidroid-js-addon-.../latest
         * @return tarballUrl if valid addon else message explaining errors
         */
        fun getTarBallUrl(url: URL): NetworkResult<String?>? {
            return try {
                // mapping for json fetched from http://registry.npmjs.org/ankidroid-js-addon-.../latest
                // Note: here IO operation happening
                val mapper = ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val addonModel = mapper.readValue(url, AddonModel::class.java)

                // check if fields like ankidroidJsApi, addonType exists or not
                if (!addonModel.isValidAnkiDroidAddon()) {
                    NetworkResult.Failure(context.getString(R.string.is_not_valid_js_addon, npmPackageName), "InstallButtonTask::Invalid Addon")
                } else {
                    // get tarball url to download it cache folder
                    NetworkResult.Success(addonModel.dist!!["tarball"])
                }
            } catch (e: JacksonException) {
                NetworkResult.Failure(context.getString(R.string.is_not_valid_js_addon, npmPackageName), e.localizedMessage)
            } catch (e: UnknownHostException) {
                NetworkResult.Failure(context.getString(R.string.network_no_connection), e.localizedMessage)
            } catch (e: NullPointerException) {
                NetworkResult.Failure(context.getString(R.string.error_occur_downloading_addon, npmPackageName), e.localizedMessage)
            } catch (e: IOException) {
                NetworkResult.Failure(context.getString(R.string.error_occur_downloading_addon, npmPackageName), e.localizedMessage)
            }
        }
    }

    /**
     * Show/hide download button in webview listener
     *
     * in onPostExecute after page loaded result is tarballUrl, show hidden button and set OnClickListener for calling another Task which download and extract .tgz file
     */
    class InstallButtonListener(
        private val activity: Activity,
        private val downloadButton: Button,
        private val addonName: String
    ) : TaskListener<Void?, NetworkResult<String?>?>() {

        var context: Context = activity.applicationContext
        override fun onPreExecute() {
            // nothing to do
        }

        override fun onPostExecute(result: NetworkResult<String?>?) {
            // show download dialog with progress bar
            // there are three task, 1) download, 2) extract and 3) complete
            var url = ""
            val downloadRunnable = Runnable {
                val progressDialog = Dialog(activity)
                progressDialog.setContentView(R.layout.addon_progress_bar_layout)
                progressDialog.setCancelable(false)

                // call another task which download .tgz file and extract and copy to addons folder
                // here result is tarBallUrl
                val cancellable = TaskManager.launchCollectionTask(
                    DownloadAddon(activity, url),
                    DownloadAddonListener(activity, addonName, progressDialog)
                )

                val cancelRunnable = Runnable {
                    cancellable.cancel(true)
                    progressDialog.dismiss()
                }

                val cancelButton: Button = progressDialog.findViewById(R.id.cancel_action)
                cancelButton.setText(R.string.dialog_cancel)
                cancelButton.setOnClickListener { cancelRunnable.run() }

                progressDialog.show()
            }

            // show when 'Install Addon' at bottom right button clicked
            // when yes button click then shown download dialog with progress bar
            downloadButton.setOnClickListener {
                val builder = MaterialDialog.Builder(activity)
                    .title(R.string.confirm_install)
                    .content(context.getString(R.string.confirm_addon_install_message, addonName))
                    .positiveText(R.string.yes)
                    .negativeText(R.string.dialog_no)
                    .onPositive { _, _ -> downloadRunnable.run() }
                    .onNegative { dialog, _ -> dialog.dismiss() }
                builder.show()
            }

            // result will .tgz url for valid npm package else message explaining errors
            when (result) {
                is NetworkResult.Success -> {
                    // show download button at bottom right with "Install Addon"
                    // when the result return from previous collection task is valid url
                    downloadButton.visibility = View.VISIBLE
                    url = result.data!!
                }

                is NetworkResult.Failure -> {
                    // show snackbar where to seek help and wiki for the errors
                    val helpUrl = Uri.parse(context.getString(R.string.link_help))
                    val activity = activity as AnkiActivity?
                    UIUtils.showSnackbar(
                        activity, result.message, false, R.string.help,
                        { activity?.openUrl(helpUrl) }, null, null
                    )
                    Timber.w(result.data)
                }
            }
        }
    }

    /**
     * Download .tgz file
     */
    class DownloadAddon(private val context: Context, private val tarballUrl: String) :
        TaskDelegate<Void?, String?>() {

        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): String? {
            return downloadPackage()
        }

        /**
         * Download .tgz file from provided url
         *
         * @return downloaded file path
         */
        fun downloadPackage(): String {
            // download the .tgz file in cache folder of AnkiDroid
            val downloadFilePath = HttpFetcher.downloadFileToSdCardMethod(tarballUrl, context, "addons", "GET")
            Timber.d("download path %s", downloadFilePath)
            return downloadFilePath
        }
    }

    class DownloadAddonListener(
        private val context: Context,
        private val addonName: String,
        private val progressDialog: Dialog
    ) : TaskListener<Void?, String?>() {
        override fun onPreExecute() {
            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title)
                .setText(context.getString(R.string.downloading_npm_package))

            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_message).setText(addonName)

            // progress bar max is 3, and it is start of first task
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).setProgress(0)
            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).setText("0/3")
        }

        override fun onPostExecute(downloadFilePath: String?) {
            // progress bar max is 3, and first task finished, second task will start
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).setProgress(1)

            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).setText("1/3")

            // extract the downloaded .tgz file to AnkiDroid/addons dir
            val cancellable = TaskManager.launchCollectionTask(
                ExtractAddon(context, downloadFilePath!!, addonName),
                ExtractAddonListener(context, addonName, progressDialog)
            )

            val cancelRunnable = Runnable {
                cancellable.cancel(true)
                progressDialog.dismiss()
            }

            val cancelButton: Button = progressDialog.findViewById(R.id.cancel_action)
            cancelButton.setText(R.string.dialog_cancel)
            cancelButton.setOnClickListener { cancelRunnable.run() }
        }
    }

    /**
     * Extract .tgz file, and copy to addons folder
     */
    class ExtractAddon(
        private val context: Context,
        private val tarballPath: String,
        private val addonName: String,
    ) : TaskDelegate<Void?, String?>() {

        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): String? {
            return extractAndCopyAddonTgz(tarballPath, addonName)
        }

        /**
         * Extract npm package .tgz file to folder name 'npmPackageName' in AnkiDroid/addons/
         *
         * @param tarballPath    path to downloaded js-addon.tgz file
         * @param npmPackageName addon name, e.g ankidroid-js-addon-progress-bar
         */
        fun extractAndCopyAddonTgz(tarballPath: String, npmPackageName: String): String {
            if (tarballPath == null) {
                return context.getString(R.string.failed_to_extract_addon_package, addonName)
            }

            val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)

            // AnkiDroid/addons/js-addons
            // here npmAddonName is id of npm package which may not contain ../ or other bad path
            val addonsDir = File(currentAnkiDroidDirectory, "addons")
            val addonsPackageDir = File(addonsDir, npmPackageName)
            val tarballFile = File(tarballPath)
            if (!tarballFile.exists()) {
                return context.getString(R.string.failed_to_extract_addon_package, addonName)
            }

            try {
                NpmPackageTgzExtract.extractTarGzipToAddonFolder(tarballFile, addonsPackageDir)
                Timber.d("js addon .tgz extracted")
            } catch (e: IOException) {
                Timber.w(e.localizedMessage)
                return context.getString(R.string.failed_to_extract_addon_package, addonName)
            } catch (e: ArchiveException) {
                Timber.w(e.localizedMessage)
                return context.getString(R.string.failed_to_extract_addon_package, addonName)
            } finally {
                tarballFile.delete()
            }
            return context.getString(R.string.addon_install_complete, addonName)
        }
    }

    class ExtractAddonListener(
        private val context: Context,
        private val addonName: String,
        private val progressDialog: Dialog
    ) : TaskListener<Void?, String?>() {

        override fun onPreExecute() {
            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title)
                .setText(context.getString(R.string.extracting_npm_package))

            // progress bar max is 3, and it is start of final task so progress bar set to 2
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).setProgress(2)
            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).setText("2/3")
        }

        override fun onPostExecute(result: String?) {
            if (result.equals(context.getString(R.string.addon_install_complete, addonName))) {
                progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title)
                    .setText(R.string.success)
            } else {
                progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title)
                    .setText(R.string.failed)
            }

            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_message).setText(result)

            // progress bar max is 3, and it is third and final task so progress bar set to 3
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).setProgress(3)
            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).setText("3/3")

            val okButton: Button = progressDialog.findViewById<Button>(R.id.cancel_action)
            okButton.setText(R.string.dialog_ok)
            okButton.setOnClickListener { progressDialog.dismiss() }
        }
    }

    sealed class NetworkResult<T>(
        val data: T? = null,
        val message: String? = null
    ) {
        class Success<T>(data: T) : NetworkResult<T>(data)
        class Failure<T>(message: String?, data: T? = null) : NetworkResult<T>(data, message)
    }
}
