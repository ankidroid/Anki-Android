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
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
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
    class ShowHideInstallButton(private val context: Context, private val npmPackageName: String) : TaskDelegate<Void?, String?>() {

        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): String? {
            val url = URL(context.getString(R.string.npmjs_registry, npmPackageName))
            return getTarBallUrl(url)
        }

        /**
         * Using Jackson the latest package.json for the addon fetched, then mapped to AddonModel
         * For valid package it gets tarball url from mapped AddonModel,
         * then downloads and extract to AnkiDroid/addons folder using {@code extractAndCopyAddonTgz} and toast with success message returned
         *
         * For invalid addon or for exception occurred, it returns message to respective to the errors from catch block
         *
         * @param url npmjs.org package registry url http://registry.npmjs.org/ankidroid-js-addon-.../latest
         * @return tarballUrl if valid addon else message explaining errors
         */
        fun getTarBallUrl(url: URL): String? {
            try {
                // mapping for json fetched from http://registry.npmjs.org/ankidroid-js-addon-.../latest
                val mapper = ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val addonModel = mapper.readValue(url, AddonModel::class.java)

                // check if fields like ankidroidJsApi, addonType exists or not
                if (!addonModel.isValidAnkiDroidAddon()) {
                    return context.getString(R.string.is_not_valid_js_addon, npmPackageName)
                }

                // get tarball url to download it cache folder
                val tarballUrl = addonModel.dist!!["tarball"]
                return tarballUrl

                // addonTitle sent to list the addons in recycler view
            } catch (e: JacksonException) {
                // json format is not valid as required by AnkiDroid JS Addon specifications
                // also ObjectMapper failed to parse the fields for e.g. requested fields in AddonModel is String but
                // package.json contains array, so it may leads to parse exception or mapping exception
                Timber.w(e.localizedMessage)
                return context.getString(R.string.is_not_valid_js_addon, npmPackageName)
            } catch (e: UnknownHostException) {
                // user not connected to internet
                Timber.w(e.localizedMessage)
                return context.getString(R.string.network_no_connection)
            } catch (e: NullPointerException) {
                Timber.w(e.localizedMessage)
                return context.getString(R.string.error_occur_downloading_addon, npmPackageName)
            } catch (e: IOException) {
                Timber.w(e.localizedMessage)
                return context.getString(R.string.error_occur_downloading_addon, npmPackageName)
            }
        }
    }

    /**
     * Show/hide download button in webview listener
     *
     * in onPostExecute after page loaded result is tarballUrl, show hidden button and set OnClickListener for calling another Task which download and extract .tgz file
     */
    class ShowHideInstallButtonListener(
        private val activity: Activity,
        private val downloadButton: Button,
        private val addonName: String
    ) : TaskListener<Void?, String?>() {

        var context: Context = activity.applicationContext
        override fun onPreExecute() {
            // nothing to do
        }

        override fun onPostExecute(result: String?) {
            // show download dialog with progress bar
            // there are three task, 1) download, 2) extract and 3) complete
            val downloadRunnable = Runnable {
                val progressDialog = Dialog(activity)
                progressDialog.setContentView(R.layout.addon_progress_bar_layout)
                progressDialog.setCancelable(false)

                // call another task which download .tgz file and extract and copy to addons folder
                // here result is tarBallUrl
                val cancellable = TaskManager.launchCollectionTask(
                    DownloadAddon(activity, result!!),
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
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(R.string.confirm_install)
                builder.setMessage(context.getString(R.string.confirm_addon_install_message, addonName))
                    .setPositiveButton(R.string.yes) { _, _ ->
                        downloadRunnable.run()
                    }
                    .setNegativeButton(R.string.dialog_no) { dialog, _ ->
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.show()
            }

            // result will .tgz url for valid npm package else message explaining errors
            if (result != null) {
                // show download button at bottom right with "Install Addon" when string starts with url
                // the result return from previous collection task
                if (result.startsWith("https://")) {
                    downloadButton.visibility = View.VISIBLE
                } else {
                    // show snackbar where to seek help and wiki for the errors
                    val helpUrl = Uri.parse(context.getString(R.string.link_help))
                    val activity = activity as AnkiActivity?
                    UIUtils.showSnackbar(
                        activity, result, false, R.string.help,
                        { v -> activity?.openUrl(helpUrl) }, null, null
                    )
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
}
