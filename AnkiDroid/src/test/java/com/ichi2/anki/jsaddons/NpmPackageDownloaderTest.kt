package com.ichi2.anki.jsaddons

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RunInBackground
import com.ichi2.anki.jsaddons.NpmPackageDownloader.DownloadAddon
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class NpmPackageDownloaderTest : RobolectricTest() {
    private val NOT_VALID_ADDON_PACKAGE_NAME = "not-valid-ankidroid-js-addon-test"
    private val VALID_ADDON_PACKAGE_NAME = "valid-ankidroid-js-addon-test"
    private val VALID_ADDON_TITLE = "Valid AnkiDroid JS Addon"
    private val context: Context = targetContext

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun validAddonTest() {
        // assumeThat(Connection.isOnline(), `is`(true))

        val result: String? = DownloadAddon(context, VALID_ADDON_PACKAGE_NAME).download()

        // this string is in toast when addon successfully installed
        // download() function return 'Valid AnkiDroid JS Addon JavaScript addon installed'
        // here addon title returned
        assertEquals(context.getString(R.string.addon_installed, VALID_ADDON_TITLE), result)
    }

    @Test
    @RunInBackground
    @Throws(ExecutionException::class, InterruptedException::class)
    fun invalidAddonTest() {
        // assumeThat(Connection.isOnline(), `is`(true))

        val result: String? = DownloadAddon(context, NOT_VALID_ADDON_PACKAGE_NAME).download()

        // this string is in toast when not valid addon requested
        // download() function return, 'addon-name not a valid js addons package for AnkiDroid'
        // here npm package name returned
        assertEquals(context.getString(R.string.is_not_valid_js_addon, NOT_VALID_ADDON_PACKAGE_NAME), result)
    }
}
