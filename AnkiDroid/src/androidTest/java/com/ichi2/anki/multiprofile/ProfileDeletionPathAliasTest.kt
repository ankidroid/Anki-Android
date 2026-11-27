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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileDeletionPathAliasTest {
    @Test
    fun deletingACaseAliasPreservesTheRunningCollection() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "path_alias_test_${UUID.randomUUID()}_"
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
        val directory = File(base.getExternalFilesDir(null), "AnkiDroid_Alias_${UUID.randomUUID()}")
        try {
            assertTrue(directory.mkdirs())
            val alias = File(directory.parentFile, directory.name.lowercase())
            assumeTrue("This storage must support case-insensitive path aliases", alias.exists())
            val collection = File(directory, "collection.anki2").apply { writeText("running collection") }
            assertTrue(
                "The alternate path must expose the same fixture",
                File(alias, "collection.anki2").readText() == "running collection",
            )
            val media =
                File(directory, "collection.media/image.jpg").apply {
                    parentFile!!.mkdirs()
                    writeText("running media")
                }
            val manager = ProfileManager.create(context)
            val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
            context.getSharedPreferences("${base.packageName}_preferences", Context.MODE_PRIVATE).edit(commit = true) {
                putString(PREF_COLLECTION_PATH, directory.absolutePath)
            }
            context
                .getSharedPreferences(
                    "profile_${work.value}_${base.packageName}_preferences",
                    Context.MODE_PRIVATE,
                ).edit(commit = true) {
                    putString(PREF_COLLECTION_PATH, alias.absolutePath)
                }

            manager.deleteProfile(work)

            assertTrue("Deleting an alias must preserve the running profile's collection", collection.exists())
            assertTrue("Deleting an alias must preserve the running profile's media", media.exists())
        } finally {
            directory.deleteRecursively()
            preferenceNames.forEach { base.deleteSharedPreferences(it) }
        }
    }
}
