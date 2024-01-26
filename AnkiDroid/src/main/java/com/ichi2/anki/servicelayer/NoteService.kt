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
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.FieldEditText
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.*
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
     * @param model the model in JSOBObject format
     * @return a new note instance
     */
    fun createEmptyNote(model: JSONObject): MultimediaEditableNote? {
        try {
            val fieldsArray = model.getJSONArray("flds")
            val numOfFields = fieldsArray.length()
            if (numOfFields > 0) {
                val note = MultimediaEditableNote()
                note.setNumFields(numOfFields)
                for (i in 0 until numOfFields) {
                    val fieldObject = fieldsArray.getJSONObject(i)
                    val uiTextField = TextField()
                    uiTextField.name = fieldObject.getString("name")
                    uiTextField.text = fieldObject.getString("name")
                    note.setField(i, uiTextField)
                }
                note.modelId = model.getLong("id")
                return note
            }
        } catch (e: JSONException) {
            // TODO Auto-generated catch block
            Timber.w(e)
        }
        return null
    }

    fun updateMultimediaNoteFromFields(col: com.ichi2.libanki.Collection, fields: Array<String>, modelId: NoteTypeId, mmNote: MultimediaEditableNote) {
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
    fun importMediaToDirectory(col: com.ichi2.libanki.Collection, field: IField?) {
        var tmpMediaPath: String? = null
        when (field!!.type) {
            EFieldType.AUDIO_RECORDING, EFieldType.MEDIA_CLIP -> tmpMediaPath = field.audioPath
            EFieldType.IMAGE -> tmpMediaPath = field.imagePath
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
                        EFieldType.AUDIO_RECORDING, EFieldType.MEDIA_CLIP -> field.audioPath = outFile.absolutePath
                        EFieldType.IMAGE -> field.imagePath = outFile.absolutePath
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
            note.delTag("marked")
        } else {
            note.addTag("marked")
        }

        undoableOp(handler) {
            updateNote(note)
        }
    }

    fun isMarked(note: Note): Boolean {
        return note.hasTag("marked")
    }

    //  TODO: should make a direct SQL query to do this
    /**
     * returns the average ease of all the non-new cards in the note,
     * or if all the cards in the note are new, returns null
     */
    fun avgEase(note: Note): Int? {
        val nonNewCards = note.cards().filter { it.type != Consts.CARD_TYPE_NEW }

        return nonNewCards.average { it.factor }?.let { it / 10 }?.toInt()
    }

    //  TODO: should make a direct SQL query to do this
    fun totalLapses(note: Note) = note.cards().sumOf { it.lapses }

    fun totalReviews(note: Note) = note.cards().sumOf { it.reps }

    /**
     * Returns the average interval of all the non-new and non-learning cards in the note,
     * or if all the cards in the note are new or learning, returns null
     */
    fun avgInterval(note: Note): Int? {
        val nonNewOrLearningCards = note.cards().filter { it.type != Consts.CARD_TYPE_NEW && it.type != Consts.CARD_TYPE_LRN }

        return nonNewOrLearningCards.average { it.ivl }?.toInt()
    }

    interface NoteField {
        val ord: Int

        // ideally shouldn't be nullable
        val fieldText: String?
    }
}

const val MARKED_TAG = "marked"

fun Card.totalLapsesOfNote(col: Collection) = NoteService.totalLapses(note(col))

fun Card.totalReviewsForNote(col: Collection) = NoteService.totalReviews(note(col))

fun Card.avgIntervalOfNote(col: Collection) = NoteService.avgInterval(note(col))
