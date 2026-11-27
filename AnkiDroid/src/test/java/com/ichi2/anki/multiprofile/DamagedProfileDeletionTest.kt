// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DamagedProfileDeletionTest {
    @After
    fun tearDown() {
        ProfileManager.resetForTesting()
    }

    @Test
    fun `a profile with an unusable files folder can be deleted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        val manager = ProfileManager.create(context)
        val profile = manager.createNewProfile(ProfileName.fromTrustedSource("Damaged"))
        val profileDir = File(context.filesDir.parentFile, profile.value).apply { mkdirs() }
        File(profileDir, "files").writeText("not a directory")

        manager.deleteProfile(profile)

        assertFalse("The damaged directory should be removed", profileDir.exists())
        assertFalse("The deleted profile should leave the registry", manager.getAllProfiles().containsKey(profile))
    }
}
