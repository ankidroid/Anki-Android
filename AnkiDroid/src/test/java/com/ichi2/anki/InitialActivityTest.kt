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

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.kotlin.never
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class) // no point in Application init if we don't use it
class InitialActivityTest : RobolectricTest() {

    private lateinit var mSharedPreferences: SharedPreferences

    @Before
    fun before() {
        mSharedPreferences = AnkiDroidApp.getSharedPrefs(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun perform_setup_returns_true_after_first_launch_or_data_wipe() {
        val result = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(mSharedPreferences)
        assertThat(result, equalTo(true))
    }

    @Test
    fun perform_setup_returns_false_after_setup() {
        InitialActivity.setUpgradedToLatestVersion(mSharedPreferences)

        val resultAfterUpgrade = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(mSharedPreferences)
        assertThat("should not perform initial setup if setup has already occurred", resultAfterUpgrade, equalTo(false))
    }

    @Test
    fun initially_not_latest_version() {
        assertThat(InitialActivity.isLatestVersion(mSharedPreferences), equalTo(false))
    }

    @Test
    fun not_latest_version_with_valid_value() {
        mSharedPreferences.edit { putString("lastVersion", "0.1") }
        assertThat(InitialActivity.isLatestVersion(mSharedPreferences), equalTo(false))
    }

    @Test
    fun latest_version_upgrade_is_now_latest_version() {
        InitialActivity.setUpgradedToLatestVersion(mSharedPreferences)
        assertThat(InitialActivity.isLatestVersion(mSharedPreferences), equalTo(true))
    }

    @Test
    @SuppressLint("CheckResult") // performSetupFromFreshInstallOrClearedPreferences
    fun new_install_or_preference_data_wipe_means_preferences_up_to_date() {
        mockStatic(PreferenceUpgradeService::class.java).use { mocked ->
            InitialActivity.performSetupFromFreshInstallOrClearedPreferences(mSharedPreferences)
            mocked.verify({ PreferenceUpgradeService.setPreferencesUpToDate(mSharedPreferences) }, times(1))
        }
    }

    @Test
    fun prefs_may_not_be_up_to_date_if_upgraded() {
        mockStatic(PreferenceUpgradeService::class.java).use { mocked ->
            InitialActivity.setUpgradedToLatestVersion(mSharedPreferences)
            assertThat(InitialActivity.performSetupFromFreshInstallOrClearedPreferences(mSharedPreferences), equalTo(false))
            mocked.verify({ PreferenceUpgradeService.setPreferencesUpToDate(mSharedPreferences) }, never())
        }
    }

    @Test
    fun perform_setup_integration_test() {
        val sharedPrefs = AnkiDroidApp.getSharedPrefs(ApplicationProvider.getApplicationContext())
        val initialSetupResult = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(AnkiDroidApp.getSharedPrefs(ApplicationProvider.getApplicationContext()))
        assertThat(initialSetupResult, equalTo(true))
        val secondResult = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPrefs)
        assertThat("should not perform initial setup if setup has already occurred", secondResult, equalTo(false))
    }
}
