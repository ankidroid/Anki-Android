// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.Manifest
import android.os.Build
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.databinding.FragmentInternetPermissionBinding
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R])
class PermissionsActivityStorageRefreshTest : RobolectricTest() {
    @Test
    @Config(sdk = [Build.VERSION_CODES.Q, Build.VERSION_CODES.R])
    fun `restoring the original request after a saved storage change finishes`() {
        val intent = PermissionsActivity.getIntent(targetContext, StoragePermissionSet.LEGACY_ACCESS)
        val privatePath = File(targetContext.getExternalFilesDir(null), "AnkiDroid").path
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, privatePath) }
        // A new instance has no retained ViewModel, as when Android restores the task after process death.
        val controller = Robolectric.buildActivity(PermissionsActivity::class.java, intent)
        saveControllerForCleanup(controller)
        controller.setup()

        assertTrue(controller.get().isFinishing)
        assertEquals(privatePath, targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q, Build.VERSION_CODES.R])
    fun `restoring a completed storage change still requires Internet`() {
        val intent = PermissionsActivity.getIntent(targetContext, StoragePermissionSet.LEGACY_ACCESS)
        val privatePath = File(targetContext.getExternalFilesDir(null), "AnkiDroid").path
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, privatePath) }
        denyPermissions(Manifest.permission.INTERNET)
        val controller = Robolectric.buildActivity(PermissionsActivity::class.java, intent)
        saveControllerForCleanup(controller)
        controller.setup()
        val activity = controller.get()

        assertFalse(activity.isFinishing)
        assertFalse(activity.binding.continueButton.isEnabled)
        val fragment = assertIs<InternetPermissionFragment>(activity.supportFragmentManager.findFragmentById(R.id.fragment_container))
        val fragmentBinding = FragmentInternetPermissionBinding.bind(fragment.requireView())
        assertTrue(fragmentBinding.internetPermission.isVisible)
        controller.pause().stop()
        grantPermissions(Manifest.permission.INTERNET)
        controller.start().resume()
        advanceRobolectricLooper()
        assertTrue(activity.binding.continueButton.isEnabled)
        activity.binding.continueButton.performClick()
        assertTrue(activity.isFinishing)
        assertEquals(privatePath, targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
    }

    @Test
    fun `a private path chosen by another task refreshes the required permissions`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            val controller =
                Robolectric.buildActivity(
                    PermissionsActivity::class.java,
                    PermissionsActivity.getIntent(targetContext, selectStoragePermissions(targetContext)),
                )
            saveControllerForCleanup(controller)
            controller.setup()
            val activity = controller.get()
            val button = activity.binding.continueButton
            denyPermissions(Manifest.permission.INTERNET)
            val privatePath = File(targetContext.getExternalFilesDir(null), "chosen-by-another-task").path
            targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, privatePath) }
            button.performClick()
            advanceRobolectricLooper()

            assertEquals(privatePath, targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
            assertFalse(activity.isFinishing)
            val fragment = assertIs<InternetPermissionFragment>(activity.supportFragmentManager.findFragmentById(R.id.fragment_container))
            val fragmentBinding = FragmentInternetPermissionBinding.bind(fragment.requireView())
            assertTrue(fragmentBinding.internetPermission.isVisible)
            assertFalse(button.isEnabled)
            controller.pause().stop()
            grantPermissions(Manifest.permission.INTERNET)
            controller.start().resume()
            advanceRobolectricLooper()
            assertTrue(button.isEnabled)
            button.performClick()
            assertTrue(activity.isFinishing)
            assertEquals(privatePath, targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
        }
    }
}
