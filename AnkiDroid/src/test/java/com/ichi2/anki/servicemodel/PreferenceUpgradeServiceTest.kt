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
import android.text.TextUtils
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.noteeditor.CustomToolbarButton
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade
import com.ichi2.anki.servicelayer.RemovedPreferences
import com.ichi2.anki.web.CustomSyncServer
import com.ichi2.libanki.Consts
import com.ichi2.utils.HashUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.lessThan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceUpgradeServiceTest : RobolectricTest() {

    private lateinit var mPrefs: SharedPreferences

    @Before
    override fun setUp() {
        super.setUp()
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
    fun `Legacy custom media sync URL is removed during upgrade`() {
        val syncURL = "https://msync.ankiweb.net"
        mPrefs.edit { putString(CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_URL, syncURL) }
        assertThat(mPrefs.getString(CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_URL, null), equalTo(syncURL))
        PreferenceUpgrade.RemoveLegacyMediaSyncUrl().performUpgrade(mPrefs)
        assertThat(mPrefs.getString(CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_URL, null), equalTo(null))
    }

    @Test
    fun note_editor_toolbar_button_text() {
        // add two example toolbar buttons
        val buttons = HashUtil.HashSetInit<String>(2)

        var values = arrayOf(0, "<h1>", "</h1>")
        buttons.add(TextUtils.join(Consts.FIELD_SEPARATOR, values))

        values = arrayOf(1, "<p>", "</p>")
        buttons.add(TextUtils.join(Consts.FIELD_SEPARATOR, values))

        mPrefs.edit {
            putStringSet("note_editor_custom_buttons", buttons)
        }

        // now update it and check it
        PreferenceUpgrade.UpdateNoteEditorToolbarPrefs().performUpgrade(mPrefs)

        val set = mPrefs.getStringSet("note_editor_custom_buttons", HashUtil.HashSetInit<String>(0)) as Set<String?>
        val toolbarButtons = CustomToolbarButton.fromStringSet(set)

        assertEquals("Set size", 2, set.size)
        assertEquals("Toolbar buttons size", 2, toolbarButtons.size)

        assertEquals("Button text prefs", "1", toolbarButtons[0].buttonText)
        assertEquals("Button text prefs", "2", toolbarButtons[1].buttonText)
    }

    @Test
    fun day_and_night_themes() {
        // Plain and Dark
        mPrefs.edit {
            putString("dayTheme", "1")
            putString("nightTheme", "1")
            putBoolean("invertedColors", true)
        }
        PreferenceUpgrade.UpgradeDayAndNightThemes().performUpgrade(mPrefs)

        assertThat(mPrefs.getString("dayTheme", "0"), equalTo("2"))
        assertThat(mPrefs.getString("nightTheme", "0"), equalTo("4"))
        assertThat(mPrefs.contains("invertedColors"), equalTo(false))

        // Light and Black
        mPrefs.edit {
            putString("dayTheme", "0")
            putString("nightTheme", "0")
        }
        PreferenceUpgrade.UpgradeDayAndNightThemes().performUpgrade(mPrefs)

        assertThat(mPrefs.getString("dayTheme", "1"), equalTo("1"))
        assertThat(mPrefs.getString("nightTheme", "1"), equalTo("3"))
        assertThat(mPrefs.contains("invertedColors"), equalTo(false))
    }

    @Test
    fun `Custom collection sync URL preference contains full path after upgrade`() {
        mPrefs.edit {
            putString(RemovedPreferences.PREFERENCE_CUSTOM_SYNC_BASE, "http://foo")
        }

        PreferenceUpgrade.UpgradeCustomCollectionSyncUrl().performUpgrade(mPrefs)

        assertThat(mPrefs.contains(RemovedPreferences.PREFERENCE_CUSTOM_SYNC_BASE), equalTo(false))
        assertThat(mPrefs.getString(CustomSyncServer.PREFERENCE_CUSTOM_COLLECTION_SYNC_URL, ""), equalTo("http://foo/sync/"))
    }

    @Test
    fun `Removed Use custom sync server preference is applied to both sync URL preferences after upgrade`() {
        mPrefs.edit {
            putString(CustomSyncServer.PREFERENCE_CUSTOM_COLLECTION_SYNC_URL, "http://foo/sync/")
            putBoolean(RemovedPreferences.PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER, true)
        }

        assertThat(CustomSyncServer.getCollectionSyncUrlIfSetAndEnabledOrNull(mPrefs), equalTo(null))
        assertThat(CustomSyncServer.getMediaSyncUrlIfSetAndEnabledOrNull(mPrefs), equalTo(null))

        PreferenceUpgrade.UpgradeCustomSyncServerEnabled().performUpgrade(mPrefs)

        assertThat(mPrefs.getBoolean(CustomSyncServer.PREFERENCE_CUSTOM_COLLECTION_SYNC_SERVER_ENABLED, false), equalTo(true))
        assertThat(mPrefs.getBoolean(CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_SERVER_ENABLED, false), equalTo(false))
        assertThat(CustomSyncServer.getCollectionSyncUrlIfSetAndEnabledOrNull(mPrefs), equalTo("http://foo/sync/"))
        assertThat(CustomSyncServer.getMediaSyncUrlIfSetAndEnabledOrNull(mPrefs), equalTo(null))
    }
}
