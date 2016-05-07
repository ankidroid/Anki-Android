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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BuildConfig;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.FlashCardsContract.CardTemplate;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

import static com.ichi2.anki.FlashCardsContract.READ_WRITE_PERMISSION;

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
    private Context mContext;

    /* URI types */
    private static final int NOTES = 1000;
    private static final int NOTES_ID = 1001;
    private static final int NOTES_ID_CARDS = 1003;
    private static final int NOTES_ID_CARDS_ORD = 1004;
    private static final int NOTES_V2 = 1005;
    private static final int MODELS = 2000;
    private static final int MODELS_ID = 2001;
    private static final int MODELS_ID_EMPTY_CARDS = 2002;
    private static final int MODELS_ID_TEMPLATES = 2003;
    private static final int MODELS_ID_TEMPLATES_ID = 2004;
    private static final int MODELS_ID_FIELDS = 2005;
    private static final int SCHEDULE = 3000;
    private static final int DECKS = 4000;
    private static final int DECK_SELECTED = 4001;
    private static final int DECKS_ID = 4002;

    private static final UriMatcher sUriMatcher =
            new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // Here you can see all the URIs at a glance
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes_v2", NOTES_V2);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#", NOTES_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#/cards", NOTES_ID_CARDS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "notes/#/cards/#", NOTES_ID_CARDS_ORD);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models", MODELS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*", MODELS_ID); // the model ID can also be "current"
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*/empty_cards", MODELS_ID_EMPTY_CARDS);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*/templates", MODELS_ID_TEMPLATES);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*/templates/#", MODELS_ID_TEMPLATES_ID);
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "models/*/fields", MODELS_ID_FIELDS);
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
    private static final String COL_NULL_ERROR_MSG = "AnkiDroid database inaccessible. Open AnkiDroid to see what's wrong.";

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
        mContext = getContext();
        return true;
    }

    @Override
    public String getType(Uri uri) {
        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        switch (match) {
            case NOTES_V2:
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
            case MODELS_ID_EMPTY_CARDS:
                return FlashCardsContract.Card.CONTENT_TYPE;
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

    /** Only enforce permissions for queries and inserts on Android M and above, or if its a 'rogue client' **/
    private boolean shouldEnforceQueryOrInsertSecurity() {
        return CompatHelper.isMarshmallow() || knownRogueClient();
    }
    /** Enforce permissions for all updates on Android M and above. Otherwise block depending on URI and client app **/
    private boolean shouldEnforceUpdateSecurity(Uri uri) {
        final List<Integer> WHITELIST = Arrays.asList(NOTES_ID_CARDS_ORD, MODELS_ID, MODELS_ID_TEMPLATES_ID, SCHEDULE, DECK_SELECTED);
        return CompatHelper.isMarshmallow() || !WHITELIST.contains(sUriMatcher.match(uri)) || knownRogueClient();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String order) {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("query", uri);
        }

        Collection col = CollectionHelper.getInstance().getCol(mContext);
        if (col == null) {
            throw new IllegalStateException(COL_NULL_ERROR_MSG);
        }
        Timber.d(getLogMessage("query", uri));

        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        switch (match) {
            case NOTES_V2: {
                /* Search for notes using direct SQL query */
                String[] proj = sanitizeNoteProjection(projection);
                return col.getDb().getDatabase().query("notes", proj, selection, selectionArgs, null, null, order);
            }
            case NOTES: {
                /* Search for notes using the libanki browser syntax */
                String[] proj = sanitizeNoteProjection(projection);
                String query = (selection != null) ? selection : "";
                List<Long> noteIds = col.findNotes(query);
                if ((noteIds != null) && (!noteIds.isEmpty())) {
                    String sel = String.format("id in (%s)", TextUtils.join(",", noteIds));
                    return col.getDb().getDatabase().query("notes", proj, sel, null, null, null, order);
                } else {
                    return null;
                }
            }
            case NOTES_ID: {
                /* Direct access note with specific ID*/
                String noteId = uri.getPathSegments().get(1);
                String[] proj = sanitizeNoteProjection(projection);
                return col.getDb().getDatabase().query("notes", proj, "id=?", new String[]{noteId}, null, null, order);
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
                Models models = col.getModels();
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Model.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                for (Long modelId : models.getModels().keySet()) {
                    addModelToCursor(modelId, models, rv, columns);
                }
                return rv;
            }
            case MODELS_ID: {
                long modelId = getModelIdFromUri(uri, col);
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Model.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                addModelToCursor(modelId, col.getModels(), rv, columns);
                return rv;
            }
            case MODELS_ID_TEMPLATES: {
                /* Direct access model templates */
                Models models = col.getModels();
                JSONObject currentModel = models.get(getModelIdFromUri(uri, col));
                String[] columns = ((projection != null) ? projection : CardTemplate.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                try {
                    JSONArray templates = currentModel.getJSONArray("tmpls");
                    for (int idx = 0; idx < templates.length(); idx++) {
                        JSONObject template = templates.getJSONObject(idx);
                        addTemplateToCursor(template, currentModel, idx+1, models, rv, columns);
                    }
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Model is malformed", e);
                }
                return rv;
            }
            case MODELS_ID_TEMPLATES_ID: {
                /* Direct access model template with specific ID */
                Models models = col.getModels();
                int ord = Integer.parseInt(uri.getLastPathSegment());
                JSONObject currentModel = models.get(getModelIdFromUri(uri, col));
                String[] columns = ((projection != null) ? projection : CardTemplate.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                try {
                    JSONObject template = getTemplateFromUri(uri, col);
                    addTemplateToCursor(template, currentModel, ord+1, models, rv, columns);
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
                            buttonTexts.put(col.getSched().nextIvlStr(mContext, currentCard, i + 1));
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
                /* Direct access deck */
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
        if (!hasReadWritePermission() && shouldEnforceUpdateSecurity(uri)) {
            throwSecurityException("update", uri);
        }
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        if (col == null) {
            throw new IllegalStateException(COL_NULL_ERROR_MSG);
        }
        col.log(getLogMessage("update", uri));

        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);
        int updated = 0; // Number of updated entries (return value)
        switch (match) {
            case NOTES_V2:
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
                if (col.getDecks().isDyn(did)) {
                    throw new IllegalArgumentException("Cards cannot be moved to a filtered deck");
                }
                /* now update the card
                 */
                if ((isDeckUpdate) && (did >= 0)) {
                    Timber.d("CardContentProvider: Moving card to other deck...");
                    col.getDecks().flush();
                    currentCard.setDid(did);
                    currentCard.flush();
                    col.save();
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
                Integer newSortf = values.getAsInteger(FlashCardsContract.Model.SORT_FIELD_INDEX);
                Integer newType = values.getAsInteger(FlashCardsContract.Model.TYPE);
                String newLatexPost = values.getAsString(FlashCardsContract.Model.LATEX_POST);
                String newLatexPre = values.getAsString(FlashCardsContract.Model.LATEX_PRE);
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
                        if (col.getDecks().isDyn(Long.parseLong(newDid))) {
                            throw new IllegalArgumentException("Cannot set a filtered deck as default deck for a model");
                        }
                        model.put("did", newDid);
                        updated++;
                    }
                    if (newSortf != null) {
                        model.put("sortf", newSortf);
                        updated++;
                    }
                    if (newType != null) {
                        model.put("type", newType);
                        updated++;
                    }
                    if (newLatexPost != null) {
                        model.put("latexPost", newLatexPost);
                        updated++;
                    }
                    if (newLatexPre != null) {
                        model.put("latexPre", newLatexPre);
                        updated++;
                    }
                    col.getModels().save(model);
                    col.save();
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
                    throw new IllegalArgumentException("Updates to mid or ord are not allowed");
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
                    col.save();
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
                // TODO: be sure to throw exception if change to the dyn value of a deck is requested
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
                col.save();
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
        if (!hasReadWritePermission()) {
            throwSecurityException("delete", uri);
        }
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        if (col == null) {
            throw new IllegalStateException(COL_NULL_ERROR_MSG);
        }
        col.log(getLogMessage("delete", uri));

        switch (sUriMatcher.match(uri)) {
            case NOTES_ID:
                col.remNotes(new long[]{Long.parseLong(uri.getPathSegments().get(1))});
                return 1;
            case MODELS_ID_EMPTY_CARDS:
                JSONObject model = col.getModels().get(getModelIdFromUri(uri, col));
                if (model == null) {
                    return -1;
                }
                List<Long> cids = col.genCards(col.getModels().nids(model));
                col.remCards(Utils.arrayList2array(cids));
                return cids.size();
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
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("bulkInsert", uri);
        }

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
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        if (col == null) {
            throw new IllegalStateException(COL_NULL_ERROR_MSG);
        }
        if (col.getDecks().isDyn(deckId)) {
            throw new IllegalArgumentException("A filtered deck cannot be specified as the deck in bulkInsertNotes");
        }
        col.log(String.format(Locale.US, "bulkInsertNotes: %d items.\n%s", valuesArr.length, getLogMessage("bulkInsert", null)));

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
            col.save();
            sqldb.setTransactionSuccessful();
            return result;
        } finally {
            sqldb.endTransaction();
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("insert", uri);
        }
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        if (col == null) {
            throw new IllegalStateException(COL_NULL_ERROR_MSG);
        }
        col.log(getLogMessage("insert", uri));

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
                col.save();
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
                Integer sortf = values.getAsInteger(FlashCardsContract.Model.SORT_FIELD_INDEX);
                Integer type = values.getAsInteger(FlashCardsContract.Model.TYPE);
                String latexPost = values.getAsString(FlashCardsContract.Model.LATEX_POST);
                String latexPre = values.getAsString(FlashCardsContract.Model.LATEX_PRE);
                // Throw exception if required fields empty
                if (modelName == null || fieldNames == null || numCards == null) {
                    throw new IllegalArgumentException("Model name, field_names, and num_cards can't be empty");
                }
                if (did != null && col.getDecks().isDyn(did)) {
                    throw new IllegalArgumentException("Cannot set a filtered deck as default deck for a model");
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
                    if (sortf != null && sortf < allFields.length) {
                        newModel.put("sortf", sortf);
                    }
                    if (type != null) {
                        newModel.put("type", type);
                    }
                    if (latexPost != null) {
                        newModel.put("latexPost", latexPost);
                    }
                    if (latexPre != null) {
                        newModel.put("latexPre", latexPre);
                    }
                    // Add the model to collection (from this point on edits will require a full-sync)
                    mm.add(newModel);
                    col.save();
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
            case MODELS_ID_TEMPLATES: {
                Models models = col.getModels();
                Long mid = getModelIdFromUri(uri, col);
                JSONObject existingModel = models.get(mid);
                if (existingModel == null) {
                    throw new IllegalArgumentException("model missing: " + mid);
                }
                String name = values.getAsString(CardTemplate.NAME);
                String qfmt = values.getAsString(CardTemplate.QUESTION_FORMAT);
                String afmt = values.getAsString(CardTemplate.ANSWER_FORMAT);
                String bqfmt = values.getAsString(CardTemplate.BROWSER_QUESTION_FORMAT);
                String bafmt = values.getAsString(CardTemplate.BROWSER_ANSWER_FORMAT);
                try {
                    JSONObject t = models.newTemplate(name);
                    try {
                        t.put("qfmt", qfmt);
                        t.put("afmt", afmt);
                        t.put("bqfmt", bqfmt);
                        t.put("bafmt", bafmt);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    models.addTemplate(existingModel, t);
                    models.save(existingModel);
                    col.save();
                    return ContentUris.withAppendedId(uri, t.getInt("ord"));
                } catch (ConfirmModSchemaException e) {
                    throw new IllegalArgumentException("Unable to add template", e);
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Unable to get ord from new template", e);
                }
            }
            case MODELS_ID_TEMPLATES_ID:
                throw new IllegalArgumentException("Not possible to insert template with specific ORD");
            case MODELS_ID_FIELDS: {
                Models models = col.getModels();
                Long mid = getModelIdFromUri(uri, col);
                JSONObject existingModel = models.get(mid);
                if (existingModel == null) {
                    throw new IllegalArgumentException("model missing: " + mid);
                }
                String name = values.getAsString(FlashCardsContract.Model.FIELD_NAME);
                if (name == null) {
                    throw new IllegalArgumentException("field name missing for model: " + mid);
                }
                JSONObject field = models.newField(name);
                try {
                    models.addField(existingModel, field);
                    col.save();
                    JSONArray ja = existingModel.getJSONArray("flds");
                    return ContentUris.withAppendedId(uri, ja.length() - 1);
                } catch (ConfirmModSchemaException e) {
                    throw new IllegalArgumentException("Unable to insert field: " + name, e);
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Unable to get newly created field: " + name, e);
                }
            }
            case SCHEDULE:
                // Doesn't make sense to insert an object into the schedule table
                throw new IllegalArgumentException("Not possible to perform insert operation on schedule");
            case DECKS:
                // Insert new deck with specified name
                String deckName = values.getAsString(FlashCardsContract.Deck.DECK_NAME);
                did = col.getDecks().id(deckName, false);
                if (did != null) {
                    throw new IllegalArgumentException("Deck name already exists: " + deckName);
                }
                did = col.getDecks().id(deckName, true);
                JSONObject deck = col.getDecks().get(did);
                if (deck != null) {
                    try {
                        String deckDesc = values.getAsString(FlashCardsContract.Deck.DECK_DESC);
                        if (deckDesc != null) {
                            deck.put("desc", deckDesc);
                        }
                    } catch (JSONException e) {
                        Timber.e(e, "Could not set a field of new deck %s", deckName);
                        return null;
                    }
                }
                col.getDecks().flush();
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

    private static String[] sanitizeNoteProjection(String[] projection) {
        if (projection == null || projection.length == 0) {
            return sDefaultNoteProjectionDBAccess;
        }
        List<String> sanitized = new ArrayList<>();
        for (String column : projection) {
            int idx = projSearch(FlashCardsContract.Note.DEFAULT_PROJECTION, column);
            if (idx >= 0) {
                sanitized.add(sDefaultNoteProjectionDBAccess[idx]);
            } else {
                throw new IllegalArgumentException("Unknown column " + column);
            }
        }
        return sanitized.toArray(new String[sanitized.size()]);
    }

    private static int projSearch(String[] projection, String column) {
        for (int i = 0; i < projection.length; i++) {
            if (projection[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    private void addModelToCursor(Long modelId, Models models, MatrixCursor rv, String[] columns) {
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
                } else if (column.equals(FlashCardsContract.Model.SORT_FIELD_INDEX)) {
                    rb.add(jsonObject.getLong("sortf"));
                } else if (column.equals(FlashCardsContract.Model.TYPE)) {
                    rb.add(jsonObject.getLong("type"));
                } else if (column.equals(FlashCardsContract.Model.LATEX_POST)) {
                    rb.add(jsonObject.getString("latexPost"));
                } else if (column.equals(FlashCardsContract.Model.LATEX_PRE)) {
                    rb.add(jsonObject.getString("latexPre"));
                } else if (column.equals(FlashCardsContract.Model.NOTE_COUNT)) {
                    rb.add(models.useCount(jsonObject));
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

    private void addTemplateToCursor(JSONObject tmpl, JSONObject model, int id, Models models, MatrixCursor rv, String[] columns) {
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
                } else if (column.equals(CardTemplate.CARD_COUNT)) {
                    rb.add(models.tmplUseCount(model, tmpl.getInt("ord")));
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
            }else if (column.equals(FlashCardsContract.Deck.DECK_DYN)) {
                rb.add(col.getDecks().isDyn(id));
            }else if (column.equals(FlashCardsContract.Deck.DECK_DESC)) {
                String desc = col.getDecks().getActualDescription();
                rb.add(desc);
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

    private void throwSecurityException(String methodName, Uri uri) {
        String msg = String.format("Permission not granted for: %s", getLogMessage(methodName, uri));
        Timber.e(msg);
        throw new SecurityException(msg);
    }

    private String getLogMessage(String methodName, Uri uri) {
        final String format = "%s.%s %s (%s)";
        String path = uri == null ? null : uri.getPath();
        return String.format(format, getClass().getSimpleName(), methodName, path, getCallingPackageSafe());
    }

    private boolean hasReadWritePermission() {
        if (BuildConfig.DEBUG) {    // Allow self-calling of the provider only in debug builds (e.g. for unit tests)
            return mContext.checkCallingOrSelfPermission(READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        }
        return mContext.checkCallingPermission(READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }


    /** Returns true if the calling package is known to be "rogue" and should be blocked.
     Calling package might be rogue if it has not declared #READ_WRITE_PERMISSION in its manifest, or if blacklisted **/
    private boolean knownRogueClient() {
        final PackageManager pm = mContext.getPackageManager();
        try {
            PackageInfo callingPi = pm.getPackageInfo(getCallingPackageSafe(), PackageManager.GET_PERMISSIONS);
             if (callingPi == null || callingPi.requestedPermissions == null) {
                 return false;
             }
             return !Arrays.asList(callingPi.requestedPermissions).contains(READ_WRITE_PERMISSION);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Nullable
    private String getCallingPackageSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return getCallingPackage();
        }
        String[] pkgs = mContext.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (pkgs.length == 1) {
            return pkgs[0]; // This is usual case, unless multiple packages signed with same key & using "sharedUserId"
        }
        return null;
    }
}
