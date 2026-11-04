// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.Manifest
import android.os.Build
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PermissionsActivityRecoveryTest : RobolectricTest() {
    @Before
    fun clearStorage() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q, Build.VERSION_CODES.R])
    fun `revoked storage permission cannot replace an existing collection`() {
        val originalPath = File(publicCollectionPath, "custom-collection").path
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, originalPath) }
        withManageExternalStorageInManifest {
            withPermissionsScreen { scenario ->
                scenario.onActivity { activity ->
                    activity.binding.continueButton.performClick()
                    advanceRobolectricLooper()

                    assertEquals(originalPath, collectionPath)
                    assertFalse(activity.isFinishing)
                    assertContinueRequiresPermission(activity)
                }
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q, Build.VERSION_CODES.R])
    fun `restoring access keeps a custom public collection`() {
        val originalPath = File(publicCollectionPath, "custom-collection").path
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, originalPath) }
        withManageExternalStorageInManifest {
            val permissionSet = selectStoragePermissions(targetContext)
            withPermissionsScreen { scenario ->
                scenario.onActivity { assertContinueRequiresPermission(it) }
                scenario.moveToState(Lifecycle.State.CREATED)
                withAllFilesAccess {
                    grantPermissions(*permissionSet.permissions.toTypedArray())
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity { activity ->
                        assertTrue(activity.binding.continueButton.isEnabled)
                        assertEquals(getResourceString(CommonString.dialog_continue), activity.binding.continueButton.text)
                        activity.binding.continueButton.performClick()

                        assertTrue(activity.isFinishing)
                        assertEquals(originalPath, collectionPath)
                        assertTrue(selectStoragePermissions(activity).hasRequiredPermissions(activity))
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `Internet-only request never offers to reset a private collection`() {
        val originalPath = File(targetContext.getExternalFilesDir(null), "custom-collection").path
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, originalPath) }
        denyPermissions(Manifest.permission.INTERNET)
        assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(targetContext))

        withPermissionsScreen { scenario ->
            scenario.onActivity { activity ->
                activity.binding.continueButton.performClick()
                advanceRobolectricLooper()

                assertEquals(originalPath, collectionPath)
                assertFalse(activity.isFinishing)
                assertContinueRequiresPermission(activity)
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `granting Internet enables storage opt out without storage access`() {
        denyPermissions(Manifest.permission.INTERNET)
        withManageExternalStorageInManifest {
            withPermissionsScreen { scenario ->
                scenario.onActivity { assertContinueRequiresPermission(it) }
                scenario.moveToState(Lifecycle.State.CREATED)
                grantPermissions(Manifest.permission.INTERNET)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    assertTrue(activity.binding.continueButton.isEnabled)
                    assertEquals(getResourceString(CommonString.allow_file_access), activity.binding.continueButton.text)
                    assertTrue(activity.binding.storagePermissionExplanation.isVisible)
                    activity.chooseAppFolder()
                    advanceRobolectricLooper()

                    assertTrue(activity.isFinishing)
                    assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(activity))
                    assertTrue(selectStoragePermissions(activity).hasRequiredPermissions(activity))
                }
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `granting Internet continues with already granted public storage`() {
        denyPermissions(Manifest.permission.INTERNET)
        withManageExternalStorageInManifest {
            withAllFilesAccess {
                withPermissionsScreen { scenario ->
                    scenario.onActivity { assertContinueRequiresPermission(it) }
                    scenario.moveToState(Lifecycle.State.CREATED)
                    grantPermissions(Manifest.permission.INTERNET)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity { activity ->
                        assertTrue(activity.binding.continueButton.isEnabled)
                        assertEquals(getResourceString(CommonString.dialog_continue), activity.binding.continueButton.text)
                        activity.binding.continueButton.performClick()

                        assertTrue(activity.isFinishing)
                        assertEquals(publicCollectionPath, collectionPath)
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `choosing app folder rechecks the storage decision`() {
        withManageExternalStorageInManifest {
            withPermissionsScreen { scenario ->
                scenario.onActivity { activity ->
                    val skipButton = activity.binding.continueWithoutAccess
                    assertTrue(skipButton.isEnabled)
                    val originalPath = File(publicCollectionPath, "chosen-by-another-task").path
                    targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, originalPath) }
                    skipButton.performClick()
                    advanceRobolectricLooper()

                    assertEquals(originalPath, collectionPath)
                    assertFalse(activity.isFinishing)
                    assertContinueRequiresPermission(activity)
                }
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `choosing app folder rechecks Internet permission`() {
        withManageExternalStorageInManifest {
            withPermissionsScreen { scenario ->
                scenario.onActivity { activity ->
                    val skipButton = activity.binding.continueWithoutAccess
                    assertTrue(skipButton.isEnabled)
                    denyPermissions(Manifest.permission.INTERNET)
                    skipButton.performClick()
                    advanceRobolectricLooper()

                    assertNull(collectionPath)
                    assertFalse(activity.isFinishing)
                    assertContinueRequiresPermission(activity)
                }
            }
        }
    }

    private fun withPermissionsScreen(action: (ActivityScenario<PermissionsActivity>) -> Unit) {
        val intent = PermissionsActivity.getIntent(targetContext, selectStoragePermissions(targetContext))
        ActivityScenario.launch<PermissionsActivity>(intent).use(action)
    }

    private fun assertContinueRequiresPermission(activity: PermissionsActivity) {
        assertFalse(activity.binding.continueButton.isEnabled)
        assertFalse(activity.binding.storagePermissionExplanation.isVisible)
        assertFalse(activity.binding.continueWithoutAccess.isVisible)
        assertEquals(getResourceString(CommonString.dialog_continue), activity.binding.continueButton.text)
    }

    private val collectionPath: String?
        get() = targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)
}
