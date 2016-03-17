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

package com.ichi2.anki.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import com.ichi2.libanki.DB;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.FlashCardsContract.CardTemplate;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
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
 * .../notes/#/cards (access cards of note)
 * .../notes/#/cards/# (access specific card of note)
 * .../models (search for models)
 * .../models/# (direct access to model). String id 'current' can be used in place of # for the current model
 * .../models/#/fields (access to field definitions of a model)
 * .../models/#/templates (access to card templates of a model)
 * .../schedule (access the study schedule)
 * .../decks (access the deck list)
 * .../decks/# (access the specified deck)
 * .../selected_deck (access the currently selected deck)
 * <p/>
 * Note that unlike Android's contact providers:
 * <ul>
  * <li>it's not possible to access cards of more than one note at a time</li>
 * <li>it's not possible to access cards of a note without providing the note's ID</li>
 * </ul>
 */
public class CardContentProvider extends ContentProvider {

    /* URI types */
    private static final int NOTES = 1000;
    private static final int NOTES_ID = 1001;
    private static final int NOTES_ID_CARDS = 1003;
    private static final int NOTES_ID_CARDS_ORD = 1004;
    private static final int MODELS = 2000;
    private static final int MODELS_ID = 2001;
    private static final int MODELS_ID_TEMPLATES = 2003;
    private static final int MODELS_ID_TEMPLATES_ID = 2004;
    private static final int SCHEDULE = 3000;
    private static final int DECKS = 4000;
    private static final int DECK_SELECTED = 4001;
    private static final int DECKS_ID = 4002;

    private static final UriMatcher sUriMatcher =
            new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // Here you can see all the URIs at a glance
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#", NOTES_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#/cards", NOTES_ID_CARDS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#/cards/#", NOTES_ID_CARDS_ORD);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models", MODELS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*", MODELS_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*/templates", MODELS_ID_TEMPLATES);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*/templates/#", MODELS_ID_TEMPLATES_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "schedule/", SCHEDULE);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "decks/", DECKS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "decks/#", DECKS_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "selected_deck/", DECK_SELECTED);
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
            case NOTES_ID_CARDS:
                return FlashCardsContract.Card.CONTENT_TYPE;
            case NOTES_ID_CARDS_ORD:
                return FlashCardsContract.Card.CONTENT_ITEM_TYPE;
            case MODELS:
                return FlashCardsContract.Model.CONTENT_TYPE;
            case MODELS_ID:
                return FlashCardsContract.Model.CONTENT_ITEM_TYPE;
            case MODELS_ID_TEMPLATES:
                return FlashCardsContract.CardTemplate.CONTENT_TYPE;
            case MODELS_ID_TEMPLATES_ID:
                return FlashCardsContract.CardTemplate.CONTENT_ITEM_TYPE;
            case SCHEDULE:
                return FlashCardsContract.ReviewInfo.CONTENT_TYPE;
            case DECKS:
                return FlashCardsContract.Deck.CONTENT_TYPE;
            case DECKS_ID:
                return FlashCardsContract.Deck.CONTENT_TYPE;
            case DECK_SELECTED:
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

        checkAllowedOrThrow(uri, false);

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
                String sqlSelection = (selectionArgs == null && selection == null) ? "" : " where ";
                // Add optional sql query selection: e.g. {"mid = 12345678"}
                if (selectionArgs != null) {
                    sqlSelection += TextUtils.join(" and ", selectionArgs);
                }
                // Add optional nid selection based on anki browser search: e.g. "deck:'My Awesome Deck'"
                if (selection != null) {
                    List<Long> noteIds = col.findNotes(query);
                    if ((noteIds != null) && (!noteIds.isEmpty())) {
                        if (selectionArgs != null) {
                            sqlSelection += " and ";
                        }
                        sqlSelection += "id in " + Utils.ids2str(noteIds);
                    } else {
                        return null;
                    }
                }
                // Make the main SQL query and return the cursor
                Cursor cur;
                try {
                    cur = col.getDb().getDatabase().rawQuery("select " + columnsStr + " from notes" + sqlSelection, null);
                } catch (SQLException e) {
                    throw new IllegalArgumentException("Not possible to query for data " + sqlSelection, e);
                }
                return cur;
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

            case NOTES_ID_CARDS: {
                Note currentNote = getNoteFromUri(uri, col);
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Card.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                for (Card currentCard : currentNote.cards()) {
                    addCardToCursor(currentCard, rv, col, columns);
                }
                return rv;
            }
            case NOTES_ID_CARDS_ORD: {
                Card currentCard = getCardFromUri(uri, col);
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Card.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                addCardToCursor(currentCard, rv, col, columns);
                return rv;
            }
            case MODELS: {
                HashMap<Long, JSONObject> models = col.getModels().getModels();
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Model.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                for (Long modelId : models.keySet()) {
                    addModelToCursor(modelId, models, rv, columns);
                }
                return rv;
            }
            case MODELS_ID: {
                long modelId = getModelIdFromUri(uri, col);
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Model.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                HashMap<Long, JSONObject> models = col.getModels().getModels();
                addModelToCursor(modelId, models, rv, columns);
                return rv;
            }
            case MODELS_ID_TEMPLATES: {
                /* Direct access model templates
                 */
                JSONObject currentModel = col.getModels().get(getModelIdFromUri(uri, col));
                String[] columns = ((projection != null) ? projection : CardTemplate.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                try {
                    JSONArray templates = currentModel.getJSONArray("tmpls");
                    for (int idx = 0; idx < templates.length(); idx++) {
                        JSONObject template = templates.getJSONObject(idx);
                        addTemplateToCursor(template, currentModel, idx+1, rv, columns);
                    }
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Model is malformed", e);
                }
                return rv;
            }
            case MODELS_ID_TEMPLATES_ID: {
                /* Direct access model template with specific ID
                 */
                int ord = Integer.parseInt(uri.getLastPathSegment());
                JSONObject currentModel = col.getModels().get(getModelIdFromUri(uri, col));
                String[] columns = ((projection != null) ? projection : CardTemplate.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                try {
                    JSONObject template = getTemplateFromUri(uri, col);
                    addTemplateToCursor(template, currentModel, ord+1, rv, columns);
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Model is malformed", e);
                }
                return rv;
            }

            case SCHEDULE: {
                String[] columns = ((projection != null) ? projection : FlashCardsContract.ReviewInfo.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
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
                            //check if value is a placeholder ("?"), if so replace with the next value of selectionArgs
                            String value = keyAndValue[1].trim().equals("?") ? selectionArgs[selectionArgIndex++] :
                                    keyAndValue[1];
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
                            buttonTexts.put(col.getSched().nextIvlStr(getContext(), currentCard, i + 1));
                        }
                        addReviewInfoToCursor(currentCard, buttonTexts, buttonCount, rv, col, columns);
                    }else{
                        break;
                    }
                }

                if (deckIdOfTemporarilySelectedDeck != -1) {//if the selected deck was changed
                    //change the selected deck back to the one it was before the query
                    col.getDecks().select(selectedDeckBeforeQuery);
                }
                return rv;
            }
            case DECKS: {
                List<Sched.DeckDueTreeNode> allDecks = col.getSched().deckDueList();
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, allDecks.size());
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
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
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
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
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
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        logProviderCall("update", uri);

        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        // permissions for a couple of updates are relaxed (for when adding notes and models)
        checkAllowedOrThrow(uri, match != NOTES_ID_CARDS_ORD && match != MODELS_ID_TEMPLATES_ID);

        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col == null) {
            return 0;
        }

        int updated = 0; // Number of updated entries (return value)
        switch (match) {
            case NOTES:
                throw new IllegalArgumentException("Not possible to update notes directly (only through data URI)");
            case NOTES_ID: {
                /* Direct access note details
                 */
                Note currentNote = getNoteFromUri(uri, col);
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                Set<Map.Entry<String, Object>> valueSet = values.valueSet();
                for (Map.Entry<String, Object> entry : valueSet) {
                    String key = entry.getKey();
                    // when the client does not specify FLDS, then don't update the FLDS
                    if (key.equals(FlashCardsContract.Note.FLDS)) {
                        // Update FLDS
                        Timber.d("CardContentProvider: flds update...");
                        String newFldsEncoded = (String) entry.getValue();
                        String[] flds = Utils.splitFields(newFldsEncoded);
                        // Check that correct number of flds specified
                        if (flds.length != currentNote.getFields().length) {
                            throw new IllegalArgumentException("Incorrect flds argument : " + newFldsEncoded);
                        }
                        // Update the note
                        for (int idx=0; idx < flds.length; idx++) {
                            currentNote.setField(idx, flds[idx]);
                        }
                        updated++;
                    } else if (key.equals(FlashCardsContract.Note.TAGS)) {
                        // Update tags
                        Timber.d("CardContentProvider: tags update...");
                        Object tags = entry.getValue();
                        if (tags != null) {
                            currentNote.setTagsFromStr(String.valueOf(tags));
                        }
                        updated++;
                    } else {
                        // Unsupported column
                        throw new IllegalArgumentException("Unsupported column: " + key);
                    }
                }
                Timber.d("CardContentProvider: Saving note...");
                currentNote.flush();
                break;
            }
            case NOTES_ID_CARDS:
                // TODO: To be implemented
                throw new UnsupportedOperationException("Not yet implemented");
//                break;
            case NOTES_ID_CARDS_ORD: {
                Card currentCard = getCardFromUri(uri, col);
                boolean isDeckUpdate = false;
                long did = -1;
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                Set<Map.Entry<String, Object>> valueSet = values.valueSet();
                for (Map.Entry<String, Object> entry : valueSet) {
                    // Only updates on deck id is supported
                    String key = entry.getKey();
                    isDeckUpdate = key.equals(FlashCardsContract.Card.DECK_ID);
                    did = values.getAsLong(key);
                }

                /* now update the card
                 */
                if ((isDeckUpdate) && (did >= 0)) {
                    Timber.d("CardContentProvider: Moving card to other deck...");
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
                throw new IllegalArgumentException("Cannot update models in bulk");
            case MODELS_ID:
                // Get the input parameters
                String newModelName = values.getAsString(FlashCardsContract.Model.NAME);
                String newCss = values.getAsString(FlashCardsContract.Model.CSS);
                String newDid = values.getAsString(FlashCardsContract.Model.DECK_ID);
                String newFieldList = values.getAsString(FlashCardsContract.Model.FIELD_NAMES);
                if (newFieldList != null) {
                    // Changing the field names would require a full-sync
                    throw new IllegalArgumentException("Field names cannot be changed via provider");
                }
                // Get the original note JSON
                JSONObject model = col.getModels().get(getModelIdFromUri(uri, col));
                try {
                    // Update model name and/or css
                    if (newModelName != null) {
                        model.put("name", newModelName);
                        updated++;
                    }
                    if (newCss != null) {
                        model.put("css", newCss);
                        updated++;
                    }
                    if (newDid != null) {
                        model.put("did", newDid);
                        updated++;
                    }
                    col.getModels().save(model);
                } catch (JSONException e) {
                    Timber.e(e, "JSONException updating model");
                }
                break;
            case MODELS_ID_TEMPLATES:
                throw new IllegalArgumentException("Cannot update templates in bulk");
            case MODELS_ID_TEMPLATES_ID:
                Long mid = values.getAsLong(CardTemplate.MODEL_ID);
                Integer ord = values.getAsInteger(CardTemplate.ORD);
                String name = values.getAsString(CardTemplate.NAME);
                String qfmt = values.getAsString(CardTemplate.QUESTION_FORMAT);
                String afmt = values.getAsString(CardTemplate.ANSWER_FORMAT);
                String bqfmt = values.getAsString(CardTemplate.BROWSER_QUESTION_FORMAT);
                String bafmt = values.getAsString(CardTemplate.BROWSER_ANSWER_FORMAT);
                // Throw exception if read-only fields are included
                if (mid != null || ord != null) {
                    throw new IllegalArgumentException("Can update mid or ord");
                }
                // Update the model
                try {
                    Integer templateOrd = Integer.parseInt(uri.getLastPathSegment());
                    JSONObject existingModel = col.getModels().get(getModelIdFromUri(uri, col));
                    JSONArray templates = existingModel.getJSONArray("tmpls");
                    JSONObject template = templates.getJSONObject(templateOrd);
                    if (name != null) {
                        template.put("name", name);
                        updated++;
                    }
                    if (qfmt != null) {
                        template.put("qfmt", qfmt);
                        updated++;
                    }
                    if (afmt != null) {
                        template.put("afmt", afmt);
                        updated++;
                    }
                    if (bqfmt != null) {
                        template.put("bqfmt", bqfmt);
                        updated++;
                    }
                    if (bafmt != null) {
                        template.put("bafmt", bafmt);
                        updated++;
                    }
                    // Save the model
                    templates.put(templateOrd, template);
                    existingModel.put("tmpls", templates);
                    col.getModels().save(existingModel, true);
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Model is malformed", e);
                }
                break;
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
                        Timber.e("Requested card with noteId %d and cardOrd %d was not found. Either the provided " +
                            "noteId/cardOrd were wrong or the card has been deleted in the meantime.", noteID, cardOrd);
                    }
                }
                break;
            }
            case DECKS:
                throw new IllegalArgumentException("Can't update decks in bulk");
            case DECKS_ID:
                throw new UnsupportedOperationException("Not yet implemented");
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
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
        return updated;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        logProviderCall("delete", uri);
        checkAllowedOrThrow(uri, true);

        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col == null) {
            return 0;
        }
        switch (sUriMatcher.match(uri)) {
            case NOTES_ID:
                col.remNotes(new long[]{Long.parseLong(uri.getPathSegments().get(1))});
                return 1;
            default:
                throw new UnsupportedOperationException();
        }
    }

    /**
     * This can be used to insert multiple notes into a single deck. The deck is specified as a query parameter.
     *
     * For example: content://com.ichi2.anki.flashcards/notes?deckId=1234567890123
     *
     * @param uri content Uri
     * @param values for notes uri, it is acceptable for values to contain null items. Such items will be skipped
     * @return number of notes added (does not include existing notes that were updated)
     */
    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        logProviderCall("bulkInsert", uri);
        checkAllowedOrThrow(uri, false);

        // by default, #bulkInsert simply calls insert for each item in #values
        // but in some cases, we want to override this behavior
        int match = sUriMatcher.match(uri);
        if (match == NOTES) {
            String deckIdStr = uri.getQueryParameter(FlashCardsContract.Note.DECK_ID_QUERY_PARAM);
            if (deckIdStr != null) {
                try {
                    long deckId = Long.valueOf(deckIdStr);
                    return bulkInsertNotes(values, deckId);
                } catch (NumberFormatException e) {
                    Timber.d("Invalid %s: %s", FlashCardsContract.Note.DECK_ID_QUERY_PARAM, deckIdStr);
                }
            }
            // deckId not specified, so default to #super implementation (as in spec version 1)
        }
        return super.bulkInsert(uri, values);
    }

    /**
     * This implementation optimizes for when the notes are grouped according to model
     */
    private int bulkInsertNotes(ContentValues[] valuesArr, long deckId) {
        if (valuesArr == null || valuesArr.length == 0) {
            return 0;
        }
        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col == null) {
            return 0;
        }

        Timber.d("performing bulkInsertNotes for " + valuesArr.length + " items");

        // for caching model information (so we don't have to query for each note)
        long modelId = -1L;
        JSONObject model = null;

        col.getDecks().flush(); // is it okay to move this outside the for-loop? Is it needed at all?
        SQLiteDatabase sqldb = col.getDb().getDatabase();
        try {
            int result = 0;
            sqldb.beginTransaction();
            for (int i = 0; i < valuesArr.length; i++) {
                ContentValues values = valuesArr[i];
                if (values == null) {
                    continue;
                }
                String flds = values.getAsString(FlashCardsContract.Note.FLDS);
                if (flds == null) {
                    continue;
                }
                Long thisModelId = values.getAsLong(FlashCardsContract.Note.MID);
                if (thisModelId == null || thisModelId < 0) {
                    Timber.d("Unable to get model at index: " + i);
                    continue;
                }
                String[] fldsArray = Utils.splitFields(flds);

                if (model == null || thisModelId != modelId) {
                    // new modelId so need to recalculate model, modelId and invalidate duplicateChecker (which is based on previous model)
                    model = col.getModels().get(thisModelId);
                    modelId = thisModelId;
                }

                // Create empty note
                com.ichi2.libanki.Note newNote = new com.ichi2.libanki.Note(col, model); // for some reason we cannot pass modelId in here
                // Set fields
                // Check that correct number of flds specified
                if (fldsArray.length != newNote.getFields().length) {
                    throw new IllegalArgumentException("Incorrect flds argument : " + flds);
                }
                for (int idx = 0; idx < fldsArray.length; idx++) {
                    newNote.setField(idx, fldsArray[idx]);
                }
                // Set tags
                String tags = values.getAsString(FlashCardsContract.Note.TAGS);
                if (tags != null) {
                    newNote.setTagsFromStr(tags);
                }
                // Add to collection
                col.addNote(newNote);
                for (Card card : newNote.cards()) {
                    card.setDid(deckId);
                    card.flush();
                }
                result++;
            }
            col.flush(); // TODO is this necessary? Probably better to be safe than sorry
            sqldb.setTransactionSuccessful();
            return result;
        } finally {
            sqldb.endTransaction();
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        logProviderCall("insert", uri);
        checkAllowedOrThrow(uri, false);

        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col == null) {
            return null;
        }

        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        switch (match) {
            case NOTES: {
                /* Insert new note with specified fields and tags
                 */
                Long modelId = values.getAsLong(FlashCardsContract.Note.MID);
                String flds = values.getAsString(FlashCardsContract.Note.FLDS);
                String tags = values.getAsString(FlashCardsContract.Note.TAGS);
                // Create empty note
                com.ichi2.libanki.Note newNote = new com.ichi2.libanki.Note(col, col.getModels().get(modelId));
                // Set fields
                String[] fldsArray = Utils.splitFields(flds);
                // Check that correct number of flds specified
                if (fldsArray.length != newNote.getFields().length) {
                    throw new IllegalArgumentException("Incorrect flds argument : " + flds);
                }
                for (int idx=0; idx < fldsArray.length; idx++) {
                    newNote.setField(idx, fldsArray[idx]);
                }
                // Set tags
                if (tags != null) {
                    newNote.setTagsFromStr(tags);
                }
                // Add to collection
                col.addNote(newNote);
                return Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(newNote.getId()));
            }
            case NOTES_ID:
                // Note ID is generated automatically by libanki
                throw new IllegalArgumentException("Not possible to insert note with specific ID");
            case NOTES_ID_CARDS:
                // Cards are generated automatically by libanki
                throw new IllegalArgumentException("Not possible to insert cards directly (only through NOTES)");
            case NOTES_ID_CARDS_ORD:
                // Cards are generated automatically by libanki
                throw new IllegalArgumentException("Not possible to insert cards directly (only through NOTES)");
            case MODELS:
                // Get input arguments
                String modelName = values.getAsString(FlashCardsContract.Model.NAME);
                String css = values.getAsString(FlashCardsContract.Model.CSS);
                Long did = values.getAsLong(FlashCardsContract.Model.DECK_ID);
                String fieldNames = values.getAsString(FlashCardsContract.Model.FIELD_NAMES);
                Integer numCards = values.getAsInteger(FlashCardsContract.Model.NUM_CARDS);
                // Throw exception if required fields empty
                if (modelName == null || fieldNames == null || numCards == null) {
                    throw new IllegalArgumentException("Model name, field_names, and num_cards can't be empty");
                }
                // Create a new model
                Models mm = col.getModels();
                JSONObject newModel = mm.newModel(modelName);
                try {
                    // Add the fields
                    String[] allFields = Utils.splitFields(fieldNames);
                    for (String f: allFields) {
                        mm.addField(newModel, mm.newField(f));
                    }
                    // Add some empty card templates
                    for (int idx = 0; idx < numCards; idx++) {
                        JSONObject t = mm.newTemplate("Card " + (idx+1));
                        t.put("qfmt",String.format("{{%s}}", allFields[0]));
                        String answerField = allFields[0];
                        if (allFields.length > 1) {
                            answerField = allFields[1];
                        }
                        t.put("afmt",String.format("{{FrontSide}}\\n\\n<hr id=answer>\\n\\n{{%s}}", answerField));
                        mm.addTemplate(newModel, t);
                    }
                    // Add the CSS if specified
                    if (css != null) {
                        newModel.put("css", css);
                    }
                    // Add the did if specified
                    if (did != null) {
                        newModel.put("did", did);
                    }
                    // Add the model to collection (from this point on edits will require a full-sync)
                    mm.add(newModel);
                    mm.save(newModel);  // TODO: is this necessary?
                    mm.flush();
                    // Get the mid and return a URI
                    String mid = Long.toString(newModel.getLong("id"));
                    return Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, mid);
                } catch (ConfirmModSchemaException e) {
                    // This exception should never be thrown when inserting new models
                    Timber.e(e, "Unexpected ConfirmModSchema exception adding new model %s", modelName);
                    throw new IllegalArgumentException("ConfirmModSchema exception adding new model " + modelName);
                } catch (JSONException e) {
                    Timber.e(e, "Could not set a field of new model %s", modelName);
                    return null;
                }
            case MODELS_ID:
                // Model ID is generated automatically by libanki
                throw new IllegalArgumentException("Not possible to insert model with specific ID");
            case MODELS_ID_TEMPLATES:
                // Adding new templates after the model is created could require a full-sync
                throw new IllegalArgumentException("Templates can only be added at the time of model insertion");
            case MODELS_ID_TEMPLATES_ID:
                // Adding new templates after the model is created could require a full-sync
                throw new IllegalArgumentException("Templates can only be added at the time of model insertion");
            case SCHEDULE:
                // Doesn't make sense to insert an object into the schedule table
                throw new IllegalArgumentException("Not possible to perform insert operation on schedule");
            case DECKS:
                // Insert new deck with specified name
                String deckName = values.getAsString(FlashCardsContract.Deck.DECK_NAME);
                did = col.getDecks().id(deckName);
                //col.getDecks().flush(); // have not found a situation where flush() is necessary (so not adding it, yet)
                return Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, Long.toString(did));
            case DECK_SELECTED:
                // Can't have more than one selected deck
                throw new IllegalArgumentException("Selected deck can only be queried and updated");
            case DECKS_ID:
                // Deck ID is generated automatically by libanki
                throw new IllegalArgumentException("Not possible to insert deck with specific ID");
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

    private void addModelToCursor(Long modelId, HashMap<Long, JSONObject> models, MatrixCursor rv, String[] columns) {
        JSONObject jsonObject = models.get(modelId);
        MatrixCursor.RowBuilder rb = rv.newRow();
        try {
            for (String column : columns) {
                if (column.equals(FlashCardsContract.Model._ID)) {
                    rb.add(modelId);
                } else if (column.equals(FlashCardsContract.Model.NAME)) {
                    rb.add(jsonObject.getString("name"));
                } else if (column.equals(FlashCardsContract.Model.FIELD_NAMES)) {
                    JSONArray flds = jsonObject.getJSONArray("flds");
                    String[] allFlds = new String[flds.length()];
                    for (int idx = 0; idx < flds.length(); idx++) {
                        allFlds[idx] = flds.getJSONObject(idx).optString("name", "");
                    }
                    rb.add(Utils.joinFields(allFlds));
                } else if (column.equals(FlashCardsContract.Model.NUM_CARDS)) {
                    rb.add(jsonObject.getJSONArray("tmpls").length());
                } else if (column.equals(FlashCardsContract.Model.CSS)) {
                    rb.add(jsonObject.getString("css"));
                } else if (column.equals(FlashCardsContract.Model.DECK_ID)) {
                    rb.add(jsonObject.getLong("did"));
                } else {
                    throw new UnsupportedOperationException("Column \"" + column + "\" is unknown");
                }
            }
        } catch (JSONException e) {
            Timber.e(e, "Error parsing JSONArray");
            throw new IllegalArgumentException("Model " + modelId + " is malformed", e);
        }
    }

    private void addCardToCursor(Card currentCard, MatrixCursor rv, Collection col, String[] columns) {
        String cardName;
        try {
            cardName = currentCard.template().getString("name");
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
            } else if (column.equals(FlashCardsContract.Card.DECK_ID)) {
                rb.add(currentCard.getDid());
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
            DB db = col.getDb();
            db.getDatabase().beginTransaction();
            try {
                if (cardToAnswer != null) {
                    if(timeTaken != -1){
                        cardToAnswer.setTimerStarted(Utils.now()-timeTaken/1000);
                    }
                    sched.answerCard(cardToAnswer, ease);
                }
                db.getDatabase().setTransactionSuccessful();
            } finally {
                db.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "answerCard - RuntimeException on answering card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAnswerCard");
            return;
        }
    }

    private void addTemplateToCursor(JSONObject tmpl, JSONObject model, int id, MatrixCursor rv, String[] columns) {
        try {
            MatrixCursor.RowBuilder rb = rv.newRow();
            for (String column : columns) {
                if (column.equals(CardTemplate._ID)) {
                    rb.add(id);
                } else if (column.equals(CardTemplate.MODEL_ID)) {
                    rb.add(model.getLong("id"));
                } else if (column.equals(CardTemplate.ORD)) {
                    rb.add(tmpl.getInt("ord"));
                } else if (column.equals(CardTemplate.NAME)) {
                    rb.add(tmpl.getString("name"));
                } else if (column.equals(CardTemplate.QUESTION_FORMAT)) {
                    rb.add(tmpl.getString("qfmt"));
                } else if (column.equals(CardTemplate.ANSWER_FORMAT)) {
                    rb.add(tmpl.getString("afmt"));
                } else if (column.equals(CardTemplate.BROWSER_QUESTION_FORMAT)) {
                    rb.add(tmpl.getString("bqfmt"));
                } else if (column.equals(CardTemplate.BROWSER_ANSWER_FORMAT)) {
                    rb.add(tmpl.getString("bafmt"));
                } else {
                    throw new UnsupportedOperationException("Support for column \"" + column +
                            "\" is not implemented");
                }
            }
        } catch (JSONException e) {
            Timber.e(e, "Error adding template to cursor");
            throw new IllegalArgumentException("Template is malformed", e);
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


    private long getModelIdFromUri(Uri uri, Collection col) {
        String modelIdSegment = uri.getPathSegments().get(1);
        long id;
        if (modelIdSegment.equals(FlashCardsContract.Model.CURRENT_MODEL_ID)) {
            id = col.getModels().current().optLong("id", -1);
        } else {
            try {
                id = Long.parseLong(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Model ID must be either numeric or the String CURRENT_MODEL_ID");
            }
        }
        return id;
    }

    private JSONObject getTemplateFromUri(Uri uri, Collection col) throws JSONException {
        JSONObject model = col.getModels().get(getModelIdFromUri(uri, col));
        Integer ord = Integer.parseInt(uri.getLastPathSegment());
        return model.getJSONArray("tmpls").getJSONObject(ord);
    }

    private void logProviderCall(String methodName, Uri uri) {
        String format = "%s.%s %s";
        String path = uri == null ? null : uri.getPath();
        String msg;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            msg = String.format(format, CardContentProvider.class.getSimpleName(), methodName, path);
        } else {
            msg = String.format(format + " (%s)", CardContentProvider.class.getSimpleName(), methodName, path, getCallingPackage());
        }
        Timber.i(msg);
        Collection col = CollectionHelper.getInstance().getCol(getContext());
        if (col != null) {
            col.log(msg);
        }
    }

    /**
     * Check whether the specified uri is allowed. Two checks:
     *
     * 1. 23+: Everything protected by permission
     * 2. <23: update/delete protected by permission, everything else unprotected
     *
     * @param contentUri the uri
     * @param updateDelete true iff this is an update or delete; false iff query/insert/bulkInsert
     * @return true iff the caller holds the READ_WRITE permission. False iff uri is allowed, but permission is not granted
     * @throws SecurityException if the specified uri is not allowed
     */
    private boolean checkAllowedOrThrow(Uri contentUri, boolean updateDelete) {
        if (getContext().checkCallingPermission(FlashCardsContract.READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            return true; // permission granted, and so allowed
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (getContext().getPackageName().equals(getCallingPackage())) {
                // it's okay for AnkiDroid to access its own provider (e.g. in tests)
                return false; // permission not granted, but allowed anyway
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && !updateDelete) {
            Timber.w("Client does not hold AnkiDroid read/write permission: " + contentUri);
            return false; // permission not granted, but not update/delete so allowed on <23
        }
        throw new SecurityException("Permission not granted for: " + contentUri);
    }
}
