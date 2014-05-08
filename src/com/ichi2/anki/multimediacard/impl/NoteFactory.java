/**
 * @author Zaur
 */

package com.ichi2.anki.multimediacard.impl;

import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;

/**
 * Creates IMultimediaEditableNotes according to various parameters.
 */
public class NoteFactory {
    public static IMultimediaEditableNote createNote() {
        MultimediaEditableNote note = new MultimediaEditableNote();
        note.setNumFields(4);

        TextField tf = new TextField();
        tf.setText("world");
        note.setField(0, tf);

        TextField tf2 = new TextField();
        tf2.setText("Welt");
        note.setField(1, tf2);

        TextField tf3 = new TextField();
        tf3.setText("Übung");
        note.setField(2, tf3);

        ImageField imageField = new ImageField();
        imageField.setImagePath("/mnt/sdcard/img/1.jpg");
        note.setField(3, imageField);

        return note;
    }

}
