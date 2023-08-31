/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import android.annotation.SuppressLint
import com.ichi2.anki.BackupManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.utils.TimeManager
import java.io.File

@Suppress("unused")
object Backup {
    /**
     * Creates a single backup. This only works once per test.
     *
     * WARN: This does not create multiple backups: the filenames are on a per-minute basis, and calling
     * in a loop causes a conflict
     *
     * @throws IllegalStateException If collection is in-memory. See: [RobolectricTest.useInMemoryDatabase]
     * @throws IllegalStateException If backup creation failed, or timed out after 1 second
     */
    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
    fun create(col: com.ichi2.libanki.Collection) {
        BackupManagerTestUtilities.setupSpaceForBackup(col.context)
        val path = col.path
        val time = TimeManager.time
        col.close()

        val originalBackupCount = getBackupCount(path)

        if (!File(path).exists()) {
            throw IllegalStateException("collection was in-memory. Set useInMemoryDatabase to false")
        }

        val backupManager = BackupManager.createInstance()

        if (!backupManager.performBackupInBackground(path, 0, time)) {
            throw IllegalStateException("failed to create backup")
        }

        // spin until the background thread creates backup
        // TODO: This would be faster if code is redesigned.
        val startTime = System.currentTimeMillis()
        while (getBackupCount(path) == originalBackupCount) {
            if (System.currentTimeMillis() - startTime > 1000) {
                throw IllegalStateException("backup wasn't created in 1s")
            }
        }
    }

    private fun getBackupCount(file: String): Int = BackupManager.getBackups(File(file)).size
}
