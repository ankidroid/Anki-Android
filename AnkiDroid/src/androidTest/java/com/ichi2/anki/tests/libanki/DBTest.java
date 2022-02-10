/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2018 Mike Hardy <github@mikehardy.net>                                 *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.tests.libanki;

import android.Manifest;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.libanki.DB;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

@RunWith(AndroidJUnit4.class)
public class DBTest extends InstrumentedTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Test
    public void testDBCorruption() throws Exception {

        String storagePath = CollectionHelper.getDefaultAnkiDroidDirectory(getTestContext());
        File illFatedDBFile = new File(storagePath, "illFatedDB.anki2");

        // Make sure we have clean state to start with
        SQLiteDatabase.deleteDatabase(illFatedDBFile);
        Assert.assertFalse("database exists already", illFatedDBFile.exists());

        TestDB illFatedDB = new TestDB(illFatedDBFile.getCanonicalPath());
        Assert.assertFalse("database should not be corrupt yet", illFatedDB.mDatabaseIsCorrupt);

        // Scribble in it
        byte[] b = new byte[1024];
        new Random().nextBytes(b);
        FileOutputStream illFatedDBFileStream = new FileOutputStream(illFatedDBFile);
        illFatedDBFileStream.write(b, 0, 1024);
        illFatedDBFileStream.flush();
        illFatedDBFileStream.close();

        // Try to do something
        try {
            illFatedDB.execute("CREATE TABLE test_table (test_column INTEGER NOT NULL);");
            Assert.fail("There should have been a corruption exception");
        }
        catch (SQLiteDatabaseCorruptException e) {
            // do nothing, it is expected
        }

        Assert.assertTrue("database corruption not detected", illFatedDB.mDatabaseIsCorrupt);

        // our handler avoids deleting databases, in contrast with default handler
        Assert.assertTrue("database incorrectly deleted on corruption", illFatedDBFile.exists());

        illFatedDB.close();
        SQLiteDatabase.deleteDatabase(illFatedDBFile);
    }



    // Test fixture that lets us inspect corruption handler status
    public static class TestDB extends DB {

        private boolean mDatabaseIsCorrupt = false;

        private TestDB(String ankiFilename) {
            super(ankiFilename);
        }

        @Override
        protected SupportSQLiteOpenHelperCallback getDBCallback() {
            return new TestSupportSQLiteOpenHelperCallback(1);
        }

        public class TestSupportSQLiteOpenHelperCallback extends SupportSQLiteOpenHelperCallback {
            private TestSupportSQLiteOpenHelperCallback(int version) {
                super(version);
            }

            @Override
            public void onCorruption(SupportSQLiteDatabase db) {
                mDatabaseIsCorrupt = true;
                super.onCorruption(db);
            }
        }
    }
}
