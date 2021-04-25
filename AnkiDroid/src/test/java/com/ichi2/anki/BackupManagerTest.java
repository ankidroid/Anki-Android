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

import com.ichi2.testutils.MockTime;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

        BackupManager bm = spy(BackupManager.createInstance());

        doReturn(true).when(bm).hasDisabledBackups(any());

        MockTime time = new MockTime(2010, 1, 1, 1, 1, 1, 1, 0);

        // act

        boolean performBackupResult = bm.performBackupInBackground("", 100, false, time);

        // assert

        assertThat("should fail if backups are disabled", performBackupResult, is(false));

        verify(bm, times(1)).performBackupInBackground(anyString(), anyInt(), anyBoolean(), any());
        verify(bm, times(1)).hasDisabledBackups(any());

        verifyNoMoreInteractions(bm);
    }
}
