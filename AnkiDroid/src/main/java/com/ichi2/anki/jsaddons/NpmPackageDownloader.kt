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

import android.app.Dialog
import android.content.Context
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.core.type.TypeReference
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
     * Get all packages json info
     *
     * @param context
     */
    class GetAddonsPackageJson(private val context: Context) : TaskDelegate<Void?, MutableList<AddonModel>?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): MutableList<AddonModel>? {
            val url = URL(context.getString(R.string.ankidroid_js_addon_json))
            return getJson(url)
        }

        fun getJson(url: URL): MutableList<AddonModel>? {
            try {
                val mapper = ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                return mapper.readValue(url, object : TypeReference<MutableList<AddonModel>>() {})
            } catch (e: UnknownHostException) {
                // user not connected to internet
                Timber.w(e.localizedMessage)
            } catch (e: NullPointerException) {
                Timber.w(e.localizedMessage)
            } catch (e: IOException) {
                Timber.w(e.localizedMessage)
            }
            return null
        }
    }

    class GetAddonsPackageJsonListener(
        private val activity: AnkiActivity,
        private val addonsListRecyclerView: RecyclerView
    ) : TaskListener<Void?, MutableList<AddonModel>?>() {
        var context: Context = activity.applicationContext
        override fun onPreExecute() {
            // nothing to do
        }

        override fun onPostExecute(result: MutableList<AddonModel>?) {
            activity.hideProgressBar()

            if (result.isNullOrEmpty()) {
                activity.runOnUiThread {
                    UIUtils.showSimpleSnackbar(activity, context.getString(R.string.error_occur_downloading_addon), false)
                }
                return
            }

            addonsListRecyclerView.adapter = AddonsDownloadAdapter(result)
        }
    }

    /**
     * Download .tgz file from url
     *
     * @param context
     * @param tarballUrl
     */
    class DownloadAddon(private val context: Context, private val tarballUrl: String?) :
        TaskDelegate<Void?, String?>() {

        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): String {
            return downloadPackage()
        }

        /**
         * Download .tgz file from provided url
         *
         * @return downloaded file path
         */
        fun downloadPackage(): String {
            // download the .tgz file in cache folder of AnkiDroid
            val downloadFilePath = HttpFetcher.downloadFileToSdCard(tarballUrl!!, context, "addons")
            Timber.d("download path %s", downloadFilePath)
            return downloadFilePath
        }
    }

    class DownloadAddonListener(
        private val context: Context,
        private val addonName: String?,
        private val progressDialog: Dialog
    ) : TaskListener<Void?, String?>() {
        override fun onPreExecute() {
            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title).text =
                context.getString(R.string.downloading_npm_package)

            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_message).text = addonName

            // progress bar max is 3, and it is start of first task
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).progress = 0
            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).text = "0/3"
        }

        override fun onPostExecute(result: String?) {
            // extract the downloaded .tgz file to AnkiDroid/addons dir
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).progress = 1
            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).text = "1/3"

            TaskManager.launchCollectionTask(
                ExtractAddon(context, result!!, addonName!!),
                ExtractAddonListener(context, addonName, progressDialog)
            )
        }
    }

    /**
     * Extract .tgz file, and copy to addons folder
     *
     * @param context
     * @param tarballPath path to downloaded js-addon.tgz file
     * @param addonName
     */
    class ExtractAddon(
        private val context: Context,
        private val tarballPath: String,
        private val addonName: String,
    ) : TaskDelegate<Void?, String?>() {

        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): String {
            return extractAndCopyAddonTgz(tarballPath, addonName)
        }

        /**
         * Extract npm package .tgz file to folder name 'npmPackageName' in AnkiDroid/addons/
         *
         * @param tarballPath    path to downloaded js-addon.tgz file
         * @param npmPackageName addon name, e.g ankidroid-js-addon-progress-bar
         */
        fun extractAndCopyAddonTgz(tarballPath: String, npmPackageName: String): String {
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
            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title).text =
                context.getString(R.string.extracting_npm_package)

            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_message).text = addonName

            // progress bar max is 3, and it is start of final task so progress bar set to 2
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).progress = 2
            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).text = "2/3"
        }

        override fun onPostExecute(result: String?) {
            if (result.equals(context.getString(R.string.addon_install_complete, addonName))) {
                progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title)
                    .setText(R.string.success)
            } else {
                progressDialog.findViewById<TextView>(R.id.progress_bar_layout_title)
                    .setText(R.string.failed)
            }

            progressDialog.findViewById<TextView>(R.id.progress_bar_layout_message).text = result

            // progress bar max is 3, and it is third and final task so progress bar set to 3
            progressDialog.findViewById<ProgressBar>(R.id.progress_bar).progress = 3
            progressDialog.findViewById<TextView>(R.id.progress_bar_value_div).text = "3/3"

            val okButton: Button = progressDialog.findViewById(R.id.cancel_action)
            okButton.setText(R.string.dialog_ok)
            okButton.setOnClickListener { progressDialog.dismiss() }
        }
    }
}
