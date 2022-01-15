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

package com.ichi2.anki.jsaddons

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.apache.commons.compress.archivers.ArchiveException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.net.URL

@RunWith(AndroidJUnit4::class)
class NpmPackageTgzExtractTest : RobolectricTest() {
    private lateinit var context: Context
    private lateinit var url: URL
    private lateinit var tarballUrl: String
    private lateinit var packageName: String
    private lateinit var downloadPath: String
    private lateinit var result: MutableList<AddonModel>
    private lateinit var currentAnkiDroidDirectory: String
    private lateinit var addonDir: File

    @Before
    override fun setUp() {
        super.setUp()
        context = targetContext
        url = URL(context.getString(R.string.ankidroid_js_addon_json))

        result = NpmPackageDownloader.GetAddonsPackageJson(context).getJson(url)!!

        // For valid json the following will be true
        tarballUrl = result[0].dist?.get("tarball")!!
        packageName = result[0].name!!

        // use the .tgz url to download
        downloadPath = NpmPackageDownloader.DownloadAddon(context, tarballUrl).downloadPackage()

        currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        addonDir = File(currentAnkiDroidDirectory, "addons")
        if (!addonDir.exists()) {
            addonDir.mkdirs()
        }
    }

    /**
     * Test if the file is valid GZip file
     * @throws IOException
     */

    @Test
    @Throws(IOException::class)
    fun isGzipTest() {
        // test if file is tar gzip
        assertNotNull(result)
        assertTrue(NpmPackageTgzExtract.isGzip(File(downloadPath)))
    }

    /**
     * Test if extracted file exists in the output folder.
     * The current test will extract in .tgz in following structure
     * tempAddonDir
     * - package
     * - index.js
     * - README.md
     * - package.json
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun extractTarGzipToAddonFolderTest() {
        assertNotNull(result)

        // extract file to tempAddonFolder, the function first unGzip .tgz to .tar then unTar(extract) .tar file
        NpmPackageTgzExtract.extractTarGzipToAddonFolder(File(downloadPath), addonDir)

        // test if package folder exists
        val packagePath = File(addonDir, "package")
        assertTrue(File(packagePath.toString()).exists())

        // test if index.js extracted successfully
        val indexJsPath = File(packagePath, "index.js")
        assertTrue(File(indexJsPath.toString()).exists())

        // test if README.md extracted successfully
        val readmePath = File(packagePath, "README.md")
        assertTrue(File(readmePath.toString()).exists())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertTrue(File(packageJsonPath.toString()).exists())
    }

    /**
     * Test if .tar file unTar successfully to temp folder
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun unTarTest() {

        // first unGzip .tgz file to .tar
        val unGzipFile = NpmPackageTgzExtract.unGzip(File(downloadPath), addonDir)

        // unTar .tar file to temp folder, it is same as extract of files to tempAddonDir
        NpmPackageTgzExtract.unTar(unGzipFile, addonDir)

        // test if package folder exists
        val packagePath = File(addonDir, "package")
        assertTrue(File(packagePath.toString()).exists())

        // test if index.js extracted successfully
        val indexJsPath = File(packagePath, "index.js")
        assertTrue(File(indexJsPath.toString()).exists())

        // test if README.md extracted successfully
        val readmePath = File(packagePath, "README.md")
        assertTrue(File(readmePath.toString()).exists())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertTrue(File(packageJsonPath.toString()).exists())
    }

    /**
     * Test if .tgz file successfully unGzipped
     * i.e. .tgz changed to .tar file
     *
     * @throws IOException
     */
    @Test
    @Throws(IOException::class)
    fun unGzipTest() {
        val unGzipFile = NpmPackageTgzExtract.unGzip(File(downloadPath), addonDir)

        // test if unGzip successfully return tar file
        assertTrue(File(unGzipFile.toString()).exists())

        // test if .tgz file changed to .tar file
        assertTrue(File(unGzipFile.toString()).absolutePath.endsWith(".tar"))
    }
}
