/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.analytics.UsageAnalytics.preferencesWhoseChangesShouldBeReported
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.SettingsFragment
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PreferencesAnalyticsTest : RobolectricTest() {
    private val devOptionsKeys =
        PreferenceTestUtils.getKeysFromXml(targetContext, R.xml.preferences_dev_options).toSet()

    /** All preference keys besides dev options */
    private val allKeys =
        PreferenceTestUtils.getAllPreferenceKeys(targetContext)
            .subtract(devOptionsKeys)

    /** Keys of preferences that shouldn't be reported */
    private val excludedPrefs =
        setOf(
            // Share feature usage: analytics are only reported if this is enabled :)
            "analyticsOptIn",
            // Screens: don't have a value
            "generalScreen",
            "reviewingScreen",
            "syncScreen",
            "notificationsScreen",
            "controlsScreen",
            "accessibilityScreen",
            "customSyncServerScreen",
            "appBarButtonsScreen",
            "pref_screen_advanced",
            "backups_help",
            // Categories: don't have a value
            "appearance_preference_group",
            "category_plugins",
            "category_workarounds",
            // Preferences that only click: don't have a value
            "tts",
            "resetLanguages",
            // Opens App Bar buttons fragment
            "custom_buttons_link",
            // Opens Custom sync server fragment
            "custom_sync_server_link",
            "thirdpartyapps_link",
            // will be reworked in the future
            // Notify when
            "minimumCardsDueForNotification",
            // Vibrate
            "widgetVibrate",
            // Blink light
            "widgetBlink",
            // potential personal data
            "syncAccount",
            "syncBaseUrl",
            "language",
        )

    @Test
    fun `The include and excluded prefs lists don't share elements`() {
        val intersection = preferencesWhoseChangesShouldBeReported.intersect(excludedPrefs)
        assertThat(
            "The include and exclude prefs list shouldn't share elements: $intersection",
            intersection.isEmpty(),
        )
    }

    @Test
    fun `All preferences are either included or excluded in the report list`() {
        val keysNotInAList =
            allKeys
                .subtract(excludedPrefs)
                .subtract(preferencesWhoseChangesShouldBeReported)

        assertThat(
            "All preference keys must be included in either the" +
                " `preferencesWhoseChangesShouldBeReported` or the `excludedPrefs` list" +
                ": $keysNotInAList",
            keysNotInAList.isEmpty(),
        )
    }

    @Test
    fun `preferencesWhoseChangesShouldBeReported list does not have extra keys`() {
        val extraKeys = preferencesWhoseChangesShouldBeReported.subtract(allKeys)
        assertThat(
            "preferencesWhoseChangesShouldBeReported should not have" +
                " elements that aren't in the preference keys" +
                ": $extraKeys",
            extraKeys.isEmpty(),
        )
    }

    @Test
    fun `Excluded prefs list does not have extra keys`() {
        val extraKeys = excludedPrefs.subtract(allKeys)
        assertThat(
            "excludedPrefs should not have elements that aren't in the preference keys" +
                ": $extraKeys",
            extraKeys.isEmpty(),
        )
    }

    @Test
    fun `Dev options changes must not be reported`() {
        val devOptionsKeys = PreferenceTestUtils.getKeysFromXml(targetContext, R.xml.preferences_dev_options)
        val devOptionsAtReportList = preferencesWhoseChangesShouldBeReported.intersect(devOptionsKeys.toSet())

        assertThat(
            "dev options keys must not be in the `preferencesWhoseChangesShouldBeReported` list" +
                ": $devOptionsAtReportList",
            devOptionsAtReportList.isEmpty(),
        )
    }

    @Test
    fun `getPreferenceReportableValue - String`() {
        assertThat(
            SettingsFragment.getPreferenceReportableValue("3"),
            Matchers.equalTo(3),
        )
        assertNull(SettingsFragment.getPreferenceReportableValue("foo"))
    }
}
