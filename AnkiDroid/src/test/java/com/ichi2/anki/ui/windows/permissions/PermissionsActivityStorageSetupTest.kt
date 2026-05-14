// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.testutils.withManageExternalStorageInManifest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R])
class PermissionsActivityStorageSetupTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun clearStorage() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
    }

    @Test
    fun `app folder opens a private collection and retains its path after restart`() {
        withPermissionsScreen { activity ->
            useAppFolder(activity)
            advanceRobolectricLooperUntil { activity.isFinishing }
        }
        val selectedPath = assertNotNull(collectionPath)
        assertTrue(File(selectedPath).isDirectory)
        assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(targetContext))
        assertTrue(selectStoragePermissions(targetContext).hasRequiredPermissions(targetContext))
        assertEquals(0, col.noteCount())
        assertEquals(File(selectedPath).canonicalFile, assertNotNull(col.colDb.parentFile).canonicalFile)
        CollectionManager.closeCollectionBlocking()
        InitialActivity.decideStorageIfUndecided(targetContext)
        assertEquals(selectedPath, collectionPath)
        assertNull(InitialActivity.getStartupFailureType(targetContext))
        assertEquals(0, col.noteCount())
    }

    @Test
    fun `failed directory setup leaves storage undecided and allows retry`() {
        withPermissionsScreen { activity ->
            mockkObject(CollectionHelper)
            try {
                every { CollectionHelper.initializeAnkiDroidDirectory(any()) } throws StorageAccessException("No space")
                useAppFolder(activity)
                advanceRobolectricLooperUntil { ShadowToast.getTextOfLatestToast() != null }
                assertEquals(getResourceString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
                assertNull(collectionPath)
                assertFalse(activity.isFinishing)
                assertTrue(activity.binding.continueButton.isEnabled)
            } finally {
                unmockkObject(CollectionHelper)
            }
            useAppFolder(activity)
            advanceRobolectricLooperUntil { activity.isFinishing }
            assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(activity))
        }
    }

    private fun useAppFolder(activity: PermissionsActivity) {
        activity.binding.continueButton.performClick()
        advanceRobolectricLooper()
    }

    private fun withPermissionsScreen(action: (PermissionsActivity) -> Unit) {
        withManageExternalStorageInManifest {
            val intent = PermissionsActivity.getIntent(targetContext, selectStoragePermissions(targetContext))
            ActivityScenario.launch<PermissionsActivity>(intent).use { scenario ->
                scenario.onActivity(action)
            }
        }
    }

    private val collectionPath: String?
        get() = targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)
}
