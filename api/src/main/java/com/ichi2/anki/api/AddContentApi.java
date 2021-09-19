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
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.SparseArray;

import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.FlashCardsContract.Card;
import com.ichi2.anki.FlashCardsContract.CardTemplate;
import com.ichi2.anki.FlashCardsContract.Deck;
import com.ichi2.anki.FlashCardsContract.Model;
import com.ichi2.anki.FlashCardsContract.Note;
import com.ichi2.anki.FlashCardsContract.AnkiMedia;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * API which can be used to add and query notes,cards,decks, and models to AnkiDroid
 *
 * On Android M (and higher) the #READ_WRITE_PERMISSION is required for all read/write operations.
 * On earlier SDK levels, the #READ_WRITE_PERMISSION is currently only required for update/delete operations but
 * this may be extended to all operations at a later date.
 */
@SuppressWarnings("unused")
public final class AddContentApi {
    private final ContentResolver mResolver;
    private final Context mContext;
    public static final String READ_WRITE_PERMISSION = FlashCardsContract.READ_WRITE_PERMISSION;
    public static final long DEFAULT_DECK_ID = 1L;
    private static final String TEST_TAG = "PREVIEW_NOTE";
    private static final String PROVIDER_SPEC_META_DATA_KEY = "com.ichi2.anki.provider.spec";
    private static final int DEFAULT_PROVIDER_SPEC_VALUE = 1; // for when meta-data key does not exist
    private static final String[] PROJECTION = {Note._ID, Note.FLDS, Note.TAGS};

    public AddContentApi(Context context) {
        mContext = context.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    /**
     * Create a new note with specified fields, tags, and model and place it in the specified deck.
     * No duplicate checking is performed - so the note should be checked beforehand using #findNotesByKeys
     * @param modelId ID for the model used to add the notes
     * @param deckId ID for the deck the cards should be stored in (use #DEFAULT_DECK_ID for default deck)
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

    private Uri addNoteInternal(long modelId, long deckId, String[] fields, Set<String> tags) {
        ContentValues values = new ContentValues();
        values.put(Note.MID, modelId);
        values.put(Note.FLDS, Utils.joinFields(fields));
        if (tags != null) {
            values.put(Note.TAGS, Utils.joinTags(tags));
        }
        return addNoteForContentValues(deckId, values);
    }

    private Uri addNoteForContentValues(long deckId, ContentValues values) {
        Uri newNoteUri = mResolver.insert(Note.CONTENT_URI, values);
        if (newNoteUri == null) {
            return null;
        }
        // Move cards to specified deck
        Uri cardsUri = Uri.withAppendedPath(newNoteUri, "cards");
        final Cursor cardsCursor = mResolver.query(cardsUri, null, null, null, null);
        if (cardsCursor == null) {
            return null;
        }
        try {
            while (cardsCursor.moveToNext()) {
                String ord = cardsCursor.getString(cardsCursor.getColumnIndex(Card.CARD_ORD));
                ContentValues cardValues = new ContentValues();
                cardValues.put(Card.DECK_ID, deckId);
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
     * @param deckId id for the deck the cards should be stored in (use #DEFAULT_DECK_ID for default deck)
     * @param fieldsList List of fields arrays (one per note). Array lengths should be same as number of fields in model
     * @param tagsList List of tags (one per note) (may be null)
     * @return The number of notes added (&lt;0 means there was a problem)
     */
    public int addNotes(long modelId, long deckId, List<String[]> fieldsList, List<Set<String>> tagsList) {
        if (tagsList != null && fieldsList.size() != tagsList.size()) {
            throw new IllegalArgumentException("fieldsList and tagsList different length");
        }
        List<ContentValues> newNoteValuesList = new ArrayList<>(fieldsList.size());
        for (int i = 0; i < fieldsList.size(); i++) {
            ContentValues values = new ContentValues();
            values.put(Note.MID, modelId);
            values.put(Note.FLDS, Utils.joinFields(fieldsList.get(i)));
            if (tagsList != null && tagsList.get(i) != null) {
                values.put(Note.TAGS, Utils.joinTags(tagsList.get(i)));
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
     * Add a media file to AnkiDroid's media collection. You would likely supply this uri through a FileProvider, and
     * then set FLAG_GRANT_READ_URI_PERMISSION using something like:
     *
     * <pre>
     *     <code>
     *     getContext().grantUriPermission("com.ichi2.anki", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
     *     // Then when file is added, remove the permission
     *     // add File ...
     *     getContext().revokePermission(uri, Intent.FLAG_GRAN_READ_URI_PERMISSION)
     *     </code>
     * </pre>
     *
     * Example usage:
     *  <pre>
     *      <code>
     *      Long modelId = getModelId(); // implementation can be seen in api sample app
     *      Long deckId = getDeckId(); // as above
     *      Set&lt;String&gt; tags = getTags(); // as above
     *      Uri fileUri = ... // this will be returned by a File Picker activity where we select an image file
     *      String addedImageFileName = mApi.addMediaFromUri(fileUri, "My_Image_File", "image");
     *
     *      String[] fields = new String[] {"text on front of card", "text on back of card " + addedImageFileName};
     *      mApi.addNote(modelId, deckId, fields, tags)
     *      </code>
     *  </pre>
     *
     *
     *
     *
     * @param fileUri   Uri for the file to be added, required.
     * @param preferredName String to add to start of filename (do not use a file extension), required.
     * @param mimeType  String indicating the mimeType of the media. Accepts "audio" or "image", required.
     * @return the correctly formatted String for the media file to be placed in the desired field of a Card, or null
     *          if unsuccessful.
     */
    public @Nullable String addMediaFromUri(
            @NonNull Uri fileUri, @NonNull String preferredName, @NonNull String mimeType
    ) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(AnkiMedia.FILE_URI, fileUri.toString());
        preferredName = preferredName.replace(" ", "_");
        contentValues.put(AnkiMedia.PREFERRED_NAME, preferredName);

        try {
            Uri returnUri = mResolver.insert(AnkiMedia.CONTENT_URI, contentValues);
            // get the filename from Uri, return [sound:%s] % file.getName()
            String fname = new File(returnUri.getPath()).toString();
            return formatMediaName(fname, mimeType);
        } catch (Exception e){
            return null;
        }

    }

    private @Nullable String formatMediaName(@NonNull String fname, @NonNull String mimeType) {
        String formatted_fname;
        if (mimeType.equals("audio")) {
            formatted_fname = String.format("[sound:%s]", fname.substring(1)); // first character in the path is "/"
        } else if (mimeType.equals("image")) {
            formatted_fname = String.format("<img src=\"%s\" />", fname.substring(1));
        } else {
            // something went wrong
            formatted_fname = null;
        }
        return formatted_fname;
    }


    /**
     * Find all existing notes in the collection which have mid and a duplicate key
     * @param mid model id
     * @param key the first field of a note
     * @return a list of duplicate notes
     */
    public List<NoteInfo> findDuplicateNotes(long mid, String key) {
        SparseArray<List<NoteInfo>> notes = getCompat().findDuplicateNotes(mid, Collections.singletonList(key));
        if (notes.size() == 0) {
            return Collections.emptyList();
        }
        return notes.valueAt(0);
    }

    /**
     * Find all notes in the collection which have mid and a first field that matches key
     * Much faster than calling findDuplicateNotes(long, String) when the list of keys is large
     * @param mid model id
     * @param keys list of keys
     * @return a SparseArray with a list of duplicate notes for each key
     */
    public SparseArray<List<NoteInfo>> findDuplicateNotes(long mid, List<String> keys) {
        return getCompat().findDuplicateNotes(mid, keys);
    }

    /**
     * Get the number of notes that exist for the specified model ID
     * @param mid id of the model to be used
     * @return number of notes that exist with that model ID or -1 if there was a problem
     */
    public int getNoteCount(long mid) {
        Cursor cursor = getCompat().queryNotes(mid);
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
     * @param noteId the ID of the note to update
     * @param tags set of tags
     * @return true if noteId was found, otherwise false
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     */
    public boolean updateNoteTags(long noteId, Set<String> tags) {
        return updateNote(noteId, null, tags);
    }

    /**
     * Set the fields for a given note
     * @param noteId the ID of the note to update
     * @param fields array of fields
     * @return true if noteId was found, otherwise false
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     */
    public boolean updateNoteFields(long noteId, String[] fields) {
        return updateNote(noteId, fields, null);
    }

    /**
     * Get the contents of a note with known ID
     * @param noteId the ID of the note to find
     * @return object containing the contents of note with noteID or null if there was a problem
     */
    public NoteInfo getNote(long noteId) {
        Uri noteUri = Uri.withAppendedPath(Note.CONTENT_URI, Long.toString(noteId));
        Cursor cursor = mResolver.query(noteUri, PROJECTION, null, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            if (!cursor.moveToNext()) {
                return null;
            }
            return NoteInfo.buildFromCursor(cursor);
        } finally {
            cursor.close();
        }
    }

    private boolean updateNote(long noteId, String[] fields, Set<String> tags) {
        Uri.Builder builder = Note.CONTENT_URI.buildUpon();
        Uri contentUri = builder.appendPath(Long.toString(noteId)).build();
        ContentValues values = new ContentValues();
        if (fields != null) {
            values.put(Note.FLDS, Utils.joinFields(fields));
        }
        if (tags != null) {
            values.put(Note.TAGS, Utils.joinTags(tags));
        }
        int numRowsUpdated = mResolver.update(contentUri, values, null, null);
        // provider doesn't check whether fields actually changed, so just returns number of notes with id == noteId
        return numRowsUpdated > 0;
    }

    /**
     * Get the html that would be generated for the specified note type and field list
     * @param flds array of field values for the note. Length must be the same as num. fields in mid.
     * @param mid id for the note type to be used
     * @return list of front &amp; back pairs for each card which contain the card HTML, or null if there was a problem
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     */
    public Map<String, Map<String, String>> previewNewNote(long mid, String[] flds) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && !hasReadWritePermission()) {
            // avoid situation where addNote will pass, but deleteNote will fail
            throw new SecurityException("previewNewNote requires full read-write-permission");
        }
        Uri newNoteUri = addNoteInternal(mid, DEFAULT_DECK_ID, flds, Collections.singleton(TEST_TAG));
        // Build map of HTML for each generated card
        Map<String, Map<String, String>> cards = new HashMap<>();
        Uri cardsUri = Uri.withAppendedPath(newNoteUri, "cards");
        final Cursor cardsCursor = mResolver.query(cardsUri, null, null, null, null);
        if (cardsCursor == null) {
            return null;
        }
        try {
            while (cardsCursor.moveToNext()) {
                // add question and answer for each card to map
                final String n = cardsCursor.getString(cardsCursor.getColumnIndex(Card.CARD_NAME));
                final String q = cardsCursor.getString(cardsCursor.getColumnIndex(Card.QUESTION));
                final String a = cardsCursor.getString(cardsCursor.getColumnIndex(Card.ANSWER));
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
                BasicModel.AFMT, null, null, null);
    }


    /**
     * Insert a new basic front/back model with two fields and TWO cards
     * The first card goes from front-&gt;back, and the second goes from back-&gt;pront
     * @param name name of the model
     * @return the mid of the model which was created, or null if it could not be created
     */
    public Long addNewBasic2Model(String name) {
        return addNewCustomModel(name, Basic2Model.FIELDS, Basic2Model.CARD_NAMES, Basic2Model.QFMT,
                Basic2Model.AFMT, null, null, null);
    }

    /**
     * Insert a new model into AnkiDroid.
     * See the <a href="https://docs.ankiweb.net/templates/intro.html">Anki Desktop Manual</a> for more help
     * @param name name of model
     * @param fields array of field names
     * @param cards array of names for the card templates
     * @param qfmt array of formatting strings for the question side of each template in cards
     * @param afmt array of formatting strings for the answer side of each template in cards
     * @param css css styling information to be shared across all of the templates. Use null for default CSS.
     * @param did default deck to add cards to when using this model. Use null or #DEFAULT_DECK_ID for default deck.
     * @param sortf index of field to be used for sorting. Use null for unspecified (unsupported in provider spec v1)
     * @return the mid of the model which was created, or null if it could not be created
     */
    public Long addNewCustomModel(String name, String[] fields, String[] cards, String[] qfmt,
                                  String[] afmt, String css, Long did, Integer sortf) {
        // Check that size of arrays are consistent
        if (qfmt.length != cards.length || afmt.length != cards.length) {
            throw new IllegalArgumentException("cards, qfmt, and afmt arrays must all be same length");
        }
        // Create the model using dummy templates
        ContentValues values = new ContentValues();
        values.put(Model.NAME, name);
        values.put(Model.FIELD_NAMES, Utils.joinFields(fields));
        values.put(Model.NUM_CARDS, cards.length);
        values.put(Model.CSS, css);
        values.put(Model.DECK_ID, did);
        values.put(Model.SORT_FIELD_INDEX, sortf);
        Uri modelUri = mResolver.insert(Model.CONTENT_URI, values);
        if (modelUri == null) {
            return null;
        }
        // Set the remaining template parameters
        Uri templatesUri = Uri.withAppendedPath(modelUri, "templates");
        for (int i = 0; i < cards.length; i++) {
            Uri uri = Uri.withAppendedPath(templatesUri, Integer.toString(i));
            values = new ContentValues();
            values.put(CardTemplate.NAME, cards[i]);
            values.put(CardTemplate.QUESTION_FORMAT, qfmt[i]);
            values.put(CardTemplate.ANSWER_FORMAT, afmt[i]);
            values.put(CardTemplate.ANSWER_FORMAT, afmt[i]);
            mResolver.update(uri, values, null, null);
        }
        return Long.parseLong(modelUri.getLastPathSegment());
    }

    /**
     * Get the ID for the note type / model which is currently in use
     * @return id for current model, or &lt;0 if there was a problem
     */
    public long getCurrentModelId() {
        // Get the current model
        Uri uri = Uri.withAppendedPath(Model.CONTENT_URI, Model.CURRENT_MODEL_ID);
        final Cursor singleModelCursor = mResolver.query(uri, null, null, null, null);
        if (singleModelCursor == null) {
            return -1L;
        }
        long modelId;
        try {
            singleModelCursor.moveToFirst();
            modelId = singleModelCursor.getLong(singleModelCursor.getColumnIndex(Model._ID));
        } finally {
            singleModelCursor.close();
        }
        return modelId;
    }


    /**
     * Get the field names belonging to specified model
     * @param modelId the ID of the model to use
     * @return the names of all the fields, or null if the model doesn't exist or there was some other problem
     */
    public String[] getFieldList(long modelId) {
        // Get the current model
        Uri uri = Uri.withAppendedPath(Model.CONTENT_URI, Long.toString(modelId));
        final Cursor modelCursor = mResolver.query(uri, null, null, null, null);
        if (modelCursor == null) {
            return null;
        }
        String[] splitFlds = null;
        try {
            if (modelCursor.moveToNext()) {
                String flds = modelCursor.getString(modelCursor.getColumnIndex(Model.FIELD_NAMES));
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
     * @return map of (id, name) pairs or null if there was a problem
     */
    public Map<Long, String> getModelList(int minNumFields) {
        // Get the current model
        final Cursor allModelsCursor = mResolver.query(Model.CONTENT_URI, null, null, null, null);
        if (allModelsCursor == null) {
            return null;
        }
        Map<Long, String> models = new HashMap<>();
        try {
            while (allModelsCursor.moveToNext()) {
                long modelId = allModelsCursor.getLong(allModelsCursor.getColumnIndex(Model._ID));
                String name = allModelsCursor.getString(allModelsCursor.getColumnIndex(Model.NAME));
                String flds = allModelsCursor.getString(
                        allModelsCursor.getColumnIndex(Model.FIELD_NAMES));
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
    public String getModelName(long mid) {
        Map<Long, String> modelList = getModelList();
        return modelList.get(mid);
    }

    /**
     * Create a new deck with specified name and save the reference to SharedPreferences for later
     * @param deckName name of the deck to add
     * @return id of the added deck, or null if the deck was not added
     */
    public Long addNewDeck(String deckName) {
        // Create a new note
        ContentValues values = new ContentValues();
        values.put(Deck.DECK_NAME, deckName);
        Uri newDeckUri = mResolver.insert(Deck.CONTENT_ALL_URI, values);
        if (newDeckUri != null) {
            return Long.parseLong(newDeckUri.getLastPathSegment());
        } else {
            return null;
        }
    }

    /**
     * Get the name of the selected deck
     * @return deck name or null if there was a problem
     */
    public String getSelectedDeckName() {
        final Cursor selectedDeckCursor = mResolver.query(Deck.CONTENT_SELECTED_URI, null, null, null, null);
        if (selectedDeckCursor == null) {
            return null;
        }
        String name = null;
        try {
            if (selectedDeckCursor.moveToNext()) {
                name=selectedDeckCursor.getString(selectedDeckCursor.getColumnIndex(Deck.DECK_NAME));
            }
        } finally {
            selectedDeckCursor.close();
        }
        return name;
    }

    /**
     * Get a list of all the deck id / name pairs
     * @return Map of (id, name) pairs, or null if there was a problem
     */
    public Map<Long, String> getDeckList() {
        // Get the current model
        final Cursor allDecksCursor = mResolver.query(Deck.CONTENT_ALL_URI, null, null, null, null);
        if (allDecksCursor == null) {
            return null;
        }
        Map<Long, String> decks = new HashMap<>();
        try {
            while (allDecksCursor.moveToNext()) {
                long deckId = allDecksCursor.getLong(allDecksCursor.getColumnIndex(Deck.DECK_ID));
                String name =allDecksCursor.getString(allDecksCursor.getColumnIndex(Deck.DECK_NAME));
                decks.put(deckId, name);
            }
        } finally {
            allDecksCursor.close();
        }
        return decks;
    }


    /**
     * Get the name of the deck which has given ID
     * @param did ID of deck
     * @return the name of the deck, or null if no deck was found
     */
    public String getDeckName(long did) {
        Map<Long, String> deckList = getDeckList();
        return deckList.get(did);
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
     * The API spec version of the installed AnkiDroid app. This is not the same as the AnkiDroid app version code.
     *
     * SPEC VERSION 1: (AnkiDroid 2.5)
     * #addNotes is very slow for large numbers of notes
     * #findDuplicateNotes is very slow for large numbers of keys
     * #addNewCustomModel is not persisted properly
     * #addNewCustomModel does not support #sortf argument
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

    private boolean hasReadWritePermission() {
        return mContext.checkPermission(READ_WRITE_PERMISSION, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Best not to store this in case the user updates AnkiDroid app while client app is staying alive
     */
    private Compat getCompat() {
        return getApiHostSpecVersion() < 2 ? new CompatV1() : new CompatV2();
    }

    private interface Compat {
        /**
         * Query all notes for a given model
         * @param modelId the model ID to limit query to
         * @return a cursor with all notes matching modelId
         */
        Cursor queryNotes(long modelId);

        /**
         * Add new notes to the AnkiDroid content provider in bulk.
         * @param deckId the deck ID to put the cards in
         * @param valuesArr the content values ready for bulk insertion into the content provider
         * @return the number of successful entries
         */
        int insertNotes(long deckId, ContentValues[] valuesArr);

        /**
         * For each key, look for an existing note that has matching first field
         * @param modelId the model ID to limit the search to
         * @param keys  list of keys for each note
         * @return array with a list of NoteInfo objects for each key if duplicates exist
         */
        SparseArray<List<NoteInfo>> findDuplicateNotes(long modelId, List<String> keys);
    }

    private class CompatV1 implements Compat {
        @Override
        public Cursor queryNotes(long modelId) {
            String modelName = getModelName(modelId);
            if (modelName == null) {
                return null;
            }
            String queryFormat = String.format("note:\"%s\"", modelName);
            return mResolver.query(Note.CONTENT_URI, PROJECTION, queryFormat, null, null);
        }

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
        public SparseArray<List<NoteInfo>> findDuplicateNotes(long modelId, List<String> keys) {
            // Content provider spec v1 does not support direct querying of the notes table, so use Anki browser syntax
            String modelName = getModelName(modelId);
            String[] modelFieldList = getFieldList(modelId);
            if (modelName == null || modelFieldList == null) {
                return null;
            }
            SparseArray<List<NoteInfo>> duplicates = new SparseArray<>();
            // Loop through each item in fieldsArray looking for an existing note, and add it to the duplicates array
            String queryFormat = String.format("%s:\"%%s\" note:\"%s\"", modelFieldList[0], modelName);
            for (int outputPos = 0; outputPos < keys.size(); outputPos++) {
                String selection = String.format(queryFormat, keys.get(outputPos));
                Cursor cursor = mResolver.query(Note.CONTENT_URI, PROJECTION, selection, null, null);
                if (cursor == null) {
                    continue;
                }
                try {
                    while (cursor.moveToNext()) {
                        addNoteToDuplicatesArray(NoteInfo.buildFromCursor(cursor), duplicates, outputPos);
                    }
                } finally {
                    cursor.close();
                }
            }
            return duplicates;
        }

        /** Add a NoteInfo object to the given duplicates SparseArray at the specified position */
        protected void addNoteToDuplicatesArray(NoteInfo note, SparseArray<List<NoteInfo>> duplicates, int position) {
            int sparseArrayIndex = duplicates.indexOfKey(position);
            if (sparseArrayIndex < 0) {
                // No existing NoteInfo objects mapping to same key as the current note so add a new List
                List<NoteInfo> duplicatesForKey = new ArrayList<>();
                duplicatesForKey.add(note);
                duplicates.put(position, duplicatesForKey);
            } else { // Append note to existing list of duplicates for key
                duplicates.valueAt(sparseArrayIndex).add(note);
            }
        }
    }

    private class CompatV2 extends CompatV1 {
        @Override
        public Cursor queryNotes(long modelId) {
            return mResolver.query(Note.CONTENT_URI_V2, PROJECTION,
                    String.format(Locale.US, "%s=%d", Note.MID, modelId), null, null);
        }

        @Override
        public int insertNotes(long deckId, ContentValues[] valuesArr) {
            Uri.Builder builder = Note.CONTENT_URI.buildUpon();
            builder.appendQueryParameter(Note.DECK_ID_QUERY_PARAM, String.valueOf(deckId));
            return mResolver.bulkInsert(builder.build(), valuesArr);
        }

        @Override
        public SparseArray<List<NoteInfo>> findDuplicateNotes(long modelId, List<String> keys) {
            // Build set of checksums and a HashMap from the key (first field) back to the original index in fieldsArray
            Set<Long> csums = new HashSet<>(keys.size());
            Map<String, List<Integer>> keyToIndexesMap = new HashMap<>(keys.size());
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                csums.add(Utils.fieldChecksum(key));
                if (!keyToIndexesMap.containsKey(key)) {    // Use a list as some keys could potentially be duplicated
                    keyToIndexesMap.put(key, new ArrayList<>());
                }
                keyToIndexesMap.get(key).add(i);
            }
            // Query for notes that have specified model and checksum of first field matches
            String sel = String.format(Locale.US, "%s=%d and %s in (%s)", Note.MID, modelId, Note.CSUM,
                    TextUtils.join(",", csums));
            Cursor notesTableCursor = mResolver.query(Note.CONTENT_URI_V2, PROJECTION, sel, null, null);
            if (notesTableCursor == null) {
                return null;
            }
            // Loop through each note in the cursor, building the result array of duplicates
            SparseArray<List<NoteInfo>> duplicates = new SparseArray<>();
            try {
                while (notesTableCursor.moveToNext()) {
                    NoteInfo note = NoteInfo.buildFromCursor(notesTableCursor);
                    if (note == null) {
                        continue;
                    }
                    if (keyToIndexesMap.containsKey(note.getKey())) { // skip notes that match csum but not key
                        // Add copy of note to EVERY position in duplicates array corresponding to the current key
                        List<Integer> outputPos = keyToIndexesMap.get(note.getKey());
                        for (int i = 0; i < outputPos.size(); i++) {
                            addNoteToDuplicatesArray(i > 0 ? new NoteInfo(note) : note, duplicates, outputPos.get(i));
                        }
                    }
                }
            } finally {
                notesTableCursor.close();
            }
            return duplicates;
        }
    }
}
