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
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.provider.FlashCardsContract.Data.Field;
import com.ichi2.anki.provider.FlashCardsContract.DataColumns;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import org.json.JSONException;
import org.json.JSONObject;
import timber.log.Timber;

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
                            rb.add(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE);
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
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
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
            } else {
                throw new UnsupportedOperationException("Column \"" + column + "\" is unknown");
            }
        }
    }

    private Card getCardFromUri(Uri uri, Collection col) {
        long noteId;
        int ord;
        noteId = Long.parseLong(uri.getPathSegments().get(1));
        ord = Integer.parseInt(uri.getPathSegments().get(3));
        Note currentNote = col.getNote(noteId);
        if (currentNote.cards().size() <= ord) {
            throw new IllegalArgumentException("Card with ord " + ord + " does not exist for note " + noteId);
        }
        return currentNote.cards().get(ord);
    }

    private Note getNoteFromUri(Uri uri, Collection col) {
        long noteId;
        noteId = Long.parseLong(uri.getPathSegments().get(1));
        return col.getNote(noteId);
    }
}
