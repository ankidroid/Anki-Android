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
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.provider.FlashCardsContract;
import com.ichi2.libanki.Collection;

import java.util.List;

/**
 * Test cases for {@link com.ichi2.anki.provider.CardContentProvider}.
 * <p/>
 * These tests should cover all supported operations for each URI.
 */
public class ContentProviderTest extends AndroidTestCase {

    private static final String TEST_FIELD_VALUE = "test field value";
    private static final String TEST_TAG = "aldskfhewjklhfczmxkjshf";
    private static final String TEST_DECK = "glekrjterglknsdfflkgj";
    private int mCreatedNotes;

    /**
     * Initially create one note for each model.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Log.i(AnkiDroidApp.TAG, "setUp()");
        final ContentResolver cr = getContext().getContentResolver();
        // Query all available models
        final Cursor allModelsCursor = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null);
        assertNotNull(allModelsCursor);
        mCreatedNotes = 0;
        int idColumnIndex = allModelsCursor.getColumnIndexOrThrow(FlashCardsContract.Model._ID);
        ContentValues values = new ContentValues();
        try {
            while (allModelsCursor.moveToNext()) {
                long modelId = allModelsCursor.getLong(idColumnIndex);
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
}
