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

package com.ichi2.anki.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.net.Uri;

import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.provider.FlashCardsContract.Data.Field;
import com.ichi2.anki.provider.FlashCardsContract.DataColumns;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Supported URIs:
 * .../notes (search for notes)
 * .../notes/# (direct access to note)
 * .../notes/#/data (access to detail data of note)
 * .../notes/#/cards (access cards of note)
 * .../notes/#/cards/# (access specific card of note)
 * .../models (search for models)
 * .../models/# (direct access to model)
 * <p/>
 * Note that unlike Android's contact providers:
 * <ul>
 * <li>it's not possible to access detail data of more than one note</li>
 * <li>it's not possible to access cards of more than one note</li>
 * <li>it's not possible to access detail data of a note without providing its ID</li>
 * <li>it's not possible to access cards of a note without providing the note's ID</li>
 * <li>detail data of a notes only have fake temporary numeric ID (i.e. column "_ID"), this
 * is due to the storage system of the AnkiDatabase.</li>
 * </ul>
 */
public class CardContentProvider extends ContentProvider {

    /* URI types */
    private static final int NOTES = 1000;
    private static final int NOTES_ID = 1001;
    private static final int NOTES_ID_DATA = 1002;
    private static final int NOTES_ID_CARDS = 1003;
    private static final int NOTES_ID_CARDS_ORD = 1004;
    private static final int MODELS = 2000;
    private static final int MODELS_ID = 2001;
    private static final int SCHEDULE = 3000;
    private static final int DECKS = 4000;
    private static final int DECK_SELECTED = 4001;
    private static final int DECKS_ID = 4002;

    private static final UriMatcher sUriMatcher =
            new UriMatcher(UriMatcher.NO_MATCH);

    /* Based on proposal from Tim's comment on https://github.com/ankidroid/Anki-Android/pull/725
     */
    static {
        // Here you can see all the URIs at a glance
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#", NOTES_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#/data", NOTES_ID_DATA);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#/cards", NOTES_ID_CARDS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#/cards/#", NOTES_ID_CARDS_ORD);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models", MODELS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/#", MODELS_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "schedule/", SCHEDULE);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "decks/", DECKS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "decks/#", DECKS_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "select_deck/", DECK_SELECTED);

    }

    /**
     * The names of the columns returned by this content provider differ slightly from the names
     * given of the database columns. This list is used to convert the column names used in a
     * projection by the user into DB column names.
     * <p/>
     * This is currently only "_id" (projection) vs. "id" (Anki DB). But should probably be
     * applied to more columns. "MID", "USN", "MOD" are not really user friendly.
     */
    private static final String[] sDefaultNoteProjectionDBAccess = FlashCardsContract.Note.DEFAULT_PROJECTION.clone();

    static {
        for (int idx = 0; idx < sDefaultNoteProjectionDBAccess.length; idx++) {
            if (sDefaultNoteProjectionDBAccess[idx].equals(FlashCardsContract.Note._ID)) {
                sDefaultNoteProjectionDBAccess[idx] = "id as _id";
            }
        }
    }


    @Override
    public boolean onCreate() {
        // Initialize content provider on startup.
        Timber.d("CardContentProvider: onCreate");

        return true;
    }

    @Override
    public String getType(Uri uri) {
        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        switch (match) {
            case NOTES:
                return FlashCardsContract.Note.CONTENT_TYPE;
            case NOTES_ID:
                return FlashCardsContract.Note.CONTENT_ITEM_TYPE;
            case NOTES_ID_DATA:
                return FlashCardsContract.Data.CONTENT_TYPE;
            case NOTES_ID_CARDS:
                return FlashCardsContract.Card.CONTENT_TYPE;
            case NOTES_ID_CARDS_ORD:
                return FlashCardsContract.Card.CONTENT_ITEM_TYPE;
            case MODELS:
                return FlashCardsContract.Model.CONTENT_TYPE;
            case MODELS_ID:
                return FlashCardsContract.Model.CONTENT_ITEM_TYPE;
            case SCHEDULE:
                return FlashCardsContract.ReviewInfo.CONTENT_TYPE;
            case DECKS:
                return FlashCardsContract.Deck.CONTENT_TYPE;
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Timber.d("CardContentProvider.query");
        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col == null) {
            return null;
        }

        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        switch (match) {
            case NOTES: {
                /* Search for notes
                 */
                // TODO: Allow sort order, then also update description in FlashCardContract
                String columnsStr = proj2str(projection);
                String query = (selection != null) ? selection : "";
                List<Long> noteIds = col.findNotes(query);
                if ((noteIds != null) && (!noteIds.isEmpty())) {
                    String selectedIds = "id in " + Utils.ids2str(noteIds);
                    Cursor cur;
                    try {
                        cur = col.getDb().getDatabase().rawQuery("select " + columnsStr + " from notes where " + selectedIds, null);
                    } catch (SQLException e) {
                        throw new IllegalArgumentException("Not possible to query for data for IDs " +
                                selectedIds, e);
                    }
                    return cur;
                } else {
                    return null;
                }
            }
            case NOTES_ID: {
                /* Direct access note
                 */
                long noteId;
                noteId = Long.parseLong(uri.getPathSegments().get(1));
                String columnsStr = proj2str(projection);
                String selectedIds = "id = " + noteId;
                Cursor cur;
                try {
                    cur = col.getDb().getDatabase().rawQuery("select " + columnsStr + " from notes where " + selectedIds, null);
                } catch (SQLException e) {
                    throw new IllegalArgumentException("Not possible to query for data for ID \"" +
                            noteId + "\"", e);
                }
                return cur;
            }
            case NOTES_ID_DATA: {
                /* Direct access note details
                 */
                Note currentNote = getNoteFromUri(uri, col);
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : DataColumns.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);
                long id = 0;

                /* First add the data from all the fields ...
                 */
                String[][] currentNoteItems = currentNote.items();
                for (String[] field : currentNoteItems) {
                    id++;
                    MatrixCursor.RowBuilder rb = rv.newRow();
                    for (String column : columns) {
                        if (column.equals(DataColumns._ID)) {
                            rb.add(id);
                        } else if (column.equals(DataColumns.NOTE_ID)) {
                            rb.add(currentNote.getId());
                        } else if (column.equals(DataColumns.MIMETYPE)) {
                            rb.add(Field.CONTENT_ITEM_TYPE);
                        } else if (column.equals(Field.FIELD_NAME)) {
                            rb.add(field[0]);
                        } else if (column.equals(Field.FIELD_CONTENT)) {
                            rb.add(field[1]);
                        } else {
                            throw new UnsupportedOperationException("Support for column \"" + column + "\" is not yet implemented for MIME type " + Field.CONTENT_ITEM_TYPE);
                        }
                    }
                }

                /* ... and then the tags. */
                {
                    id++;
                    MatrixCursor.RowBuilder rb = rv.newRow();
                    for (String column : columns) {
                        if (column.equals(DataColumns._ID)) {
                            rb.add(id);
                        } else if (column.equals(DataColumns.NOTE_ID)) {
                            rb.add(currentNote.getId());
                        } else if (column.equals(DataColumns.MIMETYPE)) {
                            rb.add(FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE);
                        } else if (column.equals(FlashCardsContract.Data.Tags.TAG_CONTENT)) {
                            rb.add(currentNote.stringTags().trim());
                        } else // noinspection StatementWithEmptyBody
                            if (column.equals(DataColumns.DATA2)) {
                            /* Ignore: This column is not used for tags */
                            } else {
                                throw new UnsupportedOperationException("Support for column \"" + column + "\" is not implemented for MIME type " + Field.CONTENT_ITEM_TYPE);
                            }
                    }
                }
                return rv;
            }
            case NOTES_ID_CARDS: {
                Note currentNote = getNoteFromUri(uri, col);
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Card.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);
                for (Card currentCard : currentNote.cards()) {
                    addCardToCursor(currentCard, rv, col, columns);
                }
                return rv;
            }
            case NOTES_ID_CARDS_ORD: {
                Card currentCard = getCardFromUri(uri, col);
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Card.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);
                addCardToCursor(currentCard, rv, col, columns);
                return rv;
            }
            case MODELS: {
                HashMap<Long, JSONObject> models = col.getModels().getModels();
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Model.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);

                for (Long modelId : models.keySet()) {
                    try {
                        addModelToCursor(modelId, models, rv, columns);
                    } catch (JSONException e) {
                        throw new IllegalArgumentException("Model " + modelId + " is malformed", e);
                    }
                }
                return rv;
            }
            case MODELS_ID: {
                long modelId;
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Model.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);
                modelId = Long.parseLong(uri.getPathSegments().get(1));
                HashMap<Long, JSONObject> models = col.getModels().getModels();
                try {
                    addModelToCursor(modelId, models, rv, columns);
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Model " + modelId + " is malformed", e);
                }
                return rv;
            }
            case SCHEDULE: {
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.ReviewInfo.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);
                long selectedDeckBeforeQuery = col.getDecks().selected();
                long deckIdOfTemporarilySelectedDeck = -1;
                int limit = 1; //the number of scheduled cards to return
                int selectionArgIndex = 0;

                //parsing the selection arguments
                if (selection != null) {
                    String[] args = selection.split(","); //split selection to get arguments like "limit=?"
                    for (String arg : args) {
                        String[] keyAndValue = arg.split("="); //split arguments into key ("limit") and value ("?")
                        try {
                            String value = keyAndValue[1].trim().equals("?") ? selectionArgs[selectionArgIndex++] : keyAndValue[1]; //check if the value is a placeholder ("?"), if it is, replace it with the next value of selectionArgs
                            if (keyAndValue[0].trim().equals("limit")) {
                                limit = Integer.valueOf(value);
                            } else if (keyAndValue[0].trim().equals("deckID")) {
                                deckIdOfTemporarilySelectedDeck = Long.valueOf(value);
                                if(!selectDeckWithCheck(col, deckIdOfTemporarilySelectedDeck)){
                                    return rv; //if the provided deckID is wrong, return empty cursor.
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            nfe.printStackTrace();
                        }
                    }
                }

                //retrieve the number of cards provided by the selection parameter "limit"
                col.getSched().reset();
                for (int k = 0; k< limit; k++){
                    Card currentCard = col.getSched().getCard();

                    if (currentCard != null) {
                        int buttonCount = col.getSched().answerButtons(currentCard);
                        JSONArray buttonTexts = new JSONArray();
                        for (int i = 0; i < buttonCount; i++) {
                            buttonTexts.put(col.getSched().nextIvlStr(currentCard, i + 1, true));
                        }
                        addReviewInfoToCursor(currentCard, buttonTexts, buttonCount, rv, col, columns);
                    }else{
                        break;
                    }
                }

                if (deckIdOfTemporarilySelectedDeck != -1) {//if the selected deck was changed
                    col.getDecks().select(selectedDeckBeforeQuery); //change the selected deck back to the one it was before the query
                }
                return rv;
            }
            case DECKS: {
                List<Sched.DeckDueTreeNode> allDecks = col.getSched().deckDueList();
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, allDecks.size());

                for (Sched.DeckDueTreeNode deck : allDecks) {
                    long id = deck.did;
                    String name = deck.names[0];

                    addDeckToCursor(id, name, getDeckCountsFromDueTreeNode(deck), rv, col, columns);
                }
                return rv;
            }
            case DECKS_ID: {
                /* Direct access deck
                 */
                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);

                List<Sched.DeckDueTreeNode> allDecks = col.getSched().deckDueList();
                long deckId;
                deckId = Long.parseLong(uri.getPathSegments().get(1));
                for (Sched.DeckDueTreeNode deck : allDecks) {
                    if(deck.did == deckId){
                        addDeckToCursor(deckId, deck.names[0], getDeckCountsFromDueTreeNode(deck), rv, col, columns);
                        return rv;
                    }
                }

                return rv;
            }
            case DECK_SELECTED: {

                long id = col.getDecks().selected();
                String name = col.getDecks().name(id);

                MatrixCursor rv;
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                rv = new MatrixCursor(columns, 1);

                JSONArray counts = new JSONArray(Arrays.asList(col.getSched().counts()));
                addDeckToCursor(id, name, counts,rv, col, columns);

                return rv;
            }
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
    }

    private JSONArray getDeckCountsFromDueTreeNode(Sched.DeckDueTreeNode deck){
        JSONArray deckCounts = new JSONArray();
        deckCounts.put(deck.lrnCount);
        deckCounts.put(deck.revCount);
        deckCounts.put(deck.newCount);
        return deckCounts;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        Timber.d("CardContentProvider.update");
        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col == null) {
            return 0;
        }

        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        int updated = 0; // Number of updated entries (return value)
        switch (match) {
            case NOTES:
                throw new IllegalArgumentException("Not possible to update notes directly (only through data URI)");
            case NOTES_ID:
                throw new IllegalArgumentException("Not possible to update notes directly (only through data URI)");
            case NOTES_ID_DATA: {
                /* Direct access note details
                 */
                Note currentNote = getNoteFromUri(uri, col);
                boolean isField = false;
                boolean isTag = false;
                String data1 = null;
                String data2 = null;
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                Set<Map.Entry<String, Object>> valueSet = values.valueSet();
                for (Map.Entry<String, Object> entry : valueSet) {
                    String key = entry.getKey();
                    if (key.equals(DataColumns.MIMETYPE)) {
                        isField = values.getAsString(key).equals(Field.CONTENT_ITEM_TYPE);
                        isTag = values.getAsString(key).equals(FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE);
                    } else if (key.equals(DataColumns.DATA1)) {
                        data1 = values.getAsString(key);
                    } else if (key.equals(DataColumns.DATA2)) {
                        data2 = values.getAsString(key);
                    } else {
                        // Unknown column
                        throw new IllegalArgumentException("Unknown/unsupported column: " + key);
                    }
                }

                /* now update the note
                 */
                if ((isField) && (data1 != null) && (data2 != null)) {
                    Timber.d("CardContentProvider: Saving note after field update...");
                    currentNote.setitem(data1, data2);
                    currentNote.flush();
                    updated++;
                } else if ((isTag) && (data1 != null)) {
                    Timber.d("CardContentProvider: Saving note after tags update...");
                    currentNote.setTagsFromStr(data1);
                    currentNote.flush();
                    updated++;
                } else {
                    // User tries an operation that is not (yet?) supported.
                    throw new IllegalArgumentException("Currently only updates of fields are supported");
                }
                break;
            }
            case NOTES_ID_CARDS:
                // TODO: To be implemented
                throw new UnsupportedOperationException("Not yet implemented");
//                break;
            case NOTES_ID_CARDS_ORD: {
                Card currentCard = getCardFromUri(uri, col);
                boolean isDeckUpdate = false;
                String newDeckName = null;
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                Set<Map.Entry<String, Object>> valueSet = values.valueSet();
                for (Map.Entry<String, Object> entry : valueSet) {
                    // Only updates on deck name is supported
                    String key = entry.getKey();
                    isDeckUpdate = key.equals(FlashCardsContract.Card.DECK_NAME);
                    newDeckName = values.getAsString(key);
                }

                /* now update the card
                 */
                if ((isDeckUpdate) && (newDeckName != null)) {
                    Timber.d("CardContentProvider: Moving card to other deck...");
                    long did = col.getDecks().id(newDeckName); // create new deck if not exists
                    col.getDecks().flush();
                    currentCard.setDid(did);
                    currentCard.flush();
                    updated++;
                } else {
                    // User tries an operation that is not (yet?) supported.
                    throw new IllegalArgumentException("Currently only updates of decks are supported");
                }
                break;
            }
            case SCHEDULE: {
                Set<Map.Entry<String, Object>> valueSet = values.valueSet();
                int cardOrd = -1;
                long noteID = -1;
                int ease = -1;
                long timeTaken = -1;
                for (Map.Entry<String, Object> entry : valueSet) {
                    String key = entry.getKey();

                    if (key.equals(FlashCardsContract.ReviewInfo.NOTE_ID)) {
                        noteID = values.getAsLong(key);
                    } else if (key.equals(FlashCardsContract.ReviewInfo.CARD_ORD)) {
                        cardOrd = values.getAsInteger(key);
                    } else if (key.equals(FlashCardsContract.ReviewInfo.EASE)) {
                        ease = values.getAsInteger(key);
                    }else if (key.equals(FlashCardsContract.ReviewInfo.TIME_TAKEN)) {
                        timeTaken = values.getAsLong(key);
                    }
                }
                if (cardOrd != -1 && noteID != -1) {
                    Card cardToAnswer = getCard(noteID, cardOrd, col);
                    if(cardToAnswer != null) {
                        answerCard(col, col.getSched(), cardToAnswer, ease, timeTaken);
                        updated++;
                    }else{
                        Timber.e("Requested card with noteId %d and cardOrd %d was not found. Either the provided noteId/cardOrd were wrong" +
                                "or the card has been deleted in the meantime."
                                , noteID, cardOrd);
                    }
                }
                break;
            }
            case DECK_SELECTED: {
                Set<Map.Entry<String, Object>> valueSet = values.valueSet();
                for (Map.Entry<String, Object> entry : valueSet) {
                    String key = entry.getKey();
                    if(key.equals(FlashCardsContract.Deck.DECK_ID)) {
                        long deckId = values.getAsLong(key);
                        if(selectDeckWithCheck(col, deckId)){
                            updated ++;
                        }
                    }
                }
                break;
            }
            case MODELS:
                throw new UnsupportedOperationException("Not yet implemented");
//                break;
            case MODELS_ID:
                throw new UnsupportedOperationException("Not yet implemented");
//                break;
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
        return updated;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Timber.d("CardContentProvider.insert");
        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col == null) {
            return null;
        }

        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        switch (match) {
            case NOTES: {
                /* Insert new note
                 */
                Long modelId = values.getAsLong(FlashCardsContract.Note.MID);
                com.ichi2.libanki.Note newNote = new com.ichi2.libanki.Note(col, col.getModels().get(modelId));
                String[] fields = newNote.getFields();
                //Setting the first field is a mandatory action. Users should overwrite this with a meaningful value.
                newNote.setField(0, "temp");
                col.addNote(newNote);
                return Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(newNote.getId()));
            }
            case NOTES_ID: {
                /* Direct access note
                 */
                throw new IllegalArgumentException("Not possible to insert note with specific ID");
            }
            case NOTES_ID_DATA:
                throw new IllegalArgumentException("Not possible to insert note data");
            case NOTES_ID_CARDS:
                throw new IllegalArgumentException("Not possible to insert cards directly (only through inserting notes)");
            case NOTES_ID_CARDS_ORD:
                throw new IllegalArgumentException("Not possible to insert cards directly (only through inserting notes)");
            case MODELS:
                // TODO: To be implemented
                throw new UnsupportedOperationException("Not yet implemented");
//                break;
            case MODELS_ID:
                throw new IllegalArgumentException("Not possible to insert model with specific ID");
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
    }

    private static String proj2str(String[] projection) {
        StringBuilder rv = new StringBuilder();
        if (projection != null) {
            for (String column : projection) {
                int idx = projSearch(FlashCardsContract.Note.DEFAULT_PROJECTION, column);
                if (idx >= 0) {
                    rv.append(sDefaultNoteProjectionDBAccess[idx]);
                    rv.append(",");
                } else {
                    throw new IllegalArgumentException("Unknown column " + column);
                }
            }
        } else {
            for (String column : sDefaultNoteProjectionDBAccess) {
                rv.append(column);
                rv.append(",");
            }
        }
        rv.deleteCharAt(rv.length() - 1);
        return rv.toString();
    }

    private static int projSearch(String[] projection, String column) {
        for (int i = 0; i < projection.length; i++) {
            if (projection[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    private void addModelToCursor(Long modelId, HashMap<Long, JSONObject> models, MatrixCursor rv, String[] columns) throws JSONException {
        JSONObject jsonObject = models.get(modelId);
        MatrixCursor.RowBuilder rb = rv.newRow();
        for (String column : columns) {
            if (column.equals(FlashCardsContract.Model._ID)) {
                rb.add(modelId);
            } else if (column.equals(FlashCardsContract.Model.NAME)) {
                rb.add(jsonObject.getString("name"));
            } else if (column.equals(FlashCardsContract.Model.JSONOBJECT)) {
                rb.add(jsonObject);
            } else {
                throw new UnsupportedOperationException("Column \"" + column + "\" is unknown");
            }
        }

    }

    private void addCardToCursor(Card currentCard, MatrixCursor rv, Collection col, String[] columns) {
        String cardName;
        String deckName;
        try {
            cardName = currentCard.template().getString("name");
            deckName = col.getDecks().get(currentCard.getDid()).getString("name");
        } catch (JSONException je) {
            throw new IllegalArgumentException("Card is using an invalid template", je);
        }
        String question = currentCard.q();
        String answer = currentCard.a();

        MatrixCursor.RowBuilder rb = rv.newRow();
        for (String column : columns) {
            if (column.equals(FlashCardsContract.Card.NOTE_ID)) {
                rb.add(currentCard.note().getId());
            } else if (column.equals(FlashCardsContract.Card.CARD_ORD)) {
                rb.add(currentCard.getOrd());
            } else if (column.equals(FlashCardsContract.Card.CARD_NAME)) {
                rb.add(cardName);
            } else if (column.equals(FlashCardsContract.Card.DECK_NAME)) {
                rb.add(deckName);
            } else if (column.equals(FlashCardsContract.Card.QUESTION)) {
                rb.add(question);
            } else if (column.equals(FlashCardsContract.Card.ANSWER)) {
                rb.add(answer);
            } else if (column.equals(FlashCardsContract.Card.QUESTION_SIMPLE)) {
                rb.add(currentCard.qSimple());
            } else if (column.equals(FlashCardsContract.Card.ANSWER_SIMPLE)) {
                rb.add(currentCard._getQA(false).get("a"));
            }else if (column.equals(FlashCardsContract.Card.ANSWER_PURE)) {
                rb.add(currentCard.getPureAnswer());
            } else {
                throw new UnsupportedOperationException("Column \"" + column + "\" is unknown");
            }
        }
    }

    private void addReviewInfoToCursor(Card currentCard, JSONArray nextReviewTimesJson, int buttonCount,MatrixCursor rv, Collection col, String[] columns) {
        MatrixCursor.RowBuilder rb = rv.newRow();
        for (String column : columns) {
            if (column.equals(FlashCardsContract.Card.NOTE_ID)) {
                rb.add(currentCard.note().getId());
            } else if (column.equals(FlashCardsContract.ReviewInfo.CARD_ORD)) {
                rb.add(currentCard.getOrd());
            } else if (column.equals(FlashCardsContract.ReviewInfo.BUTTON_COUNT)) {
                rb.add(buttonCount);
            } else if (column.equals(FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES)) {
                rb.add(nextReviewTimesJson.toString());
            } else if (column.equals(FlashCardsContract.ReviewInfo.MEDIA_FILES)) {
                rb.add(new JSONArray(col.getMedia().filesInStr(currentCard.note().getMid(), currentCard.q()+currentCard.a())));
            } else {
                throw new UnsupportedOperationException("Column \"" + column + "\" is unknown");
            }
        }
    }

    private void answerCard(Collection col, Sched sched, Card cardToAnswer, int ease, long timeTaken) {
        try {
            AnkiDb ankiDB = col.getDb();
            ankiDB.getDatabase().beginTransaction();
            try {
                if (cardToAnswer != null) {
                    if(timeTaken != -1){
                        cardToAnswer.setTimerStarted(Utils.now()-timeTaken/1000);
                    }
                    sched.answerCard(cardToAnswer, ease);
                }
                ankiDB.getDatabase().setTransactionSuccessful();
            } finally {
                ankiDB.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "answerCard - RuntimeException on answering card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAnswerCard");
            return;
        }
    }

    private void addDeckToCursor(long id, String name, JSONArray deckCounts, MatrixCursor rv, Collection col, String[] columns) {
        MatrixCursor.RowBuilder rb = rv.newRow();
        for (String column : columns) {
            if (column.equals(FlashCardsContract.Deck.DECK_NAME)) {
                rb.add(name);
            }else if (column.equals(FlashCardsContract.Deck.DECK_ID)) {
                rb.add(id);
            }else if (column.equals(FlashCardsContract.Deck.DECK_COUNTS)) {
                rb.add(deckCounts);
            }else if (column.equals(FlashCardsContract.Deck.OPTIONS)) {
                String config = col.getDecks().confForDid(id).toString();
                rb.add(config);
            }
        }
    }

    private boolean selectDeckWithCheck(Collection col, long did){
        if (col.getDecks().get(did, false) != null) {
            col.getDecks().select(did);
            return true;
        } else {
            Timber.e("Requested deck with id %d was not found in deck list. Either the deckID provided was wrong" +
                    "or the deck has been deleted in the meantime."
                    , did);
            return false;
        }
    }

    private Card getCardFromUri(Uri uri, Collection col) {
        long noteId;
        int ord;
        noteId = Long.parseLong(uri.getPathSegments().get(1));
        ord = Integer.parseInt(uri.getPathSegments().get(3));
        return getCard(noteId, ord, col);
    }

    private Card getCard(long noteId, int ord, Collection col){
        Note currentNote = col.getNote(noteId);
        Card currentCard = null;
        for(Card card : currentNote.cards()){
            if(card.getOrd() == ord){
                currentCard = card;
            }
        }
        if (currentCard == null) {
            throw new IllegalArgumentException("Card with ord " + ord + " does not exist for note " + noteId);
        }
        return currentCard;
    }

    private Note getNoteFromUri(Uri uri, Collection col) {
        long noteId;
        noteId = Long.parseLong(uri.getPathSegments().get(1));
        return col.getNote(noteId);
    }
}
