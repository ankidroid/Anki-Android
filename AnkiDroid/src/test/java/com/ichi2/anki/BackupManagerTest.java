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
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.utils.StrictMock.strictMock;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
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
    public void getNewBackupNameTest() throws ParseException {
        BackupManager bm = BackupManager.createInstance();
        // Using a timestamp number directly as MockTime parameter may
        // have different results on other computers and GitHub CI
        long timestamp = bm.getDf().parse("1970-01-02-00-46").getTime();
        String backupName = bm.getNewBackupName(new MockTime(timestamp));

        assertEquals("Backup name doesn't match naming scheme","collection-1970-01-02-00-46.colpkg", backupName);
    }

    @Test
    public void getBackupTimeStringsTest() {
        List<String> ts = BackupManager.getBackupTimeStrings("collection-1999-12-31-23-59.colpkg");
        List<String> expected = Arrays.asList(
                "1999-12-31-23-59", // dateformat
                "1999", // year
                "12", // month
                "31", // day
                "23", // hours
                "59" // minutes
        );
        assertEquals(expected, ts);
    }

    @Test
    public void newBackupNameCanBeParsed() {
        BackupManager bm = BackupManager.createInstance();
        String backupName = bm.getNewBackupName(new MockTime(100000000));
        assertNotNull(backupName);

        List<String> ts = BackupManager.getBackupTimeStrings(backupName);
        assertNotNull("New backup name couldn't be parsed by getBackupTimeStrings()", ts);
    }

    /** Should get date of item at last position on list */
    @Test
    public void getLastBackupDateTest() throws ParseException {
        BackupManager bm = BackupManager.createInstance();
        File[] backups = {
                new File ("collection-2000-12-31-23-04.colpkg"),
                new File ("collection-2010-01-02-03-04.colpkg"),
                new File ("collection-1999-12-31-23-59.colpkg"),
        };
        File[] backups2 = {
                new File ("collection-2000-12-31-23-04.colpkg"),
                new File ("foo.colpkg"),
        };
        File[] backups3 = {
                new File ("foo.colpkg"),
                new File ("bar.colpkg"),
        };

        Date expected = bm.getDf().parse("1999-12-31-23-59");
        Date expected2 = bm.getDf().parse("2000-12-31-23-04");

        assertNull(bm.getLastBackupDate(new File[]{}));
        assertEquals(expected, bm.getLastBackupDate(backups));
        assertEquals("getLastBackupDate() should return the last valid date", expected2, bm.getLastBackupDate(backups2));
        assertNull("getLastBackupDate() should return null when all files aren't parseable", bm.getLastBackupDate(backups3));
    }

    @Test
    public void failsIfNoBackupsAllowed() {
        // arrange

        BackupManager bm = getPassingBackupManagerSpy();

        doReturn(true).when(bm).hasDisabledBackups(any());


        // act
        boolean performBackupResult = performBackup(bm);

        // assert

        assertThat("should fail if backups are disabled", performBackupResult, is(false));

        verify(bm, times(1)).performBackupInBackground(anyString(), anyInt(), any());
        verify(bm, times(1)).hasDisabledBackups(any());

        verifyNoMoreInteractions(bm);
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


    private boolean performBackup(BackupManager bm, Time time) {
        return bm.performBackupInBackground("", 100, time);
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
        doReturn(null).when(spy).getLastBackupDate(any());

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
