/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Mani infinyte01@gmail.com                                         *
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

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.jsaddons.AddonModel
import com.ichi2.anki.jsaddons.NpmPackageTgzExtract
import com.ichi2.anki.jsaddons.NpmUtils.ANKIDROID_JS_ADDON_KEYWORDS
import com.ichi2.anki.jsaddons.isValidAnkiDroidAddon
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.Shared
import junit.framework.TestCase.*
import org.apache.commons.compress.archivers.ArchiveException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
open class AddonBrowserTest : InstrumentedTest() {
    var NPM_ADDON_TGZ_PACKAGE_NAME = "valid-ankidroid-js-addon-test-1.0.0.tgz"
    var NOT_VALID_NPM_ADDON_TGZ_PACKAGE_NAME = "not-valid-ankidroid-js-addon-test-1.0.0.tgz"

    @get:Rule
    var runtimePermissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /**
     * Addon Browser list addons to screen from addons directory in recycler view
     * It is needs to test if package.json in addons directory for the addon are valid or not.
     * For valid addons list it.
     */

    /**
     * test for valid package.json from addons directory
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun isValidAnkiDroidAddonTest() {
        val tempAddonDir = File(Files.createTempDirectory("AnkiDroid-addons").toString())
        val tgzPath = Shared.getTestFilePath(testContext, NPM_ADDON_TGZ_PACKAGE_NAME)

        // extract file to tempAddonFolder, the function first unGzip .tgz to .tar then unTar(extract) .tar file
        NpmPackageTgzExtract.extractTarGzipToAddonFolder(File(tgzPath), tempAddonDir)

        // test if package folder exists
        val packagePath = File(tempAddonDir, "package")
        assertTrue(File(packagePath.toString()).exists())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertTrue(File(packageJsonPath.toString()).exists())

        // mapping for json AnkiDroid/addons/ankidroid-js-addons...
        val mapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // fetch package.json for the addon and read value to AddonModel
        val addonModel: AddonModel = mapper.readValue(File(packageJsonPath.toString()), AddonModel::class.java)

        // test addon is valid
        assertTrue(addonModel.isValidAnkiDroidAddon())
        assertEquals("valid-ankidroid-js-addon-test", addonModel.name)
        assertEquals("Valid AnkiDroid JS Addon", addonModel.addonTitle)
        assertEquals("0.0.1", addonModel.ankidroidJsApi)
        assertEquals("reviewer", addonModel.addonType)

        // check if ankidroid-js-addon present or not in mapped addonModel
        var jsAddonKeywordsPresent = false
        for (keyword in addonModel.keywords!!) {
            if (keyword == ANKIDROID_JS_ADDON_KEYWORDS) {
                jsAddonKeywordsPresent = true
                break
            }
        }
        assertTrue(jsAddonKeywordsPresent)
    }

    /**
     * test if folder inside addons directory with package.json is invalid addon
     * for not-valid-ankidroid-js-addon-1.0.0.tgz file
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun notValidAnkiDroidAddonTest() {
        val tempAddonDir = File(Files.createTempDirectory("AnkiDroid-addons").toString())
        val tgzPath = Shared.getTestFilePath(testContext, NOT_VALID_NPM_ADDON_TGZ_PACKAGE_NAME)

        // extract file to tempAddonFolder, the function first unGzip .tgz to .tar then unTar(extract) .tar file
        NpmPackageTgzExtract.extractTarGzipToAddonFolder(File(tgzPath), tempAddonDir)

        // test if package folder exists
        val packagePath = File(tempAddonDir, "package")
        assertTrue(File(packagePath.toString()).exists())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertTrue(File(packageJsonPath.toString()).exists())

        // mapping for json AnkiDroid/addons/ankidroid-js-addons...
        val mapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // fetch package.json for the addon and read value to addonModel
        val addonModel: AddonModel = mapper.readValue(File(packageJsonPath.toString()), AddonModel::class.java)

        // test addon is not valid
        assertFalse(addonModel.isValidAnkiDroidAddon())
    }
}
