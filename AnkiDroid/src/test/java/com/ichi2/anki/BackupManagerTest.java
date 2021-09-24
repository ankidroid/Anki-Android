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

package com.ichi2.anki;

import com.ichi2.libanki.utils.Time;
import com.ichi2.testutils.MockTime;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.utils.StrictMock.strictMock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(AndroidJUnit4.class)
public class BackupManagerTest {

    @Test
    public void failsIfNoBackupsAllowedAndForceIsDisabled() {
        // arrange

        BackupManager bm = getPassingBackupManagerSpy();

        doReturn(true).when(bm).hasDisabledBackups(any());


        // act
        boolean force = false;

        boolean performBackupResult = performBackup(bm, force);

        // assert

        assertThat("should fail if backups are disabled", performBackupResult, is(false));

        verify(bm, times(1)).performBackupInBackground(anyString(), anyInt(), anyBoolean(), any());
        verify(bm, times(1)).hasDisabledBackups(any());

        verifyNoMoreInteractions(bm);
    }

    @Test
    public void passesIfNoBackupsAllowedAndForceIsEnabled() {
        // arrange

        BackupManager bm = getPassingBackupManagerSpy();

        doReturn(true).when(bm).hasDisabledBackups(any());


        // act
        boolean force = true;

        boolean performBackupResult = performBackup(bm, force);

        // assert

        assertThat("should pass if backups are disabled and force is enabled", performBackupResult, is(true));

        verify(bm, times(1)).hasDisabledBackups(any());
        verify(bm, times(1)).performBackupInBackground(anyString(), anyInt(), anyBoolean(), any());
    }

    /** Meta test: ensuring passingBackupManagerSpy passes */
    @Test
    public void testPassingSpy() {
        BackupManager bm = getPassingBackupManagerSpy();

        boolean result = performBackup(bm);

        verify(bm, times(1)).performBackupInNewThread(any(), any());
        assertThat("PerformBackup should pass", result, is(true));
    }

    @Test
    public void noBackupPerformedIfNoBackupNecessary() {
        BackupManager bm = getPassingBackupManagerSpy();

        doReturn(true).when(bm).isBackupUnnecessary(any(), any());

        boolean result = performBackup(bm);

        assertThat("should fail if backups not necessary", result, is(false));

        verify(bm, times(1)).isBackupUnnecessary(any(), any());
    }

    @Test
    public void noBackupPerformedIfBackupAlreadyExists() {
        File file = strictMock(File.class);
        doReturn(true).when(file).exists();

        BackupManager bm = getPassingBackupManagerSpy(file);

        boolean result = performBackup(bm);

        assertThat("should fail if backups exists", result, is(false));
    }

    @Test
    public void noBackupPerformedIfCollectionTooSmall() {
        BackupManager bm = getPassingBackupManagerSpy();

        doReturn(true).when(bm).collectionIsTooSmallToBeValid(any());

        boolean result = performBackup(bm);

        assertThat("should fail if collection too small", result, is(false));
    }

    private boolean performBackup(BackupManager bm) {
        return performBackup(bm, new MockTime(100000000));
    }

    protected boolean performBackup(BackupManager bm, Time time) {
        return performBackup(bm, time, false);
    }

    protected boolean performBackup(BackupManager bm, boolean force) {
        return performBackup(bm, new MockTime(100000000), force);
    }


    private boolean performBackup(BackupManager bm, Time time, boolean force) {
        return bm.performBackupInBackground("", 100, force, time);
    }

    /** Returns a spy of BackupManager which would pass */
    protected BackupManager getPassingBackupManagerSpy() {
        return getPassingBackupManagerSpy(null);
    }

    /** Returns a spy of BackupManager which would pass */
    protected BackupManager getPassingBackupManagerSpy(File backupFileMock) {
        BackupManager spy = spy(BackupManager.createInstance());
        doReturn(true).when(spy).hasFreeDiscSpace(any());
        doReturn(false).when(spy).collectionIsTooSmallToBeValid(any());
        doNothing().when(spy).performBackupInNewThread(any(), any());
        doReturn(null).when(spy).getLastBackupDate(any(), any());

        File f = backupFileMock != null ? backupFileMock : getBackupFileMock();
        doReturn(f).when(spy).getBackupFile(any(), any());
        return spy;
    }


    // strict mock
    private File getBackupFileMock() {
        File f = strictMock(File.class);
        doReturn(false).when(f).exists();
        return f;
    }
}
