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
import com.ichi2.anki.*
import junit.framework.TestCase.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class NpmPackageDownloaderTest : RobolectricTest() {
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
    }

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun getAddonsPackageJsonTest() {
        assertNotNull(result)

        // Take any two and check if tarball url exists or not
        // For valid json the following will be true
        assertTrue(result[0].dist?.get("tarball")?.startsWith("https://")!!)
        assertTrue(result[1].dist?.get("tarball")?.endsWith(".tgz")!!)
    }

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun downloadPackageTest() {
        assertNotNull(result)

        // use the .tgz url to download
        val downloadPath = NpmPackageDownloader.DownloadAddon(context, tarballUrl).downloadPackage()

        // is tgz file
        assertTrue("Valid .tgz file", downloadPath.endsWith(".tgz"))
    }

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun extractAndCopyAddonTgzTest() {
        assertNotNull(result)

        // is file extracted successfully
        val extracted: String = NpmPackageDownloader.ExtractAddon(context, downloadPath, packageName)
            .extractAndCopyAddonTgz(downloadPath, packageName)

        assertEquals(extracted, context.getString(R.string.addon_install_complete, packageName))
    }
}
