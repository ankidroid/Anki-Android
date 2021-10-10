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

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.jsaddons.NpmUtils.REVIEWER_ADDON
import com.ichi2.anki.jsaddons.NpmUtils.getAddonNameFromUrl
import com.ichi2.anki.jsaddons.NpmUtils.getEnabledAddonsContent
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.Shared
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class NpmUtilsTest : InstrumentedTest() {
    var NPM_ADDON_TGZ_PACKAGE_NAME = "valid-ankidroid-js-addon-test-1.0.0.tgz"

    @get:Rule
    var mRuntimePermissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /**
     * URL for openUrl in DownloadAddonBroadcastReceiver
     * https://www.npmjs.com/search?q=keywords:ankidroid-js-addon
     */

    // npm search url
    private val NPM_PACKAGE_SEARCH_URL = "https://www.npmjs.com/search?q=keywords:ankidroid-js-addon"

    // valid npm package url
    private val NPM_PACKAGE_URL = "https://www.npmjs.com/package/valid-ankidroid-js-addon-test"

    // valid npm package url with version
    private val NPM_PACKAGE_URL_WITH_VERSION = "https://www.npmjs.com/package/valid-ankidroid-js-addon-test/v/1.0.0"

    // valid npm package url with ?activeTab
    private val NPM_PACKAGE_URL_WITH_TAB = "https://www.npmjs.com/package/valid-ankidroid-js-addon-test?activeTab=versions"

    // different url
    private val ANKIDROID_DOC_URL = "https://docs.ankidroid.org/"

    /**
     *  If the url does not start with https://www.npmjs.com, then result will be null,
     *  else result will addon title for valid addon and addon package name for invalid addon
     */
    @Test
    fun addonNameFromUrlTest() {
        // if url is search url then get null
        var result = getAddonNameFromUrl(NPM_PACKAGE_SEARCH_URL)
        assertNull(result)

        // else get package name from npm url
        result = getAddonNameFromUrl(NPM_PACKAGE_URL)
        assertEquals("valid-ankidroid-js-addon-test", result)

        // also package with different version accessed then get package name from npm url
        result = getAddonNameFromUrl(NPM_PACKAGE_URL_WITH_VERSION)
        assertEquals("valid-ankidroid-js-addon-test", result)

        //
        result = getAddonNameFromUrl(NPM_PACKAGE_URL_WITH_TAB)
        assertEquals("valid-ankidroid-js-addon-test", result)

        // the url does not start with https://www.npmjs.com, then get null
        result = getAddonNameFromUrl(ANKIDROID_DOC_URL)
        assertNull(result)
    }

    @Test
    fun getEnabledAddonsContentTest() {
        // setup
        val sharedPrefs = AnkiDroidApp.getSharedPrefs(testContext)
        val jsAddonKey: String = REVIEWER_ADDON

        val tempAddonDir = File(Files.createTempDirectory("AnkiDroid-addons").toString())
        val tgzPath = Shared.getTestFilePath(testContext, NPM_ADDON_TGZ_PACKAGE_NAME)

        // extract file to tempAddonFolder, the function first unGzip .tgz to .tar then unTar(extract) .tar file
        NpmPackageTgzExtract.extractTarGzipToAddonFolder(File(tgzPath), tempAddonDir)
        val packagePath = File(tempAddonDir, "package")
        val packageJsonPath = File(packagePath, "package.json")

        // mapping for json AnkiDroid/addons/ankidroid-js-addons...
        val mapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        // fetch package.json for the addon and read value to AddonModel
        val addonModel: AddonModel = mapper.readValue(File(packageJsonPath.toString()), AddonModel::class.java)
        // enable the addon
        addonModel.updatePrefs(sharedPrefs, jsAddonKey, false)

        // test
        val result = "<script src='/storage/emulated/0/AnkiDroid/addons/valid-ankidroid-js-addon-test/package/index.js'></script>\n"
        assertEquals(result, getEnabledAddonsContent(testContext))
    }
}
