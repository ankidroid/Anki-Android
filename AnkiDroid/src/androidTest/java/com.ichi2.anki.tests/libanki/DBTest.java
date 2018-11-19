package com.ichi2.anki.tests.libanki;

import android.Manifest;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;

import com.ichi2.anki.CollectionHelper;
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
import timber.log.Timber;

@RunWith(AndroidJUnit4.class)
public class DBTest {

    private static int upgradeTestDBVersion = 1;

    private boolean databaseIsUpgraded = false;

    private boolean databaseIsCorrupt = false;

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Test
    public void testDBCorruption() throws Exception {

        String storagePath = CollectionHelper.getDefaultAnkiDroidDirectory();
        File illFatedDBFile = new File(storagePath, "testIllFatedDB.anki2");

        // Make sure we have clean state to start with
        SQLiteDatabase.deleteDatabase(illFatedDBFile);
        Assert.assertFalse("database exists already", illFatedDBFile.exists());

        TestDB illFatedDB = new TestDB(illFatedDBFile.getCanonicalPath());
        Assert.assertFalse("database should not be corrupt yet", databaseIsCorrupt);

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

        Assert.assertTrue("database corruption not detected", databaseIsCorrupt);

        // our handler avoids deleting databases, in contrast with default handler
        Assert.assertTrue("database incorrectly deleted on corruption", illFatedDBFile.exists());

        illFatedDB.close();
        SQLiteDatabase.deleteDatabase(illFatedDBFile);
    }

    @Test
    public void testDatabaseUpgrade() throws Exception {
        String storagePath = CollectionHelper.getDefaultAnkiDroidDirectory();
        File upgradeDBFile = new File(storagePath, "testUpgradeDB.anki2");

        // Make sure we have clean state to start with
        SQLiteDatabase.deleteDatabase(upgradeDBFile);
        Assert.assertFalse("database exists already", upgradeDBFile.exists());

        TestDB upgradeDB = new TestDB(upgradeDBFile.getCanonicalPath());
        Assert.assertFalse("database should not be upgraded yet", databaseIsUpgraded);
        upgradeDB.close();

        upgradeTestDBVersion++;
        TestDB upgradedDB = new TestDB(upgradeDBFile.getCanonicalPath());
        Assert.assertEquals(upgradeTestDBVersion, upgradedDB.getDatabase().getVersion());
        Timber.d("current upgraded status: %s", databaseIsUpgraded);
        Assert.assertTrue("database never upgraded", databaseIsUpgraded);

        upgradedDB.close();
        SQLiteDatabase.deleteDatabase(upgradeDBFile);
    }

    // Test fixture that lets us inspect corruption handler and upgrade status
    public class TestDB extends DB {

        private TestDB(String ankiFilename) {
            super(ankiFilename);
        }

        @Override
        protected int getDbVersion() {
            return upgradeTestDBVersion;
        }

        @Override
        protected SupportSQLiteOpenHelperCallback getDBCallback() {
            return new TestSupportSQLiteOpenHelperCallback(getDbVersion());
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

            @Override
            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                databaseIsUpgraded = true;
                super.onUpgrade(db, oldVersion, newVersion);
            }
        }
    }
}
