/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01></infinyte01>@gmail.com>                                       *
 * *
 * *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 * *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                           *
 */
package com.ichi2.anki.jsaddons

import android.content.Context
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.jsaddons.NpmUtils.isvalidAnkiDroidAddon
import com.ichi2.anki.web.HttpFetcher
import com.ichi2.async.ProgressSenderAndCancelListener
import com.ichi2.async.TaskDelegate
import com.ichi2.async.TaskListener
import com.ichi2.libanki.Collection
import org.apache.commons.compress.archivers.ArchiveException
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.UnknownHostException

class NpmPackageDownloader {

    class DownloadAddon(private val mContext: Context, private val mNpmPackageName: String) : TaskDelegate<Void?, String?>() {

        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void?>): String? {
            return download()
        }

        /**
         * Using Jackson the latest package.json for the addon fetched, then mapped to AddonModel
         * For valid package it gets tarball url from mapped AddonModel,
         * then downloads and extract to AnkiDroid/addons folder using {@code extractAndCopyAddonTgz} and toast with success message returned
         *
         * For invalid addon or for exception occurred, it returns message to respective to the errors from catch block
         */
        fun download(): String? {
            try {
                // mapping for json fetched from http://registry.npmjs.org/ankidroid-js-addon-.../latest
                val mapper = ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val addonModel = mapper.readValue(URL(mContext.getString(R.string.npmjs_registry, mNpmPackageName)), AddonModel::class.java)

                // check if fields like ankidroidJsApi, addonType exists or not
                if (!isvalidAnkiDroidAddon(addonModel)) {
                    return mContext.getString(R.string.is_not_valid_js_addon, mNpmPackageName)
                }

                // get tarball url to download it cache folder
                val tarballUrl = addonModel.dist!!["tarball"]

                // download the .tgz file in cache folder of AnkiDroid
                val downloadFilePath = HttpFetcher.downloadFileToSdCardMethod(tarballUrl, mContext, "addons", "GET")
                Timber.d("download path %s", downloadFilePath)

                // extract the .tgz file to AnkiDroid/addons dir
                val extracted = extractAndCopyAddonTgz(downloadFilePath, mNpmPackageName)

                if (!extracted) {
                    return mContext.getString(R.string.failed_to_extract_addon_package, addonModel.addonTitle)
                } else {
                    return mContext.getString(R.string.addon_installed, addonModel.addonTitle)
                }

                // addonTitle sent to list the addons in recycler view
            } catch (e: JacksonException) {
                // json format is not valid as required by AnkiDroid JS Addon specifications
                // also ObjectMapper failed to parse the fields for e.g. requested fields in AddonModel is String but
                // package.json contains array, so it may leads to parse exception or mapping exception
                Timber.w(e.localizedMessage)
                return mContext.getString(R.string.is_not_valid_js_addon, mNpmPackageName)
            } catch (e: UnknownHostException) {
                // user not connected to internet
                Timber.w(e.localizedMessage)
                return mContext.getString(R.string.network_no_connection)
            } catch (e: NullPointerException) {
                Timber.w(e.localizedMessage)
                return mContext.getString(R.string.error_occur_downloading_addon, mNpmPackageName)
            } catch (e: IOException) {
                Timber.w(e.localizedMessage)
                return mContext.getString(R.string.error_occur_downloading_addon, mNpmPackageName)
            }
        }

        /**
         * Extract npm package .tgz file to folder name 'npmPackageName' in AnkiDroid/addons/
         *
         * @param tarballPath    path to downloaded js-addon.tgz file
         * @param npmPackageName addon name, e.g ankidroid-js-addon-progress-bar
         */
        fun extractAndCopyAddonTgz(tarballPath: String?, npmPackageName: String): Boolean {
            if (tarballPath == null) {
                return false
            }

            val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(mContext)

            // AnkiDroid/addons/js-addons
            // here npmAddonName is id of npm package which may not contain ../ or other bad path
            val addonsDir = File(currentAnkiDroidDirectory, "addons")
            val addonsPackageDir = File(addonsDir, npmPackageName)
            val tarballFile = File(tarballPath)
            if (!tarballFile.exists()) {
                return false
            }

            try {
                NpmPackageTgzExtract.extractTarGzipToAddonFolder(tarballFile, addonsPackageDir)
                Timber.d("js addon .tgz extracted")
            } catch (e: IOException) {
                Timber.e(e.localizedMessage)
                return false
            } catch (e: ArchiveException) {
                Timber.e(e.localizedMessage)
                return false
            } finally {
                tarballFile.delete()
            }
            return true
        }
    }

    class DownloadAddonListener(private val mContext: Context) : TaskListener<Void?, String?>() {
        override fun onPreExecute() {
            UIUtils.showThemedToast(mContext, mContext.getString(R.string.checking_valid_addon), false)
        }

        override fun onPostExecute(result: String?) {
            UIUtils.showThemedToast(mContext, result, false)
        }
    }
}
