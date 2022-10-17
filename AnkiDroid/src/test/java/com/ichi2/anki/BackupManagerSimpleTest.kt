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

package com.ichi2.anki

import com.ichi2.anki.BackupManager.Companion.getLatestBackup
import com.ichi2.testutils.MockTime
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.ArrayMatching.arrayContainingInAnyOrder
import org.hamcrest.io.FileMatchers.anExistingFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.junit5.JUnit5Asserter.assertEquals
import kotlin.test.junit5.JUnit5Asserter.assertNotNull
import kotlin.test.junit5.JUnit5Asserter.assertNull

/**
 * Test for [BackupManager] without [RobolectricTest]. For performance
 */
class BackupManagerSimpleTest {
    @get:Rule
    var tempDirectory = TemporaryFolder()

    @Test
    fun getBackupTimeStringTest() {
        val ts = BackupManager.getBackupTimeString("collection-1999-12-31-23-59.colpkg")
        assertEquals("1999-12-31-23-59", ts)
        val ts2 = BackupManager.getBackupTimeString("backup-1999-12-31-23.59.05.colpkg")
        assertEquals("1999-12-31-23.59", ts2)
    }

    @Test
    fun parseBackupTimeStringTest() {
        assertNotNull(BackupManager.parseBackupTimeString("1970-01-02-00-46"))
        assertNotNull(BackupManager.parseBackupTimeString("1970-01-02-00.46.03"))
        assertNull(BackupManager.parseBackupTimeString("123456"))
    }

    @Test
    fun getBackupDateTest() {
        assertNotNull(BackupManager.getBackupDate("collection-1970-01-02-00-46.colpkg"))
        assertNull(BackupManager.getBackupDate("foo"))
    }

    @Test
    fun getNameForNewBackupTest() {
        // Using a timestamp number directly as MockTime parameter may
        // have different results on other computers and GitHub CI
        val date = BackupManager.parseBackupTimeString("1970-01-02-00-46")
        assertNotNull(date)
        val timestamp = date.time
        val backupName = BackupManager.getNameForNewBackup(MockTime(timestamp))

        assertEquals("Backup name doesn't match naming pattern", "collection-1970-01-02-00-46.colpkg", backupName)
    }

    @Test
    fun nameOfNewBackupsCanBeParsed() {
        val backupName = BackupManager.getNameForNewBackup(MockTime(100000000))
        assertNotNull(backupName)

        val ts = BackupManager.getBackupDate(backupName)
        assertNotNull("New backup name couldn't be parsed by getBackupTimeStrings()", ts)
    }

    @Test
    fun getLastBackupDateTest() {
        val bm = BackupManager.createInstance()
        val backups = arrayOf(
            File("collection-2000-12-31-23-04.colpkg"),
            File("collection-2010-01-02-03-04.colpkg"),
            File("collection-1999-12-31-23-59.colpkg")
        ).sortedBy { it.name }.toTypedArray()
        val expected = BackupManager.parseBackupTimeString("2010-01-02-03-04")

        assertNull(bm.getLastBackupDate(arrayOf()))
        assertNotNull(bm.getLastBackupDate(backups))
        assertEquals(expected, bm.getLastBackupDate(backups))
        assertNull("getLastBackupDate() should return null when all files aren't parsable", bm.getLastBackupDate(arrayOf()))
    }

    @Test
    fun getBackupsTest() {
        // getBackups() doesn't require a proper collection file
        // because it is only used to get its parent
        val colFile = tempDirectory.newFile()
        assertEquals(0, BackupManager.getBackups(colFile).size)
        val backupDir = BackupManager.getBackupDirectory(tempDirectory.root)
        val f1 = File(backupDir, "collection-2000-12-31-23-04.colpkg")
        val f2 = File(backupDir, "foo")
        val f3 = File(backupDir, "collection-2010-12-06-13-04.colpkg")
        val f4 = File(backupDir, "backup-2010-12-06-13.04.05.colpkg")
        f1.createNewFile()
        f2.createNewFile()
        f3.createNewFile()
        f4.createNewFile()
        val backups = BackupManager.getBackups(colFile)

        assertNotNull(backups)
        assertEquals("Only the valid backup names should have been kept", 3, backups.size)
        assertThat(backups, arrayContainingInAnyOrder(f1, f3, f4))
    }

    @Test
    fun deleteDeckBackupsTest() {
        val colFile = tempDirectory.newFile()
        val backupDir = BackupManager.getBackupDirectory(tempDirectory.root)

        val f1 = File(backupDir, "collection-2000-12-31-23-04.colpkg")
        val f2 = File(backupDir, "collection-1990-08-31-45-04.colpkg")
        val f3 = File(backupDir, "collection-2010-12-06-13-04.colpkg")
        val f4 = File(backupDir, "collection-1980-01-12-11-04.colpkg")
        f1.createNewFile()
        f2.createNewFile()
        f3.createNewFile()
        f4.createNewFile()

        BackupManager.deleteColBackups(colFile.path, 2)
        assertThat("Older backups should have been deleted", f2, not(anExistingFile()))
        assertThat("Older backups should have been deleted", f4, not(anExistingFile()))
        assertThat("Newer backups should have been kept", f1, anExistingFile())
        assertThat("Newer backups should have been kept", f3, anExistingFile())
    }

    @Test
    fun latest_backup_returns_null_on_no_backups() {
        val colFile = tempDirectory.newFile()
        assertThat(getLatestBackup(colFile), nullValue())
    }

    @Test
    fun latest_backup_returns_null_on_invalid() {
        val colFile = tempDirectory.newFile()
        val backupDir = BackupManager.getBackupDirectory(tempDirectory.root)
        File(backupDir, "blah.colpkg").createNewFile()
        assertThat(getLatestBackup(colFile), nullValue())
    }

    @Test
    fun latest_backup_returns_latest() {
        val colFile = tempDirectory.newFile()
        val backupDir = BackupManager.getBackupDirectory(tempDirectory.root)
        File(backupDir, "collection-1990-08-31-45-04.colpkg").createNewFile()
        File(backupDir, "collection-2010-12-06-13-04.colpkg").createNewFile()
        File(backupDir, "blah.colpkg").createNewFile()
        val latestBackup = getLatestBackup(colFile)
        assertNotNull(latestBackup)
        assertThat(latestBackup.name, equalTo("collection-2010-12-06-13-04.colpkg"))
    }
}
