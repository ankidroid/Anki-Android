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
package com.ichi2.anki.tests.libanki

import android.Manifest
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.libanki.DB
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class DBTest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @Test
    @Throws(Exception::class)
    fun testDBCorruption() {
        val storagePath = CollectionHelper.getDefaultAnkiDroidDirectory(testContext)
        val illFatedDBFile = File(storagePath, "illFatedDB.anki2")

        // Make sure we have clean state to start with
        SQLiteDatabase.deleteDatabase(illFatedDBFile)
        assertFalse(illFatedDBFile.exists(), "database exists already")
        val callback = TestCallback(1)
        val illFatedDB = DB(
            AnkiSupportSQLiteDatabase.withFramework(
                testContext,
                illFatedDBFile.canonicalPath,
                callback
            )
        )
        assertFalse(callback.databaseIsCorrupt, "database should not be corrupt yet")

        // Scribble in it
        val b = ByteArray(1024)
        Random().nextBytes(b)
        val illFatedDBFileStream = FileOutputStream(illFatedDBFile)
        illFatedDBFileStream.write(b, 0, 1024)
        illFatedDBFileStream.flush()
        illFatedDBFileStream.close()

        // Try to do something
        try {
            illFatedDB.execute("CREATE TABLE test_table (test_column INTEGER NOT NULL);")
            fail("There should have been a corruption exception")
        } catch (e: SQLiteDatabaseCorruptException) {
            // do nothing, it is expected
        }
        assertTrue(callback.databaseIsCorrupt, "database corruption not detected")

        // our handler avoids deleting databases, in contrast with default handler
        assertTrue(illFatedDBFile.exists(), "database incorrectly deleted on corruption")
        illFatedDB.close()
        SQLiteDatabase.deleteDatabase(illFatedDBFile)
    }

    // Test fixture that lets us inspect corruption handler status
    inner class TestCallback(version: Int) : AnkiSupportSQLiteDatabase.DefaultDbCallback(version) {
        internal var databaseIsCorrupt = false
        override fun onCorruption(db: SupportSQLiteDatabase) {
            databaseIsCorrupt = true
            super.onCorruption(db)
        }
    }
}
