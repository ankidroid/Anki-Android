// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.graphics.Color
import android.graphics.Rect
import androidx.core.content.edit
import androidx.core.view.drawToBitmap
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.databinding.FragmentPermissionsStartingAt30Binding
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.simulateSystemBars
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression coverage for configured permission icons at normal and enlarged text sizes. */
class PermissionsItemScreenshotTest : ScreenshotTest() {
    @Test
    fun `configured permission icon is visible and aligned with switch`(
        @TestParameter("1.0", "2.0") fontScale: Float,
        @TestParameter("EXTERNAL_MANAGER") permissionSet: StoragePermissionSet,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        targetContext.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, publicCollectionPath) }
        val intent = PermissionsActivity.getIntent(targetContext, permissionSet)
        Robolectric.buildActivity(PermissionsActivity::class.java, intent).use { controller ->
            val activity = controller.setup().get()
            activity.simulateSystemBars()
            advanceRobolectricLooper()
            captureScreen("permission_icon_font_$fontScale")

            val fragment = activity.binding.fragmentContainer.getFragment<PermissionsStartingAt30Fragment>()
            val itemBinding = FragmentPermissionsStartingAt30Binding.bind(fragment.requireView()).allFilesPermission.binding
            val icon = itemBinding.icon
            val visibleBounds = Rect()
            assertTrue(
                icon.getGlobalVisibleRect(visibleBounds),
                "The configured icon must be visible; size=${icon.width}x${icon.height}, bounds=${icon.left},${icon.top},${icon.right},${icon.bottom}",
            )
            assertEquals(icon.width, visibleBounds.width())
            assertEquals(icon.height, visibleBounds.height())
            val permissionSwitch = itemBinding.switchWidget
            assertEquals(
                permissionSwitch.top + permissionSwitch.height / 2.0,
                icon.top + icon.height / 2.0,
                1.0,
                "The permission icon and switch must have the same vertical centre",
            )
            val bitmap = icon.drawToBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { Color.alpha(it) > 0 }, "The configured icon must draw visible pixels")
        }
    }
}
