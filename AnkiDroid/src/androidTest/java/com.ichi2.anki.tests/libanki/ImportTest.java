/****************************************************************************************
 * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.Suppress;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.tests.Shared;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.importer.Anki2Importer;
import com.ichi2.libanki.importer.AnkiPackageImporter;
import com.ichi2.libanki.importer.Importer;
import com.ichi2.libanki.importer.TextImporter;

import org.json.JSONException;
import org.json.JSONObject;
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

@RunWith(AndroidJUnit4.class)
public class ImportTest {

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
    public RetryRule retry = new RetryRule(3);

    @Test
    public void testAnki2Mediadupes() throws IOException, JSONException {
        List<String> actual;

        // Build a temporary collection with one note, containing a sound file
        Collection tmpCol = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        Note n = tmpCol.newNote();
        n.setItem("Front", "[sound:foo.mp3]");
        long mid = n.model().getLong("id");
        tmpCol.addNote(n);
        // add that sound to the media folder
        FileOutputStream out = new FileOutputStream(new File(tmpCol.getMedia().dir(), "foo.mp3"), false);
        out.write("foo".getBytes());
        out.close();
        tmpCol.close();

        // it should be imported correctly into an empty deck
        Collection emptyCol = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        new Anki2Importer(emptyCol, tmpCol.getPath()).run();
        actual = Arrays.asList(new File(emptyCol.getMedia().dir()).list());
        assertEquals(1, actual.size());

        // and importing again will not duplicate, as the file content matches
        emptyCol.remCards(Utils.arrayList2array(emptyCol.getDb().queryColumn(Long.class, "select id from cards", 0)));
        new Anki2Importer(emptyCol, tmpCol.getPath()).run();
        actual = Arrays.asList(new File(emptyCol.getMedia().dir()).list());
        assertEquals(1, actual.size());
        n = emptyCol.getNote(emptyCol.getDb().queryLongScalar("select id from notes"));
        assertEquals("[sound:foo.mp3]", n.getFields()[0]);

        // if the local file content is different, and import should trigger a rename
        emptyCol.remCards(Utils.arrayList2array(emptyCol.getDb().queryColumn(Long.class, "select id from cards", 0)));
        out = new FileOutputStream(new File(emptyCol.getMedia().dir(), "foo.mp3"), false);
        out.write("bar".getBytes());
        out.close();
        new Anki2Importer(emptyCol, tmpCol.getPath()).run();
        actual = Arrays.asList(new File(emptyCol.getMedia().dir()).list());
        assertEquals(2, actual.size());
        n = emptyCol.getNote(emptyCol.getDb().queryLongScalar("select id from notes"));
        assertEquals(String.format("[sound:foo_%s.mp3]", mid), n.getFields()[0]);

        // if the localized media file already exists, we rewrite the note and media
        emptyCol.remCards(Utils.arrayList2array(emptyCol.getDb().queryColumn(Long.class, "select id from cards", 0)));
        out = new FileOutputStream(new File(emptyCol.getMedia().dir(), "foo.mp3"));
        out.write("bar".getBytes());
        out.close();
        new Anki2Importer(emptyCol, tmpCol.getPath()).run();
        actual = Arrays.asList(new File(emptyCol.getMedia().dir()).list());
        assertEquals(2, actual.size());
        n = emptyCol.getNote(emptyCol.getDb().queryLongScalar("select id from notes"));
        assertEquals(String.format("[sound:foo_%s.mp3]", mid), n.getFields()[0]);
    }

    @Test
    public void testApkg() throws IOException {
        List<String> expected;
        List<String> actual;

        Collection tmp = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        String apkg = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "media.apkg");
        Importer imp = new AnkiPackageImporter(tmp, apkg);
        expected = Collections.emptyList();
        actual = Arrays.asList(new File(tmp.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(actual.size(), expected.size());
        imp.run();
        expected = Collections.singletonList("foo.wav");
        actual = Arrays.asList(new File(tmp.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        // import again should be idempotent in terms of media
        tmp.remCards(Utils.arrayList2array(tmp.getDb().queryColumn(Long.class, "select id from cards", 0)));
        imp = new AnkiPackageImporter(tmp, apkg);
        imp.run();
        expected = Collections.singletonList("foo.wav");
        actual = Arrays.asList(new File(tmp.getMedia().dir()).list());
        actual.retainAll(expected);
        assertEquals(actual.size(), expected.size());
        // but if the local file has different data, it will rename
        tmp.remCards(Utils.arrayList2array(tmp.getDb().queryColumn(Long.class, "select id from cards", 0)));
        FileOutputStream os;
        os = new FileOutputStream(new File(tmp.getMedia().dir(), "foo.wav"), false);
        os.write("xyz".getBytes());
        os.close();
        imp = new AnkiPackageImporter(tmp, apkg);
        imp.run();
        assertTrue(new File(tmp.getMedia().dir()).list().length == 2);
    }

    @Test
    public void testAnki2Diffmodels() throws IOException {
        // create a new empty deck
        Collection dst = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        // import the 1 card version of the model
        String tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "diffmodels2-1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(dst, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        int before = dst.noteCount();
        // repeating the process should do nothing
        imp = new AnkiPackageImporter(dst, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        assertTrue(before == dst.noteCount());
        // then the 2 card version
        tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "diffmodels2-2.apkg");
        imp = new AnkiPackageImporter(dst, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        int after = dst.noteCount();
        // as the model schemas differ, should have been imported as new model
        assertTrue(after == before + 1);
        // and the new model should have both cards
        assertTrue(dst.cardCount() == 3);
        // repeating the process should do nothing
        imp = new AnkiPackageImporter(dst, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        after = dst.noteCount();
        assertTrue(after == before + 1);
        assertTrue(dst.cardCount() == 3);
    }

    @Test
    public void testAnki2DiffmodelTemplates() throws IOException, JSONException {
        // different from the above as this one tests only the template text being
        // changed, not the number of cards/fields
        Collection dst = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        // import the first version of the model
        String tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "diffmodeltemplates-1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(dst, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        // then the version with updated template
        tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "diffmodeltemplates-2.apkg");
        imp = new AnkiPackageImporter(dst, tmp);
        imp.setDupeOnSchemaChange(true);
        imp.run();
        // collection should contain the note we imported
        assertTrue(dst.noteCount() == 1);
        // the front template should contain the text added in the 2nd package
        Long tcid = dst.findCards("").get(0);
        Note tnote = dst.getCard(tcid).note();
        assertTrue(dst.findTemplates(tnote).get(0).getString("qfmt").contains("Changed Front Template"));
    }

    @Test
    public void testAnki2Updates() throws IOException {
        // create a new empty deck
        Collection dst = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        String tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "update1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(dst, tmp);
        imp.run();
        assertTrue(imp.getDupes() == 0);
        assertTrue(imp.getAdded() == 1);
        assertTrue(imp.getUpdated() == 0);
        // importing again should be idempotent
        imp = new AnkiPackageImporter(dst, tmp);
        imp.run();
        assertTrue(imp.getDupes() == 1);
        assertTrue(imp.getAdded() == 0);
        assertTrue(imp.getUpdated() == 0);
        // importing a newer note should update
        assertTrue(dst.noteCount() == 1);
        assertTrue(dst.getDb().queryString("select flds from notes").startsWith("hello"));
        tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "update2.apkg");
        imp = new AnkiPackageImporter(dst, tmp);
        imp.run();
        assertTrue(imp.getDupes()== 1);
        assertTrue(imp.getAdded() == 0);
        assertTrue(imp.getUpdated() == 1);
        assertTrue(dst.getDb().queryString("select flds from notes").startsWith("goodbye"));
    }

    // Remove @Suppress when csv importer is implemented
    @Suppress
    public void testCsv() throws IOException {
        Collection deck = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        String file = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "text-2fields.txt");
        TextImporter i = new TextImporter(deck, file);
        i.initMapping();
        i.run();
        // four problems - too many & too few fields, a missing front, and a
        // duplicate entry
        assertTrue(i.getLog().size() == 5);
        assertTrue(i.getTotal() == 5);
        // if we run the import again, it should update instead
        i.run();
        assertTrue(i.getLog().size() == 10);
        assertTrue(i.getTotal() == 5);
        // but importing should not clobber tags if they're unmapped
        Note n = deck.getNote(deck.getDb().queryLongScalar("select id from notes"));
        n.addTag("test");
        n.flush();
        i.run();
        n.load();
        assertTrue((n.getTags().size() == 1) && (n.getTags().get(0).equals("test")));
        // if add-only mode, count will be 0
        i.setImportMode(1);
        i.run();
        assertTrue(i.getTotal() == 0);
        // and if dupes mode, will reimport everything
        assertTrue(deck.cardCount() == 5);
        i.setImportMode(2);
        i.run();
        // includes repeated field
        assertTrue(i.getTotal() == 6);
        assertTrue(deck.cardCount() == 11);
        deck.close();
    }

    // Remove @Suppress when csv importer is implemented
    @Suppress
    public void testCsv2() throws  IOException, ConfirmModSchemaException {
        Collection deck = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        Models mm = deck.getModels();
        JSONObject m = mm.current();
        JSONObject f = mm.newField("Three");
        mm.addField(m, f);
        mm.save(m);
        Note n = deck.newNote();
        n.setItem("Front", "1");
        n.setItem("Back", "2");
        n.setItem("Three", "3");
        deck.addNote(n);
        // an update with unmapped fields should not clobber those fields
        String file = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "text-update.txt");
        TextImporter i = new TextImporter(deck, file);
        i.initMapping();
        i.run();
        n.load();
        assertTrue(n.getItem("Front").equals("1"));
        assertTrue(n.getItem("Back").equals("x"));
        assertTrue(n.getItem("Three").equals("3"));
        deck.close();
    }

    /**
     * Custom tests for AnkiDroid.
     */
    @Test
    public void testDupeIgnore() throws IOException {
        // create a new empty deck
        Collection dst = Shared.getEmptyCol(InstrumentationRegistry.getTargetContext());
        String tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "update1.apkg");
        AnkiPackageImporter imp = new AnkiPackageImporter(dst, tmp);
        imp.run();
        tmp = Shared.getTestFilePath(InstrumentationRegistry.getTargetContext(), "update3.apkg");
        imp = new AnkiPackageImporter(dst, tmp);
        imp.run();
        // there is a dupe, but it was ignored
        assertTrue(imp.getDupes() == 1);
        assertTrue(imp.getAdded() == 0);
        assertTrue(imp.getUpdated() == 0);
    }
}
