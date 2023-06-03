/****************************************************************************************
 * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.Shared
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.libanki.Collection
import com.ichi2.libanki.importer.Anki2Importer
import com.ichi2.libanki.importer.AnkiPackageImporter
import com.ichi2.libanki.importer.Importer
import net.ankiweb.rsdroid.BackendFactory.defaultLegacySchema
import org.hamcrest.Matchers.equalTo
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ImportTest : InstrumentedTest() {
    private lateinit var testCol: Collection

    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance

    // testAnki2Mediadupes() failed on Travis API=22 EMU_FLAVOR=default ABI=armeabi-v7a
    // com.ichi2.anki.tests.libanki.ImportTest > testAnki2Mediadupes[test(AVD) - 5.1.1] FAILED
    // error:
    // android.database.sqlite.SQLiteReadOnlyDatabaseException: attempt to write a readonly database (code 1032)
    // at io.requery.android.database.sqlite.SQLiteConnection.nativeExecuteForChangedRowCount(Native Method)
    //        :AnkiDroid:connectedDebugAndroidTest FAILED
    //
    // Error code 1032 is https://www.sqlite.org/rescode.html#readonly_dbmoved - which should be impossible
    //
    // I was unable to reproduce it on the same emulator locally, even with thousands of iterations.
    // Allowing it to re-run now, 3 times, in case it flakes again.
    @get:Rule
    var retry = RetryRule(10)

    @Before
    @Throws(IOException::class)
    fun setUp() {
        testCol = emptyCol
        // the backend provides its own importing methods
        assumeThat(defaultLegacySchema, equalTo(true))
    }

    @After
    fun tearDown() {
        testCol.close()
    }

    @Test
    @Throws(IOException::class, JSONException::class, ImportExportException::class)
    fun testAnki2Mediadupes() {
        // add a note that references a sound
        var n = testCol.newNote()
        n.setField(0, "[sound:foo.mp3]")
        val mid = n.model().getLong("id")
        testCol.addNote(n)
        // add that sound to the media directory
        var os = FileOutputStream(File(testCol.media.dir(), "foo.mp3"), false)
        os.write("foo".toByteArray())
        os.close()
        testCol.close()
        // it should be imported correctly into an empty deck
        val empty = emptyCol
        var imp: Importer = Anki2Importer(empty, testCol.path)
        imp.run(col)
        var expected = listOf("foo.mp3")
        var actual = File(empty.media.dir()).list()!!.toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        // and importing again will not duplicate, as the file content matches
        empty.remCards(empty.db.queryLongList("select id from cards"))
        imp = Anki2Importer(empty, testCol.path)
        imp.run(col)
        expected = listOf("foo.mp3")
        actual = mutableListOf(*File(empty.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        n = empty.getNote(empty.db.queryLongScalar("select id from notes"))
        assertTrue("foo.mp3" in n.fields[0])
        // if the local file content is different, and import should trigger a rename
        empty.remCards(empty.db.queryLongList("select id from cards"))
        os = FileOutputStream(File(empty.media.dir(), "foo.mp3"), false)
        os.write("bar".toByteArray())
        os.close()
        imp = Anki2Importer(empty, testCol.path)
        imp.run(col)
        expected = listOf("foo.mp3", "foo_$mid.mp3")
        actual = mutableListOf(*File(empty.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        n = empty.getNote(empty.db.queryLongScalar("select id from notes"))
        assertTrue(n.fields[0].contains("_"))
        // if the localized media file already exists, we rewrite the note and media
        empty.remCards(empty.db.queryLongList("select id from cards"))
        os = FileOutputStream(File(empty.media.dir(), "foo.mp3"))
        os.write("bar".toByteArray())
        os.close()
        imp = Anki2Importer(empty, testCol.path)
        imp.run(col)
        expected = listOf("foo.mp3", "foo_$mid.mp3")
        actual = mutableListOf(*File(empty.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        n = empty.getNote(empty.db.queryLongScalar("select id from notes"))
        assertTrue(n.fields[0].contains("_"))
        empty.close()
    }

    @Test
    @Throws(IOException::class, ImportExportException::class)
    fun testApkg() {
        val apkg = Shared.getTestFilePath(testContext, "media.apkg")
        var imp: Importer = AnkiPackageImporter(testCol, apkg)
        var expected: List<String?> = emptyList<String>()
        var actual = mutableListOf(
            *File(
                testCol.media.dir()
            ).list()!!
        )
        actual.retainAll(expected)
        assertEquals(actual.size.toLong(), 0)
        imp.run(col)
        expected = listOf("foo.wav")
        actual = mutableListOf(*File(testCol.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        // import again should be idempotent in terms of media
        testCol.remCards(testCol.db.queryLongList("select id from cards"))
        imp = AnkiPackageImporter(testCol, apkg)
        imp.run(col)
        expected = listOf("foo.wav")
        actual = mutableListOf(*File(testCol.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        // but if the local file has different data, it will rename
        testCol.remCards(testCol.db.queryLongList("select id from cards"))
        val os = FileOutputStream(File(testCol.media.dir(), "foo.wav"), false)
        os.write("xyz".toByteArray())
        os.close()
        imp = AnkiPackageImporter(testCol, apkg)
        imp.run(col)
        assertEquals(2, File(testCol.media.dir()).list()!!.size.toLong())
    }

    @Test
    @Throws(IOException::class, JSONException::class, ImportExportException::class)
    fun testAnki2DiffmodelTemplates() {
        // different from the above as this one tests only the template text being
        // changed, not the number of cards/fields
        // import the first version of the model
        var tmp = Shared.getTestFilePath(testContext, "diffmodeltemplates-1.apkg")
        var imp = AnkiPackageImporter(testCol, tmp)
        imp.setDupeOnSchemaChange(true)
        imp.run(col)
        // then the version with updated template
        tmp = Shared.getTestFilePath(testContext, "diffmodeltemplates-2.apkg")
        imp = AnkiPackageImporter(testCol, tmp)
        imp.setDupeOnSchemaChange(true)
        imp.run(col)
        // collection should contain the note we imported
        assertEquals(1, testCol.noteCount().toLong())
        // the front template should contain the text added in the 2nd package
        val tcid = testCol.findCards("")[0]
        val tnote = testCol.getCard(tcid).note(col)
        assertTrue(
            testCol.findTemplates(tnote)[0].getString("qfmt").contains("Changed Front Template")
        )
    }

    @Test
    @Throws(IOException::class, ImportExportException::class)
    fun testAnki2Updates() {
        // create a new empty deck
        var tmp = Shared.getTestFilePath(testContext, "update1.apkg")
        var imp = AnkiPackageImporter(testCol, tmp)
        imp.run(col)
        assertEquals(0, imp.dupes)
        assertEquals(1, imp.added)
        assertEquals(0, imp.updated)
        // importing again should be idempotent
        imp = AnkiPackageImporter(testCol, tmp)
        imp.run(col)
        assertEquals(1, imp.dupes)
        assertEquals(0, imp.added)
        assertEquals(0, imp.updated)
        // importing a newer note should update
        assertEquals(1, testCol.noteCount().toLong())
        assertTrue(testCol.db.queryString("select flds from notes").startsWith("hello"))
        tmp = Shared.getTestFilePath(testContext, "update2.apkg")
        imp = AnkiPackageImporter(testCol, tmp)
        imp.run(col)
        assertEquals(1, imp.dupes)
        assertEquals(0, imp.added)
        assertEquals(1, imp.updated)
        assertTrue(testCol.db.queryString("select flds from notes").startsWith("goodbye"))
    }

    /**
     * Custom tests for AnkiDroid.
     */
    @Test
    @Throws(IOException::class, ImportExportException::class)
    fun testDupeIgnore() {
        // create a new empty deck
        var tmp = Shared.getTestFilePath(testContext, "update1.apkg")
        var imp = AnkiPackageImporter(testCol, tmp)
        imp.run(col)
        tmp = Shared.getTestFilePath(testContext, "update3.apkg")
        imp = AnkiPackageImporter(testCol, tmp)
        imp.run(col)
        // there is a dupe, but it was ignored
        assertEquals(1, imp.dupes)
        assertEquals(0, imp.added)
        assertEquals(0, imp.updated)
    }
}
