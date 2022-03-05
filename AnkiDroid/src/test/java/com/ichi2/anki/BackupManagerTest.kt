/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.utils.Time
import com.ichi2.testutils.MockTime
import com.ichi2.utils.StrictMock.Companion.strictMock
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import java.io.File

@RunWith(AndroidJUnit4::class)
open class BackupManagerTest {
    @Test
    fun failsIfNoBackupsAllowed() {
        // arrange
        val bm = passingBackupManagerSpy
        doReturn(true).`when`(bm).hasDisabledBackups(any())

        // act
        val performBackupResult = performBackup(bm)

        // assert
        assertThat("should fail if backups are disabled", performBackupResult, equalTo(false))
        verify(bm, times(1)).performBackupInBackground(anyString(), anyInt(), any())
        verify(bm, times(1)).hasDisabledBackups(any())
        verifyNoMoreInteractions(bm)
    }

    /** Meta test: ensuring passingBackupManagerSpy passes  */
    @Test
    fun testPassingSpy() {
        val bm = passingBackupManagerSpy

        val result = performBackup(bm)

        verify(bm, times(1)).performBackupInNewThread(any(), any())
        assertThat("PerformBackup should pass", result, equalTo(true))
    }

    @Test
    fun noBackupPerformedIfNoBackupNecessary() {
        val bm = passingBackupManagerSpy

        doReturn(true).`when`(bm).isBackupUnnecessary(any(), any())

        val result = performBackup(bm)

        assertThat("should fail if backups not necessary", result, equalTo(false))

        verify(bm, times(1)).isBackupUnnecessary(any(), any())
    }

    @Test
    fun noBackupPerformedIfBackupAlreadyExists() {
        val file = strictMock(File::class.java)
        doReturn(true).`when`(file).exists()

        val bm = getPassingBackupManagerSpy(file)

        val result = performBackup(bm)

        assertThat("should fail if backups exists", result, equalTo(false))
    }

    @Test
    fun noBackupPerformedIfCollectionTooSmall() {
        val bm = passingBackupManagerSpy

        doReturn(true).`when`(bm).collectionIsTooSmallToBeValid(any())

        val result = performBackup(bm)

        assertThat("should fail if collection too small", result, equalTo(false))
    }

    private fun performBackup(bm: BackupManager, time: Time = MockTime(100000000)): Boolean {
        return bm.performBackupInBackground("", 100, time)
    }

    /** Returns a spy of BackupManager which would pass  */
    private val passingBackupManagerSpy: BackupManager
        get() = getPassingBackupManagerSpy(null)

    /** Returns a spy of BackupManager which would pass  */
    private fun getPassingBackupManagerSpy(backupFileMock: File?): BackupManager {
        val spy = spy(BackupManager.createInstance())
        doReturn(true).`when`(spy).hasFreeDiscSpace(any())
        doReturn(false).`when`(spy).collectionIsTooSmallToBeValid(any())
        doNothing().`when`(spy).performBackupInNewThread(any(), any())
        doReturn(null).`when`(spy).getLastBackupDate(any())

        val f = backupFileMock ?: this.backupFileMock
        doReturn(f).`when`(spy).getBackupFile(any(), any())
        return spy
    }

    // strict mock
    private val backupFileMock: File
        get() {
            val f = strictMock(File::class.java)
            doReturn(false).`when`(f).exists()
            return f
        }
}
