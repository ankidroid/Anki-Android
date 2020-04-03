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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.anki.tests.Shared;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.importer.Anki2Importer;
import com.ichi2.libanki.importer.AnkiPackageImporter;
import com.ichi2.libanki.importer.Importer;

import com.ichi2.utils.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("deprecation")
@RunWith(androidx.test.runner.AndroidJUnit4.class)
public class ImportTest {

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
        testCol = Shared.getEmptyCol(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void tearDown() {
        testCol.close();
    }

    @Test
    public void testAnki2Mediadupes() throws IOException, JSONException, ImportExportException {
        List<String> expected;
        List<String> actual;

        // add a note that references a sound
        Note n = testCol.newNote();
        n.setItem("Front", "[sound:foo.mp3]");
        long mid = n.model().getLong("id");
        testCol.addNote(n);
        // add that sound to the media folder
        FileOutputStream os;
        os = new FileOutputStream(new File(testCol.getMedia().dir(), "foo.mp3"), false);
        os.write("foo".getBytes());
        os.close();
        testCol.close();
        // it should be imported correctly into an empty deck
        Collection empty = Shared.getEmptyCol(InstrumentationRegistry.getInstrumentation().getTargetContext());
        Importer imp = new Anki2Importer(empty, testCol.getPath());
        imp.run();
        expected = Collections.singletonList("foo.mp3");
        actual = Arrays.asList(new File(empty.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        // and importing again will not duplicate, as the file content matches
        empty.remCards(Utils.arrayList2array(empty.getDb().queryColumn(Long.class, "select id from cards", 0)));
        imp = new Anki2Importer(empty, testCol.getPath());
        imp.run();
        expected = Collections.singletonList("foo.mp3");
        actual = Arrays.asList(new File(empty.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        n = empty.getNote(empty.getDb().queryLongScalar("select id from notes"));
        assertTrue(n.getFields()[0].contains("foo.mp3"));
        // if the local file content is different, and import should trigger a rename
        empty.remCards(Utils.arrayList2array(empty.getDb().queryColumn(Long.class, "select id from cards", 0)));
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
        empty.remCards(Utils.arrayList2array(empty.getDb().queryColumn(Long.class, "select id from cards", 0)));
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
        List<String> expected;
        List<String> actual;

        String apkg = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "media.apkg");
        Importer imp = new AnkiPackageImporter(testCol, apkg);
        expected = Collections.emptyList();
        actual = Arrays.asList(new File(testCol.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(actual.size(), expected.size());
        imp.run();
        expected = Collections.singletonList("foo.wav");
        actual = Arrays.asList(new File(testCol.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        // import again should be idempotent in terms of media
        testCol.remCards(Utils.arrayList2array(testCol.getDb().queryColumn(Long.class, "select id from cards", 0)));
        imp = new AnkiPackageImporter(testCol, apkg);
        imp.run();
        expected = Collections.singletonList("foo.wav");
        actual = Arrays.asList(new File(testCol.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        // but if the local file has different data, it will rename
        testCol.remCards(Utils.arrayList2array(testCol.getDb().queryColumn(Long.class, "select id from cards", 0)));
        FileOutputStream os;
        os = new FileOutputStream(new File(testCol.getMedia().dir(), "foo.wav"), false);
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
        String tmp = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "diffmodeltemplates-1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(testCol, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        // then the version with updated template
        tmp = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "diffmodeltemplates-2.apkg");
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
        String tmp = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "update1.apkg");
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
        tmp = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "update2.apkg");
        imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        assertEquals(1, imp.getDupes());
        assertEquals(0, imp.getAdded());
        assertEquals(1, imp.getUpdated());
        assertTrue(testCol.getDb().queryString("select flds from notes").startsWith("goodbye"));
    }

    // Exchange @Suppress for @Test when csv importer is implemented
//    @Suppress
//    public void testCsv() throws IOException {
//        String file = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "text-2fields.txt");
//        TextImporter i = new TextImporter(testCol, file);
//        i.initMapping();
//        i.run();
//        // four problems - too many & too few fields, a missing front, and a
//        // duplicate entry
//        assertTrue(i.getLog().size() == 5);
//        assertTrue(i.getTotal() == 5);
//        // if we run the import again, it should update instead
//        i.run();
//        assertTrue(i.getLog().size() == 10);
//        assertTrue(i.getTotal() == 5);
//        // but importing should not clobber tags if they're unmapped
//        Note n = testCol.getNote(testCol.getDb().queryLongScalar("select id from notes"));
//        n.addTag("test");
//        n.flush();
//        i.run();
//        n.load();
//        assertTrue((n.getTags().size() == 1) && (n.getTags().get(0) == "test"));
//        // if add-only mode, count will be 0
//        i.setImportMode(1);
//        i.run();
//        assertTrue(i.getTotal() == 0);
//        // and if dupes mode, will reimport everything
//        assertTrue(testCol.cardCount() == 5);
//        i.setImportMode(2);
//        i.run();
//        // includes repeated field
//        assertTrue(i.getTotal() == 6);
//        assertTrue(testCol.cardCount() == 11);
//    }
//
//    // Exchange @Suppress for @Test when csv importer is implemented
//    @Suppress
//    public void testCsv2() throws  IOException, ConfirmModSchemaException {
//        Models mm = testCol.getModels();
//        JSONObject m = mm.current();
//        JSONObject f = mm.newField("Three");
//        mm.addField(m, f);
//        mm.save(m);
//        Note n = deck.newNote();
//        n.setItem("Front", "1");
//        n.setItem("Back", "2");
//        n.setItem("Three", "3");
//        testCol.addNote(n);
//        // an update with unmapped fields should not clobber those fields
//        String file = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "text-update.txt");
//        TextImporter i = new TextImporter(testCol, file);
//        i.initMapping();
//        i.run();
//        n.load();
//        assertTrue("1".equals(n.getItem("Front")));
//        assertTrue("x".equals(n.getItem("Back")));
//        assertTrue("3".equals(n.getItem("Three")));
//    }

    /**
     * Custom tests for AnkiDroid.
     */

    @Test
    public void testDupeIgnore() throws IOException, ImportExportException {
        // create a new empty deck
        String tmp = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "update1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        tmp = Shared.getTestFilePath(InstrumentationRegistry.getInstrumentation().getTargetContext(), "update3.apkg");
        imp = new AnkiPackageImporter(testCol, tmp);
        imp.run();
        // there is a dupe, but it was ignored
        assertEquals(1, imp.getDupes());
        assertEquals(0, imp.getAdded());
        assertEquals(0, imp.getUpdated());
    }
}
