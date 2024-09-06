/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.servicelayer

import android.os.Bundle
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.FieldEditText
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.EFieldType
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.NoteTypeId
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.libanki.undoableOp
import com.ichi2.utils.CollectionUtils.average
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException

object NoteService {
    /**
     * Creates an empty Note from given Model
     *
     * @param model the model in JSONObject format
     * @return a new MultimediaEditableNote instance
     */
    fun createEmptyNote(model: JSONObject): MultimediaEditableNote {
        val note = MultimediaEditableNote()
        try {
            val fieldsArray = model.getJSONArray("flds")
            val numOfFields = fieldsArray.length()
            note.setNumFields(numOfFields)

            for (i in 0 until numOfFields) {
                val fieldObject = fieldsArray.getJSONObject(i)
                val uiTextField = TextField().apply {
                    name = fieldObject.getString("name")
                    text = fieldObject.getString("name")
                }
                note.setField(i, uiTextField)
            }
            note.modelId = model.getLong("id")
        } catch (e: JSONException) {
            Timber.w(e, "Error parsing model: %s", model)
            // Return note with default/empty fields
        }
        return note
    }

    fun updateMultimediaNoteFromFields(
        col: Collection,
        fields: Array<String>,
        modelId: NoteTypeId,
        mmNote: MultimediaEditableNote
    ) {
        for (i in fields.indices) {
            val value = fields[i]
            val field: IField = if (value.startsWith("<img")) {
                ImageField()
            } else if (value.startsWith("[sound:") && value.contains("rec")) {
                AudioRecordingField()
            } else if (value.startsWith("[sound:")) {
                MediaClipField()
            } else {
                TextField()
            }
            field.setFormattedString(col, value)
            mmNote.setField(i, field)
        }
        mmNote.modelId = modelId
        mmNote.freezeInitialFieldValues()
        // TODO: set current id of the note as well
    }

    /**
     * Updates the JsonNote field values from MultimediaEditableNote When both notes are using the same Model, it updates
     * the destination field values with source values. If models are different it throws an Exception
     *
     * @param noteSrc
     * @param editorNoteDst
     */
    fun updateJsonNoteFromMultimediaNote(noteSrc: IMultimediaEditableNote?, editorNoteDst: Note) {
        if (noteSrc is MultimediaEditableNote) {
            if (noteSrc.modelId != editorNoteDst.mid) {
                throw RuntimeException("Source and Destination Note ID do not match.")
            }
            val totalFields: Int = noteSrc.numberOfFields
            for (i in 0 until totalFields) {
                editorNoteDst.values()[i] = noteSrc.getField(i)!!.formattedValue!!
            }
        }
    }

    /**
     * Considering the field is new, if it has media handle it
     *
     * @param field
     */
    fun importMediaToDirectory(col: Collection, field: IField?) {
        var tmpMediaPath: String? = null
        when (field!!.type) {
            EFieldType.AUDIO_RECORDING, EFieldType.MEDIA_CLIP, EFieldType.IMAGE -> tmpMediaPath = field.mediaPath
            EFieldType.TEXT -> {
            }
        }
        if (tmpMediaPath != null) {
            try {
                val inFile = File(tmpMediaPath)
                if (inFile.exists() && inFile.length() > 0) {
                    val fname = col.media.addFile(inFile)
                    val outFile = File(col.media.dir, fname)
                    Timber.v("""File "%s" should be copied to "%s""", fname, outFile)
                    if (field.hasTemporaryMedia && outFile.absolutePath != tmpMediaPath) {
                        // Delete original
                        inFile.delete()
                    }
                    when (field.type) {
                        EFieldType.AUDIO_RECORDING, EFieldType.MEDIA_CLIP, EFieldType.IMAGE -> field.mediaPath = outFile.absolutePath
                        else -> {
                        }
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (mediaException: EmptyMediaException) {
                // This shouldn't happen, but we're fine to ignore it if it does.
                Timber.w(mediaException)
                CrashReportService.sendExceptionReport(mediaException, "noteService::importMediaToDirectory")
            }
        }
    }

    /**
     * @param replaceNewlines Converts [FieldEditText.NEW_LINE] to HTML linebreaks
     */
    @VisibleForTesting
    @CheckResult
    fun getFieldsAsBundleForPreview(editFields: List<NoteField?>?, replaceNewlines: Boolean): Bundle {
        val fields = Bundle()
        // Save the content of all the note fields. We use the field's ord as the key to
        // easily map the fields correctly later.
        if (editFields == null) {
            return fields
        }
        for (e in editFields) {
            if (e?.fieldText == null) {
                continue
            }
            val fieldValue = convertToHtmlNewline(e.fieldText!!, replaceNewlines)
            fields.putString(e.ord.toString(), fieldValue)
        }
        return fields
    }

    fun convertToHtmlNewline(fieldData: String, replaceNewlines: Boolean): String {
        return if (!replaceNewlines) {
            fieldData
        } else {
            fieldData.replace(FieldEditText.NEW_LINE, "<br>")
        }
    }

    suspend fun toggleMark(note: Note, handler: Any? = null) {
        if (isMarked(note)) {
            note.removeTag("marked")
        } else {
            note.addTag("marked")
        }

        undoableOp(handler) {
            updateNote(note)
        }
    }

    suspend fun isMarked(note: Note): Boolean = withCol { isMarked(this, note) }

    fun isMarked(col: Collection, note: Note): Boolean {
        return note.hasTag(col, tag = "marked")
    }

    //  TODO: should make a direct SQL query to do this
    /**
     * returns the average ease of all the non-new cards in the note,
     * or if all the cards in the note are new, returns null
     */
    fun avgEase(col: Collection, note: Note): Int? {
        val nonNewCards = note.cards(col).filter { it.type != Consts.CARD_TYPE_NEW }

        return nonNewCards.average { it.factor }?.let { it / 10 }?.toInt()
    }

    //  TODO: should make a direct SQL query to do this
    fun totalLapses(col: Collection, note: Note) = note.cards(col).sumOf { it.lapses }

    fun totalReviews(col: Collection, note: Note) = note.cards(col).sumOf { it.reps }

    /**
     * Returns the average interval of all the non-new and non-learning cards in the note,
     * or if all the cards in the note are new or learning, returns null
     */
    fun avgInterval(col: Collection, note: Note): Int? {
        val nonNewOrLearningCards = note.cards(col).filter { it.type != Consts.CARD_TYPE_NEW && it.type != Consts.CARD_TYPE_LRN }

        return nonNewOrLearningCards.average { it.ivl }?.toInt()
    }

    interface NoteField {
        val ord: Int

        // ideally shouldn't be nullable
        val fieldText: String?
    }
}

const val MARKED_TAG = "marked"

fun Card.totalLapsesOfNote(col: Collection) = NoteService.totalLapses(col, note(col))

fun Card.totalReviewsForNote(col: Collection) = NoteService.totalReviews(col, note(col))

fun Card.avgIntervalOfNote(col: Collection) = NoteService.avgInterval(col, note(col))

suspend fun isBuryNoteAvailable(card: Card): Boolean {
    return withCol {
        db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue >=  " + Consts.QUEUE_TYPE_NEW + " limit 1",
            card.nid,
            card.id
        ) == 1
    }
}

suspend fun isSuspendNoteAvailable(card: Card): Boolean {
    return withCol {
        db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue != " + Consts.QUEUE_TYPE_SUSPENDED + " limit 1",
            card.nid,
            card.id
        ) == 1
    }
}
