/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2015 Frank Oltmanns <frank.oltmanns@gmail.com>                         *
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.util.Log;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.provider.FlashCardsContract;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Sched;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Test cases for {@link com.ichi2.anki.provider.CardContentProvider}.
 * <p/>
 * These tests should cover all supported operations for each URI.
 */
public class ContentProviderTest extends AndroidTestCase {

    private static final String TEST_FIELD_VALUE = "test field value";
    private static final String TEST_TAG = "aldskfhewjklhfczmxkjshf";
    private static final String TEST_TAG_2 = "hlawiejfglaksjdfliwueu";
    private static final String TEST_DECK = "glekrjterglknsdfflkgj";
    private static final String[] TEST_DECKS = {"cmxieunwoogyxsctnjmv"
                                                ,"sstuljxgmfdyugiujyhq"
                                                ,"pdsqoelhmemmmbwjunnu"
                                                ,"scxipjiyozczaaczoawo"
                                                ,"srwcdmseymjeliacsaas"
                                                ,"iuzqjlqejtyanluroajl"
                                                ,"qbcmsoghvbklgrfyinqh"
                                                ,"safinsysttgwkhclgwks"
                                                ,"lcgmprhkkdrgydscoseo"
                                                ,"ejrzzvpwtremcgmbnnjh" };

    private int mCreatedNotes;
    private int numDecksBeforeTest;
    private long[] testDeckIds = new long[TEST_DECKS.length];
    /**
     * Initially create one note for each model.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Log.i(AnkiDroidApp.TAG, "setUp()");
        long modelId = 0;

        final ContentResolver cr = getContext().getContentResolver();
        // Query all available models
        final Cursor allModelsCursor = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null);
        assertNotNull(allModelsCursor);
        mCreatedNotes = 0;
        int idColumnIndex = allModelsCursor.getColumnIndexOrThrow(FlashCardsContract.Model._ID);
        ContentValues values = new ContentValues();
        try {
            while (allModelsCursor.moveToNext()) {
                modelId = allModelsCursor.getLong(idColumnIndex);
                values.clear();
                values.put(FlashCardsContract.Note.MID, modelId);
                Uri newNoteUri = cr.insert(FlashCardsContract.Note.CONTENT_URI, values);

                // Now set a special tag, so that the note can easily be deleted after test
                Uri newNoteDataUri = Uri.withAppendedPath(newNoteUri, "data");
                values.clear();
                values.put(FlashCardsContract.DataColumns.MIMETYPE, FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE);
                values.put(FlashCardsContract.Data.Tags.TAG_CONTENT, TEST_TAG);
                assertEquals("Tag set", 1, cr.update(newNoteDataUri, values, null, null));
                mCreatedNotes++;
            }
        } finally {
            allModelsCursor.close();
        }
        assertTrue("Check that at least one model exists, i.e. one note was created", mCreatedNotes != 0);


        // create test decks
        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());
        numDecksBeforeTest = col.getDecks().count();


        // create one note for every test deck
        int i = 0;
        for(String newDeckName : TEST_DECKS) {
            testDeckIds[i] = col.getDecks().id(newDeckName, true);

            values.clear();
            values.put(FlashCardsContract.Note.MID, modelId);
            Uri newNoteUri = cr.insert(FlashCardsContract.Note.CONTENT_URI, values);

            // Now set a special tag, so that the note can easily be deleted after test
            Uri newNoteDataUri = Uri.withAppendedPath(newNoteUri, "data");
            values.clear();
            values.put(FlashCardsContract.DataColumns.MIMETYPE, FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE);
            values.put(FlashCardsContract.Data.Tags.TAG_CONTENT, TEST_TAG_2);
            assertEquals("Tag set", 1, cr.update(newNoteDataUri, values, null, null));

            final Cursor cardsCursor = cr.query(newNoteDataUri, null, null, null, null);
            assertNotNull("Check that there is a valid cursor after query for cards", cardsCursor);
            try {
                assertTrue("Check that there is at least one result for cards", cardsCursor.getCount() > 0);
                while (cardsCursor.moveToNext()) {
                    // Move to deck
                    ContentValues cardValues = new ContentValues();
                    cardValues.put(FlashCardsContract.Card.DECK_NAME, newDeckName);
                    Uri cardUri = Uri.withAppendedPath(
                            Uri.withAppendedPath(newNoteUri,"cards"),
                            ""+0);
                    cr.update(cardUri, cardValues, null, null);
                }
            } finally {
                cardsCursor.close();
            }


            i++;
        }

    }

    /**
     * Remove the notes created in setUp().
     * <p/>
     * Using direct access to the collection, because there is no plan to include a delete
     * interface in the content provider.
     */
    @Override
    protected void tearDown() throws Exception {
        Log.i(AnkiDroidApp.TAG, "tearDown()");
        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());
        int deletedNotes;
        List<Long> noteIds = col.findNotes("tag:" + TEST_TAG);
        if ((noteIds != null) && (noteIds.size() > 0)) {
            long[] delNotes = new long[noteIds.size()];
            for (int i = 0; i < noteIds.size(); i++) {
                delNotes[i] = noteIds.get(i);
            }
            col.remNotes(delNotes);
            deletedNotes = noteIds.size();
        } else {
            deletedNotes = 0;
        }
        assertEquals("Check that all created notes have been deleted", mCreatedNotes, deletedNotes);


        // delete test decks
        for(long did : testDeckIds) {
            col.getDecks().rem(did, true);
        }
        // delete notes of test decks
        noteIds = col.findNotes("tag:" + TEST_TAG_2);
        if ((noteIds != null) && (noteIds.size() > 0)) {
            long[] delNotes = new long[noteIds.size()];
            for (int i = 0; i < noteIds.size(); i++) {
                delNotes[i] = noteIds.get(i);
            }
            col.remNotes(delNotes);
        }
        col.getDecks().rem(col.getDecks().id(TEST_DECK));
        col.getDecks().flush();
        assertEquals("Check that all created decks have been deleted", numDecksBeforeTest, col.getDecks().count());

        super.tearDown();
    }

    public void testQueryNoteIds() {
        final ContentResolver cr = getContext().getContentResolver();
        // Query all available notes
        final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:" + TEST_TAG, null, null);
        assertNotNull(allNotesCursor);
        try {
            assertEquals("Check number of results", mCreatedNotes, allNotesCursor.getCount());
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

    public void testQueryNotesProjection() {
        final ContentResolver cr = getContext().getContentResolver();
        // Query all available notes
        for (int i = 0; i < FlashCardsContract.Note.DEFAULT_PROJECTION.length; i++) {
            String[] projection = removeFromProjection(FlashCardsContract.Note.DEFAULT_PROJECTION, i);
            final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, projection, "tag:" + TEST_TAG, null, null);
            assertNotNull("Check that there is a valid cursor", allNotesCursor);
            try {
                assertEquals("Check number of results", mCreatedNotes, allNotesCursor.getCount());
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

    public void testQueryNoteData() {
        final ContentResolver cr = getContext().getContentResolver();
        // Query all available notes
        final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:" + TEST_TAG, null, null);
        assertNotNull(allNotesCursor);
        try {
            assertEquals("Check number of results", mCreatedNotes, allNotesCursor.getCount());
            while (allNotesCursor.moveToNext()) {
                // Now iterate over all cursors
                Uri dataUri = Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, allNotesCursor.getString(allNotesCursor.getColumnIndex(FlashCardsContract.Note._ID))), "data");
                final Cursor noteDataCursor = cr.query(dataUri, null, null, null, null);
                assertNotNull("Check that there is a valid cursor for detail data", noteDataCursor);
                try {
                    assertTrue("Check that there is at least one result for detail data", noteDataCursor.getCount() > 0);
                    while (noteDataCursor.moveToNext()) {
                        String mimeType = noteDataCursor.getString(noteDataCursor.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE));
                        if (mimeType.equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
                            assertEquals("Check field content", "temp", noteDataCursor.getString(noteDataCursor.getColumnIndex(FlashCardsContract.Data.Field.FIELD_CONTENT)));
                        } else if (mimeType.equals(FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE)) {
                            assertEquals("Unknown tag", TEST_TAG, noteDataCursor.getString(noteDataCursor.getColumnIndex(FlashCardsContract.Data.Tags.TAG_CONTENT)));
                        } else {
                            fail("Unknown MIME type " + noteDataCursor.getString(allNotesCursor.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)));
                        }
                    }
                } finally {
                    noteDataCursor.close();
                }
            }
        } finally {
            allNotesCursor.close();
        }
    }

    public void testUpdateNoteField() {
        final ContentResolver cr = getContext().getContentResolver();
        // Query all available notes
        final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:" + TEST_TAG, null, null);
        assertNotNull(allNotesCursor);
        try {
            assertEquals("Check number of results", mCreatedNotes, allNotesCursor.getCount());
            while (allNotesCursor.moveToNext()) {
                // Now iterate over all notes
                Uri dataUri = Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, allNotesCursor.getString(allNotesCursor.getColumnIndex(FlashCardsContract.Note._ID))), "data");
                Cursor noteDataCursor = cr.query(dataUri, null, null, null, null);
                assertNotNull("Check that there is a valid cursor for detail data", noteDataCursor);
                try {
                    assertTrue("Check that there is at least one result for detail data", noteDataCursor.getCount() > 0);
                    while (noteDataCursor.moveToNext()) {
                        if (noteDataCursor.getString(noteDataCursor.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
                            // Update field
                            ContentValues values = new ContentValues();
                            values.put(FlashCardsContract.DataColumns.MIMETYPE, FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE);
                            values.put(FlashCardsContract.Data.Field.FIELD_NAME, noteDataCursor.getString(noteDataCursor.getColumnIndex(FlashCardsContract.Data.Field.FIELD_NAME)));
                            values.put(FlashCardsContract.Data.Field.FIELD_CONTENT, TEST_FIELD_VALUE);
                            assertEquals("Tag set", 1, cr.update(dataUri, values, null, null));
                        } else {
                            // ignore other data
                        }
                    }
                } finally {
                    noteDataCursor.close();
                }

                // After update query again
                noteDataCursor = cr.query(dataUri, null, null, null, null);
                assertNotNull("Check that there is a valid cursor for detail data after update", noteDataCursor);
                try {
                    assertTrue("Check that there is at least one result for detail data after update", noteDataCursor.getCount() > 0);
                    while (noteDataCursor.moveToNext()) {
                        if (noteDataCursor.getString(noteDataCursor.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
                            assertEquals("Check field content", TEST_FIELD_VALUE, noteDataCursor.getString(noteDataCursor.getColumnIndex(FlashCardsContract.Data.Field.FIELD_CONTENT)));
                        } else {
                            //ignore other data
                        }
                    }
                } finally {
                    noteDataCursor.close();
                }
            }
        } finally {
            allNotesCursor.close();
        }
    }

    public void testQueryAllModels() {
        final ContentResolver cr = getContext().getContentResolver();
        // Query all available models
        final Cursor allModelsCursor = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null);
        assertNotNull(allModelsCursor);
        try {
            assertTrue("Check that there is at least one result", allModelsCursor.getCount() > 0);
            while (allModelsCursor.moveToNext()) {
                long modelId = allModelsCursor.getLong(allModelsCursor.getColumnIndex(FlashCardsContract.Model._ID));
                Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
                final Cursor singleModelCursor = cr.query(modelUri, null, null, null, null);
                assertNotNull(singleModelCursor);
                try {
                    assertEquals("Check that there is exactly one result", 1, singleModelCursor.getCount());
                    assertTrue("Move to beginning of cursor", singleModelCursor.moveToFirst());
                    String nameFromModels = allModelsCursor.getString(allModelsCursor.getColumnIndex(FlashCardsContract.Model.NAME));
                    String nameFromModel = singleModelCursor.getString(allModelsCursor.getColumnIndex(FlashCardsContract.Model.NAME));
                    assertEquals("Check that model names are the same", nameFromModel, nameFromModels);
                    String jsonFromModels = allModelsCursor.getString(allModelsCursor.getColumnIndex(FlashCardsContract.Model.JSONOBJECT));
                    String jsonFromModel = singleModelCursor.getString(allModelsCursor.getColumnIndex(FlashCardsContract.Model.JSONOBJECT));
                    assertEquals("Check that jsonobjects are the same", jsonFromModel, jsonFromModels);
                } finally {
                    singleModelCursor.close();
                }
            }
        } finally {
            allModelsCursor.close();
        }
    }

    public void testMoveToOtherDeck() {
        final ContentResolver cr = getContext().getContentResolver();
        // Query all available notes
        final Cursor allNotesCursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:" + TEST_TAG, null, null);
        assertNotNull(allNotesCursor);
        try {
            assertEquals("Check number of results", mCreatedNotes, allNotesCursor.getCount());
            while (allNotesCursor.moveToNext()) {
                // Now iterate over all cursors
                Uri cardsUri = Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, allNotesCursor.getString(allNotesCursor.getColumnIndex(FlashCardsContract.Note._ID))), "cards");
                final Cursor cardsCursor = cr.query(cardsUri, null, null, null, null);
                assertNotNull("Check that there is a valid cursor after query for cards", cardsCursor);
                try {
                    assertTrue("Check that there is at least one result for cards", cardsCursor.getCount() > 0);
                    while (cardsCursor.moveToNext()) {
                        String deckName = cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.DECK_NAME));
                        assertEquals("Make sure that card is in default deck", "Default", deckName);
                        // Move to test deck
                        ContentValues values = new ContentValues();
                        values.put(FlashCardsContract.Card.DECK_NAME, TEST_DECK);
                        Uri cardUri = Uri.withAppendedPath(cardsUri, cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.CARD_ORD)));
                        cr.update(cardUri, values, null, null);
                        Cursor movedCardCur = cr.query(cardUri, null, null, null, null);
                        assertNotNull("Check that there is a valid cursor after moving card", movedCardCur);
                        assertTrue("Move to beginning of cursor after moving card", movedCardCur.moveToFirst());
                        deckName = movedCardCur.getString(movedCardCur.getColumnIndex(FlashCardsContract.Card.DECK_NAME));
                        assertEquals("Make sure that card is in test deck", TEST_DECK, deckName);
                    }
                } finally {
                    cardsCursor.close();
                }
            }
        } finally {
            allNotesCursor.close();
        }
    }

    public void testUnsupportedOperations() {
        final ContentResolver cr = getContext().getContentResolver();
        ContentValues dummyValues = new ContentValues();
        Uri[] updateUris = {
                FlashCardsContract.Note.CONTENT_URI,
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .build(),
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("cards")
                        .build(),
                FlashCardsContract.Model.CONTENT_URI.buildUpon()
                        .appendPath("1234")
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
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .build(),
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("data")
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
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .build(),
                FlashCardsContract.Note.CONTENT_URI.buildUpon()
                        .appendPath("1234")
                        .appendPath("data")
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
                FlashCardsContract.Model.CONTENT_URI,
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

    public void testQueryAllDecks(){
        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());
        Decks decks = col.getDecks();

        Cursor decksCursor = getContext().getContentResolver()
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
        } catch (JSONException e) {
            //this should never be the case, since deck always contains the string "name"
            e.printStackTrace();
        } finally {
            decksCursor.close();
        }
    }

    public void testQueryCertainDeck(){
        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());

        long deckId = testDeckIds[0]; //<-- insert real deck ID here
        Uri deckUri = Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, Long.toString(deckId));
        Cursor decksCursor = getContext().getContentResolver().query(deckUri, null, null, null, null);
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
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            decksCursor.close();
        }
    }

    public void testQueryNextCard(){
        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());
        Sched sched = col.getSched();

        Cursor reviewInfoCursor = getContext().getContentResolver().query(
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

    public void testQueryCardFromCertainDeck(){
        long deckToTest = testDeckIds[0];
        String deckSelector = "deckID=?";
        String deckArguments[] = {Long.toString(deckToTest)};
        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());
        Sched sched = col.getSched();
        long selectedDeckBeforeTest = col.getDecks().selected();
        col.getDecks().select(1); //select Default deck

        Cursor reviewInfoCursor = getContext().getContentResolver().query(
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


    public void testSetSelectedDeck(){
        long deckId = testDeckIds[0];
        ContentResolver cr = getContext().getContentResolver();
        Uri selectDeckUri = FlashCardsContract.Deck.CONTENT_SELECTED_URI;
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.Deck.DECK_ID, deckId);
        cr.update(selectDeckUri, values, null, null);

        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());
        assertEquals("Check that the selected deck has not changed", deckId, col.getDecks().selected());
    }

    public void testAnswerCard(){
        Collection col;
        col = CollectionHelper.getInstance().getCol(getContext());
        Sched sched = col.getSched();
        long deckId = testDeckIds[0];
        col.getDecks().select(deckId);
        Card card = sched.getCard();

        ContentResolver cr = getContext().getContentResolver();
        Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
        ContentValues values = new ContentValues();
        long noteId = card.note().getId();
        int cardOrd = card.getOrd();
        int ease = AbstractFlashcardViewer.EASE_MID; //<- insert real ease here

        values.put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId);
        values.put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd);
        values.put(FlashCardsContract.ReviewInfo.EASE, ease);
        int updateCount = cr.update(reviewInfoUri, values, null, null);

        assertEquals("Check if update returns 1", 1, updateCount);

        sched.reset();
        Card newCard = sched.getCard();
        if(newCard != null){
            if(newCard.note().getId() == card.note().getId() && newCard.getOrd() == card.getOrd()){
                fail("Next scheduled card has not changed");
            }
        }else{
            //We expected this
        }
    }

}
