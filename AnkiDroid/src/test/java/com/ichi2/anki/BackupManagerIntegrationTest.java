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

package com.ichi2.anki;

import android.annotation.SuppressLint;

import com.ichi2.async.CollectionTask;
import com.ichi2.testutils.AnkiAssert;
import com.ichi2.testutils.BackupManagerTestUtilities;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class BackupManagerIntegrationTest extends RobolectricTest {

    @Test
    @Ignore("Fails on line: if (!f.renameTo(new File(colPath))) {")
    public void restoreBackupLeavesCollectionWritable() throws InterruptedException {
        getCol();
        String path = createBackup();

        // Perform a write
        addNoteUsingBasicModel("Hello", "World");

        waitFortask(new CollectionTask.ImportReplace(path), 1000);

        assertThat("database should be read-write", getCol().getDb().getDatabase().isReadOnly(), is(false));
        AnkiAssert.assertDoesNotThrow(() -> addNoteUsingBasicModel("Hello", "World"));
    }


    private String createBackup() {
        try {
            BackupManagerTestUtilities.setupSpaceForBackup(getTargetContext());

            assertThat("Backup should work", BackupManager.performBackupInBackground(getCol().getPath(), getCol().getTime()), is(true));

            return spinUntilBackupExists(1000);
        } finally {
            BackupManagerTestUtilities.reset();
        }

    }


    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
    private String spinUntilBackupExists(int timeoutMs) {
        long time = System.currentTimeMillis();
        while (true) {
            File colFile = new File(getCol().getPath());
            File[] backups = BackupManager.getBackups(colFile);
            if (backups.length > 0) {
                return backups[0].getAbsolutePath();
            }

            if (System.currentTimeMillis() - time > timeoutMs) {
                throw new RuntimeException("span for longer than " + timeoutMs);
            }
        }
    }
}
