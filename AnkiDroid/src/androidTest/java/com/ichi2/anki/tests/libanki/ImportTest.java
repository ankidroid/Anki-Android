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
package com.ichi2.anki.tests.libanki;


import android.Manifest;
import android.os.Build;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.anki.tests.Shared;
import com.ichi2.anki.testutil.TestEnvironment;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.ModelManager;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.importer.Anki2Importer;
import com.ichi2.libanki.importer.AnkiPackageImporter;
import com.ichi2.libanki.importer.Importer;
import com.ichi2.libanki.importer.NoteImporter;
import com.ichi2.libanki.importer.TextImporter;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.rule.GrantPermissionRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ImportTest extends InstrumentedTest {

    private Collection testCol;

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    // testAnki2Mediadupes() failed on Travis API=22 EMU_FLAVOR=default ABI=armeabi-v7a
    //com.ichi2.anki.tests.libanki.ImportTest > testAnki2Mediadupes[test(AVD) - 5.1.1] FAILED
    // error:
    //android.database.sqlite.SQLiteReadOnlyDatabaseException: attempt to write a readonly database (code 1032)
    //at io.requery.android.database.sqlite.SQLiteConnection.nativeExecuteForChangedRowCount(Native Method)
    //        :AnkiDroid:connectedDebugAndroidTest FAILED
    //
    // Error code 1032 is https://www.sqlite.org/rescode.html#readonly_dbmoved - which should be impossible
    //
    // I was unable to reproduce it on the same emulator locally, even with thousands of iterations.
    // Allowing it to re-run now, 3 times, in case it flakes again.
    @Rule
    public RetryRule retry = new RetryRule(10);

    @Before
    public void setUp() throws IOException {
        testCol = getEmptyCol();
    }

    @After
    public void tearDown() {
        testCol.close();
    }

    @Test
    public void testAnki2Mediadupes() throws IOException, JSONException, ImportExportException {

        // add a note that references a sound
        Note n = testCol.newNote();
        n.setField(0, "[sound:foo.mp3]");
        long mid = n.model().getLong("id");
        testCol.addNote(n);
        // add that sound to the media folder
        FileOutputStream os = new FileOutputStream(new File(testCol.getMedia().dir(), "foo.mp3"), false);
        os.write("foo".getBytes());
        os.close();
        testCol.close();
        // it should be imported correctly into an empty deck
        Collection empty = getEmptyCol();
        Importer imp = new Anki2Importer(empty, testCol.getPath());
        imp.run();
        List<String> expected = Collections.singletonList("foo.mp3");
        List<String> actual = Arrays.asList(new File(empty.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        // and importing again will not duplicate, as the file content matches
        empty.remCards(empty.getDb().queryLongList("select id from cards"));
        imp = new Anki2Importer(empty, testCol.getPath());
        imp.run();
        expected = Collections.singletonList("foo.mp3");
        actual = Arrays.asList(new File(empty.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        n = empty.getNote(empty.getDb().queryLongScalar("select id from notes"));
        assertTrue(n.getFields()[0].contains("foo.mp3"));
        // if the local file content is different, and import should trigger a rename
        empty.remCards(empty.getDb().queryLongList("select id from cards"));
        os = new FileOutputStream(new File(empty.getMedia().dir(), "foo.mp3"), false);
        os.write("bar".getBytes());
        os.close();
        imp = new Anki2Importer(empty, testCol.getPath());
        imp.run();
        expected = Arrays.asList("foo.mp3", String.format("foo_%s.mp3", mid));
        actual = Arrays.asList(new File(empty.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        n = empty.getNote(empty.getDb().queryLongScalar("select id from notes"));
        assertTrue(n.getFields()[0].contains("_"));
        // if the localized media file already exists, we rewrite the note and media
        empty.remCards(empty.getDb().queryLongList("select id from cards"));
        os = new FileOutputStream(new File(empty.getMedia().dir(), "foo.mp3"));
        os.write("bar".getBytes());
        os.close();
        imp = new Anki2Importer(empty, testCol.getPath());
        imp.run();
        expected =  Arrays.asList("foo.mp3", String.format("foo_%s.mp3", mid));
        actual = Arrays.asList(new File(empty.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        n = empty.getNote(empty.getDb().queryLongScalar("select id from notes"));
        assertTrue(n.getFields()[0].contains("_"));
        empty.close();
    }

    @Test
    public void testApkg() throws IOException, ImportExportException {

        String apkg = Shared.getTestFilePath(getTestContext(), "media.apkg");
        Importer imp = new AnkiPackageImporter(testCol, apkg);
        List<String> expected = Collections.emptyList();
        List<String> actual = Arrays.asList(new File(testCol.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(actual.size(), expected.size());
        imp.run();
        expected = Collections.singletonList("foo.wav");
        actual = Arrays.asList(new File(testCol.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        // import again should be idempotent in terms of media
        testCol.remCards(testCol.getDb().queryLongList("select id from cards"));
        imp = new AnkiPackageImporter(testCol, apkg);
        imp.run();
        expected = Collections.singletonList("foo.wav");
        actual = Arrays.asList(new File(testCol.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        // but if the local file has different data, it will rename
        testCol.remCards(testCol.getDb().queryLongList("select id from cards"));
        FileOutputStream os = new FileOutputStream(new File(testCol.getMedia().dir(), "foo.wav"), false);
        os.write("xyz".getBytes());
        os.close();
        imp = new AnkiPackageImporter(testCol, apkg);
        imp.run();
        assertEquals(2, new File(testCol.getMedia().dir()).list().length);
    }

    @Test
    public void testAnki2DiffmodelTemplates() throws IOException, JSONException, ImportExportException {
        // different from the above as this one tests only the template text being
        // changed, not the number of cards/fields
        // import the first version of the model
        String tmp = Shared.getTestFilePath(getTestContext(), "diffmodeltemplates-1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(testCol, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        // then the version with updated template
        tmp = Shared.getTestFilePath(getTestContext(), "diffmodeltemplates-2.apkg");
        imp = new AnkiPackageImporter(testCol, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        // collection should contain the note we imported
        assertEquals(1, testCol.noteCount());
        // the front template should contain the text added in the 2nd package
        Long tcid = testCol.findCards("").get(0);
        Note tnote = testCol.getCard(tcid).note();
        assertTrue(testCol.findTemplates(tnote).get(0).getString("qfmt").contains("Changed Front Template"));
    }

    @Test
    public void testAnki2Updates() throws IOException, ImportExportException {
        // create a new empty deck
        String tmp = Shared.getTestFilePath(getTestContext(), "update1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        assertEquals(0, imp.getDupes());
        assertEquals(1, imp.getAdded());
        assertEquals(0, imp.getUpdated());
        // importing again should be idempotent
        imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        assertEquals(1, imp.getDupes());
        assertEquals(0, imp.getAdded());
        assertEquals(0, imp.getUpdated());
        // importing a newer note should update
        assertEquals(1, testCol.noteCount());
        assertTrue(testCol.getDb().queryString("select flds from notes").startsWith("hello"));
        tmp = Shared.getTestFilePath(getTestContext(), "update2.apkg");
        imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        assertEquals(1, imp.getDupes());
        assertEquals(0, imp.getAdded());
        assertEquals(1, imp.getUpdated());
        assertTrue(testCol.getDb().queryString("select flds from notes").startsWith("goodbye"));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    public void testCsv() throws IOException {
        String file = Shared.getTestFilePath(getTestContext(), "text-2fields.txt");
        TextImporter i = new TextImporter(testCol, file);
        i.initMapping();
        i.run();
        if (TestEnvironment.isDisplayingDefaultEnglishStrings()) {
            assertThat(i.getLog(), contains(
                    "‘多すぎる too many fields’ had 3 fields, expected 2",
                    "‘not, enough, fields’ had 1 fields, expected 2",
                    "Appeared twice in file: 飲む",
                    "Empty first field:  to play",
                    "5 notes added, 0 notes updated, 0 notes unchanged."));
        } else {
            assertThat(i.getLog(), hasSize(5));
        }

        assertEquals(5, i.getTotal());
        // if we run the import again, it should update instead
        i.run();
        if (TestEnvironment.isDisplayingDefaultEnglishStrings()) {
            assertThat(i.getLog(), contains(
                    "‘多すぎる too many fields’ had 3 fields, expected 2",
                    "‘not, enough, fields’ had 1 fields, expected 2",
                    "Appeared twice in file: 飲む",
                    "Empty first field:  to play",
                    "0 notes added, 0 notes updated, 5 notes unchanged.",
                    "First field matched: 食べる",
                    "First field matched: 飲む",
                    "First field matched: テスト",
                    "First field matched: to eat",
                    "First field matched: 遊ぶ"));
        } else {
            assertThat(i.getLog(), hasSize(10));
        }
        assertEquals(5, i.getTotal());
        // but importing should not clobber tags if they're unmapped
        Note n = testCol.getNote(testCol.getDb().queryLongScalar("select id from notes"));
        n.addTag("test");
        n.flush();
        i.run();
        n.load();
        assertThat(n.getTags(), contains("test"));
        assertThat(n.getTags(), hasSize(1));
        // if add-only mode, count will be 0
        i.setImportMode(NoteImporter.ImportMode.IGNORE_MODE);
        i.run();
        assertEquals(0, i.getTotal());
        // and if dupes mode, will reimport everything
        assertEquals(5, testCol.cardCount());
        i.setImportMode(NoteImporter.ImportMode.ADD_MODE);
        i.run();
        // includes repeated field
        assertEquals(6, i.getTotal());
        assertEquals(11, testCol.cardCount());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    public void testCsv2() throws  IOException, ConfirmModSchemaException {
        ModelManager mm = testCol.getModels();
        Model m = mm.current();
        JSONObject f = mm.newField("Three");
        mm.addField(m, f);
        mm.save(m);
        Note n = testCol.newNote();
        n.setField(0, "1");
        n.setField(1, "2");
        n.setField(2, "3");
        testCol.addNote(n);
        // an update with unmapped fields should not clobber those fields
        String file = Shared.getTestFilePath(getTestContext(), "text-update.txt");
        TextImporter i = new TextImporter(testCol, file);
        i.initMapping();
        i.run();
        n.load();
        List<String> fields = Arrays.asList(n.getFields());
        assertThat(fields, contains("1", "x", "3"));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    public void testCsvWithByteOrderMark() throws IOException {
        String file = Shared.getTestFilePath(getTestContext(), "text-utf8-bom.txt");
        TextImporter i = new TextImporter(testCol, file);
        i.initMapping();
        i.run();
        Note n = testCol.getNote(testCol.getDb().queryLongScalar("select id from notes"));
        assertThat(Arrays.asList(n.getFields()), contains("Hello", "world"));
    }


    @Test
    @Ignore("Not yet handled")
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    public void testUcs2CsvWithByteOrderMark() throws IOException {
        String file = Shared.getTestFilePath(getTestContext(), "text-ucs2-be-bom.txt");
        TextImporter i = new TextImporter(testCol, file);
        i.initMapping();
        i.run();
        Note n = testCol.getNote(testCol.getDb().queryLongScalar("select id from notes"));
        assertThat(Arrays.asList(n.getFields()), contains("Hello", "world"));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    public void csvManualBasicExample() throws IOException, ConfirmModSchemaException {
        String file = Shared.getTestFilePath(getTestContext(), "text-anki-manual-csv-single-line.txt");
        addFieldToCurrentModel("Third");
        TextImporter i = new TextImporter(testCol, file);
        i.setAllowHtml(true);
        i.initMapping();
        i.run();
        Note n = testCol.getNote(testCol.getDb().queryLongScalar("select id from notes"));
        assertThat(Arrays.asList(n.getFields()), contains("foo bar", "bar baz", "baz quux"));
    }


    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    public void csvManualLineBreakExample() throws IOException {
        String file = Shared.getTestFilePath(getTestContext(), "text-anki-manual-csv-multi-line.txt");
        TextImporter i = new TextImporter(testCol, file);
        i.setAllowHtml(true);
        i.initMapping();
        i.run();
        Note n = testCol.getNote(testCol.getDb().queryLongScalar("select id from notes"));
        assertThat(Arrays.asList(n.getFields()), contains("hello", "this is\na two line answer"));
    }

    /**
     * Custom tests for AnkiDroid.
     */

    @Test
    public void testDupeIgnore() throws IOException, ImportExportException {
        // create a new empty deck
        String tmp = Shared.getTestFilePath(getTestContext(), "update1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        tmp = Shared.getTestFilePath(getTestContext(), "update3.apkg");
        imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        // there is a dupe, but it was ignored
        assertEquals(1, imp.getDupes());
        assertEquals(0, imp.getAdded());
        assertEquals(0, imp.getUpdated());
    }


    private void addFieldToCurrentModel(String fieldName) throws ConfirmModSchemaException {
        ModelManager mm = testCol.getModels();
        Model m = mm.current();
        JSONObject f = mm.newField(fieldName);
        mm.addField(m, f);
        mm.save(m);
    }
}
