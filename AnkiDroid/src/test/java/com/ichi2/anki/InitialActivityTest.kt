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
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidFolder.AppPrivateFolder
import com.ichi2.anki.AnkiDroidFolder.PublicFolder
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.core.IsInstanceOf.instanceOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.kotlin.never
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class) // no point in Application init if we don't use it
class InitialActivityTest : RobolectricTest() {

    private lateinit var sharedPreferences: SharedPreferences
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun before() {
        sharedPreferences = appContext.sharedPrefs()
    }

    @Test
    fun perform_setup_returns_true_after_first_launch_or_data_wipe() {
        val result = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences)
        assertThat(result, equalTo(true))
    }

    @Test
    fun perform_setup_returns_false_after_setup() {
        InitialActivity.setUpgradedToLatestVersion(sharedPreferences)

        val resultAfterUpgrade = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences)
        assertThat("should not perform initial setup if setup has already occurred", resultAfterUpgrade, equalTo(false))
    }

    @Test
    fun initially_not_latest_version() {
        assertThat(InitialActivity.isLatestVersion(sharedPreferences), equalTo(false))
    }

    @Test
    fun not_latest_version_with_valid_value() {
        sharedPreferences.edit { putString("lastVersion", "0.1") }
        assertThat(InitialActivity.isLatestVersion(sharedPreferences), equalTo(false))
    }

    @Test
    fun latest_version_upgrade_is_now_latest_version() {
        InitialActivity.setUpgradedToLatestVersion(sharedPreferences)
        assertThat(InitialActivity.isLatestVersion(sharedPreferences), equalTo(true))
    }

    @Test
    @SuppressLint("CheckResult") // performSetupFromFreshInstallOrClearedPreferences
    fun new_install_or_preference_data_wipe_means_preferences_up_to_date() {
        mockStatic(PreferenceUpgradeService::class.java).use { mocked ->
            InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences)
            mocked.verify({ PreferenceUpgradeService.setPreferencesUpToDate(sharedPreferences) }, times(1))
        }
    }

    @Test
    fun prefs_may_not_be_up_to_date_if_upgraded() {
        mockStatic(PreferenceUpgradeService::class.java).use { mocked ->
            InitialActivity.setUpgradedToLatestVersion(sharedPreferences)
            assertThat(InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPreferences), equalTo(false))
            mocked.verify({ PreferenceUpgradeService.setPreferencesUpToDate(sharedPreferences) }, never())
        }
    }

    @Test
    fun perform_setup_integration_test() {
        val sharedPrefs = appContext.sharedPrefs()
        val initialSetupResult = InitialActivity.performSetupFromFreshInstallOrClearedPreferences(
            appContext.sharedPrefs()
        )
        assertThat(initialSetupResult, equalTo(true))
        val secondResult =
            InitialActivity.performSetupFromFreshInstallOrClearedPreferences(sharedPrefs)
        assertThat(
            "should not perform initial setup if setup has already occurred",
            secondResult,
            equalTo(false)
        )
    }

    @Config(sdk = [BEFORE_Q])
    @Test
    fun startupBeforeQ() {
        val expectedPermissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        // force a safe startup before Q
        assertThat(
            (selectAnkiDroidFolder(false) as PublicFolder).requiredPermissions.asIterable(),
            contains(*expectedPermissions)
        )
        assertThat(
            (selectAnkiDroidFolder(true) as PublicFolder).requiredPermissions.asIterable(),
            contains(*expectedPermissions)
        )
    }

    @Config(sdk = [Q])
    @Test
    fun startupQ() {
        assertThat(
            selectAnkiDroidFolder(false),
            instanceOf(PublicFolder::class.java)
        )
        assertThat(
            selectAnkiDroidFolder(true),
            instanceOf(PublicFolder::class.java)
        )
    }

    @SuppressLint("InlinedApi")
    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `Android 11 - After upgrade from AnkiDroid 2 15 (with MANAGE_EXTERNAL_STORAGE)`() {
        // after an upgrade, all we need is READ/WRITE. Once we reinstall, we need MANAGE_EXTERNAL_STORAGE
        val expectedPermissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        selectAnkiDroidFolder(
            canManageExternalStorage = true,
            currentFolderIsAccessibleAndLegacy = true
        ).let {
            assertThat(
                (it as PublicFolder).requiredPermissions.asIterable(),
                contains(*expectedPermissions)
            )
        }
    }

    @SuppressLint("InlinedApi")
    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `Android 11 - After reinstall (with MANAGE_EXTERNAL_STORAGE)`() {
        val ankiDroidFolder = selectAnkiDroidFolder(
            canManageExternalStorage = true,
            currentFolderIsAccessibleAndLegacy = false
        ) as PublicFolder

        assertTrue(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE in ankiDroidFolder.requiredPermissions)
    }

    @Config(sdk = [R_OR_AFTER])
    @Test
    fun startupAfterQWithoutManageExternalStorage() {
        assertThat(
            selectAnkiDroidFolder(canManageExternalStorage = false),
            instanceOf(AppPrivateFolder::class.java)
        )
    }

    private val AnkiDroidFolder.requiredPermissions
        get() = permissionSet.permissions

    /**
     * Helper for [com.ichi2.anki.selectAnkiDroidFolder], making `currentFolderIsAccessibleAndLegacy` optional
     */
    private fun selectAnkiDroidFolder(
        canManageExternalStorage: Boolean,
        currentFolderIsAccessibleAndLegacy: Boolean = false
    ): AnkiDroidFolder {
        return com.ichi2.anki.selectAnkiDroidFolder(
            canManageExternalStorage = canManageExternalStorage,
            currentFolderIsAccessibleAndLegacy = currentFolderIsAccessibleAndLegacy
        )
    }

    companion object {
        const val BEFORE_Q = Build.VERSION_CODES.Q - 1
        const val Q = Build.VERSION_CODES.Q
        const val R_OR_AFTER = Build.VERSION_CODES.R
    }
}
