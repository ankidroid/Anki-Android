// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.OptionalPermissionSet
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.databinding.FragmentPermissionsStartingAt30Binding
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PermissionsActivityTest : RobolectricTest() {
    @Before
    fun clearStorage() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
    }

    @Test
    fun testActivityCantBeClosedByBackButton() {
        testActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
            assertThat("activity is not finishing", !activity.isFinishing)
        }
    }

    @Test
    fun testOnClickingContinueActivityFinishes() {
        grantPermissions(*ARBITRARY_PERMISSION_SET.permissions.toTypedArray())
        testActivity { activity ->
            assertTrue(activity.binding.continueButton.isEnabled)
            activity.binding.continueButton.performClick()
            assertThat("activity is finishing", activity.isFinishing)
        }
    }

    @Test // Issue 13574: completing the screen must record the storage decision
    @Config(sdk = [Build.VERSION_CODES.R])
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    fun `continuing decides the collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            withAllFilesAccess {
                testActivity(StoragePermissionSet.EXTERNAL_MANAGER) { activity ->
                    assertTrue(activity.binding.continueButton.isEnabled)
                    activity.binding.continueButton.performClick()
                    assertThat("activity is finishing", activity.isFinishing)
                }
            }
        }
        assertEquals(publicCollectionPath, collectionPath)
    }

    @Test // Issue 13574: PermissionsStartingAt30Fragment completes the screen once 'All files access' is granted
    @Config(sdk = [Build.VERSION_CODES.R])
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    fun `completing without Continue decides the collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        val settingsScreen = ComponentName("com.android.settings", "AllFilesAccessSettings")
        shadowOf(targetContext.packageManager).apply {
            addActivityIfNotPresent(settingsScreen).exported = true
            addIntentFilterForActivity(
                settingsScreen,
                IntentFilter(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("package")
                },
            )
        }

        withManageExternalStorageInManifest {
            testActivity(StoragePermissionSet.EXTERNAL_MANAGER) { activity ->
                assertTrue(activity.binding.continueButton.isEnabled)
                val fragment = activity.binding.fragmentContainer.getFragment<PermissionsStartingAt30Fragment>()
                FragmentPermissionsStartingAt30Binding.bind(fragment.requireView()).allFilesPermission.performClick()

                val shadowActivity = shadowOf(activity)
                val settingsIntent = assertNotNull(shadowActivity.nextStartedActivityForResult).intent
                assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, settingsIntent.action)
                assertNull(collectionPath)

                withAllFilesAccess {
                    shadowActivity.receiveResult(settingsIntent, Activity.RESULT_CANCELED, Intent())
                }

                assertEquals(publicCollectionPath, collectionPath)
                assertThat("activity is finishing", activity.isFinishing)
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `fresh setup explains app folder without choosing it`() {
        testOptionalStorageActivity { activity ->
            assertEquals(getResourceString(CommonString.use_app_folder), activity.binding.continueButton.text)
            assertTrue(activity.binding.privateStorageExplanation.isVisible)
            assertEquals(getResourceString(CommonString.private_storage_explanation), activity.binding.privateStorageExplanation.text)
            assertNull(collectionPath)
            assertNull(ShadowDialog.getLatestDialog())
            assertFalse(activity.isFinishing)
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `choosing app folder writes its path without a confirmation dialog`() {
        testOptionalStorageActivity { activity ->
            activity.binding.continueButton.performClick()
            advanceRobolectricLooper()
            val expectedPath = File(CollectionHelper.getAppSpecificExternalDirectories(activity).first(), "AnkiDroid").absolutePath
            assertThat("collection path is set to private dir", collectionPath, equalTo(expectedPath))
            assertNull(ShadowDialog.getLatestDialog())
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q, Build.VERSION_CODES.R])
    fun `legacy storage permissions cannot be skipped`() {
        testActivity(StoragePermissionSet.LEGACY_ACCESS) { activity ->
            assertFalse(activity.binding.continueButton.isEnabled)
            assertFalse(activity.binding.privateStorageExplanation.isVisible)
            assertEquals(getResourceString(CommonString.dialog_continue), activity.binding.continueButton.text)
            activity.binding.continueButton.performClick()
            advanceRobolectricLooper()

            assertNull(ShadowDialog.getLatestDialog())
            assertNull(collectionPath)
            assertFalse(activity.isFinishing)
        }
    }

    @SuppressLint("NewApi") // Callers use @Config(sdk = [R]), which guarantees EXTERNAL_MANAGER is available.
    private fun testOptionalStorageActivity(action: ActivityAction<PermissionsActivity>) {
        withManageExternalStorageInManifest {
            testActivity(StoragePermissionSet.EXTERNAL_MANAGER, action)
        }
    }

    @Test // Issue 21046
    @Config(sdk = [Build.VERSION_CODES.R])
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    fun `after choosing app folder, storage permissions are no longer required on startup`() {
        withManageExternalStorageInManifest {
            assertThat("this is a fresh install", InitialActivity.wasFreshInstall(targetContext.sharedPrefs()))
            testActivity(StoragePermissionSet.EXTERNAL_MANAGER) { activity ->
                activity.binding.continueButton.performClick()
                advanceRobolectricLooper()
                assertThat(
                    "no storage permissions are required for the app folder",
                    selectStoragePermissions(activity),
                    equalTo(StoragePermissionSet.APP_PRIVATE),
                )
            }
        }
    }

    private val collectionPath: String?
        get() = targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    @Test
    fun `error toast is shown if EXTRA_PERMISSIONS_SET is missing`() {
        testInvalidActivityFinishes()
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(getResourceString(CommonString.something_wrong)),
        )
    }

    @Test
    fun `Each screen starts normally and has the same permissions of a PermissionSet`() {
        testActivity { activity ->
            val permissionSets: List<PermissionSet> = StoragePermissionSet.entries + OptionalPermissionSet.entries
            for (permissionSet in permissionSets) {
                val fragment = permissionSet.permissionsFragment.getDeclaredConstructor().newInstance()
                activity.supportFragmentManager.commitNow {
                    replace(R.id.fragment_container, fragment)
                }
                val allPermissions = fragment.permissionsItems.flatMap { it.permissions }

                assertThat(permissionSet.permissions, containsInAnyOrder(allPermissions))
            }
        }
    }

    private fun testInvalidActivityFinishes() {
        val ex = assertFailsWith<NullPointerException> { testActivity(permissionSet = null) { } }
        assertEquals("Cannot run onActivity since Activity has been destroyed already", ex.message)
    }

    private fun testActivity(
        permissionSet: StoragePermissionSet? = ARBITRARY_PERMISSION_SET,
        action: ActivityAction<PermissionsActivity>,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            if (permissionSet != null) {
                PermissionsActivity.getIntent(context, permissionSet)
            } else {
                Intent(context, PermissionsActivity::class.java)
            }
        ActivityScenario.launch<PermissionsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                action.perform(activity)
            }
        }
    }

    companion object {
        val ARBITRARY_PERMISSION_SET = StoragePermissionSet.entries.first()
    }
}
