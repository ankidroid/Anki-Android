// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileDeletionReadOnlyStorageTest {
    @Test
    fun readOnlyCollectionRetainsItsProfileForRetry() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "readonly_test_${UUID.randomUUID()}_"
        val preferenceNames = mutableSetOf<String>()
        val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context? = null

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences {
                    val scopedName = namespace + name
                    preferenceNames.add(scopedName)
                    return base.getSharedPreferences(scopedName, mode)
                }
            }
        val directory = File(base.cacheDir, "read_only_collection_${UUID.randomUUID()}")
        try {
            assertTrue(directory.mkdirs())
            val collection = File(directory, "collection.anki2").apply { writeText("collection") }
            val manager = ProfileManager.create(context)
            val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
            context
                .getSharedPreferences(
                    "profile_${work.value}_${base.packageName}_preferences",
                    Context.MODE_PRIVATE,
                ).edit(commit = true) {
                    putString(PREF_COLLECTION_PATH, directory.absolutePath)
                }
            assumeTrue(directory.setWritable(false, false))
            assumeFalse("The app must lack permission to remove the collection", directory.canWrite())
            var deletionFailed = false

            try {
                manager.deleteProfile(work)
            } catch (_: IOException) {
                deletionFailed = true
            }

            assertTrue("A readable collection that cannot be removed must report failure", deletionFailed)
            assertTrue("The profile must remain registered for retry", work in manager.getAllProfiles())
            assertTrue("The undeletable collection must remain available", collection.exists())
        } finally {
            directory.setWritable(true, true)
            directory.deleteRecursively()
            preferenceNames.forEach { base.deleteSharedPreferences(it) }
        }
    }
}
