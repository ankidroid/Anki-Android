// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.Manifest
import android.graphics.Rect
import android.view.View
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.databinding.FragmentInternetPermissionBinding
import com.ichi2.anki.databinding.FragmentPermissionsStartingAt30Binding
import com.ichi2.anki.databinding.FragmentPermissionsUntil29Binding
import com.ichi2.anki.simulateEdgeToEdge
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Screenshots of optional storage access, required permissions, and system-bar insets. */
class PermissionsActivityScreenshotTest : ScreenshotTest() {
    @Test
    fun permissions(
        @TestParameter isEdgeToEdge: Boolean,
    ) {
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, publicCollectionPath) }
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                PermissionsActivity::class.java,
                PermissionsActivity.getIntent(targetContext, StoragePermissionSet.LEGACY_ACCESS),
            )
        assertFalse(activity.isFinishing, "The permissions screen must remain open for capture")
        assertIs<PermissionsUntil29Fragment>(activity.supportFragmentManager.findFragmentById(R.id.fragment_container))

        if (isEdgeToEdge) {
            activity.simulateEdgeToEdge()
        }

        captureScreen("permissions" + if (isEdgeToEdge) "_edgeToEdge" else "")
    }

    @Test
    fun `storage permission and app folder explanation`(
        @TestParameter("EXTERNAL_MANAGER") permissionSet: StoragePermissionSet,
    ) {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withPermissionsScreen(permissionSet) { activity ->
            assertFalse(permissionSet.hasRequiredPermissions(activity))
            assertTrue(activity.binding.privateStorageExplanation.isVisible)
            assertTrue(activity.binding.continueButton.isEnabled)
            assertEquals(getResourceString(CommonString.use_app_folder), activity.binding.continueButton.text)
            val name = permissionSet.name.lowercase()
            captureScreen(name)
        }
    }

    @Test
    fun `storage choices remain reachable with large text in a short window`(
        @TestParameter("EXTERNAL_MANAGER") permissionSet: StoragePermissionSet,
        @TestParameter("320", "600") heightDp: Int,
    ) {
        RuntimeEnvironment.setQualifiers("+w400dp-h${heightDp}dp")
        RuntimeEnvironment.setFontScale(2f)
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withPermissionsScreen(permissionSet) { activity ->
            val name = "app_folder_large_text_$heightDp"
            val fragment = activity.binding.fragmentContainer.getFragment<PermissionsStartingAt30Fragment>()
            val permissionSwitch =
                FragmentPermissionsStartingAt30Binding
                    .bind(
                        fragment.requireView(),
                    ).allFilesPermission.binding.switchWidget
            assertCanScrollTo(permissionSwitch)
            captureScreen("${name}_permission")

            // The warning can be taller than the viewport; both its first and last lines must be reachable.
            val explanation = activity.binding.privateStorageExplanation
            assertCanScrollTo(explanation, Rect(0, 0, explanation.width, explanation.paddingTop + explanation.lineHeight))
            captureScreen("${name}_warning_start")
            assertCanScrollTo(
                explanation,
                Rect(0, explanation.height - explanation.paddingBottom - explanation.lineHeight, explanation.width, explanation.height),
            )
            captureScreen("${name}_warning_end")

            val button = activity.binding.continueButton
            assertTrue(button.isEnabled)
            val visibleBounds = Rect()
            assertTrue(button.getLocalVisibleRect(visibleBounds))
            assertEquals(Rect(0, 0, button.width, button.height), visibleBounds)
        }
    }

    private fun assertCanScrollTo(
        view: View,
        rectangle: Rect = Rect(0, 0, view.width, view.height),
    ) {
        view.requestRectangleOnScreen(Rect(rectangle), true)
        advanceRobolectricLooper()
        val visibleBounds = Rect()
        assertTrue(view.getLocalVisibleRect(visibleBounds), "The requested view must be reachable by scrolling")
        assertTrue(visibleBounds.contains(rectangle), "Visible area $visibleBounds must contain the requested area $rectangle")
    }

    @Test
    fun `revoked storage access`(
        @TestParameter("LEGACY_ACCESS", "EXTERNAL_MANAGER") permissionSet: StoragePermissionSet,
    ) {
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, publicCollectionPath) }
        withPermissionsScreen(permissionSet) { activity ->
            captureRequiredPermissions(activity, "${permissionSet.name.lowercase()}_revoked")
        }
    }

    @Test
    fun `Internet permission required`(
        @TestParameter("APP_PRIVATE", "EXTERNAL_MANAGER", "LEGACY_ACCESS") permissionSet: StoragePermissionSet,
    ) {
        targetContext.sharedPrefs().edit {
            if (permissionSet == StoragePermissionSet.APP_PRIVATE) {
                putString(CollectionHelper.PREF_COLLECTION_PATH, File(targetContext.getExternalFilesDir(null), "AnkiDroid").path)
            } else if (permissionSet == StoragePermissionSet.LEGACY_ACCESS) {
                putString(CollectionHelper.PREF_COLLECTION_PATH, publicCollectionPath)
            } else {
                remove(CollectionHelper.PREF_COLLECTION_PATH)
            }
        }
        denyPermissions(Manifest.permission.INTERNET)
        withPermissionsScreen(permissionSet) { activity ->
            captureRequiredPermissions(activity, "${permissionSet.name.lowercase()}_internet_required")
            val fragmentView =
                activity.binding.fragmentContainer
                    .getFragment<PermissionsFragment>()
                    .requireView()
            val permissionItem =
                when (permissionSet) {
                    StoragePermissionSet.APP_PRIVATE -> FragmentInternetPermissionBinding.bind(fragmentView).internetPermission
                    StoragePermissionSet.EXTERNAL_MANAGER -> FragmentPermissionsStartingAt30Binding.bind(fragmentView).internetPermission
                    StoragePermissionSet.LEGACY_ACCESS -> FragmentPermissionsUntil29Binding.bind(fragmentView).internetPermission
                }
            assertNotNull(permissionItem.binding.icon.drawable, "The Internet permission must have an icon")
        }
    }

    private fun captureRequiredPermissions(
        activity: PermissionsActivity,
        name: String,
    ) {
        val continueButton = activity.binding.continueButton
        assertFalse(continueButton.isEnabled)
        assertEquals(getResourceString(CommonString.dialog_continue), continueButton.text)
        captureScreen(name)
    }

    private fun withPermissionsScreen(
        permissionSet: StoragePermissionSet,
        action: (PermissionsActivity) -> Unit,
    ) {
        withManageExternalStorageInManifest {
            val intent = PermissionsActivity.getIntent(targetContext, permissionSet)
            ActivityScenario.launch<PermissionsActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    activity.simulateSystemBars()
                    advanceRobolectricLooper()
                    action(activity)
                }
            }
        }
    }
}
