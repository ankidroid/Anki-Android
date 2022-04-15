/****************************************************************************************
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

import android.content.SharedPreferences
import android.os.Looper.getMainLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.AnkiSerialization
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.jsaddons.AddonsConst.REVIEWER_ADDON
import com.ichi2.utils.FileOperation
import junit.framework.TestCase.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.collections.HashSet

@RunWith(AndroidJUnit4::class)
class AddonModelTest : RobolectricTest() {
    private lateinit var validNpmPackageJson: String
    private lateinit var notValidNpmPackageJson: String
    private lateinit var mapper: ObjectMapper
    private lateinit var mPrefs: SharedPreferences

    @Before
    fun before() {
        mPrefs = AnkiDroidApp.getSharedPrefs(targetContext)
    }

    @Before
    override fun setUp() {
        super.setUp()

        // for mapping json from assets folder
        mapper = AnkiSerialization.objectMapper

        validNpmPackageJson = FileOperation.getFileResource("valid-ankidroid-js-addon-test.json")
        notValidNpmPackageJson = FileOperation.getFileResource("not-valid-ankidroid-js-addon-test.json")
    }

    @Test
    @Throws(IOException::class)
    fun isValidAnkiDroidAddonTest() {
        // test addon is valid or not, for valid addon the result string will be empty
        val result: Pair<AddonModel?, List<String>> = getAddonModelFromJson(validNpmPackageJson)
        assertTrue(result.second.isEmpty())
        assertTrue("package.json contains required fields", result.first != null)

        // needs to test these fields
        val addon = result.first!!
        assertEquals(addon.name, "valid-ankidroid-js-addon-test")
        assertEquals(addon.addonTitle, "Valid AnkiDroid JS Addon")
        assertEquals(addon.version, "1.0.0")
        assertEquals(addon.ankidroidJsApi, "0.0.1")
        assertEquals(addon.addonType, "reviewer")
        assertEquals(addon.icon, "") // reviewer icon is empty

        val expected: List<String> = listOf("ankidroid-js-addon")
        assertEquals(addon.keywords, expected)
    }

    @Test
    @Throws(IOException::class)
    fun notValidAnkiDroidAddonTest() {
        // test addon is valid or not, for not valid addon the result string will not be empty
        val result: Pair<AddonModel?, List<String>> = getAddonModelFromJson(notValidNpmPackageJson)
        // assert that addon model is null i.e. the package.json not mapped to addon model
        assertTrue("package.json not contains required fields", result.first == null)
        // assert that error list contains error when the package.json not mapped to AddonModel
        assertFalse(result.second.isEmpty())
    }

    @Test
    fun updatePrefsTest() {
        shadowOf(getMainLooper()).idle()

        // test that prefs hashset for reviewer is empty
        var reviewerEnabledAddonSet = mPrefs.getStringSet(REVIEWER_ADDON, HashSet())
        assertEquals(0, reviewerEnabledAddonSet?.size)

        val result: Pair<AddonModel?, List<String>> = getAddonModelFromJson(validNpmPackageJson)
        val addonModel = result.first!!

        // update the prefs make it enabled
        addonModel.updatePrefs(mPrefs, REVIEWER_ADDON, false)

        // test that new prefs added and size is 1 and the prefs hashset contains enabled addons name
        reviewerEnabledAddonSet = mPrefs.getStringSet(REVIEWER_ADDON, HashSet())
        assertEquals(1, reviewerEnabledAddonSet?.size)
        assertTrue(reviewerEnabledAddonSet!!.contains(addonModel.name))

        // now remove the addons from prefs
        addonModel.updatePrefs(mPrefs, REVIEWER_ADDON, true)

        // prefs hashset size for reviewer should be zero and prefs will not have addon name
        reviewerEnabledAddonSet = mPrefs.getStringSet(REVIEWER_ADDON, HashSet())
        assertEquals(0, reviewerEnabledAddonSet?.size)
        assertFalse(reviewerEnabledAddonSet!!.contains(addonModel.name))
    }
}
