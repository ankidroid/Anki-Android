/****************************************************************************************
 * Copyright (c) 2021 Mani infinyte01@gmail.com                                         *
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
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                  *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki.jsaddons

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RunInBackground
import com.ichi2.anki.web.HttpFetcher
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class NpmPackageDownloaderTest : RobolectricTest() {
    private val NOT_VALID_ADDON_PACKAGE_NAME = "not-valid-ankidroid-js-addon-test"
    private val VALID_ADDON_PACKAGE_NAME = "valid-ankidroid-js-addon-test"
    private val context: Context = targetContext

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun getTarBallUrlTest() {
        val validUrl = URL(context.getString(R.string.npmjs_registry, VALID_ADDON_PACKAGE_NAME))
        var result: String? = NpmPackageDownloader.InstallButtonTask(context, VALID_ADDON_PACKAGE_NAME).getTarBallUrl(validUrl)!!.data

        // url will like this, version may be changed for new file
        // https://registry.npmjs.org/valid-ankidroid-js-addon-test/-/valid-ankidroid-js-addon-test-1.0.0.tgz
        assertTrue("Valid .tgz file", (result!!.startsWith("https://") && (result.endsWith(".tgz"))))

        val inValidUrl = URL(context.getString(R.string.npmjs_registry, NOT_VALID_ADDON_PACKAGE_NAME))
        result = NpmPackageDownloader.InstallButtonTask(context, VALID_ADDON_PACKAGE_NAME).getTarBallUrl(inValidUrl)!!.message

        // for invalid package url, result will be error
        assertFalse("Not a valid .tgz url", result!!.startsWith("https://"))
    }

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun downloadPackageTest() {
        val validUrl = URL(context.getString(R.string.npmjs_registry, VALID_ADDON_PACKAGE_NAME))
        var result: String? = NpmPackageDownloader.InstallButtonTask(context, VALID_ADDON_PACKAGE_NAME).getTarBallUrl(validUrl)!!.data

        // use the .tgz url to download
        result = NpmPackageDownloader.DownloadAddon(context, result!!).downloadPackage()

        // compare success message
        assertTrue("Valid .tgz file", result.endsWith(".tgz"))
    }

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun extractAndCopyAddonTgzTest() {
        val validUrl = URL(context.getString(R.string.npmjs_registry, VALID_ADDON_PACKAGE_NAME))

        // url will like this, version may be changed for new file
        // https://registry.npmjs.org/valid-ankidroid-js-addon-test/-/valid-ankidroid-js-addon-test-1.0.0.tgz
        val result: String? = NpmPackageDownloader.InstallButtonTask(context, VALID_ADDON_PACKAGE_NAME).getTarBallUrl(validUrl)!!.data

        // download .tgz file from previous result
        val downloadFilePath = HttpFetcher.downloadFileToSdCardMethod(result, context, "addons", "GET")

        // is file extracted successfully
        val extracted: String = NpmPackageDownloader.ExtractAddon(context, result!!, VALID_ADDON_PACKAGE_NAME)
            .extractAndCopyAddonTgz(downloadFilePath, VALID_ADDON_PACKAGE_NAME)

        assertEquals(extracted, context.getString(R.string.addon_install_complete, VALID_ADDON_PACKAGE_NAME))
    }
}
