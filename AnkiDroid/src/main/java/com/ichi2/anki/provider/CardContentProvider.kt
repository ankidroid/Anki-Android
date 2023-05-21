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
package com.ichi2.anki.provider

import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ichi2.anki.*
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.compat.CompatHelper.Companion.isMarshmallow
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.BUTTON_TYPE
import com.ichi2.libanki.DB.Companion.safeEndInTransaction
import com.ichi2.libanki.Models.AllowEmpty
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.libanki.sched.AbstractSched
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.sched.TreeNode
import com.ichi2.libanki.sched.findInDeckTree
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.FileUtil.internalizeUri
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.Permissions.arePermissionsDefinedInManifest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Supported URIs:
 *
 * * .../notes (search for notes)
 * * .../notes/# (direct access to note)
 * * .../notes/#/cards (access cards of note)
 * * .../notes/#/cards/# (access specific card of note)
 * * .../models (search for models)
 * * .../models/# (direct access to model). String id 'current' can be used in place of # for the current model
 * * .../models/#/fields (access to field definitions of a model)
 * * .../models/#/templates (access to card templates of a model)
 * * .../schedule (access the study schedule)
 * * .../decks (access the deck list)
 * * .../decks/# (access the specified deck)
 * * .../selected_deck (access the currently selected deck)
 * * .../media (add media files to anki collection.media)
 *
 * Note that unlike Android's contact providers:
 *
 *  * it's not possible to access cards of more than one note at a time
 *  * it's not possible to access cards of a note without providing the note's ID
 *
 */
class CardContentProvider : ContentProvider() {

    companion object {
        /* URI types */
        private const val NOTES = 1000
        private const val NOTES_ID = 1001
        private const val NOTES_ID_CARDS = 1003
        private const val NOTES_ID_CARDS_ORD = 1004
        private const val NOTES_V2 = 1005
        private const val MODELS = 2000
        private const val MODELS_ID = 2001
        private const val MODELS_ID_EMPTY_CARDS = 2002
        private const val MODELS_ID_TEMPLATES = 2003
        private const val MODELS_ID_TEMPLATES_ID = 2004
        private const val MODELS_ID_FIELDS = 2005
        private const val SCHEDULE = 3000
        private const val DECKS = 4000
        private const val DECK_SELECTED = 4001
        private const val DECKS_ID = 4002
        private const val MEDIA = 5000
        private val sUriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        /**
         * The names of the columns returned by this content provider differ slightly from the names
         * given of the database columns. This list is used to convert the column names used in a
         * projection by the user into DB column names.
         *
         *
         * This is currently only "_id" (projection) vs. "id" (Anki DB). But should probably be
         * applied to more columns. "MID", "USN", "MOD" are not really user friendly.
         */
        private val sDefaultNoteProjectionDBAccess = FlashCardsContract.Note.DEFAULT_PROJECTION.clone()
        private const val COL_NULL_ERROR_MSG = "AnkiDroid database inaccessible. Open AnkiDroid to see what's wrong."

        private fun sanitizeNoteProjection(projection: Array<String>?): Array<String> {
            if (projection == null || projection.isEmpty()) {
                return sDefaultNoteProjectionDBAccess
            }
            val sanitized = ArrayList<String>(projection.size)
            for (column in projection) {
                val idx = FlashCardsContract.Note.DEFAULT_PROJECTION.indexOf(column)
                if (idx >= 0) {
                    sanitized.add(sDefaultNoteProjectionDBAccess[idx])
                } else {
                    throw IllegalArgumentException("Unknown column $column")
                }
            }
            return sanitized.toTypedArray()
        }

        init {
            fun addUri(path: String, code: Int) = sUriMatcher.addURI(FlashCardsContract.AUTHORITY, path, code)
            // Here you can see all the URIs at a glance
            addUri("notes", NOTES)
            addUri("notes_v2", NOTES_V2)
            addUri("notes/#", NOTES_ID)
            addUri("notes/#/cards", NOTES_ID_CARDS)
            addUri("notes/#/cards/#", NOTES_ID_CARDS_ORD)
            addUri("models", MODELS)
            addUri("models/*", MODELS_ID) // the model ID can also be "current"
            addUri("models/*/empty_cards", MODELS_ID_EMPTY_CARDS)
            addUri("models/*/templates", MODELS_ID_TEMPLATES)
            addUri("models/*/templates/#", MODELS_ID_TEMPLATES_ID)
            addUri("models/*/fields", MODELS_ID_FIELDS)
            addUri("schedule/", SCHEDULE)
            addUri("decks/", DECKS)
            addUri("decks/#", DECKS_ID)
            addUri("selected_deck/", DECK_SELECTED)
            addUri("media", MEDIA)

            for (idx in sDefaultNoteProjectionDBAccess.indices) {
                if (sDefaultNoteProjectionDBAccess[idx] == FlashCardsContract.Note._ID) {
                    sDefaultNoteProjectionDBAccess[idx] = "id as _id"
                }
            }
        }
    }

    override fun onCreate(): Boolean {
        // Initialize content provider on startup.
        Timber.d("CardContentProvider: onCreate")
        return true
    }

    // keeps the nullability declared by the platform
    @Suppress("RedundantNullableReturnType")
    override fun getType(uri: Uri): String? {
        // Find out what data the user is requesting
        return when (sUriMatcher.match(uri)) {
            NOTES_V2, NOTES -> FlashCardsContract.Note.CONTENT_TYPE
            NOTES_ID -> FlashCardsContract.Note.CONTENT_ITEM_TYPE
            NOTES_ID_CARDS, MODELS_ID_EMPTY_CARDS -> FlashCardsContract.Card.CONTENT_TYPE
            NOTES_ID_CARDS_ORD -> FlashCardsContract.Card.CONTENT_ITEM_TYPE
            MODELS -> FlashCardsContract.Model.CONTENT_TYPE
            MODELS_ID -> FlashCardsContract.Model.CONTENT_ITEM_TYPE
            MODELS_ID_TEMPLATES -> FlashCardsContract.CardTemplate.CONTENT_TYPE
            MODELS_ID_TEMPLATES_ID -> FlashCardsContract.CardTemplate.CONTENT_ITEM_TYPE
            SCHEDULE -> FlashCardsContract.ReviewInfo.CONTENT_TYPE
            DECKS, DECK_SELECTED, DECKS_ID -> FlashCardsContract.Deck.CONTENT_TYPE
            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
    }

    /** Only enforce permissions for queries and inserts on Android M and above, or if its a 'rogue client'  */
    private fun shouldEnforceQueryOrInsertSecurity(): Boolean {
        return isMarshmallow || knownRogueClient()
    }

    /** Enforce permissions for all updates on Android M and above. Otherwise block depending on URI and client app  */
    private fun shouldEnforceUpdateSecurity(uri: Uri): Boolean {
        val whitelist = listOf(NOTES_ID_CARDS_ORD, MODELS_ID, MODELS_ID_TEMPLATES_ID, SCHEDULE, DECK_SELECTED)
        return isMarshmallow || !whitelist.contains(sUriMatcher.match(uri)) || knownRogueClient()
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, order: String?): Cursor? {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("query", uri)
        }
        val col = CollectionHelper.instance.getCol(context!!)
            ?: throw IllegalStateException(COL_NULL_ERROR_MSG)
        Timber.d(getLogMessage("query", uri))

        // Find out what data the user is requesting
        return when (sUriMatcher.match(uri)) {
            NOTES_V2 -> {
                /* Search for notes using direct SQL query */
                val proj = sanitizeNoteProjection(projection)
                val sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, selection, null, null, order, null)
                col.db.query(sql, *(selectionArgs ?: arrayOf()))
            }
            NOTES -> {
                /* Search for notes using the libanki browser syntax */
                val proj = sanitizeNoteProjection(projection)
                val query = selection ?: ""
                val noteIds = col.findNotes(query)
                if (noteIds.isNotEmpty()) {
                    val sel = "id in (${noteIds.joinToString(",")})"
                    val sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, sel, null, null, order, null)
                    col.db.database.query(sql)
                } else {
                    null
                }
            }
            NOTES_ID -> {
                /* Direct access note with specific ID*/
                val noteId = uri.pathSegments[1]
                val proj = sanitizeNoteProjection(projection)
                val sql = SQLiteQueryBuilder.buildQueryString(false, "notes", proj, "id=?", null, null, order, null)
                col.db.query(sql, noteId)
            }
            NOTES_ID_CARDS -> {
                val currentNote = getNoteFromUri(uri, col)
                val columns = projection ?: FlashCardsContract.Card.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                for (currentCard: Card in currentNote.cards()) {
                    addCardToCursor(currentCard, rv, col, columns)
                }
                rv
            }
            NOTES_ID_CARDS_ORD -> {
                val currentCard = getCardFromUri(uri, col)
                val columns = projection ?: FlashCardsContract.Card.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                addCardToCursor(currentCard, rv, col, columns)
                rv
            }
            MODELS -> {
                val models = col.models
                val columns = projection ?: FlashCardsContract.Model.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                for (modelId: NoteTypeId in models.getModels().keys) {
                    addModelToCursor(modelId, models, rv, columns)
                }
                rv
            }
            MODELS_ID -> {
                val modelId = getModelIdFromUri(uri, col)
                val columns = projection ?: FlashCardsContract.Model.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                addModelToCursor(modelId, col.models, rv, columns)
                rv
            }
            MODELS_ID_TEMPLATES -> {
                /* Direct access model templates */
                val models = col.models
                val currentModel = models.get(getModelIdFromUri(uri, col))
                val columns = projection ?: FlashCardsContract.CardTemplate.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                try {
                    val templates = currentModel!!.getJSONArray("tmpls")
                    var idx = 0
                    while (idx < templates.length()) {
                        val template = templates.getJSONObject(idx)
                        addTemplateToCursor(template, currentModel, idx + 1, models, rv, columns)
                        idx++
                    }
                } catch (e: JSONException) {
                    throw IllegalArgumentException("Model is malformed", e)
                }
                rv
            }
            MODELS_ID_TEMPLATES_ID -> {
                /* Direct access model template with specific ID */
                val models = col.models
                val ord = uri.lastPathSegment!!.toInt()
                val currentModel = models.get(getModelIdFromUri(uri, col))
                val columns = projection ?: FlashCardsContract.CardTemplate.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                try {
                    val template = getTemplateFromUri(uri, col)
                    addTemplateToCursor(template, currentModel, ord + 1, models, rv, columns)
                } catch (e: JSONException) {
                    throw IllegalArgumentException("Model is malformed", e)
                }
                rv
            }
            SCHEDULE -> {
                val columns = projection ?: FlashCardsContract.ReviewInfo.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                val selectedDeckBeforeQuery = col.decks.selected()
                var deckIdOfTemporarilySelectedDeck: Long = -1
                var limit = 1 // the number of scheduled cards to return
                var selectionArgIndex = 0

                // parsing the selection arguments
                if (selection != null) {
                    val args = selection.split(",").toTypedArray() // split selection to get arguments like "limit=?"
                    for (arg: String in args) {
                        val keyAndValue = arg.split("=").toTypedArray() // split arguments into key ("limit") and value ("?")
                        try {
                            // check if value is a placeholder ("?"), if so replace with the next value of selectionArgs
                            val value = if ("?" == keyAndValue[1].trim { it <= ' ' }) selectionArgs!![selectionArgIndex++] else keyAndValue[1]
                            if ("limit" == keyAndValue[0].trim { it <= ' ' }) {
                                limit = value.toInt()
                            } else if ("deckID" == keyAndValue[0].trim { it <= ' ' }) {
                                deckIdOfTemporarilySelectedDeck = value.toLong()
                                if (!selectDeckWithCheck(col, deckIdOfTemporarilySelectedDeck)) {
                                    return rv // if the provided deckID is wrong, return empty cursor.
                                }
                            }
                        } catch (nfe: NumberFormatException) {
                            Timber.w(nfe)
                        }
                    }
                }

                // retrieve the number of cards provided by the selection parameter "limit"
                col.sched.deferReset()
                var k = 0
                while (k < limit) {
                    val currentCard = col.sched.card ?: break
                    val buttonCount = col.sched.answerButtons(currentCard)
                    val buttonTexts = JSONArray()
                    var i = 0
                    while (i < buttonCount) {
                        buttonTexts.put(col.sched.nextIvlStr(context!!, currentCard, i + 1))
                        i++
                    }
                    addReviewInfoToCursor(currentCard, buttonTexts, buttonCount, rv, col, columns)
                    k++
                }
                if (deckIdOfTemporarilySelectedDeck != -1L) { // if the selected deck was changed
                    // change the selected deck back to the one it was before the query
                    col.decks.select(selectedDeckBeforeQuery)
                }
                rv
            }
            DECKS -> {
                val columns = projection ?: FlashCardsContract.Deck.DEFAULT_PROJECTION
                val allDecks = col.sched.deckDueTree()
                val rv = MatrixCursor(columns, 1)
                fun forEach(nodeList: List<TreeNode<DeckDueTreeNode>>, fn: (DeckDueTreeNode) -> Unit) {
                    for (node in nodeList) {
                        fn(node.value)
                        forEach(node.children, fn)
                    }
                }
                forEach(allDecks) {
                    addDeckToCursor(
                        it.did,
                        it.fullDeckName,
                        getDeckCountsFromDueTreeNode(it),
                        rv,
                        col,
                        columns
                    )
                }
                rv
            }
            DECKS_ID -> {
                /* Direct access deck */
                val columns = projection ?: FlashCardsContract.Deck.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                val allDecks = col.sched.deckDueTree()
                val desiredDeckId = uri.pathSegments[1].toLong()
                findInDeckTree(allDecks, desiredDeckId)?.let {
                    addDeckToCursor(it.did, it.fullDeckName, getDeckCountsFromDueTreeNode(it), rv, col, columns)
                }
                rv
            }
            DECK_SELECTED -> {
                val id = col.decks.selected()
                val name = col.decks.name(id)
                val columns = projection ?: FlashCardsContract.Deck.DEFAULT_PROJECTION
                val rv = MatrixCursor(columns, 1)
                val counts = JSONArray(listOf(col.sched.counts()))
                addDeckToCursor(id, name, counts, rv, col, columns)
                rv
            }
            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
    }

    private fun getDeckCountsFromDueTreeNode(deck: DeckDueTreeNode): JSONArray = JSONArray().apply {
        put(deck.lrnCount)
        put(deck.revCount)
        put(deck.newCount)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (!hasReadWritePermission() && shouldEnforceUpdateSecurity(uri)) {
            throwSecurityException("update", uri)
        }
        val col = CollectionHelper.instance.getCol(context!!)
            ?: throw IllegalStateException(COL_NULL_ERROR_MSG)
        col.log(getLogMessage("update", uri))

        // Find out what data the user is requesting
        val match = sUriMatcher.match(uri)
        var updated = 0 // Number of updated entries (return value)
        when (match) {
            NOTES_V2, NOTES -> throw IllegalArgumentException("Not possible to update notes directly (only through data URI)")
            NOTES_ID -> {
                /* Direct access note details
                 */
                val currentNote = getNoteFromUri(uri, col)
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                val valueSet = values!!.valueSet()
                for ((key, tags) in valueSet) {
                    // when the client does not specify FLDS, then don't update the FLDS
                    when (key) {
                        FlashCardsContract.Note.FLDS -> {
                            // Update FLDS
                            Timber.d("CardContentProvider: flds update...")
                            val newFldsEncoded = tags as String
                            val flds = Utils.splitFields(newFldsEncoded)
                            // Check that correct number of flds specified
                            require(flds.size == currentNote.fields.size) { "Incorrect flds argument : $newFldsEncoded" }
                            // Update the note
                            var idx = 0
                            while (idx < flds.size) {
                                currentNote.setField(idx, flds[idx])
                                idx++
                            }
                            updated++
                        }
                        FlashCardsContract.Note.TAGS -> {
                            // Update tags
                            Timber.d("CardContentProvider: tags update...")
                            if (tags != null) {
                                currentNote.setTagsFromStr(tags.toString())
                            }
                            updated++
                        }
                        else -> {
                            // Unsupported column
                            throw IllegalArgumentException("Unsupported column: $key")
                        }
                    }
                }
                Timber.d("CardContentProvider: Saving note...")
                currentNote.flush()
            }
            NOTES_ID_CARDS -> throw UnsupportedOperationException("Not yet implemented")
            NOTES_ID_CARDS_ORD -> {
                val currentCard = getCardFromUri(uri, col)
                var isDeckUpdate = false
                var did = Decks.NOT_FOUND_DECK_ID
                // the key of the ContentValues contains the column name
                // the value of the ContentValues contains the row value.
                val valueSet = values!!.valueSet()
                for ((key) in valueSet) {
                    // Only updates on deck id is supported
                    isDeckUpdate = key == FlashCardsContract.Card.DECK_ID
                    did = values.getAsLong(key)
                }
                require(!col.decks.isDyn(did)) { "Cards cannot be moved to a filtered deck" }
                /* now update the card
                 */if (isDeckUpdate && did >= 0) {
                    Timber.d("CardContentProvider: Moving card to other deck...")
                    col.decks.flush()
                    currentCard.did = did
                    currentCard.flush()
                    col.save()
                    updated++
                } else {
                    // User tries an operation that is not (yet?) supported.
                    throw IllegalArgumentException("Currently only updates of decks are supported")
                }
            }
            MODELS -> throw IllegalArgumentException("Cannot update models in bulk")
            MODELS_ID -> {
                // Get the input parameters
                val newModelName = values!!.getAsString(FlashCardsContract.Model.NAME)
                val newCss = values.getAsString(FlashCardsContract.Model.CSS)
                val newDid = values.getAsString(FlashCardsContract.Model.DECK_ID)
                val newFieldList = values.getAsString(FlashCardsContract.Model.FIELD_NAMES)
                require(newFieldList == null) {
                    // Changing the field names would require a full-sync
                    "Field names cannot be changed via provider"
                }
                val newSortf = values.getAsInteger(FlashCardsContract.Model.SORT_FIELD_INDEX)
                val newType = values.getAsInteger(FlashCardsContract.Model.TYPE)
                val newLatexPost = values.getAsString(FlashCardsContract.Model.LATEX_POST)
                val newLatexPre = values.getAsString(FlashCardsContract.Model.LATEX_PRE)
                // Get the original note JSON
                val model = col.models.get(getModelIdFromUri(uri, col))
                try {
                    // Update model name and/or css
                    if (newModelName != null) {
                        model!!.put("name", newModelName)
                        updated++
                    }
                    if (newCss != null) {
                        model!!.put("css", newCss)
                        updated++
                    }
                    if (newDid != null) {
                        if (col.decks.isDyn(newDid.toLong())) {
                            throw IllegalArgumentException("Cannot set a filtered deck as default deck for a model")
                        }
                        model!!.put("did", newDid)
                        updated++
                    }
                    if (newSortf != null) {
                        model!!.put("sortf", newSortf)
                        updated++
                    }
                    if (newType != null) {
                        model!!.put("type", newType)
                        updated++
                    }
                    if (newLatexPost != null) {
                        model!!.put("latexPost", newLatexPost)
                        updated++
                    }
                    if (newLatexPre != null) {
                        model!!.put("latexPre", newLatexPre)
                        updated++
                    }
                    col.models.save(model)
                    col.save()
                } catch (e: JSONException) {
                    Timber.e(e, "JSONException updating model")
                }
            }
            MODELS_ID_TEMPLATES -> throw IllegalArgumentException("Cannot update templates in bulk")
            MODELS_ID_TEMPLATES_ID -> {
                val mid = values!!.getAsLong(FlashCardsContract.CardTemplate.MODEL_ID)
                val ord = values.getAsInteger(FlashCardsContract.CardTemplate.ORD)
                val name = values.getAsString(FlashCardsContract.CardTemplate.NAME)
                val qfmt = values.getAsString(FlashCardsContract.CardTemplate.QUESTION_FORMAT)
                val afmt = values.getAsString(FlashCardsContract.CardTemplate.ANSWER_FORMAT)
                val bqfmt = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT)
                val bafmt = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT)
                // Throw exception if read-only fields are included
                if (mid != null || ord != null) {
                    throw IllegalArgumentException("Updates to mid or ord are not allowed")
                }
                // Update the model
                try {
                    val templateOrd = uri.lastPathSegment!!.toInt()
                    val existingModel = col.models.get(getModelIdFromUri(uri, col))
                    val templates = existingModel!!.getJSONArray("tmpls")
                    val template = templates.getJSONObject(templateOrd)
                    if (name != null) {
                        template.put("name", name)
                        updated++
                    }
                    if (qfmt != null) {
                        template.put("qfmt", qfmt)
                        updated++
                    }
                    if (afmt != null) {
                        template.put("afmt", afmt)
                        updated++
                    }
                    if (bqfmt != null) {
                        template.put("bqfmt", bqfmt)
                        updated++
                    }
                    if (bafmt != null) {
                        template.put("bafmt", bafmt)
                        updated++
                    }
                    // Save the model
                    templates.put(templateOrd, template)
                    existingModel.put("tmpls", templates)
                    col.models.save(existingModel, true)
                    col.save()
                } catch (e: JSONException) {
                    throw IllegalArgumentException("Model is malformed", e)
                }
            }
            SCHEDULE -> {
                val valueSet = values!!.valueSet()
                var cardOrd = -1
                var noteID: Long = -1
                var ease = -1
                var timeTaken: Long = -1
                var bury = -1
                var suspend = -1
                for ((key) in valueSet) {
                    when (key) {
                        FlashCardsContract.ReviewInfo.NOTE_ID -> noteID = values.getAsLong(key)
                        FlashCardsContract.ReviewInfo.CARD_ORD -> cardOrd = values.getAsInteger(key)
                        FlashCardsContract.ReviewInfo.EASE -> ease = values.getAsInteger(key)
                        FlashCardsContract.ReviewInfo.TIME_TAKEN -> timeTaken = values.getAsLong(key)
                        FlashCardsContract.ReviewInfo.BURY -> bury = values.getAsInteger(key)
                        FlashCardsContract.ReviewInfo.SUSPEND -> suspend = values.getAsInteger(key)
                    }
                }
                if (cardOrd != -1 && noteID != -1L) {
                    val cardToAnswer: Card = getCard(noteID, cardOrd, col)
                    @Suppress("SENSELESS_COMPARISON")
                    @KotlinCleanup("based on getCard() method, cardToAnswer does seem to be not null")
                    if (cardToAnswer != null) {
                        if (bury == 1) {
                            // bury card
                            buryOrSuspendCard(col, col.sched, cardToAnswer, true)
                        } else if (suspend == 1) {
                            // suspend card
                            buryOrSuspendCard(col, col.sched, cardToAnswer, false)
                        } else {
                            answerCard(col, col.sched, cardToAnswer, ease, timeTaken)
                        }
                        updated++
                    } else {
                        Timber.e(
                            "Requested card with noteId %d and cardOrd %d was not found. Either the provided " +
                                "noteId/cardOrd were wrong or the card has been deleted in the meantime.",
                            noteID,
                            cardOrd
                        )
                    }
                }
            }
            DECKS -> throw IllegalArgumentException("Can't update decks in bulk")
            DECKS_ID -> throw UnsupportedOperationException("Not yet implemented")
            DECK_SELECTED -> {
                val valueSet = values!!.valueSet()
                for ((key) in valueSet) {
                    if (key == FlashCardsContract.Deck.DECK_ID) {
                        val deckId = values.getAsLong(key)
                        if (selectDeckWithCheck(col, deckId)) {
                            updated++
                        }
                    }
                }
                col.save()
            }
            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
        return updated
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        if (!hasReadWritePermission()) {
            throwSecurityException("delete", uri)
        }
        val col = CollectionHelper.instance.getCol(context!!)
            ?: throw IllegalStateException(COL_NULL_ERROR_MSG)
        col.log(getLogMessage("delete", uri))
        return when (sUriMatcher.match(uri)) {
            NOTES_ID -> {
                col.remNotes(longArrayOf(uri.pathSegments[1].toLong()))
                1
            }
            MODELS_ID_EMPTY_CARDS -> {
                val model = col.models.get(getModelIdFromUri(uri, col)) ?: return -1
                val cids: List<Long> = col.genCards(col.models.nids(model), model)!!
                col.removeCardsAndOrphanedNotes(cids)
                cids.size
            }
            else -> throw UnsupportedOperationException()
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
    override fun bulkInsert(uri: Uri, values: Array<ContentValues>): Int {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("bulkInsert", uri)
        }

        // by default, #bulkInsert simply calls insert for each item in #values
        // but in some cases, we want to override this behavior
        val match = sUriMatcher.match(uri)
        if (match == NOTES) {
            val deckIdStr = uri.getQueryParameter(FlashCardsContract.Note.DECK_ID_QUERY_PARAM)
            if (deckIdStr != null) {
                try {
                    val deckId = deckIdStr.toLong()
                    return bulkInsertNotes(values, deckId)
                } catch (e: NumberFormatException) {
                    Timber.d(e, "Invalid %s: %s", FlashCardsContract.Note.DECK_ID_QUERY_PARAM, deckIdStr)
                }
            }
            // deckId not specified, so default to #super implementation (as in spec version 1)
        }
        return super.bulkInsert(uri, values)
    }

    /**
     * This implementation optimizes for when the notes are grouped according to model.
     */
    private fun bulkInsertNotes(valuesArr: Array<ContentValues>?, deckId: DeckId): Int {
        if (valuesArr == null || valuesArr.isEmpty()) {
            return 0
        }
        val col = CollectionHelper.instance.getCol(context!!)
            ?: throw IllegalStateException(COL_NULL_ERROR_MSG)
        if (col.decks.isDyn(deckId)) {
            throw IllegalArgumentException("A filtered deck cannot be specified as the deck in bulkInsertNotes")
        }
        col.log(String.format(Locale.US, "bulkInsertNotes: %d items.\n%s", valuesArr.size, getLogMessage("bulkInsert", null)))

        // for caching model information (so we don't have to query for each note)
        var modelId = Models.NOT_FOUND_NOTE_TYPE
        var model: Model? = null
        col.decks.flush() // is it okay to move this outside the for-loop? Is it needed at all?
        val sqldb = col.db.database
        return try {
            var result = 0
            sqldb.beginTransaction()
            for (i in valuesArr.indices) {
                val values: ContentValues = valuesArr[i]
                val flds = values.getAsString(FlashCardsContract.Note.FLDS) ?: continue
                val allowEmpty = AllowEmpty.fromBoolean(values.getAsBoolean(FlashCardsContract.Note.ALLOW_EMPTY))
                val thisModelId = values.getAsLong(FlashCardsContract.Note.MID)
                if (thisModelId == null || thisModelId < 0) {
                    Timber.d("Unable to get model at index: %d", i)
                    continue
                }
                val fldsArray = Utils.splitFields(flds)
                if (model == null || thisModelId != modelId) {
                    // new modelId so need to recalculate model, modelId and invalidate duplicateChecker (which is based on previous model)
                    model = col.models.get(thisModelId)
                    modelId = thisModelId
                }

                // Create empty note
                val newNote = Note(col, model!!) // for some reason we cannot pass modelId in here
                // Set fields
                // Check that correct number of flds specified
                if (fldsArray.size != newNote.fields.size) {
                    throw IllegalArgumentException("Incorrect flds argument : $flds")
                }
                for (idx in fldsArray.indices) {
                    newNote.setField(idx, fldsArray[idx])
                }
                // Set tags
                val tags = values.getAsString(FlashCardsContract.Note.TAGS)
                if (tags != null) {
                    newNote.setTagsFromStr(tags)
                }
                // Add to collection
                col.addNote(newNote, allowEmpty)
                for (card: Card in newNote.cards()) {
                    card.did = deckId
                    card.flush()
                }
                result++
            }
            col.save()
            sqldb.setTransactionSuccessful()
            result
        } finally {
            sqldb.safeEndInTransaction()
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (!hasReadWritePermission() && shouldEnforceQueryOrInsertSecurity()) {
            throwSecurityException("insert", uri)
        }
        val col = CollectionHelper.instance.getCol(context!!)
            ?: throw IllegalStateException(COL_NULL_ERROR_MSG)
        col.log(getLogMessage("insert", uri))

        // Find out what data the user is requesting
        return when (sUriMatcher.match(uri)) {
            NOTES -> {
                /* Insert new note with specified fields and tags
                 */
                val modelId = values!!.getAsLong(FlashCardsContract.Note.MID)
                val flds = values.getAsString(FlashCardsContract.Note.FLDS)
                val tags = values.getAsString(FlashCardsContract.Note.TAGS)
                val allowEmpty = AllowEmpty.fromBoolean(values.getAsBoolean(FlashCardsContract.Note.ALLOW_EMPTY))
                // Create empty note
                val newNote = Note(col, col.models.get(modelId)!!)
                // Set fields
                val fldsArray = Utils.splitFields(flds)
                // Check that correct number of flds specified
                if (fldsArray.size != newNote.fields.size) {
                    throw IllegalArgumentException("Incorrect flds argument : $flds")
                }
                var idx = 0
                while (idx < fldsArray.size) {
                    newNote.setField(idx, fldsArray[idx])
                    idx++
                }
                // Set tags
                if (tags != null) {
                    newNote.setTagsFromStr(tags)
                }
                // Add to collection
                col.addNote(newNote, allowEmpty)
                col.save()
                Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, newNote.id.toString())
            }
            NOTES_ID -> throw IllegalArgumentException("Not possible to insert note with specific ID")
            NOTES_ID_CARDS, NOTES_ID_CARDS_ORD -> throw IllegalArgumentException("Not possible to insert cards directly (only through NOTES)")
            MODELS -> {
                // Get input arguments
                val modelName = values!!.getAsString(FlashCardsContract.Model.NAME)
                val css = values.getAsString(FlashCardsContract.Model.CSS)
                val did = values.getAsLong(FlashCardsContract.Model.DECK_ID)
                val fieldNames = values.getAsString(FlashCardsContract.Model.FIELD_NAMES)
                val numCards = values.getAsInteger(FlashCardsContract.Model.NUM_CARDS)
                val sortf = values.getAsInteger(FlashCardsContract.Model.SORT_FIELD_INDEX)
                val type = values.getAsInteger(FlashCardsContract.Model.TYPE)
                val latexPost = values.getAsString(FlashCardsContract.Model.LATEX_POST)
                val latexPre = values.getAsString(FlashCardsContract.Model.LATEX_PRE)
                // Throw exception if required fields empty
                if (modelName == null || fieldNames == null || numCards == null) {
                    throw IllegalArgumentException("Model name, field_names, and num_cards can't be empty")
                }
                if (did != null && col.decks.isDyn(did)) {
                    throw IllegalArgumentException("Cannot set a filtered deck as default deck for a model")
                }
                // Create a new model
                val mm = col.models
                val newModel = mm.newModel(modelName)
                return try {
                    // Add the fields
                    val allFields = Utils.splitFields(fieldNames)
                    for (f: String? in allFields) {
                        mm.addFieldInNewModel(newModel, mm.newField(f!!))
                    }
                    // Add some empty card templates
                    var idx = 0
                    while (idx < numCards) {
                        val cardName = context!!.resources.getString(R.string.card_n_name, idx + 1)
                        val t = Models.newTemplate(cardName)
                        t.put("qfmt", "{{${allFields[0]}}}")
                        var answerField: String? = allFields[0]
                        if (allFields.size > 1) {
                            answerField = allFields[1]
                        }
                        t.put("afmt", "{{FrontSide}}\\n\\n<hr id=answer>\\n\\n{{$answerField}}")
                        mm.addTemplateInNewModel(newModel, t)
                        idx++
                    }
                    // Add the CSS if specified
                    if (css != null) {
                        newModel.put("css", css)
                    }
                    // Add the did if specified
                    if (did != null) {
                        newModel.put("did", did)
                    }
                    if (sortf != null && sortf < allFields.size) {
                        newModel.put("sortf", sortf)
                    }
                    if (type != null) {
                        newModel.put("type", type)
                    }
                    if (latexPost != null) {
                        newModel.put("latexPost", latexPost)
                    }
                    if (latexPre != null) {
                        newModel.put("latexPre", latexPre)
                    }
                    // Add the model to collection (from this point on edits will require a full-sync)
                    mm.add(newModel)
                    col.save()
                    // Get the mid and return a URI
                    val mid = newModel.getLong("id").toString()
                    Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, mid)
                } catch (e: JSONException) {
                    Timber.e(e, "Could not set a field of new model %s", modelName)
                    null
                }
            }
            MODELS_ID -> throw IllegalArgumentException("Not possible to insert model with specific ID")
            MODELS_ID_TEMPLATES -> {
                run {
                    val models: ModelManager = col.models
                    val mid: NoteTypeId = getModelIdFromUri(uri, col)
                    val existingModel: Model = models.get(mid)
                        ?: throw IllegalArgumentException("model missing: $mid")
                    val name: String = values!!.getAsString(FlashCardsContract.CardTemplate.NAME)
                    val qfmt: String = values.getAsString(FlashCardsContract.CardTemplate.QUESTION_FORMAT)
                    val afmt: String = values.getAsString(FlashCardsContract.CardTemplate.ANSWER_FORMAT)
                    val bqfmt: String = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT)
                    val bafmt: String = values.getAsString(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT)
                    try {
                        val t: JSONObject = Models.newTemplate(name)
                        t.put("qfmt", qfmt)
                        t.put("afmt", afmt)
                        t.put("bqfmt", bqfmt)
                        t.put("bafmt", bafmt)
                        models.addTemplate(existingModel, t)
                        models.save(existingModel)
                        col.save()
                        return ContentUris.withAppendedId(uri, t.getInt("ord").toLong())
                    } catch (e: ConfirmModSchemaException) {
                        throw IllegalArgumentException("Unable to add template without user requesting/accepting full-sync", e)
                    } catch (e: JSONException) {
                        throw IllegalArgumentException("Unable to get ord from new template", e)
                    }
                }
            }
            MODELS_ID_TEMPLATES_ID -> throw IllegalArgumentException("Not possible to insert template with specific ORD")
            MODELS_ID_FIELDS -> {
                run {
                    val models: ModelManager = col.models
                    val mid: NoteTypeId = getModelIdFromUri(uri, col)
                    val existingModel: Model = models.get(mid)
                        ?: throw IllegalArgumentException("model missing: $mid")
                    val name: String = values!!.getAsString(FlashCardsContract.Model.FIELD_NAME)
                        ?: throw IllegalArgumentException("field name missing for model: $mid")
                    val field: JSONObject = models.newField(name)
                    try {
                        models.addField(existingModel, field)
                        col.save()
                        val flds: JSONArray = existingModel.getJSONArray("flds")
                        return ContentUris.withAppendedId(uri, (flds.length() - 1).toLong())
                    } catch (e: ConfirmModSchemaException) {
                        throw IllegalArgumentException("Unable to insert field: $name", e)
                    } catch (e: JSONException) {
                        throw IllegalArgumentException("Unable to get newly created field: $name", e)
                    }
                }
            }
            SCHEDULE -> throw IllegalArgumentException("Not possible to perform insert operation on schedule")
            DECKS -> {
                // Insert new deck with specified name
                val deckName = values!!.getAsString(FlashCardsContract.Deck.DECK_NAME)
                var did = col.decks.id_for_name(deckName)
                if (did != null) {
                    throw IllegalArgumentException("Deck name already exists: $deckName")
                }
                if (!Decks.isValidDeckName(deckName)) {
                    throw IllegalArgumentException("Invalid deck name '$deckName'")
                }
                try {
                    did = col.decks.id(deckName)
                } catch (filteredSubdeck: DeckRenameException) {
                    throw IllegalArgumentException(filteredSubdeck.message)
                }
                val deck: Deck = col.decks.get(did)
                @KotlinCleanup("remove the null check if deck is found to be not null in DeckManager.get(Long)")
                @Suppress("SENSELESS_COMPARISON")
                if (deck != null) {
                    try {
                        val deckDesc = values.getAsString(FlashCardsContract.Deck.DECK_DESC)
                        if (deckDesc != null) {
                            deck.put("desc", deckDesc)
                        }
                    } catch (e: JSONException) {
                        Timber.e(e, "Could not set a field of new deck %s", deckName)
                        return null
                    }
                }
                col.decks.flush()
                Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, did.toString())
            }
            DECK_SELECTED -> throw IllegalArgumentException("Selected deck can only be queried and updated")
            DECKS_ID -> throw IllegalArgumentException("Not possible to insert deck with specific ID")
            MEDIA ->
                // insert a media file
                // contentvalue should have data and preferredFileName values
                insertMediaFile(values, col)
            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
    }

    private fun insertMediaFile(values: ContentValues?, col: Collection): Uri? {
        // Insert media file using libanki.Media.addFile and return Uri for the inserted file.
        val fileUri = Uri.parse(values!!.getAsString(FlashCardsContract.AnkiMedia.FILE_URI))
        val preferredName = values.getAsString(FlashCardsContract.AnkiMedia.PREFERRED_NAME)
        return try {
            val cR = context!!.contentResolver
            val media = col.media
            // idea, open input stream and save to cache directory, then
            // pass this (hopefully temporary) file to the media.addFile function.
            val fileMimeType = MimeTypeMap.getSingleton().getExtensionFromMimeType(cR.getType(fileUri)) // return eg "jpeg"
            // should we be enforcing strict mimetypes? which types?
            val tempFile: File
            val externalCacheDir = context!!.externalCacheDir
            if (externalCacheDir == null) {
                Timber.e("createUI() unable to get external cache directory")
                return null
            }
            val tempMediaDir = File(externalCacheDir.absolutePath + "/temp-media")
            if (!tempMediaDir.exists() && !tempMediaDir.mkdir()) {
                Timber.e("temp-media dir did not exist and could not be created")
                return null
            }
            try {
                tempFile = File.createTempFile(
                    preferredName + "_", // the beginning of the filename.
                    ".$fileMimeType", // this is the extension, if null, '.tmp' is used, need to get the extension from MIME type?
                    tempMediaDir
                )
                tempFile.deleteOnExit()
            } catch (e: Exception) {
                Timber.w(e, "Could not create temporary media file. ")
                return null
            }
            internalizeUri(fileUri, tempFile, cR)
            val fname = media.addFile(tempFile)
            Timber.d("insert -> MEDIA: fname = %s", fname)
            val f = File(fname)
            Timber.d("insert -> MEDIA: f = %s", f)
            val uriFromF = Uri.fromFile(f)
            Timber.d("insert -> MEDIA: uriFromF = %s", uriFromF)
            Uri.fromFile(File(fname))
        } catch (e: IOException) {
            Timber.w(e, "insert failed from %s", fileUri)
            null
        } catch (e: EmptyMediaException) {
            Timber.w(e, "insert failed from %s", fileUri)
            null
        }
    }

    private fun addModelToCursor(modelId: NoteTypeId, models: ModelManager, rv: MatrixCursor, columns: Array<String>) {
        val jsonObject = models.get(modelId)
        val rb = rv.newRow()
        try {
            for (column in columns) {
                when (column) {
                    FlashCardsContract.Model._ID -> rb.add(modelId)
                    FlashCardsContract.Model.NAME -> rb.add(jsonObject!!.getString("name"))
                    FlashCardsContract.Model.FIELD_NAMES -> {
                        val flds = jsonObject!!.getJSONArray("flds")
                        val allFlds = arrayOfNulls<String>(flds.length())
                        var idx = 0
                        while (idx < flds.length()) {
                            allFlds[idx] = flds.getJSONObject(idx).optString("name", "")
                            idx++
                        }
                        @KotlinCleanup("remove requireNoNulls")
                        rb.add(Utils.joinFields(allFlds.requireNoNulls()))
                    }
                    FlashCardsContract.Model.NUM_CARDS -> rb.add(jsonObject!!.getJSONArray("tmpls").length())
                    FlashCardsContract.Model.CSS -> rb.add(jsonObject!!.getString("css"))
                    FlashCardsContract.Model.DECK_ID -> // #6378 - Anki Desktop changed schema temporarily to allow null
                        rb.add(jsonObject!!.optLong("did", Consts.DEFAULT_DECK_ID))
                    FlashCardsContract.Model.SORT_FIELD_INDEX -> rb.add(jsonObject!!.getLong("sortf"))
                    FlashCardsContract.Model.TYPE -> rb.add(jsonObject!!.getLong("type"))
                    FlashCardsContract.Model.LATEX_POST -> rb.add(jsonObject!!.getString("latexPost"))
                    FlashCardsContract.Model.LATEX_PRE -> rb.add(jsonObject!!.getString("latexPre"))
                    FlashCardsContract.Model.NOTE_COUNT -> rb.add(models.useCount(jsonObject!!))
                    else -> throw UnsupportedOperationException("Queue \"$column\" is unknown")
                }
            }
        } catch (e: JSONException) {
            Timber.e(e, "Error parsing JSONArray")
            throw IllegalArgumentException("Model $modelId is malformed", e)
        }
    }

    private fun addCardToCursor(currentCard: Card, rv: MatrixCursor, @Suppress("UNUSED_PARAMETER") col: Collection, columns: Array<String>) {
        val cardName: String = try {
            currentCard.template().getString("name")
        } catch (je: JSONException) {
            throw IllegalArgumentException("Card is using an invalid template", je)
        }
        val question = currentCard.q()
        val answer = currentCard.a()
        val rb = rv.newRow()
        for (column in columns) {
            when (column) {
                FlashCardsContract.Card.NOTE_ID -> rb.add(currentCard.nid)
                FlashCardsContract.Card.CARD_ORD -> rb.add(currentCard.ord)
                FlashCardsContract.Card.CARD_NAME -> rb.add(cardName)
                FlashCardsContract.Card.DECK_ID -> rb.add(currentCard.did)
                FlashCardsContract.Card.QUESTION -> rb.add(question)
                FlashCardsContract.Card.ANSWER -> rb.add(answer)
                FlashCardsContract.Card.QUESTION_SIMPLE -> rb.add(currentCard.qSimple())
                FlashCardsContract.Card.ANSWER_SIMPLE -> rb.add(currentCard.render_output(false).answer_text)
                FlashCardsContract.Card.ANSWER_PURE -> rb.add(currentCard.pureAnswer)
                else -> throw UnsupportedOperationException("Queue \"$column\" is unknown")
            }
        }
    }

    private fun addReviewInfoToCursor(currentCard: Card, nextReviewTimesJson: JSONArray, buttonCount: Int, rv: MatrixCursor, col: Collection, columns: Array<String>) {
        val rb = rv.newRow()
        for (column in columns) {
            when (column) {
                FlashCardsContract.Card.NOTE_ID -> rb.add(currentCard.nid)
                FlashCardsContract.ReviewInfo.CARD_ORD -> rb.add(currentCard.ord)
                FlashCardsContract.ReviewInfo.BUTTON_COUNT -> rb.add(buttonCount)
                FlashCardsContract.ReviewInfo.NEXT_REVIEW_TIMES -> rb.add(nextReviewTimesJson.toString())
                FlashCardsContract.ReviewInfo.MEDIA_FILES -> rb.add(JSONArray(col.media.filesInStr(currentCard.note().mid, currentCard.q() + currentCard.a())))
                else -> throw UnsupportedOperationException("Queue \"$column\" is unknown")
            }
        }
    }

    private fun answerCard(col: Collection, sched: AbstractSched, cardToAnswer: Card?, @BUTTON_TYPE ease: Int, timeTaken: Long) {
        try {
            val db = col.db
            db.database.beginTransaction()
            try {
                if (cardToAnswer != null) {
                    if (timeTaken != -1L) {
                        cardToAnswer.timerStarted = TimeManager.time.intTimeMS() - timeTaken
                    }
                    sched.answerCard(cardToAnswer, ease)
                }
                db.database.setTransactionSuccessful()
            } finally {
                db.safeEndInTransaction()
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "answerCard - RuntimeException on answering card")
            CrashReportService.sendExceptionReport(e, "doInBackgroundAnswerCard")
        }
    }

    private fun buryOrSuspendCard(col: Collection, sched: AbstractSched, card: Card?, bury: Boolean) {
        try {
            @KotlinCleanup("move lambda outside parentheses")
            col.db.executeInTransaction {
                if (card != null) {
                    if (bury) {
                        // bury
                        sched.buryCards(longArrayOf(card.id))
                    } else {
                        // suspend
                        sched.suspendCards(longArrayOf(card.id))
                    }
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "buryOrSuspendCard - RuntimeException on burying or suspending card")
            CrashReportService.sendExceptionReport(e, "doInBackgroundBurySuspendCard")
        }
    }

    private fun addTemplateToCursor(tmpl: JSONObject, model: Model?, id: Int, models: ModelManager, rv: MatrixCursor, columns: Array<String>) {
        try {
            val rb = rv.newRow()
            for (column in columns) {
                when (column) {
                    FlashCardsContract.CardTemplate._ID -> rb.add(id)
                    FlashCardsContract.CardTemplate.MODEL_ID -> rb.add(model!!.getLong("id"))
                    FlashCardsContract.CardTemplate.ORD -> rb.add(tmpl.getInt("ord"))
                    FlashCardsContract.CardTemplate.NAME -> rb.add(tmpl.getString("name"))
                    FlashCardsContract.CardTemplate.QUESTION_FORMAT -> rb.add(tmpl.getString("qfmt"))
                    FlashCardsContract.CardTemplate.ANSWER_FORMAT -> rb.add(tmpl.getString("afmt"))
                    FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT -> rb.add(tmpl.getString("bqfmt"))
                    FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT -> rb.add(tmpl.getString("bafmt"))
                    FlashCardsContract.CardTemplate.CARD_COUNT -> rb.add(models.tmplUseCount(model!!, tmpl.getInt("ord")))
                    else -> throw UnsupportedOperationException(
                        "Support for column \"$column\" is not implemented"
                    )
                }
            }
        } catch (e: JSONException) {
            Timber.e(e, "Error adding template to cursor")
            throw IllegalArgumentException("Template is malformed", e)
        }
    }

    private fun addDeckToCursor(id: Long, name: String, deckCounts: JSONArray, rv: MatrixCursor, col: Collection, columns: Array<String>) {
        val rb = rv.newRow()
        for (column in columns) {
            when (column) {
                FlashCardsContract.Deck.DECK_NAME -> rb.add(name)
                FlashCardsContract.Deck.DECK_ID -> rb.add(id)
                FlashCardsContract.Deck.DECK_COUNTS -> rb.add(deckCounts)
                FlashCardsContract.Deck.OPTIONS -> {
                    val config = col.decks.confForDid(id).toString()
                    rb.add(config)
                }
                FlashCardsContract.Deck.DECK_DYN -> rb.add(col.decks.isDyn(id))
                FlashCardsContract.Deck.DECK_DESC -> {
                    val desc = col.decks.getActualDescription()
                    rb.add(desc)
                }
            }
        }
    }

    private fun selectDeckWithCheck(col: Collection, did: DeckId): Boolean {
        return if (col.decks.get(did, false) != null) {
            col.decks.select(did)
            true
        } else {
            Timber.e(
                "Requested deck with id %d was not found in deck list. Either the deckID provided was wrong" +
                    "or the deck has been deleted in the meantime.",
                did
            )
            false
        }
    }

    private fun getCardFromUri(uri: Uri, col: Collection): Card {
        val noteId = uri.pathSegments[1].toLong()
        val ord = uri.pathSegments[3].toInt()
        return getCard(noteId, ord, col)
    }

    private fun getCard(noteId: NoteId, ord: Int, col: Collection): Card {
        val currentNote = col.getNote(noteId)
        var currentCard: Card? = null
        for (card in currentNote.cards()) {
            if (card.ord == ord) {
                currentCard = card
            }
        }
        if (currentCard == null) {
            throw IllegalArgumentException("Card with ord $ord does not exist for note $noteId")
        }
        return currentCard
    }

    private fun getNoteFromUri(uri: Uri, col: Collection): Note {
        val noteId = uri.pathSegments[1].toLong()
        return col.getNote(noteId)
    }

    private fun getModelIdFromUri(uri: Uri, col: Collection): Long {
        val modelIdSegment = uri.pathSegments[1]
        val id: Long = if (modelIdSegment == FlashCardsContract.Model.CURRENT_MODEL_ID) {
            col.models.current()!!.optLong("id", -1)
        } else {
            try {
                uri.pathSegments[1].toLong()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Model ID must be either numeric or the String CURRENT_MODEL_ID", e)
            }
        }
        return id
    }

    @Throws(JSONException::class)
    private fun getTemplateFromUri(uri: Uri, col: Collection): JSONObject {
        val model: JSONObject? = col.models.get(getModelIdFromUri(uri, col))
        val ord = uri.lastPathSegment!!.toInt()
        return model!!.getJSONArray("tmpls").getJSONObject(ord)
    }

    private fun throwSecurityException(methodName: String, uri: Uri) {
        val msg = "Permission not granted for: ${getLogMessage(methodName, uri)}"
        Timber.e("%s", msg)
        throw SecurityException(msg)
    }

    private fun getLogMessage(methodName: String, uri: Uri?): String {
        val format = "%s.%s %s (%s)"
        val path = uri?.path
        return String.format(format, javaClass.simpleName, methodName, path, callingPackage)
    }

    private fun hasReadWritePermission(): Boolean {
        return if (BuildConfig.DEBUG) { // Allow self-calling of the provider only in debug builds (e.g. for unit tests)
            context!!.checkCallingOrSelfPermission(FlashCardsContract.READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else {
            context!!.checkCallingPermission(FlashCardsContract.READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Returns true if the calling package is known to be "rogue" and should be blocked.
     * Calling package might be rogue if it has not declared #READ_WRITE_PERMISSION in its manifest */
    private fun knownRogueClient(): Boolean =
        !context!!.arePermissionsDefinedInManifest(callingPackage!!, FlashCardsContract.READ_WRITE_PERMISSION)
}
