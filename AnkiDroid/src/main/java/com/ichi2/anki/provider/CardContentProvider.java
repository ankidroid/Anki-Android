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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import androidx.annotation.NonNull;

import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BuildConfig;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.FlashCardsContract.CardTemplate;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.ModelManager;
import com.ichi2.libanki.backend.exception.DeckRenameException;
import com.ichi2.libanki.exception.EmptyMediaException;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;

import com.ichi2.libanki.Deck;
import com.ichi2.libanki.sched.DeckDueTreeNode;
import com.ichi2.utils.FileUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import androidx.sqlite.db.SupportSQLiteDatabase;
import timber.log.Timber;

import static com.ichi2.anki.FlashCardsContract.READ_WRITE_PERMISSION;
import static com.ichi2.libanki.Models.NOT_FOUND_NOTE_TYPE;

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
 * .../media (add media files to anki collection.media)
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
    private static final int MEDIA = 5000;

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
        sUriMatcher.addURI(FlashCardsContract.AUTHORITY, "media", MEDIA);
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
    public String getType(@NonNull Uri uri) {
        // Find out what data the user is requesting
        int match = sUriMatcher.match(uri);

        switch (match) {
            case NOTES_V2:
            case NOTES:
                return FlashCardsContract.Note.CONTENT_TYPE;
            case NOTES_ID:
                return FlashCardsContract.Note.CONTENT_ITEM_TYPE;
            case NOTES_ID_CARDS:
            case MODELS_ID_EMPTY_CARDS:
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
            case DECK_SELECTED:
            case DECKS_ID:
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
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String order) {
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
                String sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, selection, null, null, order, null);
                //noinspection RedundantCast
                return col.getDb().query(sql, (Object[]) selectionArgs); // Needed for varargs of query
            }
            case NOTES: {
                /* Search for notes using the libanki browser syntax */
                String[] proj = sanitizeNoteProjection(projection);
                String query = (selection != null) ? selection : "";
                List<Long> noteIds = col.findNotes(query);
                if ((noteIds != null) && (!noteIds.isEmpty())) {
                    String sel = String.format("id in (%s)", TextUtils.join(",", noteIds));
                    String sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, sel, null, null, order, null);
                    return col.getDb().getDatabase().query(sql);
                } else {
                    return null;
                }
            }
            case NOTES_ID: {
                /* Direct access note with specific ID*/
                String noteId = uri.getPathSegments().get(1);
                String[] proj = sanitizeNoteProjection(projection);
                String sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, "id=?", null, null, order, null);
                return col.getDb().query(sql, noteId);
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
                ModelManager models = col.getModels();
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
                ModelManager models = col.getModels();
                Model currentModel = models.get(getModelIdFromUri(uri, col));
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
                ModelManager models = col.getModels();
                int ord = Integer.parseInt(uri.getLastPathSegment());
                Model currentModel = models.get(getModelIdFromUri(uri, col));
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
                            String value = "?".equals(keyAndValue[1].trim()) ? selectionArgs[selectionArgIndex++] :
                                    keyAndValue[1];
                            if ("limit".equals(keyAndValue[0].trim())) {
                                limit = Integer.parseInt(value);
                            } else if ("deckID".equals(keyAndValue[0].trim())) {
                                deckIdOfTemporarilySelectedDeck = Long.parseLong(value);
                                if(!selectDeckWithCheck(col, deckIdOfTemporarilySelectedDeck)){
                                    return rv; //if the provided deckID is wrong, return empty cursor.
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            Timber.w(nfe);
                        }
                    }
                }

                //retrieve the number of cards provided by the selection parameter "limit"
                col.getSched().deferReset();
                for (int k = 0; k< limit; k++){
                    Card currentCard = col.getSched().getCard();

                    if (currentCard == null) {
                        break;
                    }
                    int buttonCount = col.getSched().answerButtons(currentCard);
                    JSONArray buttonTexts = new JSONArray();
                    for (int i = 0; i < buttonCount; i++) {
                        buttonTexts.put(col.getSched().nextIvlStr(mContext, currentCard, i + 1));
                    }
                    addReviewInfoToCursor(currentCard, buttonTexts, buttonCount, rv, col, columns);
                }

                if (deckIdOfTemporarilySelectedDeck != -1) {//if the selected deck was changed
                    //change the selected deck back to the one it was before the query
                    col.getDecks().select(selectedDeckBeforeQuery);
                }
                return rv;
            }
            case DECKS: {
                List<DeckDueTreeNode> allDecks = col.getSched().deckDueList();
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, allDecks.size());
                for (DeckDueTreeNode deck : allDecks) {
                    long id = deck.getDid();
                    String name = deck.getFullDeckName();
                    addDeckToCursor(id, name, getDeckCountsFromDueTreeNode(deck), rv, col, columns);
                }
                return rv;
            }
            case DECKS_ID: {
                /* Direct access deck */
                String[] columns = ((projection != null) ? projection : FlashCardsContract.Deck.DEFAULT_PROJECTION);
                MatrixCursor rv = new MatrixCursor(columns, 1);
                List<DeckDueTreeNode> allDecks = col.getSched().deckDueList();
                long deckId = Long.parseLong(uri.getPathSegments().get(1));
                for (DeckDueTreeNode deck : allDecks) {
                    if(deck.getDid() == deckId){
                        addDeckToCursor(deckId, deck.getFullDeckName(), getDeckCountsFromDueTreeNode(deck), rv, col, columns);
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
                JSONArray counts = new JSONArray(Collections.singletonList(col.getSched().counts()));
                addDeckToCursor(id, name, counts,rv, col, columns);
                return rv;
            }
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
    }

    private JSONArray getDeckCountsFromDueTreeNode(DeckDueTreeNode deck){
        JSONArray deckCounts = new JSONArray();
        deckCounts.put(deck.getLrnCount());
        deckCounts.put(deck.getRevCount());
        deckCounts.put(deck.getNewCount());
        return deckCounts;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
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
                long did = Decks.NOT_FOUND_DECK_ID;
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
                Model model = col.getModels().get(getModelIdFromUri(uri, col));
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
                    int templateOrd = Integer.parseInt(uri.getLastPathSegment());
                    Model existingModel = col.getModels().get(getModelIdFromUri(uri, col));
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
                int bury = -1;
                int suspend = -1;
                for (Map.Entry<String, Object> entry : valueSet) {
                    String key = entry.getKey();

                    switch (key) {
                        case FlashCardsContract.ReviewInfo.NOTE_ID:
                            noteID = values.getAsLong(key);
                            break;
                        case FlashCardsContract.ReviewInfo.CARD_ORD:
                            cardOrd = values.getAsInteger(key);
                            break;
                        case FlashCardsContract.ReviewInfo.EASE:
                            ease = values.getAsInteger(key);
                            break;
                        case FlashCardsContract.ReviewInfo.TIME_TAKEN:
                            timeTaken = values.getAsLong(key);
                            break;
                        case FlashCardsContract.ReviewInfo.BURY:
                            bury = values.getAsInteger(key);
                            break;
                        case FlashCardsContract.ReviewInfo.SUSPEND:
                            suspend = values.getAsInteger(key);
                            break;
                    }
                }
                if (cardOrd != -1 && noteID != -1) {
                    Card cardToAnswer = getCard(noteID, cardOrd, col);
                    if(cardToAnswer != null) {
                        if( bury == 1 ) {
                            // bury card
                            buryOrSuspendCard(col, col.getSched(), cardToAnswer, true);
                        } else if (suspend == 1) {
                            // suspend card
                            buryOrSuspendCard(col, col.getSched(), cardToAnswer, false);
                        } else {
                            answerCard(col, col.getSched(), cardToAnswer, ease, timeTaken);
                        }
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
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
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
                Model model = col.getModels().get(getModelIdFromUri(uri, col));
                if (model == null) {
                    return -1;
                }
                List<Long> cids = col.genCards(col.getModels().nids(model), model);
                col.remCards(cids);
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
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
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
                    long deckId = Long.parseLong(deckIdStr);
                    return bulkInsertNotes(values, deckId);
                } catch (NumberFormatException e) {
                    Timber.d(e,"Invalid %s: %s", FlashCardsContract.Note.DECK_ID_QUERY_PARAM, deckIdStr);
                }
            }
            // deckId not specified, so default to #super implementation (as in spec version 1)
        }
        return super.bulkInsert(uri, values);
    }

    /**
     * This implementation optimizes for when the notes are grouped according to model.
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
        long modelId = NOT_FOUND_NOTE_TYPE;
        Model model = null;

        col.getDecks().flush(); // is it okay to move this outside the for-loop? Is it needed at all?
        SupportSQLiteDatabase sqldb = col.getDb().getDatabase();
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
                Models.AllowEmpty allowEmpty = Models.AllowEmpty.fromBoolean(values.getAsBoolean(FlashCardsContract.Note.ALLOW_EMPTY));
                Long thisModelId = values.getAsLong(FlashCardsContract.Note.MID);
                if (thisModelId == null || thisModelId < 0) {
                    Timber.d("Unable to get model at index: %d", i);
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
                col.addNote(newNote, allowEmpty);
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
            DB.safeEndInTransaction(sqldb);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
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
                Models.AllowEmpty allowEmpty = Models.AllowEmpty.fromBoolean(values.getAsBoolean(FlashCardsContract.Note.ALLOW_EMPTY));
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
                col.addNote(newNote, allowEmpty);
                col.save();
                return Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(newNote.getId()));
            }
            case NOTES_ID:
                // Note ID is generated automatically by libanki
                throw new IllegalArgumentException("Not possible to insert note with specific ID");
            case NOTES_ID_CARDS:
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
                ModelManager mm = col.getModels();
                Model newModel = mm.newModel(modelName);
                try {
                    // Add the fields
                    String[] allFields = Utils.splitFields(fieldNames);
                    for (String f: allFields) {
                        mm.addFieldInNewModel(newModel, mm.newField(f));
                    }
                    // Add some empty card templates
                    for (int idx = 0; idx < numCards; idx++) {
                        String card_name = mContext.getResources().getString(R.string.card_n_name, idx + 1);
                        JSONObject t = Models.newTemplate(card_name);
                        t.put("qfmt",String.format("{{%s}}", allFields[0]));
                        String answerField = allFields[0];
                        if (allFields.length > 1) {
                            answerField = allFields[1];
                        }
                        t.put("afmt",String.format("{{FrontSide}}\\n\\n<hr id=answer>\\n\\n{{%s}}", answerField));
                        mm.addTemplateInNewModel(newModel, t);
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
                } catch (JSONException e) {
                    Timber.e(e, "Could not set a field of new model %s", modelName);
                    return null;
                }
            case MODELS_ID:
                // Model ID is generated automatically by libanki
                throw new IllegalArgumentException("Not possible to insert model with specific ID");
            case MODELS_ID_TEMPLATES: {
                ModelManager models = col.getModels();
                Long mid = getModelIdFromUri(uri, col);
                Model existingModel = models.get(mid);
                if (existingModel == null) {
                    throw new IllegalArgumentException("model missing: " + mid);
                }
                String name = values.getAsString(CardTemplate.NAME);
                String qfmt = values.getAsString(CardTemplate.QUESTION_FORMAT);
                String afmt = values.getAsString(CardTemplate.ANSWER_FORMAT);
                String bqfmt = values.getAsString(CardTemplate.BROWSER_QUESTION_FORMAT);
                String bafmt = values.getAsString(CardTemplate.BROWSER_ANSWER_FORMAT);
                try {
                    JSONObject t = Models.newTemplate(name);
                    t.put("qfmt", qfmt);
                    t.put("afmt", afmt);
                    t.put("bqfmt", bqfmt);
                    t.put("bafmt", bafmt);
                    models.addTemplate(existingModel, t);
                    models.save(existingModel);
                    col.save();
                    return ContentUris.withAppendedId(uri, t.getInt("ord"));
                } catch (ConfirmModSchemaException e) {
                    throw new IllegalArgumentException("Unable to add template without user requesting/accepting full-sync", e);
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Unable to get ord from new template", e);
                }
            }
            case MODELS_ID_TEMPLATES_ID:
                throw new IllegalArgumentException("Not possible to insert template with specific ORD");
            case MODELS_ID_FIELDS: {
                ModelManager models = col.getModels();
                long mid = getModelIdFromUri(uri, col);
                Model existingModel = models.get(mid);
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
                    JSONArray flds = existingModel.getJSONArray("flds");
                    return ContentUris.withAppendedId(uri, flds.length() - 1);
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
                did = col.getDecks().id_for_name(deckName);
                if (did != null) {
                    throw new IllegalArgumentException("Deck name already exists: " + deckName);
                }
                if (!Decks.isValidDeckName(deckName)) {
                    throw new IllegalArgumentException("Invalid deck name '" + deckName + "'");
                }
                try {
                    did = col.getDecks().id(deckName);
                } catch (DeckRenameException filteredSubdeck) {
                    throw new IllegalArgumentException(filteredSubdeck.getMessage());
                }
                Deck deck = col.getDecks().get(did);
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
            case MEDIA:
                // insert a media file
                // contentvalue should have data and preferredFileName values
                return insertMediaFile(values, col);
            default:
                // Unknown URI type
                throw new IllegalArgumentException("uri " + uri + " is not supported");
        }
    }

    private Uri insertMediaFile(ContentValues values, Collection col) {
        // Insert media file using libanki.Media.addFile and return Uri for the inserted file.
        Uri fileUri = Uri.parse(values.getAsString(FlashCardsContract.AnkiMedia.FILE_URI));
        String preferredName = values.getAsString(FlashCardsContract.AnkiMedia.PREFERRED_NAME);


        try {
            ContentResolver cR = mContext.getContentResolver();
            Media media = col.getMedia();
            // idea, open input stream and save to cache directory, then
            // pass this (hopefully temporary) file to the media.addFile function.

            String fileMimeType = MimeTypeMap.getSingleton().getExtensionFromMimeType(cR.getType(fileUri)); // return eg "jpeg"
            // should we be enforcing strict mimetypes? which types?
            File tempFile;
            File externalCacheDir = mContext.getExternalCacheDir();
            if (externalCacheDir == null) {
                Timber.e("createUI() unable to get external cache directory");
                return null;
            }
            File tempMediaDir = new File(externalCacheDir.getAbsolutePath() + "/temp-media");
            if (!tempMediaDir.exists() && !tempMediaDir.mkdir()) {
                Timber.e("temp-media dir did not exist and could not be created");
                return null;
            }
            try {
                tempFile = File.createTempFile(
                        preferredName+"_", // the beginning of the filename.
                        "." + fileMimeType, // this is the extension, if null, '.tmp' is used, need to get the extension from MIME type?
                        tempMediaDir
                );
                tempFile.deleteOnExit();
            } catch (Exception e) {
                Timber.w(e, "Could not create temporary media file. ");
                return null;
            }

            FileUtil.internalizeUri(fileUri, tempFile, cR);

            String fname = media.addFile(tempFile);
            Timber.d("insert -> MEDIA: fname = %s", fname);
            File f = new File(fname);
            Timber.d("insert -> MEDIA: f = %s", f);
            Uri uriFromF = Uri.fromFile(f);
            Timber.d("insert -> MEDIA: uriFromF = %s", uriFromF);
            return Uri.fromFile(new File(fname));

        } catch (IOException | EmptyMediaException e) {
            Timber.w(e, "insert failed from %s", fileUri);
            return null;
        }
    }

    private static String[] sanitizeNoteProjection(String[] projection) {
        if (projection == null || projection.length == 0) {
            return sDefaultNoteProjectionDBAccess;
        }
        List<String> sanitized = new ArrayList<>(projection.length);
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

    private void addModelToCursor(Long modelId, ModelManager models, MatrixCursor rv, String[] columns) {
        Model jsonObject = models.get(modelId);
        MatrixCursor.RowBuilder rb = rv.newRow();
        try {
            for (String column : columns) {
                switch (column) {
                    case FlashCardsContract.Model._ID:
                        rb.add(modelId);
                        break;
                    case FlashCardsContract.Model.NAME:
                        rb.add(jsonObject.getString("name"));
                        break;
                    case FlashCardsContract.Model.FIELD_NAMES:
                        JSONArray flds = jsonObject.getJSONArray("flds");
                        String[] allFlds = new String[flds.length()];
                        for (int idx = 0; idx < flds.length(); idx++) {
                            allFlds[idx] = flds.getJSONObject(idx).optString("name", "");
                        }
                        rb.add(Utils.joinFields(allFlds));
                        break;
                    case FlashCardsContract.Model.NUM_CARDS:
                        rb.add(jsonObject.getJSONArray("tmpls").length());
                        break;
                    case FlashCardsContract.Model.CSS:
                        rb.add(jsonObject.getString("css"));
                        break;
                    case FlashCardsContract.Model.DECK_ID:
                        //#6378 - Anki Desktop changed schema temporarily to allow null
                        rb.add(jsonObject.optLong("did", Consts.DEFAULT_DECK_ID));
                        break;
                    case FlashCardsContract.Model.SORT_FIELD_INDEX:
                        rb.add(jsonObject.getLong("sortf"));
                        break;
                    case FlashCardsContract.Model.TYPE:
                        rb.add(jsonObject.getLong("type"));
                        break;
                    case FlashCardsContract.Model.LATEX_POST:
                        rb.add(jsonObject.getString("latexPost"));
                        break;
                    case FlashCardsContract.Model.LATEX_PRE:
                        rb.add(jsonObject.getString("latexPre"));
                        break;
                    case FlashCardsContract.Model.NOTE_COUNT:
                        rb.add(models.useCount(jsonObject));
                        break;
                    default:
                        throw new UnsupportedOperationException("Queue \"" + column + "\" is unknown");
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
            switch (column) {
                case FlashCardsContract.Card.NOTE_ID:
                    rb.add(currentCard.note().getId());
                    break;
                case FlashCardsContract.Card.CARD_ORD:
                    rb.add(currentCard.getOrd());
                    break;
                case FlashCardsContract.Card.CARD_NAME:
                    rb.add(cardName);
                    break;
                case FlashCardsContract.Card.DECK_ID:
                    rb.add(currentCard.getDid());
                    break;
                case FlashCardsContract.Card.QUESTION:
                    rb.add(question);
                    break;
                case FlashCardsContract.Card.ANSWER:
                    rb.add(answer);
                    break;
                case FlashCardsContract.Card.QUESTION_SIMPLE:
                    rb.add(currentCard.qSimple());
                    break;
                case FlashCardsContract.Card.ANSWER_SIMPLE:
                    rb.add(currentCard.render_output(false).getAnswerText());
                    break;
                case FlashCardsContract.Card.ANSWER_PURE:
                    rb.add(currentCard.getPureAnswer());
                    break;
                default:
                    throw new UnsupportedOperationException("Queue \"" + column + "\" is unknown");
            }
        }
    }

    private void addReviewInfoToCursor(Card currentCard, JSONArray nextReviewTimesJson, int buttonCount,MatrixCursor rv, Collection col, String[] columns) {
        MatrixCursor.RowBuilder rb = rv.newRow();
        for (String column : columns) {
            switch (column) {
                case FlashCardsContract.Card.NOTE_ID:
                    rb.add(currentCard.note().getId());
                    break;
                case FlashCardsContract.ReviewInfo.CARD_ORD:
                    rb.add(currentCard.getOrd());
                    break;
                case FlashCardsContract.ReviewInfo.BUTTON_COUNT:
                    rb.add(buttonCount);
                    break;
                case FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES:
                    rb.add(nextReviewTimesJson.toString());
                    break;
                case FlashCardsContract.ReviewInfo.MEDIA_FILES:
                    rb.add(new JSONArray(col.getMedia().filesInStr(currentCard.note().getMid(), currentCard.q() + currentCard.a())));
                    break;
                default:
                    throw new UnsupportedOperationException("Queue \"" + column + "\" is unknown");
            }
        }
    }

    private void answerCard(Collection col, AbstractSched sched, Card cardToAnswer, @Consts.BUTTON_TYPE int ease, long timeTaken) {
        try {
            DB db = col.getDb();
            db.getDatabase().beginTransaction();
            try {
                if (cardToAnswer != null) {
                    if(timeTaken != -1){
                        cardToAnswer.setTimerStarted(col.getTime().intTimeMS()-timeTaken);
                    }
                    sched.answerCard(cardToAnswer, ease);
                }
                db.getDatabase().setTransactionSuccessful();
            } finally {
                DB.safeEndInTransaction(db);
            }
        } catch (RuntimeException e) {
            Timber.e(e, "answerCard - RuntimeException on answering card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAnswerCard");
        }
    }


    private void buryOrSuspendCard(Collection col, AbstractSched sched, Card card, boolean bury) {
        try {
            col.getDb().executeInTransaction(() -> {
                if (card != null) {
                    if(bury) {
                        // bury
                        sched.buryCards(new long[] {card.getId()});
                    } else {
                        // suspend
                        sched.suspendCards(new long[] {card.getId()});
                    }
                }
            });
        } catch (RuntimeException e) {
            Timber.e(e, "buryOrSuspendCard - RuntimeException on burying or suspending card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundBurySuspendCard");
        }
    }

    private void addTemplateToCursor(JSONObject tmpl, Model model, int id, ModelManager models, MatrixCursor rv, String[] columns) {
        try {
            MatrixCursor.RowBuilder rb = rv.newRow();
            for (String column : columns) {
                switch (column) {
                    case CardTemplate._ID:
                        rb.add(id);
                        break;
                    case CardTemplate.MODEL_ID:
                        rb.add(model.getLong("id"));
                        break;
                    case CardTemplate.ORD:
                        rb.add(tmpl.getInt("ord"));
                        break;
                    case CardTemplate.NAME:
                        rb.add(tmpl.getString("name"));
                        break;
                    case CardTemplate.QUESTION_FORMAT:
                        rb.add(tmpl.getString("qfmt"));
                        break;
                    case CardTemplate.ANSWER_FORMAT:
                        rb.add(tmpl.getString("afmt"));
                        break;
                    case CardTemplate.BROWSER_QUESTION_FORMAT:
                        rb.add(tmpl.getString("bqfmt"));
                        break;
                    case CardTemplate.BROWSER_ANSWER_FORMAT:
                        rb.add(tmpl.getString("bafmt"));
                        break;
                    case CardTemplate.CARD_COUNT:
                        rb.add(models.tmplUseCount(model, tmpl.getInt("ord")));
                        break;
                    default:
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
            switch (column) {
                case FlashCardsContract.Deck.DECK_NAME:
                    rb.add(name);
                    break;
                case FlashCardsContract.Deck.DECK_ID:
                    rb.add(id);
                    break;
                case FlashCardsContract.Deck.DECK_COUNTS:
                    rb.add(deckCounts);
                    break;
                case FlashCardsContract.Deck.OPTIONS:
                    String config = col.getDecks().confForDid(id).toString();
                    rb.add(config);
                    break;
                case FlashCardsContract.Deck.DECK_DYN:
                    rb.add(col.getDecks().isDyn(id));
                    break;
                case FlashCardsContract.Deck.DECK_DESC:
                    String desc = col.getDecks().getActualDescription();
                    rb.add(desc);
                    break;
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
        long noteId = Long.parseLong(uri.getPathSegments().get(1));
        int ord = Integer.parseInt(uri.getPathSegments().get(3));
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
        long noteId = Long.parseLong(uri.getPathSegments().get(1));
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
                throw new IllegalArgumentException("Model ID must be either numeric or the String CURRENT_MODEL_ID", e);
            }
        }
        return id;
    }

    private JSONObject getTemplateFromUri(Uri uri, Collection col) throws JSONException {
        JSONObject model = col.getModels().get(getModelIdFromUri(uri, col));
        int ord = Integer.parseInt(uri.getLastPathSegment());
        return model.getJSONArray("tmpls").getJSONObject(ord);
    }

    private void throwSecurityException(String methodName, Uri uri) {
        String msg = String.format("Permission not granted for: %s", getLogMessage(methodName, uri));
        Timber.e("%s", msg);
        throw new SecurityException(msg);
    }

    private String getLogMessage(String methodName, Uri uri) {
        final String format = "%s.%s %s (%s)";
        String path = uri == null ? null : uri.getPath();
        return String.format(format, getClass().getSimpleName(), methodName, path, getCallingPackage());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
            PackageInfo callingPi = pm.getPackageInfo(getCallingPackage(), PackageManager.GET_PERMISSIONS);
             if (callingPi == null || callingPi.requestedPermissions == null) {
                 return false;
             }
             return !Arrays.asList(callingPi.requestedPermissions).contains(READ_WRITE_PERMISSION);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.w(e);
            return false;
        }
    }
}
