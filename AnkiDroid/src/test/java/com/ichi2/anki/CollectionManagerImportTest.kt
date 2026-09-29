// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.storage.CollectionHelper
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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

            assertFailedImportKeepsCollectionOpen(backup)
        }

    @Test
    fun `missing backup leaves the original collection open`() =
        runTest {
            assertFailedImportKeepsCollectionOpen(File(tempFolder.root, "missing.colpkg"))
        }

    @Test
    fun `failure to reopen does not replace the import error`() =
        runTest {
            addBasicNote()
            val backup = tempFolder.newFile("invalid.colpkg").apply { writeText("invalid backup") }
            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            try {
                val failure = assertFailsWith<BackendException> { CollectionManager.importColpkg(backup.path) }

                assertFalse(failure is BackendDbLockedException)
                assertIs<BackendDbLockedException>(failure.suppressed.single())
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
        }

    @Test
    fun `valid backup replaces the collection`() =
        runTest {
            val note = addBasicNote("backed up", "answer")
            val backupDirectory = tempFolder.newFolder("backups")
            col.createBackup(backupDirectory.path, force = true, waitForCompletion = true)
            val backup = backupDirectory.listFiles()!!.single { it.extension == "colpkg" }
            addBasicNote("added after backup", "answer")

            CollectionManager.importColpkg(backup.path)

            CollectionManager.withCol {
                assertEquals(1, noteCount())
                assertEquals("backed up", getNote(note.id).fields[0])
            }
        }

    private suspend fun assertFailedImportKeepsCollectionOpen(backup: File) {
        val note = addBasicNote("original", "answer")

        assertFailsWith<BackendException> { CollectionManager.importColpkg(backup.path) }

        // withCol would reopen the collection and hide the regression.
        val fields = CollectionManager.withOpenColOrNull { getNote(note.id).fields.toList() }
        assertNotNull(fields, "The original collection should already be open after the import fails")
        assertEquals(listOf("original", "answer"), fields)
    }
}
