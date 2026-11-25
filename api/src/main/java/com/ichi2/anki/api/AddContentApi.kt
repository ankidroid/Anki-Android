// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2016 Mark Carter <mark@marcardar.com>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Process
import android.util.SparseArray
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.FlashCardsContract.AnkiMedia
import com.ichi2.anki.FlashCardsContract.Card
import com.ichi2.anki.FlashCardsContract.CardTemplate
import com.ichi2.anki.FlashCardsContract.Deck
import com.ichi2.anki.FlashCardsContract.Model
import com.ichi2.anki.FlashCardsContract.Note
import java.io.File
import java.util.Locale

/**
 * API which can be used to add and query notes,cards,decks, and models to AnkiDroid
 *
 * On Android M (and higher) the #READ_WRITE_PERMISSION is required for all read/write operations.
 * On earlier SDK levels, the #READ_WRITE_PERMISSION is currently only required for update/delete operations but
 * this may be extended to all operations at a later date.
 *
 * The API talks to the trusted AnkiDroid provider when it is the sole option, or to the provider
 * the user chose. Use [getSelectedProvider] to check what the API will use, and launch
 * [getProviderChooserIntent] from your UI when the user wants to choose a provider.
 */
@Suppress("unused")
public class AddContentApi(
    context: Context,
) {
    private val context: Context = context.applicationContext
    private val selection = ProviderSelection(this.context)
    private val resolver = TrustedResolver(this.context, selection)

    // The resolver rejects calls when there is no provider in use.
    private val authorityUri: Uri
        get() = FlashCardsContract.getAuthorityUri(selection.inUse()?.authority ?: FlashCardsContract.AUTHORITY)
    private val notesUri get() = Note.getContentUri(authorityUri)
    private val notesV2Uri get() = Note.getContentUriV2(authorityUri)
    private val modelsUri get() = Model.getContentUri(authorityUri)
    private val decksUri get() = Deck.getContentAllUri(authorityUri)
    private val selectedDeckUri get() = Deck.getContentSelectedUri(authorityUri)
    private val mediaUri get() = AnkiMedia.getContentUri(authorityUri)

    /**
     * Create a new note with specified fields, tags, and model and place it in the specified deck.
     * No duplicate checking is performed - so the note should be checked beforehand using #findNotesByKeys
     * @param modelId ID for the model used to add the notes
     * @param deckId ID for the deck the cards should be stored in (use #DEFAULT_DECK_ID for default deck)
     * @param fields fields to add to the note. Length should be the same as number of fields in model
     * @param tags tags to include in the new note
     * @return note id or null if the note could not be added
     */
    public fun addNote(
        modelId: Long,
        deckId: Long,
        fields: Array<String>,
        tags: Set<String>?,
    ): Long? {
        val noteUri = addNoteInternal(modelId, deckId, fields, tags) ?: return null
        return noteUri.lastPathSegment!!.toLong()
    }

    private fun addNoteInternal(
        modelId: Long,
        deckId: Long,
        fields: Array<String>,
        tags: Set<String>?,
    ): Uri? {
        val values =
            ContentValues().apply {
                put(Note.MID, modelId)
                put(Note.FLDS, Utils.joinFields(fields))
                if (tags != null) put(Note.TAGS, Utils.joinTags(tags))
            }
        return addNoteForContentValues(deckId, values)
    }

    private fun addNoteForContentValues(
        deckId: Long,
        values: ContentValues,
    ): Uri? {
        val newNoteUri = resolver.insert(notesUri, values) ?: return null
        // Move cards to specified deck
        val cardsUri = Uri.withAppendedPath(newNoteUri, "cards")
        val cardsQuery = resolver.query(cardsUri, null, null, null, null) ?: return null
        cardsQuery.use { cardsCursor ->
            while (cardsCursor.moveToNext()) {
                val ord = cardsCursor.getString(cardsCursor.getColumnIndex(Card.CARD_ORD))
                val cardValues = ContentValues().apply { put(Card.DECK_ID, deckId) }
                val cardUri = Uri.withAppendedPath(Uri.withAppendedPath(newNoteUri, "cards"), ord)
                resolver.update(cardUri, cardValues, null, null)
            }
        }
        return newNoteUri
    }

    /**
     * Create new notes with specified fields, tags and model and place them in the specified deck.
     * No duplicate checking is performed - so all notes should be checked beforehand using #findNotesByKeys
     * @param modelId id for the model used to add the notes
     * @param deckId id for the deck the cards should be stored in (use #DEFAULT_DECK_ID for default deck)
     * @param fieldsList List of fields arrays (one per note). Array lengths should be same as number of fields in model
     * @param tagsList List of tags (one per note) (may be null)
     * @return The number of notes added (< 0 means there was a problem)
     */
    public fun addNotes(
        modelId: Long,
        deckId: Long,
        fieldsList: List<Array<String>>,
        tagsList: List<Set<String>?>?,
    ): Int {
        require(!(tagsList != null && fieldsList.size != tagsList.size)) { "fieldsList and tagsList different length" }
        val newNoteValuesList: MutableList<ContentValues> = ArrayList(fieldsList.size)
        for (i in fieldsList.indices) {
            val values =
                ContentValues().apply {
                    put(Note.MID, modelId)
                    put(Note.FLDS, Utils.joinFields(fieldsList[i]))
                    if (tagsList != null && tagsList[i] != null) {
                        put(Note.TAGS, Utils.joinTags(tagsList[i]))
                    }
                }
            newNoteValuesList.add(values)
        }
        // Add the notes to the content provider and put the new note ids into the result array
        return if (newNoteValuesList.isEmpty()) {
            0
        } else {
            compat.insertNotes(deckId, newNoteValuesList.toTypedArray())
        }
    }

    /**
     * Add a media file to AnkiDroid's media collection. You would likely supply this uri through a FileProvider, and
     * then set FLAG_GRANT_READ_URI_PERMISSION using something like:
     *
     * ```
     * val provider = AddContentApi.getSelectedProvider(context) ?: return
     * context.grantUriPermission(provider.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
     * try {
     *     api.addMediaFromUri(uri, "My_Image_File", "image")
     * } finally {
     *     context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
     * }
     * ```
     *
     * Example usage:
     * ```
     *     Long noteTypeId = getModelId(); // implementation can be seen in api sample app
     *     Long deckId = getDeckId(); // as above
     *     Set<String> tags = getTags(); // as above
     *     Uri fileUri = ... // this will be returned by a File Picker activity where we select an image file
     *     String addedImageFileName = mApi.addMediaFromUri(fileUri, "My_Image_File", "image");
     *
     *     String[] fields = new String[] {"text on front of card", "text on back of card " + addedImageFileName};
     *     mApi.addNote(noteTypeId, deckId, fields, tags)
     * ```
     *
     * The returned reference may point to an existing file when the filename and contents match.
     * The filename may be adjusted to resolve conflicts or normalize it for Anki.
     *
     * @param fileUri   Uri for the file to be added, required.
     * @param preferredName Preferred filename without a file extension, required.
     * @param mimeType  String indicating the mimeType of the media. Accepts "audio" or "image", required.
     * @return the correctly formatted String for the media file to be placed in the desired field of a Card, or null
     * if unsuccessful.
     */
    public fun addMediaFromUri(
        fileUri: Uri,
        preferredName: String,
        mimeType: String,
    ): String? {
        val contentValues =
            ContentValues().apply {
                put(AnkiMedia.FILE_URI, fileUri.toString())
                put(AnkiMedia.PREFERRED_NAME, preferredName.replace(" ", "_"))
            }
        return try {
            val returnUri = resolver.insert(mediaUri, contentValues)
            // get the filename from Uri, return [sound:%s] % file.getName()
            val fname = File(returnUri!!.path!!).toString()
            formatMediaName(fname, mimeType)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatMediaName(
        fname: String,
        mimeType: String,
    ): String? =
        when (mimeType) {
            "audio" -> "[sound:${fname.substring(1)}]" // first character in the path is "/"
            "image" -> "<img src=\"${fname.substring(1)}\" />"
            else -> null // something went wrong
        }

    /**
     * Find all existing notes in the collection which have mid and a duplicate key
     * @param mid model id
     * @param key the first field of a note
     * @return a list of duplicate notes
     */
    public fun findDuplicateNotes(
        mid: Long,
        key: String,
    ): List<NoteInfo?> = compat.findDuplicateNotes(mid, listOf(key))?.get(0).orEmpty()

    /**
     * Find all notes in the collection which have mid and a first field that matches key
     * Much faster than calling findDuplicateNotes(long, String) when the list of keys is large
     * @param mid model id
     * @param keys list of keys
     * @return a SparseArray with a list of duplicate notes for each key
     */
    public fun findDuplicateNotes(
        mid: Long,
        keys: List<String>,
    ): SparseArray<MutableList<NoteInfo?>>? = compat.findDuplicateNotes(mid, keys)

    /**
     * Get the number of notes that exist for the specified model ID
     * @param mid id of the model to be used
     * @return number of notes that exist with that model ID, or 0 if the query failed
     */
    // TODO: return null on failure once we can make a breaking change: 0 is ambiguous with a
    //  model that genuinely has no notes
    public fun getNoteCount(mid: Long): Int = compat.queryNotes(mid)?.use { cursor -> cursor.count } ?: 0

    /**
     * Set the tags for a given note
     * @param noteId the ID of the note to update
     * @param tags set of tags
     * @return true if noteId was found, otherwise false
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     */
    public fun updateNoteTags(
        noteId: Long,
        tags: Set<String>,
    ): Boolean = updateNote(noteId, null, tags)

    /**
     * Set the fields for a given note
     * @param noteId the ID of the note to update
     * @param fields array of fields
     * @return true if noteId was found, otherwise false
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     */
    public fun updateNoteFields(
        noteId: Long,
        fields: Array<String>,
    ): Boolean = updateNote(noteId, fields, null)

    /**
     * Get the contents of a note with known ID
     * @param noteId the ID of the note to find
     * @return object containing the contents of note with noteID or null if there was a problem
     */
    public fun getNote(noteId: Long): NoteInfo? {
        val noteUri = Uri.withAppendedPath(notesUri, noteId.toString())
        val query = resolver.query(noteUri, PROJECTION, null, null, null) ?: return null
        return query.use { cursor ->
            if (!cursor.moveToNext()) {
                null
            } else {
                NoteInfo.buildFromCursor(cursor)
            }
        }
    }

    private fun updateNote(
        noteId: Long,
        fields: Array<String>?,
        tags: Set<String?>?,
    ): Boolean {
        val contentUri =
            notesUri
                .buildUpon()
                .appendPath(noteId.toString())
                .build()
        val values =
            ContentValues().apply {
                if (fields != null) put(Note.FLDS, Utils.joinFields(fields))
                if (tags != null) put(Note.TAGS, Utils.joinTags(tags))
            }
        val numRowsUpdated = resolver.update(contentUri, values, null, null)
        // provider doesn't check whether fields actually changed, so just returns number of notes with id == noteId
        return numRowsUpdated > 0
    }

    /**
     * Get the html that would be generated for the specified note type and field list
     * @param flds array of field values for the note. Length must be the same as num. fields in mid.
     * @param mid id for the note type to be used
     * @return list of front &amp; back pairs for each card which contain the card HTML, or null if there was a problem
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     */
    public fun previewNewNote(
        mid: Long,
        flds: Array<String>,
    ): Map<String, Map<String, String>>? {
        if (!hasReadWritePermission()) {
            // avoid situation where addNote will pass, but deleteNote will fail
            throw SecurityException("previewNewNote requires full read-write-permission")
        }
        val newNoteUri = addNoteInternal(mid, DEFAULT_DECK_ID, flds, setOf(TEST_TAG)) ?: return null
        // Build map of HTML for each generated card
        val cards: MutableMap<String, Map<String, String>> = HashMap()
        val cardsUri = Uri.withAppendedPath(newNoteUri, "cards")
        val cardsQuery = resolver.query(cardsUri, null, null, null, null) ?: return null
        cardsQuery.use { cardsCursor ->
            while (cardsCursor.moveToNext()) {
                // add question and answer for each card to map
                val n = cardsCursor.getString(cardsCursor.getColumnIndex(Card.CARD_NAME))
                val q = cardsCursor.getString(cardsCursor.getColumnIndex(Card.QUESTION))
                val a = cardsCursor.getString(cardsCursor.getColumnIndex(Card.ANSWER))
                cards[n] =
                    hashMapOf(
                        "q" to q,
                        "a" to a,
                    )
            }
        }
        // Delete the note
        resolver.delete(newNoteUri, null, null)
        return cards
    }

    /**
     * Insert a new basic front/back model with two fields and one card
     * @param name name of the model
     * @return the mid of the model which was created, or null if it could not be created
     */
    public fun addNewBasicModel(name: String): Long? =
        addNewCustomModel(
            name,
            BasicModel.FIELDS,
            BasicModel.CARD_NAMES,
            BasicModel.QFMT,
            BasicModel.AFMT,
            null,
            null,
            null,
        )

    /**
     * Insert a new basic front/back model with two fields and TWO cards
     * The first card goes from front->back, and the second goes from back->front
     * @param name name of the model
     * @return the mid of the model which was created, or null if it could not be created
     */
    public fun addNewBasic2Model(name: String): Long? =
        addNewCustomModel(
            name,
            Basic2Model.FIELDS,
            Basic2Model.CARD_NAMES,
            Basic2Model.QFMT,
            Basic2Model.AFMT,
            null,
            null,
            null,
        )

    /**
     * Insert a new model into AnkiDroid.
     * See the [Anki Desktop Manual](https://docs.ankiweb.net/templates/intro.html) for more help
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
    @Suppress("MemberVisibilityCanBePrivate") // silence IDE
    public fun addNewCustomModel(
        name: String,
        fields: Array<String>,
        cards: Array<String>,
        qfmt: Array<String>,
        afmt: Array<String>,
        css: String?,
        did: Long?,
        sortf: Int?,
    ): Long? {
        // Check that size of arrays are consistent
        require(!(qfmt.size != cards.size || afmt.size != cards.size)) { "cards, qfmt, and afmt arrays must all be same length" }
        // Create the model using dummy templates
        var values =
            ContentValues().apply {
                put(Model.NAME, name)
                put(Model.FIELD_NAMES, Utils.joinFields(fields))
                put(Model.NUM_CARDS, cards.size)
                put(Model.CSS, css)
                put(Model.DECK_ID, did)
                put(Model.SORT_FIELD_INDEX, sortf)
            }
        val modelUri = resolver.insert(modelsUri, values) ?: return null
        // Set the remaining template parameters
        val templatesUri = Uri.withAppendedPath(modelUri, "templates")
        for (i in cards.indices) {
            val uri = Uri.withAppendedPath(templatesUri, i.toString())
            values =
                ContentValues().apply {
                    put(CardTemplate.NAME, cards[i])
                    put(CardTemplate.QUESTION_FORMAT, qfmt[i])
                    put(CardTemplate.ANSWER_FORMAT, afmt[i])
                    put(CardTemplate.ANSWER_FORMAT, afmt[i])
                }
            resolver.update(uri, values, null, null)
        }
        return modelUri.lastPathSegment!!.toLong()
    } // Get the current model

    /**
     * Get the ID for the note type / model which is currently in use
     * @return id for current model, or < 0 if there was a problem
     */
    public val currentModelId: Long
        get() {
            // Get the current model
            val uri = Uri.withAppendedPath(modelsUri, Model.CURRENT_MODEL_ID)
            val singleModelQuery = resolver.query(uri, null, null, null, null) ?: return -1L
            return singleModelQuery.use { singleModelCursor ->
                singleModelCursor.moveToFirst()
                singleModelCursor.getLong(singleModelCursor.getColumnIndex(Model._ID))
            }
        }

    /**
     * Get the field names belonging to specified model
     * @param modelId the ID of the model to use
     * @return the names of all the fields, or null if the model doesn't exist or there was some other problem
     */
    public fun getFieldList(modelId: Long): Array<String>? {
        // Get the current model
        val uri = Uri.withAppendedPath(modelsUri, modelId.toString())
        val modelQuery = resolver.query(uri, null, null, null, null) ?: return null
        var splitFlds: Array<String>? = null
        modelQuery.use { modelCursor ->
            if (modelCursor.moveToNext()) {
                splitFlds =
                    Utils.splitFields(
                        modelCursor.getString(modelCursor.getColumnIndex(Model.FIELD_NAMES)),
                    )
            }
        }
        return splitFlds
    }

    /**
     * Get a map of all model ids and names
     * @return map of (id, name) pairs
     */
    public val modelList: Map<Long, String>?
        get() = getModelList(1)

    /**
     * Get a map of all model ids and names with number of fields larger than minNumFields
     * @param minNumFields minimum number of fields to consider the model for inclusion
     * @return map of (id, name) pairs or null if there was a problem
     */
    public fun getModelList(minNumFields: Int): Map<Long, String>? {
        // Get the current model
        val allModelsQuery =
            resolver.query(modelsUri, null, null, null, null)
                ?: return null
        val models: MutableMap<Long, String> = HashMap()
        allModelsQuery.use { allModelsCursor ->
            while (allModelsCursor.moveToNext()) {
                val noteTypeId = allModelsCursor.getLong(allModelsCursor.getColumnIndex(Model._ID))
                val name = allModelsCursor.getString(allModelsCursor.getColumnIndex(Model.NAME))
                val flds =
                    allModelsCursor.getString(allModelsCursor.getColumnIndex(Model.FIELD_NAMES))
                val numFlds: Int = Utils.splitFields(flds).size
                if (numFlds >= minNumFields) {
                    models[noteTypeId] = name
                }
            }
        }
        return models
    }

    /**
     * Get the name of the model which has given ID
     * @param mid id of model
     * @return the name of the model, or null if no model was found
     */
    public fun getModelName(mid: Long): String? = modelList?.get(mid)

    /**
     * Create a new deck with specified name and save the reference to SharedPreferences for later
     * @param deckName name of the deck to add
     * @return id of the added deck, or null if the deck was not added
     */
    public fun addNewDeck(deckName: String): Long? {
        // Create a new note
        val values = ContentValues().apply { put(Deck.DECK_NAME, deckName) }
        val newDeckUri = resolver.insert(decksUri, values)
        return if (newDeckUri != null) {
            newDeckUri.lastPathSegment!!.toLong()
        } else {
            null
        }
    }

    /**
     * Set the flag using flag code for a given note
     * @param noteId the ID of the note to update
     * @param rawFlag the flag code to set (should match a [Flag.code])
     * @return true if flag was updated, otherwise false
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     * @throws IllegalArgumentException if flag code is not a valid [Flag.code]
     */
    public fun setFlagRaw(
        noteId: Long,
        rawFlag: Int,
    ): Boolean {
        val cardsUri =
            notesUri
                .buildUpon()
                .appendPath(noteId.toString())
                .appendPath("cards")
                .build()

        val cardsQuery = resolver.query(cardsUri, null, null, null, null) ?: return false

        val values =
            ContentValues().apply {
                put(Card.FLAGS, rawFlag)
            }
        var numRowsUpdated = 0

        cardsQuery.use { cardsCursor ->
            while (cardsCursor.moveToNext()) {
                val cardUri =
                    notesUri
                        .buildUpon()
                        .appendPath(noteId.toString())
                        .appendPath("cards")
                        .appendPath(cardsCursor.getString(cardsCursor.getColumnIndex(Card.CARD_ORD)))
                        .build()
                numRowsUpdated += resolver.update(cardUri, values, null, null)
            }
        }

        return numRowsUpdated > 0
    }

    /**
     * Set the flag for a given note
     * @param noteId the ID of the note to update
     * @param flag the flag to set (should match a [Flag])
     * @return true if flag was updated, otherwise false
     * @throws SecurityException if READ_WRITE_PERMISSION not granted (e.g. due to install order bug)
     */
    public fun setFlag(
        noteId: Long,
        flag: Flag,
    ): Boolean = setFlagRaw(noteId, flag.code)

    /**
     * Get the name of the selected deck
     * @return deck name or null if there was a problem
     */
    public val selectedDeckName: String?
        get() {
            val selectedDeckQuery =
                resolver.query(
                    selectedDeckUri,
                    null,
                    null,
                    null,
                    null,
                ) ?: return null
            return selectedDeckQuery.use { selectedDeckCursor ->
                if (selectedDeckCursor.moveToNext()) {
                    selectedDeckCursor.getString(selectedDeckCursor.getColumnIndex(Deck.DECK_NAME))
                } else {
                    null
                }
            }
        } // Get the current model

    /**
     * Get a list of all the deck id / name pairs
     * @return Map of (id, name) pairs, or null if there was a problem
     */
    public val deckList: Map<Long, String>?
        get() {
            // Get the current model
            val allDecksQuery =
                resolver.query(decksUri, null, null, null, null) ?: return null
            val decks: MutableMap<Long, String> = HashMap()
            allDecksQuery.use { allDecksCursor ->
                while (allDecksCursor.moveToNext()) {
                    val deckId = allDecksCursor.getLong(allDecksCursor.getColumnIndex(Deck.DECK_ID))
                    val name =
                        allDecksCursor.getString(allDecksCursor.getColumnIndex(Deck.DECK_NAME))
                    decks[deckId] = name
                }
            }
            return decks
        }

    /**
     * Get the name of the deck which has given ID
     * @param did ID of deck
     * @return the name of the deck, or null if no deck was found
     */
    public fun getDeckName(did: Long): String? = deckList?.get(did)

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
    public val apiHostSpecVersion: Int
        get() {
            // PackageManager#resolveContentProvider docs suggest flags should be 0 (but that gives null metadata)
            // GET_META_DATA seems to work anyway
            val provider = selection.inUse() ?: return ANKIDROID_NOT_INSTALLED
            val info =
                AnkiDroidProvider.find(context.packageManager, PackageManager.GET_META_DATA, provider.authority)
                    ?: return ANKIDROID_NOT_INSTALLED

            return if (info.metaData != null &&
                info.metaData.containsKey(PROVIDER_SPEC_META_DATA_KEY)
            ) {
                info.metaData.getInt(PROVIDER_SPEC_META_DATA_KEY)
            } else {
                DEFAULT_PROVIDER_SPEC_VALUE
            }
        }

    private fun hasReadWritePermission(): Boolean =
        context.checkPermission(
            selection.inUse()?.permission ?: READ_WRITE_PERMISSION,
            Process.myPid(),
            Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Best not to store this in case the user updates AnkiDroid app while client app is staying alive
     */
    private val compat: Compat
        get() = if (apiHostSpecVersion < 2) CompatV1() else CompatV2()

    private interface Compat {
        /**
         * Query all notes for a given model
         * @param modelId the model ID to limit query to
         * @return a cursor with all notes matching noteTypeId
         */
        fun queryNotes(modelId: Long): Cursor?

        /**
         * Add new notes to the AnkiDroid content provider in bulk.
         * @param deckId the deck ID to put the cards in
         * @param valuesArr the content values ready for bulk insertion into the content provider
         * @return the number of successful entries
         */
        fun insertNotes(
            deckId: Long,
            valuesArr: Array<ContentValues>,
        ): Int

        /**
         * For each key, look for an existing note that has matching first field
         * @param modelId the model ID to limit the search to
         * @param keys  list of keys for each note
         * @return array with a list of NoteInfo objects for each key if duplicates exist
         */
        fun findDuplicateNotes(
            modelId: Long,
            keys: List<String?>,
        ): SparseArray<MutableList<NoteInfo?>>?
    }

    private open inner class CompatV1 : Compat {
        override fun queryNotes(modelId: Long): Cursor? {
            val modelName = getModelName(modelId) ?: return null
            val queryFormat = "note:\"$modelName\""
            return resolver.query(
                notesUri,
                PROJECTION,
                queryFormat,
                null,
                null,
            )
        }

        override fun insertNotes(
            deckId: Long,
            valuesArr: Array<ContentValues>,
        ): Int = valuesArr.count { addNoteForContentValues(deckId, it) != null }

        override fun findDuplicateNotes(
            modelId: Long,
            keys: List<String?>,
        ): SparseArray<MutableList<NoteInfo?>>? {
            // Content provider spec v1 does not support direct querying of the notes table, so use Anki browser syntax
            val modelName = getModelName(modelId) ?: return null
            val modelFieldList = getFieldList(modelId) ?: return null
            val duplicates = SparseArray<MutableList<NoteInfo?>>()
            // Loop through each item in fieldsArray looking for an existing note, and add it to the duplicates array
            val queryFormat = "${modelFieldList[0]}:\"%%s\" note:\"$modelName\""
            for (outputPos in keys.indices) {
                val selection = String.format(queryFormat, keys[outputPos])
                val query =
                    resolver.query(
                        notesUri,
                        PROJECTION,
                        selection,
                        null,
                        null,
                    ) ?: continue
                query.use { cursor ->
                    while (cursor.moveToNext()) {
                        addNoteToDuplicatesArray(
                            NoteInfo.buildFromCursor(cursor),
                            duplicates,
                            outputPos,
                        )
                    }
                }
            }
            return duplicates
        }

        /** Add a NoteInfo object to the given duplicates SparseArray at the specified position  */
        protected fun addNoteToDuplicatesArray(
            note: NoteInfo?,
            duplicates: SparseArray<MutableList<NoteInfo?>>,
            position: Int,
        ) {
            val sparseArrayIndex = duplicates.indexOfKey(position)
            if (sparseArrayIndex < 0) {
                // No existing NoteInfo objects mapping to same key as the current note so add a new List
                duplicates.put(position, mutableListOf(note))
            } else { // Append note to existing list of duplicates for key
                duplicates.valueAt(sparseArrayIndex).add(note)
            }
        }
    }

    private inner class CompatV2 : CompatV1() {
        override fun queryNotes(modelId: Long): Cursor? =
            resolver.query(
                notesV2Uri,
                PROJECTION,
                String.format(Locale.US, "%s=%d", Note.MID, modelId),
                null,
                null,
            )

        override fun insertNotes(
            deckId: Long,
            valuesArr: Array<ContentValues>,
        ): Int {
            val builder =
                notesUri.buildUpon().appendQueryParameter(
                    Note.DECK_ID_QUERY_PARAM,
                    deckId.toString(),
                )
            return resolver.bulkInsert(builder.build(), valuesArr)
        }

        override fun findDuplicateNotes(
            modelId: Long,
            keys: List<String?>,
        ): SparseArray<MutableList<NoteInfo?>>? {
            // Build set of checksums and a HashMap from the key (first field) back to the original index in fieldsArray
            val csums: MutableSet<Long?> = HashSet(keys.size)
            val keyToIndexesMap: MutableMap<String?, MutableList<Int>> = HashMap(keys.size)
            for (i in keys.indices) {
                val key = keys[i]
                csums.add(Utils.fieldChecksum(key!!))
                if (!keyToIndexesMap.containsKey(key)) { // Use a list as some keys could potentially be duplicated
                    keyToIndexesMap[key] = ArrayList()
                }
                keyToIndexesMap[key]!!.add(i)
            }
            // Query for notes that have specified model and checksum of first field matches
            val sel =
                String.format(
                    Locale.US,
                    "%s=%d and %s in (%s)",
                    Note.MID,
                    modelId,
                    Note.CSUM,
                    csums.joinToString(separator = ","),
                )
            val notesTableQuery =
                resolver.query(
                    notesV2Uri,
                    PROJECTION,
                    sel,
                    null,
                    null,
                ) ?: return null
            // Loop through each note in the cursor, building the result array of duplicates
            val duplicates = SparseArray<MutableList<NoteInfo?>>()
            notesTableQuery.use { notesTableCursor ->
                while (notesTableCursor.moveToNext()) {
                    val note = NoteInfo.buildFromCursor(notesTableCursor) ?: continue
                    if (keyToIndexesMap.containsKey(note.getKey())) { // skip notes that match csum but not key
                        // Add copy of note to EVERY position in duplicates array corresponding to the current key
                        val outputPos: List<Int> = keyToIndexesMap[note.getKey()]!!
                        for (i in outputPos.indices) {
                            addNoteToDuplicatesArray(
                                if (i > 0) NoteInfo(note) else note,
                                duplicates,
                                outputPos[i],
                            )
                        }
                    }
                }
            }
            return duplicates
        }
    }

    public companion object {
        public const val READ_WRITE_PERMISSION: String = FlashCardsContract.READ_WRITE_PERMISSION
        public const val DEFAULT_DECK_ID: Long = 1L
        private const val TEST_TAG = "PREVIEW_NOTE"
        private const val PROVIDER_SPEC_META_DATA_KEY = "com.ichi2.anki.provider.spec"
        private const val DEFAULT_PROVIDER_SPEC_VALUE = 1 // for when meta-data key does not exist
        private const val ANKIDROID_NOT_INSTALLED = -1
        private val PROJECTION =
            arrayOf(
                Note._ID,
                Note.FLDS,
                Note.TAGS,
            )

        /**
         * Get the verified AnkiDroid package that holds [FlashCardsContract.AUTHORITY].
         *
         * The app holding [FlashCardsContract.AUTHORITY] counts as AnkiDroid only if it has AnkiDroid's
         * package name and is signed with AnkiDroid's release key. Other builds of AnkiDroid are accepted
         * only while the calling app is debuggable. Apps that use [FlashCardsContract] directly should
         * verify the package via this method before using the fixed AnkiDroid URIs. Use
         * [getSelectedProvider] for the provider used by this class.
         *
         * @param context a Context that can be used to get the PackageManager
         * @return the package name of verified AnkiDroid, or null if unavailable
         */
        @JvmStatic // required for API
        public fun getAnkiDroidPackageName(context: Context): String? = AnkiDroidProvider.resolve(context)?.packageName

        /**
         * The provider selected for API calls, with its package, authority and permission.
         * Returns null when no provider is available or the user still needs to choose.
         */
        @JvmStatic
        public fun getSelectedProvider(context: Context): FlashCardsProvider? = ProviderSelection(context).inUse()

        /**
         * Installed apps offering the [FlashCardsContract] provider that this app can use: AnkiDroid,
         * its parallel builds and forks, if this app requests their permission in its manifest.
         * Providers declare [FlashCardsContract.ACTION_FLASHCARDS_PROVIDER] to be found.
         * @param context a Context that can be used to get the PackageManager
         * @return the providers, AnkiDroid first, empty if none is installed
         */
        @JvmStatic // required for API
        public fun getProviders(context: Context): List<FlashCardsProvider> = ProviderSelection(context).installed()

        /**
         * An explicit intent for the provider chooser. Launch it for a result from a visible Activity
         * in response to a user action, such as tapping a setting.
         *
         * The Activity returns [android.app.Activity.RESULT_OK] after saving the choice and
         * [android.app.Activity.RESULT_CANCELED] otherwise. Read [getSelectedProvider] after success.
         */
        @JvmStatic // required for API
        public fun getProviderChooserIntent(context: Context): Intent = Intent(context, ProviderChooserActivity::class.java)
    }
}

/**
 * The [ContentResolver] calls [AddContentApi] makes, which only reach the provider in use: AnkiDroid
 * signed with its release key, or the app the user chose. Blocked calls return null or 0, as for a failed call.
 */
private class TrustedResolver(
    context: Context,
    private val selection: ProviderSelection,
) {
    private val resolver: ContentResolver = context.contentResolver

    private fun blocked(uri: Uri): Boolean {
        val provider = selection.inUse() ?: return true
        return provider.authority != uri.authority
    }

    fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        if (blocked(uri)) {
            return null
        }
        return resolver.query(uri, projection, selection, selectionArgs, sortOrder)
    }

    fun insert(
        uri: Uri,
        values: ContentValues,
    ): Uri? {
        if (blocked(uri)) {
            return null
        }
        return resolver.insert(uri, values)
    }

    fun bulkInsert(
        uri: Uri,
        values: Array<ContentValues>,
    ): Int {
        if (blocked(uri)) {
            return 0
        }
        return resolver.bulkInsert(uri, values)
    }

    fun update(
        uri: Uri,
        values: ContentValues,
        where: String?,
        selectionArgs: Array<String>?,
    ): Int {
        if (blocked(uri)) {
            return 0
        }
        return resolver.update(uri, values, where, selectionArgs)
    }

    fun delete(
        uri: Uri,
        where: String?,
        selectionArgs: Array<String>?,
    ): Int {
        if (blocked(uri)) {
            return 0
        }
        return resolver.delete(uri, where, selectionArgs)
    }
}
