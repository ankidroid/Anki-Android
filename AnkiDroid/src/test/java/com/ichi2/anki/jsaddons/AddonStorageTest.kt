/****************************************************************************************
 * Copyright (c) 2022 Mani infinyte01@gmail.com                                         *
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.ShadowStatFs
import com.ichi2.utils.FileOperation
import org.apache.commons.compress.archivers.ArchiveException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers.anExistingDirectory
import org.hamcrest.io.FileMatchers.anExistingFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AddonStorageTest : RobolectricTest() {
    private lateinit var addonStorage: AddonStorage
    private lateinit var tarballPath: String
    private lateinit var addonName: String

    @Before
    override fun setUp() {
        super.setUp()
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
        ShadowStatFs.markAsNonEmpty(File(currentAnkiDroidDirectory))

        addonName = "valid-ankidroid-js-addon-test"
        addonStorage = AddonStorage(targetContext)
        tarballPath = FileOperation.getFileResource("valid-ankidroid-js-addon-test-1.0.0.tgz")
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ShadowStatFs.reset()
    }

    /**
     * Test if extracted file exists in the output folder.
     * The current test will extract in .tgz in following structure
     * tempAddonDir
     * - package
     *  - index.js
     *  - README.md
     *  - package.json
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun extractTarGzipToAddonDirTest() {
        // extract file to tempAddonFolder, the function first unGzip .tgz to .tar then unTar(extract) .tar file
        addonStorage.extractTarGzipToAddonDir(File(tarballPath), addonName)

        // test if package folder exists
        // e.g. AnkiDroid/addons/valid-addon/package/
        val packageDir = addonStorage.getSelectedAddonPackageDir(addonName)
        assertThat(packageDir, anExistingDirectory())

        // test if index.js extracted successfully
        // e.g. AnkiDroid/addons/valid-addon/package/index.js
        val indexJs = addonStorage.getSelectedAddonIndexJs(addonName)
        assertThat(indexJs, anExistingFile())

        // test if README.md extracted successfully
        // e.g. AnkiDroid/addons/valid-addon/package/README.md
        val readme = addonStorage.getSelectedAddonReadme(addonName)
        assertThat(readme, anExistingFile())

        // test if package.json extracted successfully
        // e.g. AnkiDroid/addons/valid-addon/package/package.json
        val packageJson = addonStorage.getSelectedAddonPackageJson(addonName)
        assertThat(packageJson, anExistingFile())
    }
}
