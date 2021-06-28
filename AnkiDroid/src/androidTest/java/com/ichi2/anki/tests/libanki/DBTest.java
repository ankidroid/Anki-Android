package com.ichi2.anki.tests.libanki;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.Build;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.DB;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
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
        Assert.assertFalse("database should not be corrupt yet", illFatedDB.databaseIsCorrupt);

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

        Assert.assertTrue("database corruption not detected", illFatedDB.databaseIsCorrupt);

        // our handler avoids deleting databases, in contrast with default handler
        Assert.assertTrue("database incorrectly deleted on corruption", illFatedDBFile.exists());

        illFatedDB.close();
        SQLiteDatabase.deleteDatabase(illFatedDBFile);
    }



    // Test fixture that lets us inspect corruption handler status
    public static class TestDB extends DB {

        private boolean databaseIsCorrupt = false;

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
                databaseIsCorrupt = true;
                super.onCorruption(db);
            }
        }
    }
}
