// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.exception.CollectionLockedException
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendImportException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CollectionManagerImportTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setCollectionPath() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
    }

    @Test
    fun `invalid backup leaves the original collection open`() =
        runTest {
            val backup = tempFolder.newFile("invalid.colpkg").apply { writeText("invalid backup") }

            assertFailedImportKeepsCollectionOpen<BackendSyncException>(backup)
        }

    @Test
    fun `missing backup leaves the original collection open`() =
        runTest {
            assertFailedImportKeepsCollectionOpen<BackendIoException>(File(tempFolder.root, "missing.colpkg"))
        }

    @Test
    fun `corrupt database in a valid archive leaves the original collection open`() =
        runTest {
            val failure = assertFailedImportKeepsCollectionOpen<BackendImportException>(corruptDatabaseBackup())

            assertEquals(CollectionManager.TR.importingTheProvidedFileIsNotA(), failure.localizedMessage)
        }

    @Test
    fun `failure to reopen does not replace the import error`() =
        runTest {
            addBasicNote()
            val backup = corruptDatabaseBackup()
            val expectedMessage = CollectionManager.TR.importingTheProvidedFileIsNotA()
            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            try {
                val failure = assertFailsWith<BackendImportException> { CollectionManager.importColpkg(backup.path) }

                assertEquals(expectedMessage, failure.localizedMessage)
                assertIs<CollectionLockedException>(failure.suppressed.single())
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
        }

    @Test
    fun `valid backup replaces the collection`() =
        runTest {
            val note = addBasicNote("backed up", "answer")
            val backupDirectory = tempFolder.newFolder("backups")
            assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
            val files = assertNotNull(backupDirectory.listFiles(), "Unable to list backup directory: $backupDirectory")
            val backups = files.filter { it.extension == "colpkg" }
            assertEquals(1, backups.size, "Expected one collection backup in $backupDirectory; found ${files.map { it.name }}")
            val backup = backups.single()
            addBasicNote("added after backup", "answer")

            CollectionManager.importColpkg(backup.path)

            CollectionManager.withCol {
                assertEquals(1, noteCount())
                assertEquals("backed up", getNote(note.id).fields[0])
            }
        }

    private fun corruptDatabaseBackup(): File =
        tempFolder.newFile("corrupt.colpkg").apply {
            ZipOutputStream(outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("collection.anki2"))
                archive.write("not a SQLite database".toByteArray())
                archive.closeEntry()
            }
        }

    private suspend inline fun <reified T : BackendException> assertFailedImportKeepsCollectionOpen(backup: File): T {
        val note = addBasicNote("original", "answer")

        val failure = assertFailsWith<T> { CollectionManager.importColpkg(backup.path) }

        // withCol would reopen the collection and hide the regression.
        val fields = CollectionManager.withOpenColOrNull { getNote(note.id).fields.toList() }
        assertNotNull(fields, "The original collection should already be open after the import fails")
        assertEquals(listOf("original", "answer"), fields)
        return failure
    }
}
