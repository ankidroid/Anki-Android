package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.jsaddons.NpmUtils.getAddonNameFromUrl
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NpmUtilsTest : RobolectricTest() {

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
}
