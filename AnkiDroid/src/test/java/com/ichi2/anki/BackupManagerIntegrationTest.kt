/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.async.CollectionTask.ImportReplace
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.AnkiAssert
import com.ichi2.testutils.BackupManagerTestUtilities
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupManagerIntegrationTest : RobolectricTest() {
    @Test
    @Ignore("Fails on line: if (!f.renameTo(new File(colPath))) {")
    @Throws(InterruptedException::class)
    fun restoreBackupLeavesCollectionWritable() {
        @Suppress("UNUSED_VARIABLE")
        val unusedCol = col
        val path = arrayListOf(createBackup())

        // Perform a write
        addNoteUsingBasicModel("Hello", "World")

        waitForTask(ImportReplace(path), 1000)

        assertThat("database should be read-write", this.col.db.database.isReadOnly, equalTo(false))
        AnkiAssert.assertDoesNotThrow { addNoteUsingBasicModel("Hello", "World") }
    }

    private fun createBackup(): String {
        return try {
            BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
            assertThat(
                "Backup should work",
                BackupManager.performBackupInBackground(
                    col.path,
                    TimeManager.time
                ),
                equalTo(true)
            )
            spinUntilBackupExists(1000)
        } finally {
            BackupManagerTestUtilities.reset()
        }
    }

    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
    private fun spinUntilBackupExists(timeoutMs: Int): String {
        val time = System.currentTimeMillis()
        while (true) {
            val colFile = File(col.path)
            val backups = BackupManager.getBackups(colFile)
            if (backups.isNotEmpty()) {
                return backups[0].absolutePath
            }
            if (System.currentTimeMillis() - time > timeoutMs) {
                throw RuntimeException("span for longer than $timeoutMs")
            }
        }
    }
}
