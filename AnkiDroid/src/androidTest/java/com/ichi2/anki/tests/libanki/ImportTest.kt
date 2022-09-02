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

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.Shared
import com.ichi2.libanki.Collection
import com.ichi2.libanki.importer.*
import com.ichi2.utils.JSONException
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.BackendFactory.defaultLegacySchema
import org.hamcrest.Matchers.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

@KotlinCleanup("is -> equalTo")
@KotlinCleanup("IDE Lint")
@RunWith(AndroidJUnit4::class)
class ImportTest : InstrumentedTest() {
    @KotlinCleanup("init here/lateinit")
    private var mTestCol: Collection? = null

    @get:Rule
    var runtimePermissionRule =
        GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

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
        mTestCol = emptyCol
        // the backend provides its own importing methods
        assumeThat(defaultLegacySchema, `is`(true))
    }

    @After
    fun tearDown() {
        mTestCol!!.close()
    }

    @Test
    @Throws(IOException::class, JSONException::class, ImportExportException::class)
    fun testAnki2Mediadupes() {

        // add a note that references a sound
        var n = mTestCol!!.newNote()
        n.setField(0, "[sound:foo.mp3]")
        val mid = n.model().getLong("id")
        mTestCol!!.addNote(n)
        // add that sound to the media directory
        var os = FileOutputStream(File(mTestCol!!.media.dir(), "foo.mp3"), false)
        os.write("foo".toByteArray())
        os.close()
        mTestCol!!.close()
        // it should be imported correctly into an empty deck
        val empty = emptyCol
        var imp: Importer = Anki2Importer(empty, mTestCol!!.path)
        imp.run()
        var expected: List<String?> = listOf("foo.mp3")
        var actual = Arrays.asList(*File(empty.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        // and importing again will not duplicate, as the file content matches
        empty.remCards(empty.db.queryLongList("select id from cards"))
        imp = Anki2Importer(empty, mTestCol!!.path)
        imp.run()
        expected = listOf("foo.mp3")
        actual = Arrays.asList(*File(empty.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        n = empty.getNote(empty.db.queryLongScalar("select id from notes"))
        assertTrue("foo.mp3" in n.fields[0])
        // if the local file content is different, and import should trigger a rename
        empty.remCards(empty.db.queryLongList("select id from cards"))
        os = FileOutputStream(File(empty.media.dir(), "foo.mp3"), false)
        os.write("bar".toByteArray())
        os.close()
        imp = Anki2Importer(empty, mTestCol!!.path)
        imp.run()
        expected = Arrays.asList("foo.mp3", String.format("foo_%s.mp3", mid))
        actual = Arrays.asList(*File(empty.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        n = empty.getNote(empty.db.queryLongScalar("select id from notes"))
        assertTrue(n.fields[0].contains("_"))
        // if the localized media file already exists, we rewrite the note and media
        empty.remCards(empty.db.queryLongList("select id from cards"))
        os = FileOutputStream(File(empty.media.dir(), "foo.mp3"))
        os.write("bar".toByteArray())
        os.close()
        imp = Anki2Importer(empty, mTestCol!!.path)
        imp.run()
        expected = Arrays.asList("foo.mp3", String.format("foo_%s.mp3", mid))
        actual = Arrays.asList(*File(empty.media.dir()).list()!!)
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
        var imp: Importer = AnkiPackageImporter(mTestCol, apkg)
        var expected: List<String?> = emptyList<String>()
        var actual = Arrays.asList(
            *File(
                mTestCol!!.media.dir()
            ).list()!!
        )
        actual.retainAll(expected)
        assertEquals(actual.size.toLong(), expected.size.toLong())
        imp.run()
        expected = listOf("foo.wav")
        actual = Arrays.asList(*File(mTestCol!!.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        // import again should be idempotent in terms of media
        mTestCol!!.remCards(mTestCol!!.db.queryLongList("select id from cards"))
        imp = AnkiPackageImporter(mTestCol, apkg)
        imp.run()
        expected = listOf("foo.wav")
        actual = Arrays.asList(*File(mTestCol!!.media.dir()).list()!!)
        actual.retainAll(expected)
        assertEquals(expected.size.toLong(), actual.size.toLong())
        // but if the local file has different data, it will rename
        mTestCol!!.remCards(mTestCol!!.db.queryLongList("select id from cards"))
        val os = FileOutputStream(File(mTestCol!!.media.dir(), "foo.wav"), false)
        os.write("xyz".toByteArray())
        os.close()
        imp = AnkiPackageImporter(mTestCol, apkg)
        imp.run()
        assertEquals(2, File(mTestCol!!.media.dir()).list()!!.size.toLong())
    }

    @Test
    @Throws(IOException::class, JSONException::class, ImportExportException::class)
    fun testAnki2DiffmodelTemplates() {
        // different from the above as this one tests only the template text being
        // changed, not the number of cards/fields
        // import the first version of the model
        var tmp = Shared.getTestFilePath(testContext, "diffmodeltemplates-1.apkg")
        var imp = AnkiPackageImporter(mTestCol, tmp)
        imp.setDupeOnSchemaChange(true)
        imp.run()
        // then the version with updated template
        tmp = Shared.getTestFilePath(testContext, "diffmodeltemplates-2.apkg")
        imp = AnkiPackageImporter(mTestCol, tmp)
        imp.setDupeOnSchemaChange(true)
        imp.run()
        // collection should contain the note we imported
        assertEquals(1, mTestCol!!.noteCount().toLong())
        // the front template should contain the text added in the 2nd package
        val tcid = mTestCol!!.findCards("")[0]
        val tnote = mTestCol!!.getCard(tcid).note()
        assertTrue(
            mTestCol!!.findTemplates(tnote)[0].getString("qfmt").contains("Changed Front Template")
        )
    }

    @Test
    @Throws(IOException::class, ImportExportException::class)
    fun testAnki2Updates() {
        // create a new empty deck
        var tmp = Shared.getTestFilePath(testContext, "update1.apkg")
        var imp = AnkiPackageImporter(mTestCol, tmp)
        imp.run()
        assertEquals(0, imp.dupes)
        assertEquals(1, imp.added)
        assertEquals(0, imp.updated)
        // importing again should be idempotent
        imp = AnkiPackageImporter(mTestCol, tmp)
        imp.run()
        assertEquals(1, imp.dupes)
        assertEquals(0, imp.added)
        assertEquals(0, imp.updated)
        // importing a newer note should update
        assertEquals(1, mTestCol!!.noteCount().toLong())
        assertTrue(mTestCol!!.db.queryString("select flds from notes").startsWith("hello"))
        tmp = Shared.getTestFilePath(testContext, "update2.apkg")
        imp = AnkiPackageImporter(mTestCol, tmp)
        imp.run()
        assertEquals(1, imp.dupes)
        assertEquals(0, imp.added)
        assertEquals(1, imp.updated)
        assertTrue(mTestCol!!.db.queryString("select flds from notes").startsWith("goodbye"))
    }

    /**
     * Custom tests for AnkiDroid.
     */
    @Test
    @Throws(IOException::class, ImportExportException::class)
    fun testDupeIgnore() {
        // create a new empty deck
        var tmp = Shared.getTestFilePath(testContext, "update1.apkg")
        var imp = AnkiPackageImporter(mTestCol, tmp)
        imp.run()
        tmp = Shared.getTestFilePath(testContext, "update3.apkg")
        imp = AnkiPackageImporter(mTestCol, tmp)
        imp.run()
        // there is a dupe, but it was ignored
        assertEquals(1, imp.dupes)
        assertEquals(0, imp.added)
        assertEquals(0, imp.updated)
    }
}
