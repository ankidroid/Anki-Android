// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.ContextWrapper
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.targetContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class IdsFileTest : AndroidTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `snapshot directory is created only when writing`() {
        val directory = IdsFile.getDirectory(storageContext)
        assertEquals(File(storageContext.cacheDir, "idsfiles"), directory)
        assertFalse(directory.exists())
        IdsFile(directory, listOf(42L), IdsFile.Purpose.PREVIEW)
        assertTrue(directory.isDirectory)
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

    private val now = 1_800_000_000_000L
    private lateinit var storageContext: ProfileContextWrapper

    @Before
    fun setUpStorage() {
        val time = mockk<Time>()
        every { time.intTimeMS() } returns now
        TimeManager.resetWith(time)
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

    @After
    fun resetTime() = TimeManager.reset()

    @Test
    fun `directory creation fails clearly when a file occupies the path`() {
        val path = File(storageContext.cacheDir, "idsfiles").apply { writeText("occupied") }
        val exception =
            assertFailsWith<IOException> {
                IdsFile(IdsFile.getDirectory(storageContext), listOf(1L), IdsFile.Purpose.PREVIEW)
            }
        assertTrue(exception.message.orEmpty().contains(path.path))
    }

    @Test
    fun `snapshots and cleanup are isolated by profile`() {
        val otherProfile =
            ProfileContextWrapper.create(
                storageContext.baseContext,
                ProfileId("p_other"),
                File(storageContext.filesDir.parentFile, "p_other"),
            )
        val file = IdsFile(IdsFile.getDirectory(storageContext), listOf(9L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
        val otherFile = IdsFile(IdsFile.getDirectory(otherProfile), listOf(10L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
        assertEquals(otherProfile.cacheDir, otherFile.parentFile!!.parentFile)
        IdsFile.deleteStaleFiles(storageContext)
        assertFalse(file.exists())
        assertTrue(otherFile.exists())
        IdsFile.deleteStaleFiles(otherProfile)
        assertFalse(otherFile.exists())
    }

    @Test
    fun `cleanup removes only files older than the retention boundary`() {
        val directory = IdsFile.getDirectory(storageContext)
        val expired = IdsFile(directory, listOf(1L), IdsFile.Purpose.PREVIEW).aged(7.days.inWholeMilliseconds + 1)
        val boundary = IdsFile(directory, listOf(2L), IdsFile.Purpose.PREVIEW).aged(7.days.inWholeMilliseconds)
        val recent = IdsFile(directory, listOf(3L), IdsFile.Purpose.PREVIEW).aged(1.days.inWholeMilliseconds)
        val future = IdsFile(directory, listOf(4L), IdsFile.Purpose.PREVIEW).aged(-1.days.inWholeMilliseconds)
        val unknownAge = IdsFile(directory, listOf(5L), IdsFile.Purpose.PREVIEW).apply { assertTrue(setLastModified(0)) }
        val subdirectory = File(directory, "ids123.tmp").apply { mkdir() }.aged(8.days.inWholeMilliseconds)

        IdsFile.deleteStaleFiles(storageContext)

        assertFalse(expired.exists())
        listOf(boundary, recent, future, unknownAge, subdirectory).forEach { assertTrue(it.exists(), it.name) }
    }

    @Test
    fun `cleanup leaves files outside the snapshot directory untouched`() {
        val files =
            listOf(storageContext.cacheDir, storageContext.externalCacheDir!!).map { directory ->
                IdsFile(directory, listOf(1L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
            }

        IdsFile.deleteStaleFiles(storageContext)

        files.forEach { assertTrue(it.exists(), it.path) }
    }

    @Test
    fun `cleanup continues after a deletion throws`() {
        val failedFile = mockk<File>()
        every { failedFile.isFile } returns true
        every { failedFile.lastModified() } returns now - 8.days.inWholeMilliseconds
        every { failedFile.delete() } throws SecurityException("denied")
        every { failedFile.toString() } returns "ids123.tmp"
        val expired = IdsFile(IdsFile.getDirectory(storageContext), listOf(1L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
        val directory =
            object : File(expired.parent) {
                override fun listFiles(): Array<File> = arrayOf(failedFile, expired)
            }
        IdsFile.deleteStaleFiles(directory)
        verify(exactly = 1) { failedFile.delete() }
        assertFalse(expired.exists())
    }

    @Test
    fun `parcelled snapshot remains readable until its file expires`() {
        val file = IdsFile(IdsFile.getDirectory(storageContext), listOf(7L, 11L), IdsFile.Purpose.PREVIEW).aged(7.days.inWholeMilliseconds)
        val parcel = Parcel.obtain()
        try {
            file.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = IdsFile.CREATOR.createFromParcel(parcel)
            IdsFile.deleteStaleFiles(storageContext)
            assertEquals(listOf(7L, 11L), restored.getIds())
            file.aged(8.days.inWholeMilliseconds)
            IdsFile.deleteStaleFiles(storageContext)
            assertFailsWith<IOException> { restored.getIds() }
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `cleanup continues when deleting a file returns false`() {
        val failedFile = mockk<File>()
        every { failedFile.isFile } returns true
        every { failedFile.lastModified() } returns now - 8.days.inWholeMilliseconds
        every { failedFile.delete() } returns false
        every { failedFile.toString() } returns "ids123.tmp"
        val expired = IdsFile(IdsFile.getDirectory(storageContext), listOf(1L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
        val directory =
            object : File(expired.parent) {
                override fun listFiles(): Array<File> = arrayOf(failedFile, expired)
            }
        IdsFile.deleteStaleFiles(directory)
        verify(exactly = 1) { failedFile.delete() }
        assertFalse(expired.exists())
    }

    @Test
    fun `cleanup does not create unused snapshot directories`() {
        IdsFile.deleteStaleFiles(storageContext)
        assertFalse(File(storageContext.cacheDir, "idsfiles").exists())
    }

    @Test
    fun `cleanup tolerates an unreadable snapshot directory`() {
        val directory =
            object : File(IdsFile.getDirectory(storageContext).path) {
                override fun listFiles(): Array<File> = throw SecurityException("denied")
            }
        val file = IdsFile(directory, listOf(1L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
        IdsFile.deleteStaleFiles(directory)
        assertTrue(file.exists())
    }

    private fun <T : File> T.aged(ageMillis: Long): T =
        apply {
            assertTrue(setLastModified(now - ageMillis))
        }
}
