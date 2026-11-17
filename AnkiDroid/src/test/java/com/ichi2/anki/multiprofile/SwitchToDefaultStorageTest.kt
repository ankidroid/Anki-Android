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
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.Result
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SwitchToDefaultStorageTest : RobolectricTest() {
    private val app get() = AnkiDroidApp.instance

    @Test
    fun `switching to Default is refused when its collection folder is unusable`() =
        runTest {
            val (manager, work) = restartIntoWorkProfile()
            setDefaultCollectionPath(unusableFolder(requireNotNull(app.getExternalFilesDir(null))))

            assertEquals(Result.Failed, ProfileSwitchGuard(manager, emptyList())(ProfileId.DEFAULT))
            assertEquals(work.value, lastActiveProfileId())
        }

    @Test
    fun `switching to Default is refused while shared storage is unmounted`() =
        runTest {
            val (manager, work) = restartIntoWorkProfile()
            ShadowEnvironment.setIsExternalStorageLegacy(false)
            @Suppress("DEPRECATION")
            setDefaultCollectionPath(unusableFolder(Environment.getExternalStorageDirectory()))
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)

            assertEquals(Result.Failed, ProfileSwitchGuard(manager, emptyList())(ProfileId.DEFAULT))
            assertEquals(work.value, lastActiveProfileId())
        }

    @Test
    fun `switching to Default goes ahead when startup has to ask for storage access first`() =
        runTest {
            val (manager, _) = restartIntoWorkProfile()
            ShadowEnvironment.setIsExternalStorageLegacy(true)
            setDefaultCollectionPath(unusableFolder(tempFolder.newFolder()))

            assertEquals(Result.Success, ProfileSwitchGuard(manager, emptyList())(ProfileId.DEFAULT))
            assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId())
        }

    @Test
    fun `switching to Default goes ahead when startup still has to choose its folder`() =
        runTest {
            val (manager, _) = restartIntoWorkProfile()
            setDefaultCollectionPath(null)

            assertEquals(Result.Success, ProfileSwitchGuard(manager, emptyList())(ProfileId.DEFAULT))
            assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId())
        }

    private suspend fun restartIntoWorkProfile(): Pair<ProfileManager, ProfileId> {
        val running = requireNotNull(app.profileManager)
        val work = running.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
        assertEquals(Result.Success, ProfileSwitchGuard(running, emptyList())(work))
        val restarted =
            ProfileManager.create(
                object : ContextWrapper(app.baseContext) {
                    override fun getApplicationContext(): Context? = null
                },
            )
        assertEquals(work, restarted.activeProfileContext.profileId)
        return restarted to work
    }

    private fun unusableFolder(parent: File) = File(parent, "AnkiDroid").apply { writeText("not a folder") }

    private fun setDefaultCollectionPath(folder: File?) =
        app.baseContext.sharedPrefs().edit(commit = true) {
            if (folder == null) remove(PREF_COLLECTION_PATH) else putString(PREF_COLLECTION_PATH, folder.absolutePath)
        }

    private fun lastActiveProfileId() =
        app.baseContext
            .getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
            .getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null)
}
