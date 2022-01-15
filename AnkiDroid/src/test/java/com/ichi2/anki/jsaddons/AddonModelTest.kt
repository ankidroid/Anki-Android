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

import android.os.Looper.getMainLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.jsaddons.NpmUtils.ANKIDROID_JS_ADDON_KEYWORDS
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.net.URL

@RunWith(AndroidJUnit4::class)
class AddonModelTest : RobolectricTest() {
    private val NPM_PACKAGE_NAME = "valid-ankidroid-js-addon-test"
    private val NOT_VALID_NPM_PACKAGE_NAME = "not-valid-ankidroid-js-addon-test"

    /**
     * In this test addons package fetched from http://registry.npmjs.org
     */

    /**
     * valid-ankidroid-js-addon-test contains following info
     * name: valid-ankidroid-js-addon-test      // npm package name
     * addonTitle: Valid AnkiDroid JS Addon     // for listing in Addon Browser with name
     * version: 1.0.0                           // may be changes when updated
     * description: This is valid ...
     * main: index.js                           // the content of this file injected to reviewer webview
     * ankidroidJsApi: 0.0.1
     * addonType: reviewer                      // two types of addon reviewer and note-editor
     * keywords: ['ankidroid-js-addon']         // to distinguish from other npm package
     * author: infinyte7
     * license: MIT
     * homepage: https://github.com/infinyte7/ankidroid-js-addon
     * dist: {'tarball':'https://registry.npmjs.org/valid-ankidroid-js-addon-test /-/valid-ankidroid-js-addon-test -1.0.0.tgz'}
     *
     *
     * but only name, addonTitle, main, ankiDroidJsApi, addonType, author, homepage and keywords are important
     *
     * @throws IOException
     */
    @Test
    @Throws(IOException::class)
    fun isValidAnkiDroidAddonTest() {
        // for testing locally it can be uncommented
        // assumeThat(Connection.isOnline(), is(true));
        shadowOf(getMainLooper()).idle()
        val context = targetContext
        // mapping for json fetched from http://registry.npmjs.org/ankidroid-js-addon-.../latest
        val mapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // fetch package.json for the addon and read value to AddonModel
        val addonModel: AddonModel = mapper.readValue(URL(context.getString(R.string.npmjs_registry, NPM_PACKAGE_NAME)), AddonModel::class.java)

        // test addon is valid or not
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

    @Test
    @Throws(IOException::class)
    fun notValidAnkiDroidAddonTest() {
        // for testing locally it can be uncommented
        // assumeThat(Connection.isOnline(), is(true));
        shadowOf(getMainLooper()).idle()
        val context = targetContext
        // mapping for json fetched from http://registry.npmjs.org/ankidroid-js-addon-.../latest
        val mapper = ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        // fetch package.json for the addon and read value to addonModel
        val addonModel: AddonModel = mapper.readValue(URL(context.getString(R.string.npmjs_registry, NOT_VALID_NPM_PACKAGE_NAME)), AddonModel::class.java)

        // test, it is not a valid addon for AnkiDroid
        assertFalse(addonModel.isValidAnkiDroidAddon())
    }
}
