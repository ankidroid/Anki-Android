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

package com.ichi2.anki.api

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
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
 * API which can be used to add and query notes,cards,decks, and note types to AnkiDroid
 *
 * On Android M (and higher) the #READ_WRITE_PERMISSION is required for all read/write operations.
 * On earlier SDK levels, the #READ_WRITE_PERMISSION is currently only required for update/delete operations but
 * this may be extended to all operations at a later date.
 */
@Suppress("unused")
public class AddContentApi(
    context: Context,
) {
    private val context: Context = context.applicationContext
    private val resolver: ContentResolver = this.context.contentResolver

    /**
     * Create a new note with specified fields, tags, and note type and place it in the specified deck.
     * No duplicate checking is performed - so the note should be checked beforehand using #findNotesByKeys
     * @param noteTypeId ID for the note type used to add the notes
     * @param deckId ID for the deck the cards should be stored in (use #DEFAULT_DECK_ID for default deck)
     * @param fields fields to add to the note. Length should be the same as number of fields in note type
     * @param tags tags to include in the new note
     * @return note id or null if the note could not be added
     */
    public fun addNote(
        noteTypeId: Long,
        deckId: Long,
        fields: Array<String>,
        tags: Set<String>?,
    ): Long? {
        val noteUri = addNoteInternal(noteTypeId, deckId, fields, tags) ?: return null
        return noteUri.lastPathSegment!!.toLong()
    }

    private fun addNoteInternal(
        noteTypeId: Long,
        deckId: Long,
        fields: Array<String>,
        tags: Set<String>?,
    ): Uri? {
        val values =
            ContentValues().apply {
                put(Note.MID, noteTypeId)
                put(Note.FLDS, Utils.joinFields(fields))
                if (tags != null) put(Note.TAGS, Utils.joinTags(tags))
            }
        return addNoteForContentValues(deckId, values)
    }

    private fun addNoteForContentValues(
        deckId: Long,
        values: ContentValues,
    ): Uri? {
        val newNoteUri = resolver.insert(Note.CONTENT_URI, values) ?: return null
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
     * Create new notes with specified fields, tags and note type and place them in the specified deck.
     * No duplicate checking is performed - so all notes should be checked beforehand using #findNotesByKeys
     * @param noteTypeId id for the note type used to add the notes
     * @param deckId id for the deck the cards should be stored in (use #DEFAULT_DECK_ID for default deck)
     * @param fieldsList List of fields arrays (one per note). Array lengths should be same as number of fields in note type
     * @param tagsList List of tags (one per note) (may be null)
     * @return The number of notes added (< 0 means there was a problem)
     */
    public fun addNotes(
        noteTypeId: Long,
        deckId: Long,
        fieldsList: List<Array<String>>,
        tagsList: List<Set<String>?>?,
    ): Int {
        require(!(tagsList != null && fieldsList.size != tagsList.size)) { "fieldsList and tagsList different length" }
        val newNoteValuesList: MutableList<ContentValues> = ArrayList(fieldsList.size)
        for (i in fieldsList.indices) {
            val values =
                ContentValues().apply {
                    put(Note.MID, noteTypeId)
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
     *     getContext().grantUriPermission("com.ichi2.anki", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
     *     // Then when file is added, remove the permission
     *     // add File ...
     * getContext().revokePermission(uri, Intent.FLAG_GRAN_READ_URI_PERMISSION)
     * ```
     *
     * Example usage:
     * ```
     *     Long noteTypeId = getNoteTypeId(); // implementation can be seen in api sample app
     *     Long deckId = getDeckId(); // as above
     *     Set<String> tags = getTags(); // as above
     *     Uri fileUri = ... // this will be returned by a File Picker activity where we select an image file
     *     String addedImageFileName = mApi.addMediaFromUri(fileUri, "My_Image_File", "image");
     *
     *     String[] fields = new String[] {"text on front of card", "text on back of card " + addedImageFileName};
     *     mApi.addNote(noteTypeId, deckId, fields, tags)
     * ```
     *
     * @param fileUri   Uri for the file to be added, required.
     * @param preferredName String to add to start of filename (do not use a file extension), required.
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
            val returnUri = resolver.insert(AnkiMedia.CONTENT_URI, contentValues)
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
     * @param mid note type id
     * @param key the first field of a note
     * @return a list of duplicate notes
     */
    public fun findDuplicateNotes(
        mid: Long,
        key: String,
    ): List<NoteInfo?> {
        val notes = compat.findDuplicateNotes(mid, listOf(key))
        return if (notes!!.size() == 0) {
            emptyList<NoteInfo>()
        } else {
            notes.valueAt(0)
        }
    }

    /**
     * Find all notes in the collection which have mid and a first field that matches key
     * Much faster than calling findDuplicateNotes(long, String) when the list of keys is large
     * @param mid note type id
     * @param keys list of keys
     * @return a SparseArray with a list of duplicate notes for each key
     */
    public fun findDuplicateNotes(
        mid: Long,
        keys: List<String>,
    ): SparseArray<MutableList<NoteInfo?>>? = compat.findDuplicateNotes(mid, keys)

    /**
     * Get the number of notes that exist for the specified note type ID
     * @param mid id of the note type to be used
     * @return number of notes that exist with that note type ID or -1 if there was a problem
     */
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
        val noteUri = Uri.withAppendedPath(Note.CONTENT_URI, noteId.toString())
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
            Note.CONTENT_URI
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
        val newNoteUri = addNoteInternal(mid, DEFAULT_DECK_ID, flds, setOf(TEST_TAG))
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
        resolver.delete(newNoteUri!!, null, null)
        return cards
    }

    /**
     * Insert a new basic front/back note type with two fields and one card
     * @param name name of the note type
     * @return the mid of the note type which was created, or null if it could not be created
     */
    public fun addNewBasicNoteType(name: String): Long? =
        addNewCustomNoteType(
            name,
            BasicNoteType.FIELDS,
            BasicNoteType.CARD_NAMES,
            BasicNoteType.QFMT,
            BasicNoteType.AFMT,
            null,
            null,
            null,
        )

    /**
     * Insert a new basic front/back note type with two fields and TWO cards
     * The first card goes from front->back, and the second goes from back->front
     * @param name name of the note type
     * @return the mid of the note type which was created, or null if it could not be created
     */
    public fun addNewBasic2NoteType(name: String): Long? =
        addNewCustomNoteType(
            name,
            Basic2NoteType.FIELDS,
            Basic2NoteType.CARD_NAMES,
            Basic2NoteType.QFMT,
            Basic2NoteType.AFMT,
            null,
            null,
            null,
        )

    /**
     * Insert a new note type into AnkiDroid.
     * See the [Anki Desktop Manual](https://docs.ankiweb.net/templates/intro.html) for more help
     * @param name name of note type
     * @param fields array of field names
     * @param cards array of names for the card templates
     * @param qfmt array of formatting strings for the question side of each template in cards
     * @param afmt array of formatting strings for the answer side of each template in cards
     * @param css css styling information to be shared across all of the templates. Use null for default CSS.
     * @param did default deck to add cards to when using this note type. Use null or #DEFAULT_DECK_ID for default deck.
     * @param sortf index of field to be used for sorting. Use null for unspecified (unsupported in provider spec v1)
     * @return the id of the note type which was created, or null if it could not be created
     */
    @Suppress("MemberVisibilityCanBePrivate") // silence IDE
    public fun addNewCustomNoteType(
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
        // Create the note type using dummy templates
        var values =
            ContentValues().apply {
                put(Model.NAME, name)
                put(Model.FIELD_NAMES, Utils.joinFields(fields))
                put(Model.NUM_CARDS, cards.size)
                put(Model.CSS, css)
                put(Model.DECK_ID, did)
                put(Model.SORT_FIELD_INDEX, sortf)
            }
        val noteTypeUri = resolver.insert(Model.CONTENT_URI, values) ?: return null
        // Set the remaining template parameters
        val templatesUri = Uri.withAppendedPath(noteTypeUri, "templates")
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
        return noteTypeUri.lastPathSegment!!.toLong()
    } // Get the current note type

    /**
     * Get the ID for the note type / note type which is currently in use
     * @return id for current note type, or < 0 if there was a problem
     */
    public val currentNoteTypeId: Long
        get() {
            // Get the current note type
            val uri = Uri.withAppendedPath(Model.CONTENT_URI, Model.CURRENT_NOTE_TYPE_ID)
            val singleNoteTypeQuery = resolver.query(uri, null, null, null, null) ?: return -1L
            return singleNoteTypeQuery.use { singleNoteTypeCursor ->
                singleNoteTypeCursor.moveToFirst()
                singleNoteTypeCursor.getLong(singleNoteTypeCursor.getColumnIndex(Model._ID))
            }
        }

    /**
     * Get the field names belonging to specified note type
     * @param noteTypeId the ID of the note type to use
     * @return the names of all the fields, or null if the note type doesn't exist or there was some other problem
     */
    public fun getFieldList(noteTypeId: Long): Array<String>? {
        // Get the current note type
        val uri = Uri.withAppendedPath(Model.CONTENT_URI, noteTypeId.toString())
        val noteTypeQuery = resolver.query(uri, null, null, null, null) ?: return null
        var splitFlds: Array<String>? = null
        noteTypeQuery.use { noteTypeCursor ->
            if (noteTypeCursor.moveToNext()) {
                splitFlds =
                    Utils.splitFields(
                        noteTypeCursor.getString(noteTypeCursor.getColumnIndex(Model.FIELD_NAMES)),
                    )
            }
        }
        return splitFlds
    }

    /**
     * Get a map of all note type ids and names
     * @return map of (id, name) pairs
     */
    public val noteTypeList: Map<Long, String>?
        get() = getNoteTYpeList(1)

    /**
     * Get a map of all note type ids and names with number of fields larger than minNumFields
     * @param minNumFields minimum number of fields to consider the note type for inclusion
     * @return map of (id, name) pairs or null if there was a problem
     */
    public fun getNoteTYpeList(minNumFields: Int): Map<Long, String>? {
        // Get the current note type
        val allNoteTypesQuery =
            resolver.query(Model.CONTENT_URI, null, null, null, null)
                ?: return null
        val noteTypes: MutableMap<Long, String> = HashMap()
        allNoteTypesQuery.use { allNoteTypesCursor ->
            while (allNoteTypesCursor.moveToNext()) {
                val noteTypeId = allNoteTypesCursor.getLong(allNoteTypesCursor.getColumnIndex(Model._ID))
                val name = allNoteTypesCursor.getString(allNoteTypesCursor.getColumnIndex(Model.NAME))
                val flds =
                    allNoteTypesCursor.getString(allNoteTypesCursor.getColumnIndex(Model.FIELD_NAMES))
                val numFlds: Int = Utils.splitFields(flds).size
                if (numFlds >= minNumFields) {
                    noteTypes[noteTypeId] = name
                }
            }
        }
        return noteTypes
    }

    /**
     * Get the name of the note type which has given ID
     * @param mid id of note type
     * @return the name of the note type, or null if no note type was found
     */
    public fun getNoteTypeName(mid: Long): String? = noteTypeList!![mid]

    /**
     * Create a new deck with specified name and save the reference to SharedPreferences for later
     * @param deckName name of the deck to add
     * @return id of the added deck, or null if the deck was not added
     */
    public fun addNewDeck(deckName: String): Long? {
        // Create a new note
        val values = ContentValues().apply { put(Deck.DECK_NAME, deckName) }
        val newDeckUri = resolver.insert(Deck.CONTENT_ALL_URI, values)
        return if (newDeckUri != null) {
            newDeckUri.lastPathSegment!!.toLong()
        } else {
            null
        }
    }

    /**
     * Get the name of the selected deck
     * @return deck name or null if there was a problem
     */
    public val selectedDeckName: String?
        get() {
            val selectedDeckQuery =
                resolver.query(
                    Deck.CONTENT_SELECTED_URI,
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
        } // Get the current note type

    /**
     * Get a list of all the deck id / name pairs
     * @return Map of (id, name) pairs, or null if there was a problem
     */
    public val deckList: Map<Long, String>?
        get() {
            // Get the current note type
            val allDecksQuery =
                resolver.query(Deck.CONTENT_ALL_URI, null, null, null, null) ?: return null
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
    public fun getDeckName(did: Long): String? = deckList!![did]

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
        @SuppressLint("WrongConstant") // ComponentInfoFlags bug: GET_META_DATA.toLong() was invalid
        get() {
            // PackageManager#resolveContentProvider docs suggest flags should be 0 (but that gives null metadata)
            // GET_META_DATA seems to work anyway
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.resolveContentProvider(
                        FlashCardsContract.AUTHORITY,
                        PackageManager.ComponentInfoFlags.of(
                            PackageManager.GET_META_DATA.toLong(),
                        ),
                    )
                } else {
                    context.packageManager.resolveContentProvider(
                        FlashCardsContract.AUTHORITY,
                        PackageManager.GET_META_DATA,
                    )
                }

            return if (info?.metaData != null &&
                info.metaData.containsKey(PROVIDER_SPEC_META_DATA_KEY)
            ) {
                info.metaData.getInt(PROVIDER_SPEC_META_DATA_KEY)
            } else {
                DEFAULT_PROVIDER_SPEC_VALUE
            }
        }

    private fun hasReadWritePermission(): Boolean =
        context.checkPermission(
            READ_WRITE_PERMISSION,
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
         * Query all notes for a given note type
         * @param noteTypeId the note type ID to limit query to
         * @return a cursor with all notes matching noteTypeId
         */
        fun queryNotes(noteTypeId: Long): Cursor?

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
         * @param noteTypeId the note type ID to limit the search to
         * @param keys  list of keys for each note
         * @return array with a list of NoteInfo objects for each key if duplicates exist
         */
        fun findDuplicateNotes(
            noteTypeId: Long,
            keys: List<String?>,
        ): SparseArray<MutableList<NoteInfo?>>?
    }

    private open inner class CompatV1 : Compat {
        override fun queryNotes(noteTypeId: Long): Cursor? {
            val noteTypeName = getNoteTypeName(noteTypeId) ?: return null
            val queryFormat = "note:\"$noteTypeName\""
            return resolver.query(
                Note.CONTENT_URI,
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
            noteTypeId: Long,
            keys: List<String?>,
        ): SparseArray<MutableList<NoteInfo?>>? {
            // Content provider spec v1 does not support direct querying of the notes table, so use Anki browser syntax
            val noteTypeName = getNoteTypeName(noteTypeId) ?: return null
            val noteTypeFieldList = getFieldList(noteTypeId) ?: return null
            val duplicates = SparseArray<MutableList<NoteInfo?>>()
            // Loop through each item in fieldsArray looking for an existing note, and add it to the duplicates array
            val queryFormat = "${noteTypeFieldList[0]}:\"%%s\" note:\"$noteTypeName\""
            for (outputPos in keys.indices) {
                val selection = String.format(queryFormat, keys[outputPos])
                val query =
                    resolver.query(
                        Note.CONTENT_URI,
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
        override fun queryNotes(noteTypeId: Long): Cursor? =
            resolver.query(
                Note.CONTENT_URI_V2,
                PROJECTION,
                String.format(Locale.US, "%s=%d", Note.MID, noteTypeId),
                null,
                null,
            )

        override fun insertNotes(
            deckId: Long,
            valuesArr: Array<ContentValues>,
        ): Int {
            val builder =
                Note.CONTENT_URI.buildUpon().appendQueryParameter(
                    Note.DECK_ID_QUERY_PARAM,
                    deckId.toString(),
                )
            return resolver.bulkInsert(builder.build(), valuesArr)
        }

        override fun findDuplicateNotes(
            noteTypeId: Long,
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
            // Query for notes that have specified note type and checksum of first field matches
            val sel =
                String.format(
                    Locale.US,
                    "%s=%d and %s in (%s)",
                    Note.MID,
                    noteTypeId,
                    Note.CSUM,
                    csums.joinToString(separator = ","),
                )
            val notesTableQuery =
                resolver.query(
                    Note.CONTENT_URI_V2,
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
        private val PROJECTION =
            arrayOf(
                Note._ID,
                Note.FLDS,
                Note.TAGS,
            )

        /**
         * Get the AnkiDroid package name that the API will communicate with.
         * This can be used to check that a supported version of AnkiDroid is installed,
         * or to get the application label and icon, etc.
         * @param context a Context that can be used to get the PackageManager
         * @return packageId of AnkiDroid if a supported version is not installed, otherwise null
         */
        @JvmStatic // required for API
        public fun getAnkiDroidPackageName(context: Context): String? {
            val manager = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager
                    .resolveContentProvider(
                        FlashCardsContract.AUTHORITY,
                        PackageManager.ComponentInfoFlags.of(0L),
                    )?.packageName
            } else {
                manager.resolveContentProvider(FlashCardsContract.AUTHORITY, 0)?.packageName
            }
        }
    }
}
