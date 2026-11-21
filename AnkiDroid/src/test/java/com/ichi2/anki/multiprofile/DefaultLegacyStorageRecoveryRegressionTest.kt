// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
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
class DefaultLegacyStorageRecoveryRegressionTest : RobolectricTest() {
    @Test
    fun `switching to Default preserves legacy collection recovery after reinstall`() =
        runTest {
            val app = AnkiDroidApp.instance
            val running = requireNotNull(app.profileManager)
            val work = running.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            assertEquals(ProfileSwitchGuard.Result.Success, ProfileSwitchGuard(running, emptyList())(work))
            val base =
                object : ContextWrapper(app.baseContext) {
                    override fun getApplicationContext(): Context? = null
                }
            val manager = ProfileManager.create(base)
            assertEquals(work, manager.activeProfileContext.profileId)

            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
            ShadowEnvironment.setIsExternalStorageLegacy(false)
            @Suppress("DEPRECATION")
            val publicCollection = File(Environment.getExternalStorageDirectory(), "restored-legacy-collection")
            assertTrue(publicCollection.mkdir())
            // Model scoped storage denying writes to an existing public directory.
            assumeTrue("The filesystem must support making directories unwritable", publicCollection.setWritable(false))
            try {
                base.sharedPrefs().edit(commit = true) { putString(PREF_COLLECTION_PATH, publicCollection.absolutePath) }
                assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(base))
                assertTrue(ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(base))
                assertFailsWith<StorageAccessException> { CollectionHelper.initializeAnkiDroidDirectory(publicCollection) }

                assertEquals(ProfileSwitchGuard.Result.Success, ProfileSwitchGuard(manager, emptyList())(ProfileId.DEFAULT))
                val registry = base.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                assertEquals(ProfileId.DEFAULT.value, registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null))
            } finally {
                publicCollection.setWritable(true)
            }
        }
}
