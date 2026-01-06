/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A

 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.jsapi

import android.speech.tts.TextToSpeech
import androidx.annotation.VisibleForTesting
import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.JavaScriptTTS
import com.ichi2.anki.common.utils.ext.getDoubleOrNull
import com.ichi2.anki.common.utils.ext.getIntOrNull
import com.ichi2.anki.common.utils.ext.getLongOrNull
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.redoAvailable
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.setUserFlagForCards
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object JsApi {
    @VisibleForTesting
    const val CURRENT_VERSION = "1.0.0"
    private const val SUCCESS_KEY = "success"
    private const val VALUE_KEY = "value"
    private const val ERROR_CODE_KEY = "code"
    private const val ERROR_MESSAGE_KEY = "message"
    const val REQUEST_PREFIX = "/jsapi/"

    private val tts by lazy { JavaScriptTTS() }

    fun parseRequest(byteArray: ByteArray): JSONObject? {
        val requestBody = JSONObject(byteArray.decodeToString())
        validateContract(requestBody)
        return requestBody.optJSONObject("data")
    }

    /**
     * @throws InvalidContractException if
     * * developer contact is empty
     * * request version is invalid
     * * request version is higher than the API version
     * * request major version is lower than the API version
     */
    private fun validateContract(json: JSONObject) {
        // Developer contact
        val developer = json.getStringOrNull("developer")
        if (developer.isNullOrBlank()) {
            throw InvalidContractException.ContactError()
        }
        // Version
        val versionString = json.getStringOrNull("version") ?: throw InvalidContractException.VersionError("", developer)

        val currentVersion = Version.parse(CURRENT_VERSION)
        val requestVersion =
            try {
                Version.parse(versionString)
            } catch (_: ParseException) {
                throw InvalidContractException.VersionError(versionString, developer)
            }

        when {
            requestVersion.isHigherThan(currentVersion) -> throw InvalidContractException.VersionError(versionString, developer)
            requestVersion.isSameMajorVersionAs(currentVersion) -> return
            requestVersion.isLowerThan(
                currentVersion,
            ) -> throw InvalidContractException.OutdatedVersion(CURRENT_VERSION, versionString, developer)
            else -> throw InvalidContractException.VersionError(versionString, developer)
        }
    }

    fun getEndpoint(uri: String): Endpoint? {
        val path = uri.removePrefix(REQUEST_PREFIX)
        val parts = path.split('/', limit = 2).takeIf { it.size == 2 }
        return parts?.let { (base, value) ->
            Endpoint.from(base, value)
        }
    }

    suspend fun handleEndpointRequest(
        endpoint: Endpoint,
        data: JSONObject?,
        topCard: Card,
    ): ByteArray =
        when (endpoint) {
            is Endpoint.Card -> handleCardMethods(endpoint, data, topCard)
            is Endpoint.Collection -> handleCollectionMethods(endpoint, data)
            is Endpoint.Deck -> handleDeckMethods(endpoint, data, topCard)
            is Endpoint.Note -> handleNoteMethods(endpoint, data, topCard)
            is Endpoint.NoteType -> handleNoteTypeMethods(endpoint, data, topCard)
            is Endpoint.Tts -> handleTtsEndpoints(endpoint, data)
            is Endpoint.Android, is Endpoint.StudyScreen -> fail(JsApiError.UnsupportedMethod, "Method not supported")
        }

    private suspend fun handleCardMethods(
        endpoint: Endpoint.Card,
        data: JSONObject?,
        topCard: Card,
    ): ByteArray {
        val cardId = data?.getLongOrNull("id")
        val card =
            if (cardId != null) {
                withCol { Card(this, cardId) }
            } else {
                topCard
            }
        return when (endpoint) {
            Endpoint.Card.GET_ID -> success(card.id)
            Endpoint.Card.GET_NID -> success(card.nid)
            Endpoint.Card.GET_FLAG -> success(card.flag.code)
            Endpoint.Card.GET_REPS -> success(card.reps)
            Endpoint.Card.GET_INTERVAL -> success(card.ivl)
            Endpoint.Card.GET_FACTOR -> success(card.factor)
            Endpoint.Card.GET_MOD -> success(card.mod)
            Endpoint.Card.GET_TYPE -> success(card.type.code)
            Endpoint.Card.GET_DID -> success(card.did)
            Endpoint.Card.GET_LEFT -> success(card.left)
            Endpoint.Card.GET_O_DID -> success(card.oDid)
            Endpoint.Card.GET_O_DUE -> success(card.oDue)
            Endpoint.Card.GET_QUEUE -> success(card.queue.code)
            Endpoint.Card.GET_LAPSES -> success(card.lapses)
            Endpoint.Card.GET_DUE -> success(card.due)
            Endpoint.Card.GET_QUESTION -> {
                val question = withCol { card.question(this) }
                success(question)
            }
            Endpoint.Card.GET_ANSWER -> {
                val answer = withCol { card.answer(this) }
                success(answer)
            }
            Endpoint.Card.BURY -> {
                val count = undoableOp { sched.buryCards(cids = listOf(card.id)) }.count
                success(count)
            }
            Endpoint.Card.IS_MARKED -> {
                val isMarked = withCol { card.note(this).hasTag(this, MARKED_TAG) }
                success(isMarked)
            }
            Endpoint.Card.SUSPEND -> {
                val count = undoableOp { sched.suspendCards(ids = listOf(card.id)) }.count
                success(count)
            }
            Endpoint.Card.UNBURY -> {
                undoableOp { sched.unburyCards(listOf(card.id)) }
                success()
            }
            Endpoint.Card.UNSUSPEND -> {
                undoableOp { sched.unsuspendCards(listOf(card.id)) }
                success()
            }
            Endpoint.Card.RESET_PROGRESS -> {
                undoableOp {
                    sched.forgetCards(listOf(card.id), restorePosition = false, resetCounts = false)
                }
                success()
            }
            Endpoint.Card.TOGGLE_FLAG -> {
                val requestFlag = data?.getIntOrNull("flag") ?: return fail(JsApiError.InvalidInput, "Missing flag")
                if (requestFlag !in 0..7) return fail(JsApiError.InvalidInput, "Invalid flag code")

                val newFlag = if (requestFlag == card.userFlag()) Flag.NONE else Flag.fromCode(requestFlag)
                undoableOp { setUserFlagForCards(listOf(card.id), newFlag) }
                success()
            }
            Endpoint.Card.GET_REVIEW_LOGS -> {
                val reviewLogs =
                    withCol { getReviewLogs(card.id) }.map { log ->
                        JSONObject().apply {
                            put("time", log.time)
                            put("reviewKind", log.reviewKindValue)
                            put("buttonChosen", log.buttonChosen)
                            put("interval", log.interval)
                            put("ease", log.ease)
                            put("takenSecs", log.takenSecs)
                            val memoryState =
                                if (log.hasMemoryState()) {
                                    JSONObject().apply {
                                        put("stability", log.memoryState.stability)
                                        put("difficulty", log.memoryState.difficulty)
                                    }
                                } else {
                                    null
                                }
                            put("memoryState", memoryState)
                        }
                    }
                success(reviewLogs)
            }
        }
    }

    private suspend fun handleCollectionMethods(
        endpoint: Endpoint.Collection,
        data: JSONObject?,
    ): ByteArray {
        return when (endpoint) {
            Endpoint.Collection.UNDO -> {
                val isUndoAvailable = withCol { undoAvailable() }
                if (!isUndoAvailable) return fail(JsApiError.FeatureNotAvailable, "Undo is not available")
                val changes = undoableOp { undo() }
                success(changes.operation)
            }
            Endpoint.Collection.REDO -> {
                val isRedoAvailable = withCol { redoAvailable() }
                if (!isRedoAvailable) return fail(JsApiError.FeatureNotAvailable, "Redo is not available")
                val changes = undoableOp { redo() }
                success(changes.operation)
            }
            Endpoint.Collection.IS_UNDO_AVAILABLE -> {
                val isUndoAvailable = withCol { undoAvailable() }
                success(isUndoAvailable)
            }
            Endpoint.Collection.IS_REDO_AVAILABLE -> {
                val isRedoAvailable = withCol { redoAvailable() }
                success(isRedoAvailable)
            }
            Endpoint.Collection.FIND_CARDS -> {
                val search = data?.getStringOrNull("search") ?: return fail(JsApiError.InvalidInput, "No search query found")
                val ids = withCol { findCards(search) }
                success(ids)
            }
            Endpoint.Collection.FIND_NOTES -> {
                val search = data?.getStringOrNull("search") ?: return fail(JsApiError.InvalidInput, "No search query found")
                val ids = withCol { findNotes(search) }
                success(ids)
            }
        }
    }

    private suspend fun handleNoteMethods(
        endpoint: Endpoint.Note,
        data: JSONObject?,
        topCard: Card,
    ): ByteArray {
        val noteId = data?.getLongOrNull("id")
        val note =
            if (noteId != null) {
                withCol { Note(this, noteId) }
            } else {
                withCol { topCard.note(this) }
            }
        return when (endpoint) {
            Endpoint.Note.GET_ID -> success(note.id)
            Endpoint.Note.GET_NOTE_TYPE_ID -> success(note.noteTypeId)
            Endpoint.Note.GET_CARD_IDS -> success(withCol { note.cardIds(this) })
            Endpoint.Note.BURY -> {
                val count = undoableOp { sched.buryNotes(listOf(note.id)) }.count
                success(count)
            }
            Endpoint.Note.SUSPEND -> {
                val count = undoableOp { sched.suspendNotes(listOf(note.id)) }.count
                success(count)
            }
            Endpoint.Note.GET_TAGS -> {
                val tags = withCol { note.stringTags(this) }
                success(tags)
            }
            Endpoint.Note.SET_TAGS -> {
                val tags = data?.optString("tags") ?: return fail(JsApiError.InvalidInput, "Missing tags")
                undoableOp {
                    note.setTagsFromStr(this, tags)
                    updateNote(note)
                }
                success()
            }
            Endpoint.Note.TOGGLE_MARK -> {
                NoteService.toggleMark(note)
                success()
            }
        }
    }

    private suspend fun handleNoteTypeMethods(
        endpoint: Endpoint.NoteType,
        data: JSONObject?,
        topCard: Card,
    ): ByteArray {
        val noteTypeId = data?.getLongOrNull("id")
        val noteType =
            if (noteTypeId != null) {
                withCol { notetypes }.get(noteTypeId)
                    ?: return fail(JsApiError.InvalidInput, "Found no note type with the id '$noteTypeId'")
            } else {
                withCol { topCard.noteType(this) }
            }
        return when (endpoint) {
            Endpoint.NoteType.GET_ID -> success(noteType.id)
            Endpoint.NoteType.GET_NAME -> success(noteType.name)
            Endpoint.NoteType.IS_IMAGE_OCCLUSION -> success(noteType.isImageOcclusion)
            Endpoint.NoteType.IS_CLOZE -> success(noteType.isCloze)
            Endpoint.NoteType.GET_FIELD_NAMES -> success(noteType.fieldsNames)
        }
    }

    private suspend fun handleDeckMethods(
        endpoint: Endpoint.Deck,
        data: JSONObject?,
        topCard: Card,
    ): ByteArray {
        val deckId = data?.getLongOrNull("id") ?: topCard.did
        val deck = withCol { decks.get(deckId) } ?: return fail(JsApiError.InvalidInput, "Found no deck with the id '$deckId'")
        return when (endpoint) {
            Endpoint.Deck.GET_ID -> success(deck.id)
            Endpoint.Deck.GET_NAME -> success(deck.name)
            Endpoint.Deck.IS_FILTERED -> success(deck.isFiltered)
        }
    }

    private fun handleTtsEndpoints(
        endpoint: Endpoint.Tts,
        data: JSONObject?,
    ): ByteArray {
        /** Helps with TTS methods that return SUCCESS or ERROR */
        fun ttsErrorOrSuccess(
            @JavaScriptTTS.ErrorOrSuccess result: Int,
        ) = when (result) {
            TextToSpeech.SUCCESS -> success()
            TextToSpeech.ERROR -> fail(JsApiError.TtsError, "TTS engine error")
            else -> fail(JsApiError.TtsError, "Unknown TTS error")
        }
        return when (endpoint) {
            Endpoint.Tts.SPEAK -> {
                val text = data?.optString("text") ?: return fail(JsApiError.InvalidInput, "Missing text")
                val queueMode = data.getIntOrNull("queueMode") ?: return fail(JsApiError.InvalidInput, "Missing queueMode")
                if (queueMode != TextToSpeech.QUEUE_FLUSH &&
                    queueMode != TextToSpeech.QUEUE_ADD
                ) {
                    return fail(JsApiError.InvalidInput, "Invalid queueMode")
                }
                ttsErrorOrSuccess(tts.speak(text, queueMode))
            }
            Endpoint.Tts.SET_LANGUAGE -> {
                val locale = data?.optString("locale") ?: return fail(JsApiError.InvalidInput, "Missing locale")
                success(tts.setLanguage(locale))
            }
            Endpoint.Tts.SET_PITCH -> {
                val pitch = data?.getDoubleOrNull("pitch") ?: return fail(JsApiError.InvalidInput, "Missing pitch")
                ttsErrorOrSuccess(tts.setPitch(pitch.toFloat()))
            }
            Endpoint.Tts.SET_SPEECH_RATE -> {
                val speechRate = data?.getDoubleOrNull("speechRate") ?: return fail(JsApiError.InvalidInput, "Missing speechRate")
                ttsErrorOrSuccess(tts.setSpeechRate(speechRate.toFloat()))
            }
            Endpoint.Tts.IS_SPEAKING -> {
                success(tts.isSpeaking)
            }
            Endpoint.Tts.STOP -> {
                ttsErrorOrSuccess(tts.stop())
            }
        }
    }

    fun success() = successResult(null)

    fun success(string: String) = successResult(string)

    fun success(boolean: Boolean) = successResult(boolean)

    fun success(number: Int) = successResult(number)

    fun success(number: Long) = successResult(number)

    fun success(collection: Collection<*>) = successResult(JSONArray(collection))

    private fun successResult(value: Any?): ByteArray {
        val jsonObject =
            JSONObject()
                .apply {
                    put(SUCCESS_KEY, true)
                    put(VALUE_KEY, value)
                }
        return jsonObject.toString().toByteArray()
    }

    fun fail(
        error: JsApiError,
        message: String,
    ): ByteArray {
        Timber.i("JsApi fail %s: %s", error.code, message)
        return JSONObject()
            .apply {
                put(SUCCESS_KEY, false)
                put(ERROR_CODE_KEY, error.code)
                put(ERROR_MESSAGE_KEY, message)
            }.toString()
            .toByteArray()
    }
}
