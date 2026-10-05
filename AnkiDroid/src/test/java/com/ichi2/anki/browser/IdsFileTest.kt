// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.ContextWrapper
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.targetContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class IdsFileTest : AndroidTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `getDirectory returns the profile cacheDir`() {
        val directory = IdsFile.getDirectory()
        assertTrue(directory.isDirectory)
        assertEquals(targetContext.cacheDir, directory)
    }

    @Test
    fun `purposes preserve existing snapshot prefixes`() {
        val expectedPrefixes =
            mapOf(
                IdsFile.Purpose.SELECTION to "multiselect-values",
                IdsFile.Purpose.FIND_AND_REPLACE to "find-replace",
                IdsFile.Purpose.TAGS to "ids",
                IdsFile.Purpose.PREVIEW to "ids",
                IdsFile.Purpose.EXPORT to "export",
                IdsFile.Purpose.SET_DUE_DATE to "set-due-date",
            )
        assertEquals(IdsFile.Purpose.entries.toSet(), expectedPrefixes.keys)
        for ((purpose, prefix) in expectedPrefixes) {
            val file = IdsFile(temporaryFolder.root, listOf(42L), purpose)
            assertTrue(file.name.startsWith(prefix))
            assertTrue(file.name.endsWith(".tmp"))
            assertEquals(listOf(42L), file.getIds())
        }
    }

    @Test
    fun `parcelled snapshot can be restored repeatedly`() {
        val ids = listOf(9L, 1L, Long.MAX_VALUE)
        val file = IdsFile(temporaryFolder.root, ids, IdsFile.Purpose.PREVIEW)
        val parcel = Parcel.obtain()
        try {
            file.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = IdsFile.CREATOR.createFromParcel(parcel)
            assertEquals(ids, restored.getIds())
            assertEquals(ids, restored.getIds())
            assertTrue(file.exists())
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `empty selection is distinct from a missing file`() {
        val file = IdsFile(temporaryFolder.root, emptyList(), IdsFile.Purpose.PREVIEW)
        assertEquals(emptyList(), file.getIds())
        assertTrue(file.delete())
        assertFailsWith<IOException> { file.getIds() }
    }

    @Test
    fun `incomplete header cannot be read`() {
        val file = IdsFile(temporaryFolder.root, emptyList(), IdsFile.Purpose.PREVIEW)
        file.writeBytes(byteArrayOf(0, 0))
        assertFailsWith<IOException> { file.getIds() }
    }

    @Test
    fun `incomplete selections are rejected before allocating the declared count`() {
        val file = IdsFile(temporaryFolder.root, emptyList(), IdsFile.Purpose.PREVIEW)
        for (count in listOf(2, Int.MAX_VALUE)) {
            DataOutputStream(file.outputStream()).use {
                it.writeInt(count)
                it.writeLong(1L)
            }
            assertFailsWith<IOException>("count: $count") { file.getIds() }
        }
    }

    @Test
    fun `negative counts and trailing data cannot be read`() {
        val file = IdsFile(temporaryFolder.root, emptyList(), IdsFile.Purpose.PREVIEW)
        for (count in listOf(-1, 0)) {
            DataOutputStream(file.outputStream()).use {
                it.writeInt(count)
                it.writeLong(1L)
            }
            assertFailsWith<IOException>("count: $count") { file.getIds() }
        }
    }

    private lateinit var storageContext: ProfileContextWrapper

    @Before
    fun setUpStorage() {
        val root = temporaryFolder.newFolder("storage")
        val base =
            object : ContextWrapper(targetContext) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }

                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }

                override fun getExternalCacheDir() = File(root, "external-cache").apply { mkdirs() }
            }
        storageContext = ProfileContextWrapper.create(base, ProfileId.DEFAULT, root)
    }

    @Test
    fun `snapshots are isolated by profile`() {
        val otherProfile =
            ProfileContextWrapper.create(
                storageContext.baseContext,
                ProfileId("p_other"),
                File(storageContext.filesDir.parentFile, "p_other"),
            )
        val file = IdsFile(IdsFile.getDirectory(storageContext), listOf(9L), IdsFile.Purpose.PREVIEW)
        val otherFile = IdsFile(IdsFile.getDirectory(otherProfile), listOf(10L), IdsFile.Purpose.PREVIEW)
        assertEquals(storageContext.cacheDir, file.parentFile)
        assertEquals(otherProfile.cacheDir, otherFile.parentFile)
        assertTrue(file.parentFile != otherFile.parentFile)
    }
}
