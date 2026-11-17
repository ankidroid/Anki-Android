// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.anki.servicelayer.ScopedStorageService
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class UnmountedLegacyStorageSwitchTest : RobolectricTest() {
    @Test
    fun `switching rejects inaccessible legacy storage while the primary volume is unmounted`() =
        runTest {
            val app = AnkiDroidApp.instance
            val base = app.baseContext
            val manager = requireNotNull(app.profileManager)
            val work = manager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            val workContext = ProfileContextWrapper.create(base, work, File(base.filesDir.parentFile, work.value))
            assertEquals(ProfileId.DEFAULT, manager.activeProfileContext.profileId)

            ShadowEnvironment.setIsExternalStorageLegacy(false)
            @Suppress("DEPRECATION")
            val publicCollection = File(Environment.getExternalStorageDirectory(), "work-collection")
            assertTrue(publicCollection.mkdir())
            // Robolectric keeps the host directory writable when the simulated volume is removed.
            assumeTrue("The filesystem must support making directories unwritable", publicCollection.setWritable(false))
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)
            try {
                workContext.sharedPrefs().edit(commit = true) { putString(PREF_COLLECTION_PATH, publicCollection.absolutePath) }
                assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(workContext))
                assertTrue(ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(workContext))
                assertFailsWith<StorageAccessException> { CollectionHelper.initializeAnkiDroidDirectory(publicCollection) }

                assertEquals(ProfileSwitchGuard.Result.Failed, ProfileSwitchGuard(manager, emptyList())(work))
                val registry = base.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                assertEquals(
                    ProfileId.DEFAULT.value,
                    registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, ProfileId.DEFAULT.value),
                )
            } finally {
                publicCollection.setWritable(true)
                ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
            }
        }
}
