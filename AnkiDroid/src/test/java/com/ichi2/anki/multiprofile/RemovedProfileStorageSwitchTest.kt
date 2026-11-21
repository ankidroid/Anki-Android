// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.servicelayer.ScopedStorageService
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class RemovedProfileStorageSwitchTest : RobolectricTest() {
    @Test
    fun `switching rejects a named profile after its secondary card is unmounted`() =
        runTest {
            val app = AnkiDroidApp.instance
            val card = tempFolder.newFolder("secondary-card")
            val cardFiles = File(card, "Android/data/${app.packageName}/files")
            var cardIsMounted = true
            val base =
                object : ContextWrapper(app.baseContext) {
                    override fun getApplicationContext(): Context? = null

                    override fun getExternalFilesDirs(type: String?): Array<File> {
                        val primaryFiles = super.getExternalFilesDirs(type)
                        return if (cardIsMounted) primaryFiles + cardFiles else primaryFiles
                    }
                }
            val manager = ProfileManager.create(base)
            val work = manager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            val workContext = ProfileContextWrapper.create(base, work, File(base.filesDir.parentFile, work.value))
            val workDirectory = File(cardFiles, work.value).apply { mkdirs() }
            workContext.sharedPrefs().edit(commit = true) { putString(PREF_COLLECTION_PATH, workDirectory.absolutePath) }
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
            ShadowEnvironment.setExternalStorageState(card, Environment.MEDIA_MOUNTED)
            ShadowEnvironment.setIsExternalStorageLegacy(false)

            val switchGuard = ProfileSwitchGuard(manager, emptyList())
            assertFalse(ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(workContext))
            assertEquals(ProfileSwitchGuard.Result.Success, switchGuard(work))
            manager.restoreRunningProfile()

            cardIsMounted = false
            ShadowEnvironment.setExternalStorageState(card, Environment.MEDIA_UNMOUNTED)
            // The shadow changes state without blocking host writes to the filesystem.
            assumeTrue("The filesystem must support making directories unwritable", workDirectory.setWritable(false))
            try {
                assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState())
                assertEquals(Environment.MEDIA_UNMOUNTED, Environment.getExternalStorageState(workDirectory))
                assertFailsWith<StorageAccessException> { CollectionHelper.initializeAnkiDroidDirectory(workDirectory) }

                assertEquals(ProfileSwitchGuard.Result.Failed, switchGuard(work))
                val registry = base.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                assertEquals(ProfileId.DEFAULT.value, registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null))
            } finally {
                workDirectory.setWritable(true)
            }
        }
}
