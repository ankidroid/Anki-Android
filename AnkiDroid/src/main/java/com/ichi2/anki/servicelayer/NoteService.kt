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
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.FieldEditText
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.*
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.libanki.Note
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.utils.JSONException
import com.ichi2.utils.JSONObject
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
    @JvmStatic
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

    @JvmStatic
    fun updateMultimediaNoteFromFields(col: com.ichi2.libanki.Collection?, fields: Array<String>, modelId: Long, mmNote: MultimediaEditableNote) {
        for (i in fields.indices) {
            val value = fields[i]
            var field: IField?
            field = if (value.startsWith("<img")) {
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
     * Updates the JsonNote field values from MultimediaEditableNote When both notes are using the same Model, it updaes
     * the destination field values with source values. If models are different it throws an Exception
     *
     * @param noteSrc
     * @param editorNoteDst
     */
    @JvmStatic
    fun updateJsonNoteFromMultimediaNote(noteSrc: IMultimediaEditableNote?, editorNoteDst: Note) {
        if (noteSrc is MultimediaEditableNote) {
            val mmNote = noteSrc
            if (mmNote.modelId != editorNoteDst.mid) {
                throw RuntimeException("Source and Destination Note ID do not match.")
            }
            val totalFields: Int = mmNote.numberOfFields
            for (i in 0 until totalFields) {
                editorNoteDst.values()[i] = mmNote.getField(i)!!.formattedValue
            }
        }
    }

    /**
     * Saves the multimedia associated with this card to proper path inside anki folder. For each field associated with
     * the note it checks for the following condition a. The field content should have changed b. The field content does
     * not already point to a media inside anki media path If both condition satisfies then it copies the file inside
     * the media path and deletes the file referenced by the note
     *
     * @param noteNew
     */
    @JvmStatic
    fun saveMedia(col: com.ichi2.libanki.Collection, noteNew: MultimediaEditableNote) {
        // if (noteNew.getModelId() == noteOld.getModelId())
        // {
        // int fieldCount = noteNew.getNumberOfFields();
        // for (int i = 0; i < fieldCount; i++)
        // {
        // IField newField = noteNew.getField(i);
        // IField oldField = noteOld.getField(i);
        // if
        // (newField.getFormattedValue().equals(oldField.getFormattedValue()))
        // {
        // continue;
        // }
        // importMediaToDirectory(newField);
        // }
        // }
        // else
        // {
        val fieldCount: Int = noteNew.numberOfFields
        for (i in 0 until fieldCount) {
            val newField = noteNew.getField(i)
            importMediaToDirectory(col, newField)
        }
        // }
    }

    /**
     * Considering the field is new, if it has media handle it
     *
     * @param field
     */
    @JvmStatic
    fun importMediaToDirectory(col: com.ichi2.libanki.Collection, field: IField?) {
        var tmpMediaPath: String? = null
        when (field!!.type) {
            EFieldType.AUDIO_RECORDING, EFieldType.MEDIA_CLIP -> tmpMediaPath = field.audioPath
            EFieldType.IMAGE -> tmpMediaPath = field.imagePath
            EFieldType.TEXT -> {
            }
            else -> {
            }
        }
        if (tmpMediaPath != null) {
            try {
                val inFile = File(tmpMediaPath)
                if (inFile.exists() && inFile.length() > 0) {
                    val fname = col.media.addFile(inFile)
                    val outFile = File(col.media.dir(), fname)
                    // Update imagePath in case the file name wasn't unique
                    field.imagePath = fname
                    if (field.hasTemporaryMedia() && outFile.absolutePath != tmpMediaPath) {
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
                AnkiDroidApp.sendExceptionReport(mediaException, "noteService::importMediaToDirectory")
            }
        }
    }

    /**
     * @param replaceNewlines Converts [FieldEditText.NEW_LINE] to HTML linebreaks
     */
    @JvmStatic
    @VisibleForTesting
    @CheckResult
    fun getFieldsAsBundleForPreview(editFields: Collection<NoteField?>?, replaceNewlines: Boolean): Bundle {
        val fields = Bundle()
        // Save the content of all the note fields. We use the field's ord as the key to
        // easily map the fields correctly later.
        if (editFields == null) {
            return fields
        }
        for (e in editFields) {
            if (e == null || e.fieldText == null) {
                continue
            }
            val fieldValue = convertToHtmlNewline(e.fieldText!!, replaceNewlines)
            fields.putString(Integer.toString(e.ord), fieldValue)
        }
        return fields
    }

    @JvmStatic
    fun convertToHtmlNewline(fieldData: String, replaceNewlines: Boolean): String {
        return if (!replaceNewlines) {
            fieldData
        } else fieldData.replace(FieldEditText.NEW_LINE, "<br>")
    }

    @JvmStatic
    fun toggleMark(note: Note) {
        if (isMarked(note)) {
            note.delTag("marked")
        } else {
            note.addTag("marked")
        }
        note.flush()
    }

    @JvmStatic
    fun isMarked(note: Note): Boolean {
        return note.hasTag("marked")
    }

    interface NoteField {
        val ord: Int

        // ideally shouldn't be nullable
        val fieldText: String?
    }
}
