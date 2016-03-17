/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU Lesser General Public License as published by the Free Software *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU Lesser General Public License along with  *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.api;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import com.ichi2.anki.FlashCardsContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API which can be used to add and query notes,cards,decks, and models to AnkiDroid
 *
 * On Android M (and higher) the #READ_WRITE_PERMISSION is required for all read/write operations.
 * On earlier SDK levels, the #READ_WRITE_PERMISSION is currently only required for update/delete operations but
 * this may be extended to all operations at a later date.
 */
public final class AddContentApi {
    private final ContentResolver mResolver;
    private final Context mContext;
    public static final String READ_WRITE_PERMISSION = FlashCardsContract.READ_WRITE_PERMISSION;
    private static final String TEST_TAG = "PREVIEW_NOTE";
    private static final String DECK_REF_DB = "com.ichi2.anki.api.decks";
    private static final String MODEL_REF_DB = "com.ichi2.anki.api.models";

    private static final String PROVIDER_SPEC_META_DATA_KEY = "com.ichi2.anki.provider.spec";
    private static final int DEFAULT_PROVIDER_SPEC_VALUE = 1; // for when meta-data key does not exist

    private static final String[] PROJECTION = {FlashCardsContract.Note._ID,
            FlashCardsContract.Note.FLDS, FlashCardsContract.Note.TAGS};



    public AddContentApi(Context context) {
        mContext = context.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    /**
     * Create a new note with specified fields, tags, and model and place it in the specified deck.
     * No duplicate checking is performed - so the note should be checked beforehand using #findNotesByKeys
     * @param modelId ID for the model used to add the notes
     * @param deckId ID for the deck the cards should be stored in (use 1 for default deck)
     * @param fields fields to add to the note. Length should be the same as number of fields in model
     * @param tags tags to include in the new note
     * @return note id or null if the note could not be added
     */
    public Long addNote(long modelId, long deckId, String[] fields, Set<String> tags) {
        Uri noteUri = addNoteInternal(modelId, deckId, fields, tags);
        if (noteUri == null) {
            return null;
        }
        return Long.parseLong(noteUri.getLastPathSegment());
    }

    /**
     * @deprecated As of API v2 use {@link #addNote(long, long, String[], Set)} instead.
     */
    @Deprecated
    public Uri addNewNote(long modelId, long deckId, String[] fields, String tags) {
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.Note.MID, modelId);
        values.put(FlashCardsContract.Note.FLDS, Utils.joinFields(fields));
        values.put(FlashCardsContract.Note.TAGS, tags);
        return addNoteForContentValues(deckId, values);
    }

    private Uri addNoteInternal(long modelId, long deckId, String[] fields, Set<String> tags) {
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.Note.MID, modelId);
        values.put(FlashCardsContract.Note.FLDS, Utils.joinFields(fields));
        values.put(FlashCardsContract.Note.TAGS, Utils.joinTags(tags));
        return addNoteForContentValues(deckId, values);
    }

    private Uri addNoteForContentValues(long deckId, ContentValues values) {
        Uri newNoteUri = mResolver.insert(FlashCardsContract.Note.CONTENT_URI, values);
        if (newNoteUri == null) {
            return null;
        }
        // Move cards to specified deck
        Uri cardsUri = Uri.withAppendedPath(newNoteUri, "cards");
        final Cursor cardsCursor = mResolver.query(cardsUri, null, null, null, null);
        try {
            while (cardsCursor.moveToNext()) {
                String ord = cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.CARD_ORD));
                ContentValues cardValues = new ContentValues();
                cardValues.put(FlashCardsContract.Card.DECK_ID, deckId);
                Uri cardUri = Uri.withAppendedPath(Uri.withAppendedPath(newNoteUri, "cards"), ord);
                mResolver.update(cardUri, cardValues, null, null);
            }
        } finally {
            cardsCursor.close();
        }
        return newNoteUri;
    }

    /**
     * Create new notes with specified fields, tags and model and place them in the specified deck.
     * No duplicate checking is performed - so all notes should be checked beforehand using #findNotesByKeys
     * @param modelId id for the model used to add the notes
     * @param deckId id for the deck the cards should be stored in (use 1 for default deck)
     * @param fieldsList List of fields arrays (one per note). Array lengths should be same as number of fields in model
     * @param tagsList List of tags (one per note) (may be null)
     * @return The number of notes added (<0 means there was a problem)
     */
    public int addNotes(long modelId, long deckId, List<String[]> fieldsList, List<Set<String>> tagsList) {
        if (tagsList != null && fieldsList.size() != tagsList.size()) {
            throw new IllegalArgumentException("fieldsList and tagsList different length");
        }
        List<ContentValues> newNoteValuesList = new ArrayList<>();
        for (int i = 0; i < fieldsList.size(); i++) {
            ContentValues values = new ContentValues();
            values.put(FlashCardsContract.Note.MID, modelId);
            values.put(FlashCardsContract.Note.FLDS, Utils.joinFields(fieldsList.get(i)));
            if (tagsList != null && tagsList.get(i) != null) {
                values.put(FlashCardsContract.Note.TAGS, Utils.joinTags(tagsList.get(i)));
            }
            newNoteValuesList.add(values);
        }
        // Add the notes to the content provider and put the new note ids into the result array
        if (newNoteValuesList.isEmpty()) {
            return 0;
        }
        return getCompat().insertNotes(deckId, newNoteValuesList.toArray(new ContentValues[newNoteValuesList.size()]));
    }

    /**
     * Check if the note (according to the first field) already exists.
     * Deprecated from API v2, as duplicates are handled automatically.
     * @param mid model id
     * @param did deck id (ignored in API v2)
     * @param fields list of fields
     * @return whether there already exists a card with the same model ID and content in the first field
     */
    @Deprecated
    public boolean checkForDuplicates(long mid, long did, String[] fields) {
        return findExistingNoteId(mid, fields) != null;
    }


    /**
     * Find the note id of any existing notes which have mid and has identical first field as the input list of fields.
     * If multiple notes exist with the same first field, then the first such note is returned.
     * @param mid model id
     * @param fields list of fields
     * @return the note id or null if the note does not exist
     */
    public Long findExistingNoteId(long mid, String[] fields) {
        NoteInfo note = findExistingNote(mid, fields);
        if (note == null) {
            return null;
        }
        return note.getId();
    }


    private NoteInfo findExistingNote(long mid, String[] fields) {
        String[][] fieldsArray = {fields};
        NoteInfo[] notes = getCompat().findExistingNotes(mid, fieldsArray);
        if (notes == null) {
            return null;
        }
        return notes[0];
    }


    /**
     * Get the number of notes that exist for the specified model ID
     * @param mid id of the model to be used
     * @return number of notes that exist with that model ID
     */
    public int getNoteCount(long mid) {
        String[] selectionArgs = {String.format("%s=%d", FlashCardsContract.Note.MID, mid)};
        Cursor cursor = mResolver.query(FlashCardsContract.Note.CONTENT_URI, PROJECTION, null, selectionArgs, null);
        if (cursor == null) {
            return 0;
        }
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    /**
     * Set the tags for a given note
     * @param noteId
     * @param tags set of tags
     * @return true if noteId was found, otherwise false
     */
    public boolean updateNoteTags(long noteId, Set<String> tags) {
        return updateNote(noteId, null, tags);
    }

    /**
     * Set the fields for a given note
     * @param noteId
     * @param fields array of fields
     * @return true if noteId was found, otherwise false
     */
    public boolean updateNoteFields(long noteId, String[] fields) {
        return updateNote(noteId, fields, null);
    }

    public NoteInfo getNote(long noteId) {
        String[] selectionArgs = {String.format("%s=%d", FlashCardsContract.Note._ID, noteId)};
        Cursor cursor = mResolver.query(FlashCardsContract.Note.CONTENT_URI, PROJECTION, null, selectionArgs, null);
        if (cursor == null) {
            return null;
        }
        try {
            return NoteInfo.buildFromCursor(cursor);
        } finally {
            cursor.close();
        }
    }

    private boolean updateNote(long noteId, String[] fields, Set<String> tags) {
        Uri.Builder builder = FlashCardsContract.Note.CONTENT_URI.buildUpon();
        Uri contentUri = builder.appendPath(Long.toString(noteId)).build();
        ContentValues values = new ContentValues();
        if (fields != null) {
            values.put(FlashCardsContract.Note.FLDS, Utils.joinFields(fields));
        }
        if (tags != null) {
            values.put(FlashCardsContract.Note.TAGS, Utils.joinTags(tags));
        }
        int numRowsUpdated = mResolver.update(contentUri, values, null, null);
        // provider doesn't check whether fields actually changed, so just returns number of notes with id == noteId
        return numRowsUpdated > 0;
    }

    /**
     * Get the html that would be generated for the specified note type and field list
     * @param flds array of field values for the note. Length must be the same as num. fields in mid.
     * @param mid id for the note type to be used
     * @return list of front & back pairs for each card which contain the card HTML
     */
    public Map<String, Map<String, String>> previewNewNote(long mid, String[] flds) {
        Uri newNoteUri = addNoteInternal(mid, 1, flds, Collections.singleton(TEST_TAG));
        // Build map of HTML for each generated card
        Map<String, Map<String, String>> cards = new HashMap<>();
        Uri cardsUri = Uri.withAppendedPath(newNoteUri, "cards");
        final Cursor cardsCursor = mResolver.query(cardsUri, null, null, null, null);
        try {
            while (cardsCursor.moveToNext()) {
                // add question and answer for each card to map
                final String n = cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.CARD_NAME));
                final String q = cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.QUESTION));
                final String a = cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.ANSWER));
                Map<String, String> html = new HashMap<>();
                html.put("q", q);
                html.put("a", a);
                cards.put(n, html);
            }
        } finally {
            cardsCursor.close();
        }
        // Delete the note
        mResolver.delete(newNoteUri, null, null);
        return cards;
    }

    /**
     * Insert a new basic front/back model with two fields and one card
     * @param name name of the model
     * @return the mid of the model which was created, or null if it could not be created
     */
    public Long addNewBasicModel(String name) {
        return addNewCustomModel(name, BasicModel.FIELDS, BasicModel.CARD_NAMES, BasicModel.QFMT,
                BasicModel.AFMT, null, null);
    }


    /**
     * Insert a new basic front/back model with two fields and TWO cards
     * The first card goes from front->back, and the second goes from back->front
     * @param name name of the model
     * @return the mid of the model which was created, or null if it could not be created
     */
    public Long addNewBasic2Model(String name) {
        return addNewCustomModel(name, Basic2Model.FIELDS, Basic2Model.CARD_NAMES, Basic2Model.QFMT,
                Basic2Model.AFMT, null, null);
    }

    /**
     * Insert a new model into AnkiDroid.
     * See the <a href="http://ankisrs.net/docs/manual.html#cards-and-templates">Anki Desktop Manual</a> for more help
     * @param name: name of model
     * @param fields: array of field names
     * @param cards: array of names for the card templates
     * @param qfmt: array of formatting strings for the question side of each template in cards
     * @param afmt: array of formatting strings for the answer side of each template in cards
     * @param css: css styling information to be shared across all of the templates. Use null for default CSS.
     * @param did: default deck to add cards to when using this model. Use null or 1 for the default deck.
     * @return the mid of the model which was created, or null if it could not be created
     */
    public Long addNewCustomModel(String name, String[] fields, String[] cards, String[] qfmt,
                                  String[] afmt, String css, Long did) {
        // Check that size of arrays are consistent
        if (qfmt.length != cards.length || afmt.length != cards.length) {
            throw new IllegalArgumentException("cards, qfmt, and afmt arrays must all be same length");
        }
        // Create the model using dummy templates
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.Model.NAME, name);
        values.put(FlashCardsContract.Model.FIELD_NAMES, Utils.joinFields(fields));
        values.put(FlashCardsContract.Model.NUM_CARDS, cards.length);
        values.put(FlashCardsContract.Model.CSS, css);
        values.put(FlashCardsContract.Model.DECK_ID, did);
        Uri modelUri = mResolver.insert(FlashCardsContract.Model.CONTENT_URI, values);
        // Set the remaining template parameters
        Uri templatesUri = Uri.withAppendedPath(modelUri, "templates");
        for (int i = 0; i < cards.length; i++) {
            Uri uri = Uri.withAppendedPath(templatesUri, Integer.toString(i));
            values = new ContentValues();
            values.put(FlashCardsContract.CardTemplate.NAME, cards[i]);
            values.put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, qfmt[i]);
            values.put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, afmt[i]);
            values.put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, afmt[i]);
            mResolver.update(uri, values, null, null);
        }
        if (modelUri == null) {
            return null;
        } else {
            long mid = Long.parseLong(modelUri.getLastPathSegment());
            final SharedPreferences modelsDb = mContext.getSharedPreferences(MODEL_REF_DB, Context.MODE_PRIVATE);
            modelsDb.edit().putLong(name, mid).commit();
            return mid;
        }
    }

    /**
     * Get the ID for the note type / model which is currently in use
     * @return id for current model
     */
    public long getCurrentModelId() {
        // Get the current model
        Uri uri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, FlashCardsContract.Model.CURRENT_MODEL_ID);
        final Cursor singleModelCursor = mResolver.query(uri, null, null, null, null);
        long modelId;
        try {
            singleModelCursor.moveToFirst();
            modelId = singleModelCursor.getLong(singleModelCursor.getColumnIndex(FlashCardsContract.Model._ID));
        } finally {
            singleModelCursor.close();
        }
        return modelId;
    }


    /**
     * Try to find the given model by name, accounting for renaming of the model:
     * If there's a model with this modelName that is known to have previously been created (by this app)
     *   and the corresponding model ID exists and has the required number of fields
     *   then return that ID (even though it may have since been renamed)
     * If there's a model from #getModelList with modelName and required number of fields then return its ID
     * Otherwise return null
     * @param modelName the name of the model to find
     * @param numFields the minimum number of fields the model is required to have
     * @return the model ID
     */
    public Long findModelIdByName(String modelName, int numFields) {
        SharedPreferences modelsDb = mContext.getSharedPreferences(MODEL_REF_DB, Context.MODE_PRIVATE);
        long prefsModelId = modelsDb.getLong(modelName, -1L);
        // if we have a reference saved to modelName and it exists and has at least numFields then return it
        if (prefsModelId != -1L && getModelName(prefsModelId) != null
                && getFieldList(prefsModelId).length >= numFields) { // could potentially have been renamed
            return prefsModelId;
        }
        Map<Long, String> modelList = getModelList(numFields);
        for (Map.Entry<Long, String> entry : modelList.entrySet()) {
            if (entry.getValue().equals(modelName)) {
                return entry.getKey(); // first model wins
            }
        }
        // model no longer exists (by name nor old id) or the number of fields was reduced
        return null;
    }

    /**
     * Get the field names belonging to specified model
     * @param modelId
     * @return the names of all the fields, or null if the model doesn't exist
     */
    public String[] getFieldList(long modelId) {
        // Get the current model
        Uri uri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
        final Cursor modelCursor = mResolver.query(uri, null, null, null, null);
        String[] splitFlds = null;
        try {
            if (modelCursor.moveToNext()) {
                String flds = modelCursor.getString(modelCursor.getColumnIndex(FlashCardsContract.Model.FIELD_NAMES));
                splitFlds = Utils.splitFields(flds);
            }
        } finally {
            modelCursor.close();
        }
        return splitFlds;
    }

    /**
     * Get a map of all model ids and names
     * @return map of (id, name) pairs
     */
    public Map<Long, String> getModelList() {
        return getModelList(1);
    }

    /**
     * Get a map of all model ids and names with number of fields larger than minNumFields
     * @param minNumFields minimum number of fields to consider the model for inclusion
     * @return map of (id, name) pairs
     */
    public Map<Long, String> getModelList(int minNumFields) {
        // Get the current model
        final Cursor allModelsCursor = mResolver.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null);
        HashMap<Long, String> models = new HashMap<>();
        try {
            while (allModelsCursor.moveToNext()) {
                long modelId = allModelsCursor.getLong(allModelsCursor.getColumnIndex(FlashCardsContract.Model._ID));
                String name = allModelsCursor.getString(allModelsCursor.getColumnIndex(FlashCardsContract.Model.NAME));
                String flds = allModelsCursor.getString(
                        allModelsCursor.getColumnIndex(FlashCardsContract.Model.FIELD_NAMES));
                int numFlds = Utils.splitFields(flds).length;
                if (numFlds >= minNumFields) {
                    models.put(modelId, name);
                }
            }
        } finally {
            allModelsCursor.close();
        }
        return models;
    }

    /**
     * Get the name of the model which has given ID
     * @param mid id of model
     * @return the name of the model, or null if no model was found
     */
    public String getModelName(Long mid) {
        if (mid != null && mid >= 0) {
            Map<Long, String> modelList = getModelList();
            for (Map.Entry<Long, String> entry : modelList.entrySet()) {
                if (entry.getKey().equals(mid)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Create a new deck with specified name and save the reference to SharedPreferences for later
     * @param deckName name of the deck to add
     * @return id of the added deck, or null if the deck was not added
     */
    public Long addNewDeck(String deckName) {
        // Create a new note
        ContentValues values = new ContentValues();
        values.put(FlashCardsContract.Deck.DECK_NAME, deckName);
        Uri newDeckUri = mResolver.insert(FlashCardsContract.Deck.CONTENT_ALL_URI, values);
        if (newDeckUri != null) {
            long did = Long.parseLong(newDeckUri.getLastPathSegment());
            final SharedPreferences decksDb = mContext.getSharedPreferences(DECK_REF_DB, Context.MODE_PRIVATE);
            decksDb.edit().putLong(deckName, did).commit();
            return did;
        } else {
            return null;
        }
    }

    /**
     * Get the name of the selected deck
     * @return deck name
     */
    public String getSelectedDeckName() {
        final Cursor selectedDeckCursor = mResolver.query(FlashCardsContract.Deck.CONTENT_SELECTED_URI,
                null, null, null, null);
        String name = null;
        try {
            if (selectedDeckCursor.moveToNext()) {
                name=selectedDeckCursor.getString(selectedDeckCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));
            }
        } finally {
            selectedDeckCursor.close();
        }
        return name;
    }

    /**
     * Get a list of all the deck id / name pairs
     * @return Map of (id, name) pairs
     */
    public HashMap<Long, String> getDeckList() {
        // Get the current model
        final Cursor allDecksCursor = mResolver.query(FlashCardsContract.Deck.CONTENT_ALL_URI, null, null, null, null);
        HashMap<Long, String> decks = new HashMap<>();
        try {
            while (allDecksCursor.moveToNext()) {
                long deckId = allDecksCursor.getLong(allDecksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
                String name =allDecksCursor.getString(allDecksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));
                decks.put(deckId, name);
            }
        } finally {
            allDecksCursor.close();
        }
        return decks;
    }


    /**
     * Try to find the given deck by name, accounting for potential renaming of the deck by the user as follows:
     * If there's a deck with deckName then return it's ID
     * If there's no deck with deckName, but a ref to deckName is stored in SharedPreferences, and that deck exist in
     * AnkiDroid (i.e. it was renamed), then use that deck.Note: this deck will not be found if your app is re-installed
     * If there's no reference to deckName anywhere then return null
     * @param deckName the name of the deck to find
     * @return the did of the deck in Anki
     */
    public Long findDeckIdByName(String deckName) {
        SharedPreferences decksDb = mContext.getSharedPreferences(DECK_REF_DB, Context.MODE_PRIVATE);
        // Look for deckName in the deck list
        Long did = getDeckId(deckName);
        if (did != null) {
            // If the deck was found then return it's id
            return did;
        } else {
            // Otherwise try to check if we have a reference to a deck that was renamed and return that
            did = decksDb.getLong(deckName, -1);
            if (did != -1 && getDeckName(did) != null) {
                return did;
            } else {
                // If the deck really doesn't exist then return null
                return null;
            }
        }
    }



    /**
     * Get the name of the deck which has given ID
     * @param did ID of deck
     * @return the name of the deck, or null if no deck was found
     */
    public String getDeckName(Long did) {
        if (did != null && did >= 0) {
            Map<Long, String> deckList = getDeckList();
            for (Map.Entry<Long, String> entry : deckList.entrySet()) {
                if (entry.getKey().equals(did)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }


    /**
     * Get the AnkiDroid package name that the API will communicate with.
     * This can be used to check that a supported version of AnkiDroid is installed,
     * or to get the application label and icon, etc.
     * @param context a Context that can be used to get the PackageManager
     * @return packageId of AnkiDroid if a supported version is not installed, otherwise null
     */
    public static String getAnkiDroidPackageName(Context context) {
        PackageManager manager = context.getPackageManager();
        ProviderInfo pi = manager.resolveContentProvider(FlashCardsContract.AUTHORITY, 0);
        if (pi != null) {
            return pi.packageName;
        } else {
            return null;
        }
    }


    /**
     * Get the ID for any permission which is required to use the API
     * @param context a Context that can be used to get the PackageManager
     * @return id of a permission required to access the API or null if no permission is required
     * @deprecated use {@link #READ_WRITE_PERMISSION} instead and see class-level docs
     */
    @Deprecated
    public static String checkRequiredPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? READ_WRITE_PERMISSION : null;
    }


    /**
     * Get the ID of the deck which matches the name
     * @param deckName Exact name of deck (note: deck names are unique in Anki)
     * @return the ID of the deck that has given name, or null if no deck was found
     */
    private Long getDeckId(String deckName) {
        Map<Long, String> deckList = getDeckList();
        for (Map.Entry<Long, String> entry : deckList.entrySet()) {
            if (entry.getValue().equals(deckName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * The API spec version of the installed AnkiDroid app. This is not the same as the AnkiDroid app version code.
     *
     * SPEC VERSION 1: (AnkiDroid 2.5)
     * #addNotes is very slow for large numbers of notes
     * #findNotes is very slow for large numbers of keys
     * #addNewCustomModel is not persisted properly
     *
     * SPEC VERSION 2: (AnkiDroid 2.6)
     *
     * @return the spec version number or -1 if AnkiDroid is not installed.
     */
    public int getApiHostSpecVersion() {
        // PackageManager#resolveContentProvider docs suggest flags should be 0 (but that gives null metadata)
        // GET_META_DATA seems to work anyway
        ProviderInfo info = mContext.getPackageManager().resolveContentProvider(FlashCardsContract.AUTHORITY, PackageManager.GET_META_DATA);
        if (info == null) {
            return -1;
        }
        if (info.metaData != null && info.metaData.containsKey(PROVIDER_SPEC_META_DATA_KEY)) {
            return info.metaData.getInt(PROVIDER_SPEC_META_DATA_KEY);
        } else {
            return DEFAULT_PROVIDER_SPEC_VALUE;
        }
    }

    /**
     * Best not to store this in case the user updates AnkiDroid app while client app is staying alive
     */
    private Compat getCompat() {
        return getApiHostSpecVersion() < 2 ? new CompatV1() : new CompatV2();
    }

    private interface Compat {
        /**
         * Add new notes to the AnkiDroid content provider in bulk.
         * @param deckId the deck ID to put the cards in
         * @param valuesArr the content values ready for bulk insertion into the content provider
         * @return the number of successful entries
         */
        int insertNotes(long deckId, ContentValues[] valuesArr);

        /**
         * For each item in fieldsArray, look for an existing note that has matching first field
         * @param mid the model ID to limit the search to
         * @param fieldsArray  array containing a set of fields for each note
         * @return array of NoteInfo objects
         */
        NoteInfo[] findExistingNotes(long mid, String[][] fieldsArray);
    }

    private class CompatV1 implements Compat {
        @Override
        public int insertNotes(long deckId, ContentValues[] valuesArr) {
            int result = 0;
            for (ContentValues values : valuesArr) {
                Uri noteUri = addNoteForContentValues(deckId, values);
                if (noteUri != null) {
                    result++;
                }
            }
            return result;
        }

        @Override
        public NoteInfo[] findExistingNotes(long mid, String[][] fieldsArray) {
            // Content provider spec v1 does not support direct querying of the notes table, so use Anki browser syntax
            String modelName = getModelName(mid);
            if (modelName == null) {
                modelName = ""; // empty model name will result in no query results
            }
            final String[] fieldNames = getFieldList(mid);
            NoteInfo[] result = new NoteInfo[fieldsArray.length];
            // Loop through each item in fieldsArray looking for an existing note
            for (int i = 0; i < fieldsArray.length; i++) {
                String sel = String.format("%s:\"%s\" note:\"%s\"", fieldNames[0], fieldsArray[i][0], modelName);
                Cursor cursor = mResolver.query(FlashCardsContract.Note.CONTENT_URI, PROJECTION, sel, null, null);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        result[i] = NoteInfo.buildFromCursor(cursor);
                    }
                } finally {
                    cursor.close();
                }
            }
            return result;
        }
    }

    private class CompatV2 implements Compat {
        @Override
        public int insertNotes(long deckId, ContentValues[] valuesArr) {
            Uri.Builder builder = FlashCardsContract.Note.CONTENT_URI.buildUpon();
            builder.appendQueryParameter(FlashCardsContract.Note.DECK_ID_QUERY_PARAM, String.valueOf(deckId));
            return mResolver.bulkInsert(builder.build(), valuesArr);
        }

        @Override
        public NoteInfo[] findExistingNotes(long mid, String[][] fieldsArray) {
            // Build list of checksums
            List<Long> csums = new ArrayList<>(fieldsArray.length);
            for (String[] aFieldsArray : fieldsArray) {
                csums.add(Utils.fieldChecksum(aFieldsArray[0]));
            }
            // Query for notes that have specified model and checksum of first field matches
            String sel = String.format("%s=%d and %s in ", FlashCardsContract.Note.MID, mid, FlashCardsContract.Note.CSUM);
            String[] selArgs = {sel + Utils.ids2str(csums)};
            Cursor notesTableCursor = mResolver.query(FlashCardsContract.Note.CONTENT_URI, PROJECTION, null, selArgs, null);
            if (notesTableCursor == null) {
                return null;
            }
            // Loop through each result, building a hash-map of first field to note ID
            Map<String, NoteInfo> idMap = new HashMap<>();
            try {
                while (notesTableCursor.moveToNext()) {
                    NoteInfo note = NoteInfo.buildFromCursor(notesTableCursor);
                    String key = note.getFields()[0];
                    if (!idMap.containsKey(key)) {
                        idMap.put(key, note);
                    }
                }
            } finally {
                notesTableCursor.close();
            }
            // Build the result array containing the note ID corresponding to each fieldsArray element, or null
            NoteInfo[] result = new NoteInfo[fieldsArray.length];
            for (int i = 0; i < fieldsArray.length; i++) {
                result[i] = idMap.get(fieldsArray[i][0]);
            }
            return result;
        }
    }
}
