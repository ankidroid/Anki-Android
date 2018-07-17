/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2015 Frank Oltmanns <frank.oltmanns@gmail.com>                         *
 * Copyright (c) 2015 Timothy Rae <timothy.rae@gmail.com>                               *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
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

package com.ichi2.anki.tests;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.AndroidTestCase;
import android.util.Log;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for {@link com.ichi2.anki.provider.CardContentProvider}.
 * <p/>
 * These tests should cover all supported operations for each URI.
 */
@RunWith(AndroidJUnit4.class)
public class ContentProviderTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    com.ichi2.anki.Manifest.permission.READ_WRITE_DATABASE);



    private static final String BASIC_MODEL_NAME = "com.ichi2.anki.provider.test.basic.x94oa3F";
    private static final String TEST_FIELD_NAME = "TestFieldName";
    private static final String TEST_FIELD_VALUE = "test field value";
    private static final String TEST_TAG = "aldskfhewjklhfczmxkjshf";
    private static final String[] TEST_DECKS = {"cmxieunwoogyxsctnjmv"
                                                ,"sstuljxgmfdyugiujyhq"
                                                ,"pdsqoelhmemmmbwjunnu"
                                                ,"scxipjiyozczaaczoawo"};
    private static final String TEST_MODEL_NAME = "com.ichi2.anki.provider.test.a1x6h9l";
    private static final String[] TEST_MODEL_FIELDS = {"FRONTS","BACK"};
    private static final String[] TEST_MODEL_CARDS = {"cArD1", "caRD2"};
    private static final String[] TEST_MODEL_QFMT = {"{{FRONTS}}", "{{BACK}}"};
    private static final String[] TEST_MODEL_AFMT = {"{{BACK}}", "{{FRONTS}}"};
    private static final String[] TEST_NOTE_FIELDS = {"dis is za Fr0nt", "Te$t"};
    private static final String TEST_MODEL_CSS = "styleeeee";

    private int mNumDecksBeforeTest;
    private long[] mTestDeckIds = new long[TEST_DECKS.length];
    private ArrayList<Uri> mCreatedNotes;
    private long mModelId = 0;
    private String[] mDummyFields = new String[1];
    /**
     * Initially create one note for each model.
     */
    @Before
    public void setUp() throws Exception {
        Log.i(AnkiDroidApp.TAG, "setUp()");
        mCreatedNotes = new ArrayList<>();
        final Collection col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        // Add a new basic model that we use for testing purposes (existing models could potentially be corrupted)
        JSONObject model = Models.addBasicModel(col, BASIC_MODEL_NAME);
        mModelId = model.getLong("id");
        ArrayList<String> flds = col.getModels().fieldNames(model);
        // Use the names of the fields as test values for the notes which will be added
        mDummyFields = flds.toArray(new String[flds.size()]);
        // create test decks and add one note for every deck
        mNumDecksBeforeTest = col.getDecks().count();
        for(int i = 0; i < TEST_DECKS.length; i++) {
            long did = col.getDecks().id(TEST_DECKS[i]);
            mTestDeckIds[i] = did;
            mCreatedNotes.add(setupNewNote(col, mModelId, did, mDummyFields, TEST_TAG));
        }
        // Add a note to the default deck as well so that testQueryNextCard() works
        mCreatedNotes.add(setupNewNote(col, mModelId, 1, mDummyFields, TEST_TAG));
    }

    private static Uri setupNewNote(Collection col, long mid, long did, String[] flds, String tag) {
        Note newNote = new Note(col, col.getModels().get(mid));
        for (int idx=0; idx < flds.length; idx++) {
            newNote.setField(idx, flds[idx]);
        }
        newNote.addTag(tag);
        assertTrue("At least one card added for note", col.addNote(newNote) >= 1);
        for (Card c: newNote.cards()) {
            c.setDid(did);
            c.flush();
        }
        return Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(newNote.getId()));
    }

    /**
     * Remove the notes and decks created in setUp().
     */
    @After
    public void tearDown() throws Exception {
        Log.i(AnkiDroidApp.TAG, "tearDown()");
        final Collection col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        // Delete all notes
        List<Long> remnantNotes = col.findNotes("tag:" + TEST_TAG);
        if (remnantNotes.size() > 0) {
            long[] nids = new long[remnantNotes.size()];
            for (int i = 0; i < remnantNotes.size(); i++) {
                nids[i] = remnantNotes.get(i);
            }
            col.remNotes(nids);
            col.save();
            assertEquals("Check that remnant notes have been deleted", 0, col.findNotes("tag:" + TEST_TAG).size());
        }
        // delete test decks
        for(long did : mTestDeckIds) {
            col.getDecks().rem(did, true);
        }
        col.getDecks().flush();
        assertEquals("Check that all created decks have been deleted", mNumDecksBeforeTest, col.getDecks().count());
        // Delete test model
        col.modSchema(false);
        col.getModels().rem(col.getModels().get(mModelId));
    }


    /**
     * Check that inserting and removing a note into default deck works as expected
     */
    @Test
    public void testInsertAndRemoveNote() throws Exception {
        // Get required objects for test
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        // Add the note
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.Note.MID, mModelId);
        values.put(FlashCardsContract.Note.FLDS, Utils.joinFields(TEST_NOTE_FIELDS));
        values.put(FlashCardsContract.Note.TAGS, TEST_TAG);
        Uri newNoteUri = cr.insert(FlashCardsContract.Note.CONTENT_URI, values);
        assertNotNull("Check that URI returned from addNewNote is not null", newNoteUri);
        final Collection col = reopenCol();  // test that the changes are physically saved to the DB
        // Check that it looks as expected
        Note addedNote = new Note(col, Long.parseLong(newNoteUri.getLastPathSegment()));
        addedNote.load();
        assertTrue("Check that fields were set correctly", Arrays.equals(addedNote.getFields(), TEST_NOTE_FIELDS));
        assertEquals("Check that tag was set correctly", TEST_TAG, addedNote.getTags().get(0));
        int expectedNumCards = col.getModels().get(mModelId).getJSONArray("tmpls").length();
        assertEquals("Check that correct number of cards generated", expectedNumCards, addedNote.cards().size());
        // Now delete the note
        cr.delete(newNoteUri, null, null);
        try {
            addedNote.load();
            fail("Expected RuntimeException to be thrown when deleting note");
        } catch (RuntimeException e) {
            // Expect RuntimeException to be thrown when loading deleted note
        }
    }

    /**
     * Check that inserting and removing a note into default deck works as expected
     */
    @Test
    public void testInsertTemplate() throws Exception {
        // Get required objects for test
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        Collection col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        // Add a new basic model that we use for testing purposes (existing models could potentially be corrupted)
        JSONObject model = Models.addBasicModel(col, BASIC_MODEL_NAME);
        long modelId = model.getLong("id");
        // Add the note
        Uri modelUri = ContentUris.withAppendedId(FlashCardsContract.Model.CONTENT_URI, modelId);
        int testIndex = TEST_MODEL_CARDS.length - 1; // choose the last one because not the same as the basic model template
        int expectedOrd = model.getJSONArray("tmpls").length();
        ContentValues cv = new ContentValues();
        cv.put(FlashCardsContract.CardTemplate.NAME, TEST_MODEL_CARDS[testIndex]);
        cv.put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, TEST_MODEL_QFMT[testIndex]);
        cv.put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, TEST_MODEL_AFMT[testIndex]);
        cv.put(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT, TEST_MODEL_QFMT[testIndex]);
        cv.put(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT, TEST_MODEL_AFMT[testIndex]);
        Uri templatesUri = Uri.withAppendedPath(modelUri, "templates");
        Uri templateUri = cr.insert(templatesUri, cv);
        col = reopenCol();  // test that the changes are physically saved to the DB
        assertNotNull("Check template uri", templateUri);
        assertEquals("Check template uri ord", expectedOrd, ContentUris.parseId(templateUri));
        JSONObject template = col.getModels().get(modelId).getJSONArray("tmpls").getJSONObject(expectedOrd);
        assertEquals("Check template JSONObject ord", expectedOrd, template.getInt("ord"));
        assertEquals("Check template name", TEST_MODEL_CARDS[testIndex], template.getString("name"));
        assertEquals("Check qfmt", TEST_MODEL_QFMT[testIndex], template.getString("qfmt"));
        assertEquals("Check afmt", TEST_MODEL_AFMT[testIndex], template.getString("afmt"));
        assertEquals("Check bqfmt", TEST_MODEL_QFMT[testIndex], template.getString("bqfmt"));
        assertEquals("Check bafmt", TEST_MODEL_AFMT[testIndex], template.getString("bafmt"));
        col.getModels().rem(model);
    }

    /**
     * Check that inserting and removing a note into default deck works as expected
     */
    @Test
    public void testInsertField() throws Exception {
        // Get required objects for test
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        Collection col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        JSONObject model = Models.addBasicModel(col, BASIC_MODEL_NAME);
        long modelId = model.getLong("id");
        JSONArray initialFldsArr = model.getJSONArray("flds");
        int initialFieldCount = initialFldsArr.length();
        Uri noteTypeUri = ContentUris.withAppendedId(FlashCardsContract.Model.CONTENT_URI, modelId);
        ContentValues insertFieldValues = new ContentValues();
        insertFieldValues.put(FlashCardsContract.Model.FIELD_NAME, TEST_FIELD_NAME);
        Uri fieldUri = cr.insert(Uri.withAppendedPath(noteTypeUri, "fields"), insertFieldValues);
        assertNotNull("Check field uri", fieldUri);
        // Ensure that the changes are physically saved to the DB
        col = reopenCol();
        model = col.getModels().get(modelId);
        // Test the field is as expected
        long fieldId = ContentUris.parseId(fieldUri);
        assertEquals("Check field id", initialFieldCount, fieldId);
        JSONArray fldsArr = model.getJSONArray("flds");
        assertEquals("Check fields length", initialFieldCount + 1, fldsArr.length());
        assertEquals("Check last field name", TEST_FIELD_NAME, fldsArr.getJSONObject(fldsArr.length() - 1).optString("name", ""));
        col.getModels().rem(model);
    }

    /**
     * Test queries to notes table using direct SQL URI
     */
    @Test
    public void testQueryDirectSqlQuery() {
        // search for correct mid
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI_V2, null, String.format("mid=%d", mModelId), null, null);
        assertNotNull(cursor);
        try {
            assertEquals("Check number of results", mCreatedNotes.size(), cursor.getCount());
        } finally {
            cursor.close();
        }
        // search for bogus mid
        cursor = cr.query(FlashCardsContract.Note.CONTENT_URI_V2, null, "mid=0", null, null);
        assertNotNull(cursor);
        try {
            assertEquals("Check number of results", 0, cursor.getCount());
        } finally {
            cursor.close();
        }
        // check usage of selection args
        cursor = cr.query(FlashCardsContract.Note.CONTENT_URI_V2, null, "mid=?", new String[]{"0"}, null);
        assertNotNull(cursor);
    }

    /**
     * Test that a query for all the notes added in setup() looks correct
     */
    @Test
    public void testQueryNoteIds() {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        // Query all available notes
        final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:" + TEST_TAG, null, null);
        assertNotNull(allNotesCursor);
        try {
            assertEquals("Check number of results", mCreatedNotes.size(), allNotesCursor.getCount());
            while (allNotesCursor.moveToNext()) {
                // Check that it's possible to leave out columns from the projection
                for (int i = 0; i < FlashCardsContract.Note.DEFAULT_PROJECTION.length; i++) {
                    String[] projection = removeFromProjection(FlashCardsContract.Note.DEFAULT_PROJECTION, i);
                    String noteId = allNotesCursor.getString(allNotesCursor.getColumnIndex(FlashCardsContract.Note._ID));
                    Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId);
                    final Cursor singleNoteCursor = cr.query(noteUri, projection, null, null, null);
                    assertNotNull("Check that there is a valid cursor for detail data", singleNoteCursor);
                    try {
                        assertEquals("Check that there is exactly one result", 1, singleNoteCursor.getCount());
                        assertTrue("Move to beginning of cursor after querying for detail data", singleNoteCursor.moveToFirst());
                        // Check columns
                        assertEquals("Check column count", projection.length, singleNoteCursor.getColumnCount());
                        for (int j = 0; j < projection.length; j++) {
                            assertEquals("Check column name " + j, projection[j], singleNoteCursor.getColumnName(j));
                        }
                    } finally {
                        singleNoteCursor.close();
                    }
                }
            }
        } finally {
            allNotesCursor.close();
        }
    }

    /**
     * Check that a valid Cursor is returned when querying notes table with non-default projections
     */
    @Test
    public void testQueryNotesProjection() {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        // Query all available notes
        for (int i = 0; i < FlashCardsContract.Note.DEFAULT_PROJECTION.length; i++) {
            String[] projection = removeFromProjection(FlashCardsContract.Note.DEFAULT_PROJECTION, i);
            final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, projection, "tag:" + TEST_TAG, null, null);
            assertNotNull("Check that there is a valid cursor", allNotesCursor);
            try {
                assertEquals("Check number of results", mCreatedNotes.size(), allNotesCursor.getCount());
                // Check columns
                assertEquals("Check column count", projection.length, allNotesCursor.getColumnCount());
                for (int j = 0; j < projection.length; j++) {
                    assertEquals("Check column name " + j, projection[j], allNotesCursor.getColumnName(j));
                }
            } finally {
                allNotesCursor.close();
            }
        }
    }

    private String[] removeFromProjection(String[] inputProjection, int idx) {
        String[] outputProjection = new String[inputProjection.length - 1];
        for (int i = 0; i < idx; i++) {
            outputProjection[i] = inputProjection[i];
        }
        for (int i = idx + 1; i < inputProjection.length; i++) {
            outputProjection[i - 1] = inputProjection[i];
        }
        return outputProjection;
    }


    /**
     * Check that updating the flds column works as expected
     */
    @Test
    public void testUpdateNoteFields() {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        ContentValues cv = new ContentValues();
        // Change the fields so that the first field is now "newTestValue"
        String[] dummyFields2 = mDummyFields.clone();
        dummyFields2[0] = TEST_FIELD_VALUE;
        for (Uri uri: mCreatedNotes) {
            // Update the flds
            cv.put(FlashCardsContract.Note.FLDS, Utils.joinFields(dummyFields2));
            cr.update(uri, cv, null, null);
            // Query the table again
            Cursor noteCursor = cr.query(uri, FlashCardsContract.Note.DEFAULT_PROJECTION, null, null, null);
            try {
                assertNotNull("Check that there is a valid cursor for detail data after update", noteCursor);
                assertEquals("Check that there is one and only one entry after update", 1, noteCursor.getCount());
                assertTrue("Move to first item in cursor", noteCursor.moveToFirst());
                String[] newFlds = Utils.splitFields(
                        noteCursor.getString(noteCursor.getColumnIndex(FlashCardsContract.Note.FLDS)));
                assertTrue("Check that the flds have been updated correctly", Arrays.equals(newFlds, dummyFields2));
            } finally {
                noteCursor.close();
            }
        }
    }


    /**
     * Check that inserting a new model works as expected
     */
    @Test
    public void testInsertAndUpdateModel() throws Exception {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        ContentValues cv = new ContentValues();
        // Insert a new model
        cv.put(FlashCardsContract.Model.NAME, TEST_MODEL_NAME);
        cv.put(FlashCardsContract.Model.FIELD_NAMES, Utils.joinFields(TEST_MODEL_FIELDS));
        cv.put(FlashCardsContract.Model.NUM_CARDS, TEST_MODEL_CARDS.length);
        Uri modelUri = cr.insert(FlashCardsContract.Model.CONTENT_URI, cv);
        assertNotNull("Check inserted model isn't null", modelUri);
        long mid = Long.parseLong(modelUri.getLastPathSegment());
        Collection col = reopenCol();
        try {
            JSONObject model = col.getModels().get(mid);
            assertEquals("Check model name", TEST_MODEL_NAME, model.getString("name"));
            assertEquals("Check templates length", TEST_MODEL_CARDS.length, model.getJSONArray("tmpls").length());
            assertEquals("Check field length", TEST_MODEL_FIELDS.length, model.getJSONArray("flds").length());
            JSONArray flds = model.getJSONArray("flds");
            for (int i = 0; i < flds.length(); i++) {
                assertEquals("Check name of fields", flds.getJSONObject(i).getString("name"), TEST_MODEL_FIELDS[i]);
            }
            // Test updating the model CSS (to test updating MODELS_ID Uri)
            cv = new ContentValues();
            cv.put(FlashCardsContract.Model.CSS, TEST_MODEL_CSS);
            assertTrue(cr.update(modelUri, cv, null, null) > 0);
            col = reopenCol();
            assertEquals("Check css", TEST_MODEL_CSS, col.getModels().get(mid).getString("css"));
            // Update each of the templates in model (to test updating MODELS_ID_TEMPLATES_ID Uri)
            for (int i = 0; i < TEST_MODEL_CARDS.length; i++) {
                cv = new ContentValues();
                cv.put(FlashCardsContract.CardTemplate.NAME, TEST_MODEL_CARDS[i]);
                cv.put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, TEST_MODEL_QFMT[i]);
                cv.put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, TEST_MODEL_AFMT[i]);
                cv.put(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT, TEST_MODEL_QFMT[i]);
                cv.put(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT, TEST_MODEL_AFMT[i]);
                Uri tmplUri = Uri.withAppendedPath(Uri.withAppendedPath(modelUri, "templates"), Integer.toString(i));
                assertTrue("Update rows", cr.update(tmplUri, cv, null, null) > 0);
                col = reopenCol();
                JSONObject template = col.getModels().get(mid).getJSONArray("tmpls").getJSONObject(i);
                assertEquals("Check template name", TEST_MODEL_CARDS[i], template.getString("name"));
                assertEquals("Check qfmt", TEST_MODEL_QFMT[i], template.getString("qfmt"));
                assertEquals("Check afmt", TEST_MODEL_AFMT[i], template.getString("afmt"));
                assertEquals("Check bqfmt", TEST_MODEL_QFMT[i], template.getString("bqfmt"));
                assertEquals("Check bafmt", TEST_MODEL_AFMT[i], template.getString("bafmt"));
            }
        } finally {
            // Delete the model (this will force a full-sync)
            try {
                col.modSchema(false);
                col.getModels().rem(col.getModels().get(mid));
            } catch (ConfirmModSchemaException e) {
                // This will never happen
                throw new IllegalStateException("Unexpected ConfirmModSchemaException trying to remove model");
            }
        }
    }

    /**
     * Query .../models URI
     */
    @Test
    public void testQueryAllModels() {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        // Query all available models
        final Cursor allModels = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null);
        assertNotNull(allModels);
        try {
            assertTrue("Check that there is at least one result", allModels.getCount() > 0);
            while (allModels.moveToNext()) {
                long modelId = allModels.getLong(allModels.getColumnIndex(FlashCardsContract.Model._ID));
                Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
                final Cursor singleModel = cr.query(modelUri, null, null, null, null);
                assertNotNull(singleModel);
                try {
                    assertEquals("Check that there is exactly one result", 1, singleModel.getCount());
                    assertTrue("Move to beginning of cursor", singleModel.moveToFirst());
                    String nameFromModels = allModels.getString(allModels.getColumnIndex(FlashCardsContract.Model.NAME));
                    String nameFromModel = singleModel.getString(allModels.getColumnIndex(FlashCardsContract.Model.NAME));
                    assertEquals("Check that model names are the same", nameFromModel, nameFromModels);
                    String flds = allModels.getString(allModels.getColumnIndex(FlashCardsContract.Model.FIELD_NAMES));
                    assertTrue("Check that valid number of fields", Utils.splitFields(flds).length >= 1);
                    Integer numCards = allModels.getInt(allModels.getColumnIndex(FlashCardsContract.Model.NUM_CARDS));
                    assertTrue("Check that valid number of cards", numCards >= 1);
                } finally {
                    singleModel.close();
                }
            }
        } finally {
            allModels.close();
        }
    }

    /**
     * Move all the cards from their old decks to the first deck that was added in setup()
     */
    @Test
    public void testMoveCardsToOtherDeck() {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        // Query all available notes
        final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:" + TEST_TAG, null, null);
        assertNotNull(allNotesCursor);
        try {
            assertEquals("Check number of results", mCreatedNotes.size(), allNotesCursor.getCount());
            while (allNotesCursor.moveToNext()) {
                // Now iterate over all cursors
                Uri cardsUri = Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI,
                        allNotesCursor.getString(allNotesCursor.getColumnIndex(FlashCardsContract.Note._ID))), "cards");
                final Cursor cardsCursor = cr.query(cardsUri, null, null, null, null);
                assertNotNull("Check that there is a valid cursor after query for cards", cardsCursor);
                try {
                    assertTrue("Check that there is at least one result for cards", cardsCursor.getCount() > 0);
                    while (cardsCursor.moveToNext()) {
                        long targetDid = mTestDeckIds[0];
                        // Move to test deck (to test NOTES_ID_CARDS_ORD Uri)
                        ContentValues values = new ContentValues();
                        values.put(FlashCardsContract.Card.DECK_ID, targetDid);
                        Uri cardUri = Uri.withAppendedPath(cardsUri,
                                cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.CARD_ORD)));
                        cr.update(cardUri, values, null, null);
                        reopenCol();
                        Cursor movedCardCur = cr.query(cardUri, null, null, null, null);
                        assertNotNull("Check that there is a valid cursor after moving card", movedCardCur);
                        assertTrue("Move to beginning of cursor after moving card", movedCardCur.moveToFirst());
                        long did = movedCardCur.getLong(movedCardCur.getColumnIndex(FlashCardsContract.Card.DECK_ID));
                        assertEquals("Make sure that card is in new deck", targetDid, did);
                    }
                } finally {
                    cardsCursor.close();
                }
            }
        } finally {
            allNotesCursor.close();
        }
    }

    /**
     * Check that querying the current model gives a valid result
     */
    @Test
    public void testQueryCurrentModel() {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        Uri uri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, FlashCardsContract.Model.CURRENT_MODEL_ID);
        final Cursor modelCursor = cr.query(uri, null, null, null, null);
        assertNotNull(modelCursor);
        try {
            assertEquals("Check that there is exactly one result", 1, modelCursor.getCount());
            assertTrue("Move to beginning of cursor", modelCursor.moveToFirst());
            assertNotNull("Check non-empty field names",
                    modelCursor.getString(modelCursor.getColumnIndex(FlashCardsContract.Model.FIELD_NAMES)));
            assertTrue("Check at least one template",
                    modelCursor.getInt(modelCursor.getColumnIndex(FlashCardsContract.Model.NUM_CARDS)) > 0);
        } finally {
            modelCursor.close();
        }
    }

    /**
     * Check that an Exception is thrown when unsupported operations are performed
     */
    @Test
    public void testUnsupportedOperations() {
        final ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        ContentValues dummyValues = new ContentValues();
        Uri[] updateUris = {
                // Can't update most tables in bulk -- only via ID
                FlashCardsContract.Note.CONTENT_URI,
                FlashCardsContract.Model.CONTENT_URI,
                FlashCardsContract.Deck.CONTENT_ALL_URI,
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("cards")
                        .build(),
        };
        for (Uri uri : updateUris) {
            try {
                cr.update(uri, dummyValues, null, null);
                fail("Update on " + uri + " was supposed to throw exception");
            } catch (UnsupportedOperationException e) {
                // This was expected ...
            } catch (IllegalArgumentException e) {
                // ... or this.
            }
        }
        Uri[] deleteUris = {
                FlashCardsContract.Note.CONTENT_URI,
                // Only note/<id> is supported
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("cards")
                        .build(),
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("cards")
                        .appendPath("2345")
                        .build(),
                FlashCardsContract.Model.CONTENT_URI,
                FlashCardsContract.Model.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .build(),
        };
        for (Uri uri : deleteUris) {
            try {
                cr.delete(uri, null, null);
                fail("Delete on " + uri + " was supposed to throw exception");
            } catch (UnsupportedOperationException e) {
                // This was expected
            }
        }
        Uri[] insertUris = {
                // Can't do an insert with specific ID on the following tables
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .build(),
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("cards")
                        .build(),
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("cards")
                        .appendPath("2345")
                        .build(),
                FlashCardsContract.Model.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .build(),
        };
        for (Uri uri : insertUris) {
            try {
                cr.insert(uri, dummyValues);
                fail("Insert on " + uri + " was supposed to throw exception");
            } catch (UnsupportedOperationException e) {
                // This was expected ...
            } catch (IllegalArgumentException e) {
                // ... or this.
            }
        }
    }

    /**
     * Test query to decks table
     * @throws Exception
     */
    @Test
    public void testQueryAllDecks() throws Exception{
        Collection col;
        col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        Decks decks = col.getDecks();

        Cursor decksCursor = InstrumentationRegistry.getTargetContext().getContentResolver()
                .query(FlashCardsContract.Deck.CONTENT_ALL_URI, FlashCardsContract.Deck.DEFAULT_PROJECTION, null, null, null);

        assertNotNull(decksCursor);
        try {
            assertEquals("Check number of results", decks.count(), decksCursor.getCount());
            while (decksCursor.moveToNext()) {
                long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
                String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));

                JSONObject deck = decks.get(deckID);
                assertNotNull("Check that the deck we received actually exists", deck);
                assertEquals("Check that the received deck has the correct name", deck.getString("name"), deckName);
            }
        } finally {
            decksCursor.close();
        }
    }

    /**
     * Test query to specific deck ID
     * @throws Exception
     */
    @Test
    public void testQueryCertainDeck() throws Exception {
        Collection col;
        col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());

        long deckId = mTestDeckIds[0];
        Uri deckUri = Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, Long.toString(deckId));
        Cursor decksCursor = InstrumentationRegistry.getTargetContext().getContentResolver().query(deckUri, null, null, null, null);
        try {
            if (decksCursor == null || !decksCursor.moveToFirst()) {
                fail("No deck received. Should have delivered deck with id " + deckId);
            } else {
                long returnedDeckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
                String returnedDeckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));

                JSONObject realDeck = col.getDecks().get(deckId);
                assertEquals("Check that received deck ID equals real deck ID", deckId, returnedDeckID);
                assertEquals("Check that received deck name equals real deck name", realDeck.getString("name"), returnedDeckName);
            }
        } finally {
            decksCursor.close();
        }
    }

    /**
     * Test that query for the next card in the schedule returns a valid result without any deck selector
     */
    @Test
    public void testQueryNextCard(){
        Collection col;
        col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        Sched sched = col.getSched();

        Cursor reviewInfoCursor = InstrumentationRegistry.getTargetContext().getContentResolver().query(
                FlashCardsContract.ReviewInfo.CONTENT_URI, null, null, null, null);
        assertNotNull(reviewInfoCursor);
        assertEquals("Check that we actually received one card", 1, reviewInfoCursor.getCount());

        reviewInfoCursor.moveToFirst();
        int cardOrd = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD));
        long noteID = reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID));


        Card nextCard = null;
        for(int i = 0; i < 10; i++) {//minimizing fails, when sched.reset() randomly chooses between multiple cards
            sched.reset();
            nextCard = sched.getCard();
            if(nextCard.note().getId() == noteID && nextCard.getOrd() == cardOrd)break;
        }
        assertNotNull("Check that there actually is a next scheduled card", nextCard);
        assertEquals("Check that received card and actual card have same note id", nextCard.note().getId(), noteID);
        assertEquals("Check that received card and actual card have same card ord", nextCard.getOrd(), cardOrd);

    }

    /**
     * Test that query for the next card in the schedule returns a valid result WITH a deck selector
     */
    @Test
    public void testQueryCardFromCertainDeck(){
        long deckToTest = mTestDeckIds[0];
        String deckSelector = "deckID=?";
        String deckArguments[] = {Long.toString(deckToTest)};
        Collection col;
        col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        Sched sched = col.getSched();
        long selectedDeckBeforeTest = col.getDecks().selected();
        col.getDecks().select(1); //select Default deck

        Cursor reviewInfoCursor = InstrumentationRegistry.getTargetContext().getContentResolver().query(
                FlashCardsContract.ReviewInfo.CONTENT_URI, null, deckSelector, deckArguments, null);
        assertNotNull(reviewInfoCursor);
        assertEquals("Check that we actually received one card", 1, reviewInfoCursor.getCount());
        try {
            reviewInfoCursor.moveToFirst();
            int cardOrd = reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD));
            long noteID = reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID));
            assertEquals("Check that the selected deck has not changed", 1, col.getDecks().selected());

            col.getDecks().select(deckToTest);
            Card nextCard = null;
            for(int i = 0; i < 10; i++) {//minimizing fails, when sched.reset() randomly chooses between multiple cards
                sched.reset();
                nextCard = sched.getCard();
                if(nextCard.note().getId() == noteID && nextCard.getOrd() == cardOrd)break;
            }
            assertNotNull("Check that there actually is a next scheduled card", nextCard);
            assertEquals("Check that received card and actual card have same note id", nextCard.note().getId(), noteID);
            assertEquals("Check that received card and actual card have same card ord", nextCard.getOrd(), cardOrd);
        }finally {
            reviewInfoCursor.close();
        }

        col.getDecks().select(selectedDeckBeforeTest);
    }

    /**
     * Test changing the selected deck
     */
    @Test
    public void testSetSelectedDeck(){
        long deckId = mTestDeckIds[0];
        ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        Uri selectDeckUri = FlashCardsContract.Deck.CONTENT_SELECTED_URI;
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.Deck.DECK_ID, deckId);
        cr.update(selectDeckUri, values, null, null);
        Collection col = reopenCol();
        assertEquals("Check that the selected deck has been correctly set", deckId, col.getDecks().selected());
    }

    /**
     * Test giving the answer for a reviewed card
     */
    @Test
    public void testAnswerCard(){
        Collection col;
        col = CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
        long deckId = mTestDeckIds[0];
        col.getDecks().select(deckId);
        Card card = col.getSched().getCard();

        ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
        ContentValues values = new ContentValues();
        long noteId = card.note().getId();
        int cardOrd = card.getOrd();
        int ease = AbstractFlashcardViewer.EASE_3; //<- insert real ease here

        values.put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId);
        values.put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd);
        values.put(FlashCardsContract.ReviewInfo.EASE, ease);
        int updateCount = cr.update(reviewInfoUri, values, null, null);
        assertEquals("Check if update returns 1", 1, updateCount);
        col.getSched().reset();
        Card newCard = col.getSched().getCard();
        if(newCard != null){
            if(newCard.note().getId() == card.note().getId() && newCard.getOrd() == card.getOrd()){
                fail("Next scheduled card has not changed");
            }
        }else{
            //We expected this
        }
    }

    private Collection reopenCol() {
        CollectionHelper.getInstance().closeCollection(false);
        return CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext());
    }

}
