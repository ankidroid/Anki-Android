/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.servicemodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade
import com.ichi2.anki.web.CustomSyncServer
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.lessThan
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class) // no point in Application init if we don't use it
class PreferenceUpgradeServiceTest : RobolectricTest() {

    private lateinit var mPrefs: SharedPreferences

    @Before
    fun before() {
        mPrefs = AnkiDroidApp.getSharedPrefs(targetContext)
    }

    @Test
    fun first_app_load_performs_no_upgrades() {
        PreferenceUpgradeService.setPreferencesUpToDate(mPrefs)
        val result = PreferenceUpgradeService.upgradePreferences(mPrefs, 0)
        assertThat("no upgrade should have taken place", result, equalTo(false))
    }

    @Test
    fun preference_upgrade_leads_to_max_version_in_preferences() {
        val result = PreferenceUpgradeService.upgradePreferences(mPrefs, 0)
        assertThat("preferences were upgraded", result, equalTo(true))
        val version = PreferenceUpgrade.getPreferenceVersion(mPrefs)
        PreferenceUpgradeService.setPreferencesUpToDate(mPrefs)
        val secondVersion = PreferenceUpgrade.getPreferenceVersion(mPrefs)
        assertThat("setPreferencesUpToDate should not change the version", secondVersion, equalTo(version))
    }

    @Test
    fun two_upgrades_does_nothing() {
        val result = PreferenceUpgradeService.upgradePreferences(mPrefs, 0)
        assertThat("preferences were upgraded", result, equalTo(true))
        val secondResult = PreferenceUpgradeService.upgradePreferences(mPrefs, 0)
        assertThat("a second preference upgrade does nothing", secondResult, equalTo(false))
    }

    @Test
    fun each_version_code_is_distinct() {
        val codes = PreferenceUpgrade.getAllVersionIdentifiers().toList()
        assertThat("all version IDs should be distinct", codes.size, equalTo(codes.distinct().size))
    }

    @Test
    fun version_codes_do_not_decrease() {
        // in this test, we ensure that version codes are monotonically increasing.
        val codes = PreferenceUpgrade.getAllVersionIdentifiers()

        codes.zip(codes.drop(1)).forEach {
            assertThat(
                "versions should be increasing, but found (${it.first}) before (${it.second})",
                it.first,
                lessThan(it.second)
            )
        }
    }

    @Test
    fun one_version_code_per_nested_class() {
        val nestedClasses = PreferenceUpgrade::class.nestedClasses.filter { it.simpleName != "Companion" }
        val nestedClassCount = nestedClasses.size
        val upgradeCount = PreferenceUpgrade.getAllVersionIdentifiers().toList().size

        assertThat(
            "Different count of nested classes ($nestedClassCount) and upgrades ($upgradeCount). \n" +
                "nested classes:\n ${nestedClasses.map { it.simpleName }.joinToString("\n")}",
            nestedClassCount,
            equalTo(upgradeCount)
        )
    }

    @Test
    fun check_custom_media_sync_url() {
        var syncURL = "https://msync.ankiweb.net"
        mPrefs.edit { putString(CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_URL, syncURL) }
        assertThat("Preference of custom media sync url is set to ($syncURL).", CustomSyncServer.getMediaSyncUrl(mPrefs).equals(syncURL))
        PreferenceUpgrade.RemoveLegacyMediaSyncUrl().performUpgrade(mPrefs)
        assertThat("Preference of custom media sync url is removed.", CustomSyncServer.getMediaSyncUrl(mPrefs).equals(null))
    }
}
