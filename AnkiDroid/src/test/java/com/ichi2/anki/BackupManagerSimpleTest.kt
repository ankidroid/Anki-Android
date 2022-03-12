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
import com.ichi2.testutils.assertFalse
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.junit.JUnitAsserter.assertEquals
import kotlin.test.junit.JUnitAsserter.assertNotNull
import kotlin.test.junit.JUnitAsserter.assertNull
import kotlin.test.junit.JUnitAsserter.assertTrue

/**
 * Test for [BackupManager] without [RobolectricTest]. For performance
 */
class BackupManagerSimpleTest {
    @get:Rule
    var tempFolder = TemporaryFolder()

    @Test
    fun getBackupTimeStringTest() {
        val ts = BackupManager.getBackupTimeString("collection-1999-12-31-23-59.colpkg")
        assertEquals("1999-12-31-23-59", ts)
    }

    @Test
    fun parseBackupTimeStringTest() {
        assertNotNull(BackupManager.parseBackupTimeString("1970-01-02-00-46"))
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
        )
        val backups2 = arrayOf(
            File("collection-2000-12-31-23-04.colpkg"),
            File("foo.colpkg")
        )
        val backups3 = arrayOf(
            File("foo.colpkg"),
            File("bar.colpkg")
        )
        val expected = BackupManager.parseBackupTimeString("2010-01-02-03-04")
        val expected2 = BackupManager.parseBackupTimeString("2000-12-31-23-04")

        assertNull(bm.getLastBackupDate(arrayOf()))
        assertNotNull(bm.getLastBackupDate(backups))
        assertEquals(expected, bm.getLastBackupDate(backups))
        assertEquals("getLastBackupDate() should return the last valid date", expected2, bm.getLastBackupDate(backups2))
        assertNull("getLastBackupDate() should return null when all files aren't parseable", bm.getLastBackupDate(backups3))
    }

    @Test
    fun getBackupsTest() {
        // getBackups() doesn't require a proper collection file
        // because it is only used to get its parent
        val colFile = tempFolder.newFile()
        assertEquals(0, BackupManager.getBackups(colFile).size)
        val backupDir = BackupManager.getBackupDirectory(tempFolder.root)
        val f1 = File(backupDir, "collection-2000-12-31-23-04.colpkg")
        val f2 = File(backupDir, "foo")
        val f3 = File(backupDir, "collection-2010-12-06-13-04.colpkg")
        f1.createNewFile()
        f2.createNewFile()
        f3.createNewFile()
        val backups = BackupManager.getBackups(colFile)

        assertNotNull(backups)
        assertEquals("Only the valid backup names should have been kept", 2, backups.size)
        Arrays.sort(backups)
        assertEquals("collection-2000-12-31-23-04.colpkg", backups[0].name)
        assertEquals("collection-2010-12-06-13-04.colpkg", backups[1].name)
    }

    @Test
    fun deleteDeckBackupsTest() {
        val colFile = tempFolder.newFile()
        val backupDir = BackupManager.getBackupDirectory(tempFolder.root)

        val f1 = File(backupDir, "collection-2000-12-31-23-04.colpkg")
        val f2 = File(backupDir, "collection-1990-08-31-45-04.colpkg")
        val f3 = File(backupDir, "collection-2010-12-06-13-04.colpkg")
        val f4 = File(backupDir, "collection-1980-01-12-11-04.colpkg")
        f1.createNewFile()
        f2.createNewFile()
        f3.createNewFile()
        f4.createNewFile()

        BackupManager.deleteDeckBackups(colFile.path, 2)
        assertFalse("Older backups should have been deleted", f2.exists())
        assertFalse("Older backups should have been deleted", f4.exists())
        assertTrue("Newer backups should have been kept", f1.exists())
        assertTrue("Newer backups should have been kept", f3.exists())
    }

    @Test
    fun latest_backup_returns_null_on_no_backups() {
        val colFile = tempFolder.newFile()
        assertThat(getLatestBackup(colFile), nullValue())
    }

    @Test
    fun latest_backup_returns_null_on_invalid() {
        val colFile = tempFolder.newFile()
        val backupDir = BackupManager.getBackupDirectory(tempFolder.root)
        File(backupDir, "blah.colpkg").createNewFile()
        assertThat(getLatestBackup(colFile), nullValue())
    }

    @Test
    fun latest_backup_returns_latest() {
        val colFile = tempFolder.newFile()
        val backupDir = BackupManager.getBackupDirectory(tempFolder.root)
        File(backupDir, "collection-1990-08-31-45-04.colpkg").createNewFile()
        File(backupDir, "collection-2010-12-06-13-04.colpkg").createNewFile()
        File(backupDir, "blah.colpkg").createNewFile()
        val latestBackup = getLatestBackup(colFile)
        assertNotNull(latestBackup)
        assertThat(latestBackup.name, equalTo("collection-2010-12-06-13-04.colpkg"))
    }
}
