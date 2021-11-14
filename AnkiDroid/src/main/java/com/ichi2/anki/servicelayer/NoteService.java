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

package com.ichi2.anki.servicelayer;

import android.os.Bundle;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.FieldEditText;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.AudioClipField;
import com.ichi2.anki.multimediacard.fields.AudioRecordingField;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;

import com.ichi2.libanki.exception.EmptyMediaException;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.File;
import java.io.IOException;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

public class NoteService {
    /**
     * Creates an empty Note from given Model
     * 
     * @param model the model in JSOBObject format
     * @return a new note instance
     */
    public static MultimediaEditableNote createEmptyNote(JSONObject model) {
        try {
            JSONArray fieldsArray = model.getJSONArray("flds");
            int numOfFields = fieldsArray.length();
            if (numOfFields > 0) {
                MultimediaEditableNote note = new MultimediaEditableNote();
                note.setNumFields(numOfFields);

                for (int i = 0; i < numOfFields; i++) {
                    JSONObject fieldObject = fieldsArray.getJSONObject(i);
                    TextField uiTextField = new TextField();
                    uiTextField.setName(fieldObject.getString("name"));
                    uiTextField.setText(fieldObject.getString("name"));
                    note.setField(i, uiTextField);
                }
                note.setModelId(model.getLong("id"));
                return note;
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            Timber.w(e);
        }
        return null;
    }


    public static void updateMultimediaNoteFromFields(Collection col, String[] fields, long modelId, MultimediaEditableNote mmNote) {
        for (int i = 0; i < fields.length; i++) {
            String value = fields[i];
            IField field = null;
            if (value.startsWith("<img")) {
                field = new ImageField();
            } else if (value.startsWith("[sound:") && value.contains("rec")) {
                field = new AudioRecordingField();
            } else if (value.startsWith("[sound:")) {
                field = new AudioClipField();
            } else {
                field = new TextField();
            }
            field.setFormattedString(col, value);
            mmNote.setField(i, field);
        }
        mmNote.setModelId(modelId);
        mmNote.freezeInitialFieldValues();
        // TODO: set current id of the note as well
    }


    /**
     * Updates the JsonNote field values from MultimediaEditableNote When both notes are using the same Model, it updaes
     * the destination field values with source values. If models are different it throws an Exception
     * 
     * @param noteSrc
     * @param editorNoteDst
     */
    public static void updateJsonNoteFromMultimediaNote(final IMultimediaEditableNote noteSrc, final Note editorNoteDst) {
        if (noteSrc instanceof MultimediaEditableNote) {
            MultimediaEditableNote mmNote = (MultimediaEditableNote) noteSrc;
            if (mmNote.getModelId() != editorNoteDst.getMid()) {
                throw new RuntimeException("Source and Destination Note ID do not match.");
            }

            int totalFields = mmNote.getNumberOfFields();
            for (int i = 0; i < totalFields; i++) {
                editorNoteDst.values()[i] = mmNote.getField(i).getFormattedValue();
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
    public static void saveMedia(Collection col, final MultimediaEditableNote noteNew) {
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
        int fieldCount = noteNew.getNumberOfFields();
        for (int i = 0; i < fieldCount; i++) {
            IField newField = noteNew.getField(i);
            importMediaToDirectory(col, newField);
        }
        // }
    }


    /**
     * Considering the field is new, if it has media handle it
     * 
     * @param field
     */
    private static void importMediaToDirectory(Collection col, IField field) {
        String tmpMediaPath = null;
        switch (field.getType()) {
            case AUDIO_RECORDING:
            case AUDIO_CLIP:
                tmpMediaPath = field.getAudioPath();
                break;

            case IMAGE:
                tmpMediaPath = field.getImagePath();
                break;

            case TEXT:
            default:
                break;
        }
        if (tmpMediaPath != null) {
            try {
                File inFile = new File(tmpMediaPath);
                if (inFile.exists() && inFile.length() > 0) {
                    String fname = col.getMedia().addFile(inFile);
                    File outFile = new File(col.getMedia().dir(), fname);
                    if (field.hasTemporaryMedia() && !outFile.getAbsolutePath().equals(tmpMediaPath)) {
                        // Delete original
                        inFile.delete();
                    }
                    switch (field.getType()) {
                        case AUDIO_RECORDING:
                        case AUDIO_CLIP:
                            field.setAudioPath(outFile.getAbsolutePath());
                            break;
                        case IMAGE:
                            field.setImagePath(outFile.getAbsolutePath());
                            break;
                        default:
                            break;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (EmptyMediaException mediaException) {
                // This shouldn't happen, but we're fine to ignore it if it does.
                Timber.w(mediaException);
                AnkiDroidApp.sendExceptionReport(mediaException, "noteService::importMediaToDirectory");
            }
        }
    }



    /**
     * @param replaceNewlines Converts {@link FieldEditText#NEW_LINE} to HTML linebreaks
     */
    @VisibleForTesting
    @NonNull
    @CheckResult
    public static Bundle getFieldsAsBundleForPreview(java.util.Collection<? extends NoteField> editFields, boolean replaceNewlines) {
        Bundle fields = new Bundle();
        // Save the content of all the note fields. We use the field's ord as the key to
        // easily map the fields correctly later.
        if (editFields == null) {
            return fields;
        }
        for (NoteField e : editFields) {
            if (e == null || e.getFieldText() == null) {
                continue;
            }

            String fieldValue = convertToHtmlNewline(e.getFieldText(), replaceNewlines);
            fields.putString(Integer.toString(e.getOrd()), fieldValue);
        }
        return fields;
    }

    @NonNull
    public static String convertToHtmlNewline(@NonNull String fieldData, boolean replaceNewlines) {
        if (!replaceNewlines) {
            return fieldData;
        }
        return fieldData.replace(FieldEditText.NEW_LINE, "<br>");
    }


    public static void toggleMark(@NonNull Note note) {
        if (isMarked(note)) {
            note.delTag("marked");
        } else {
            note.addTag("marked");
        }
        note.flush();
    }

    public static boolean isMarked(@NonNull Note note) {
        return note.hasTag("marked");
    }


    public interface NoteField {
        int getOrd();
        // ideally shouldn't be nullable
        @Nullable
        String getFieldText();
    }
}
