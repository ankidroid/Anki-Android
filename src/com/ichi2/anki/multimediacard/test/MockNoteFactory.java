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

package com.ichi2.anki.multimediacard.test;

import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;

/**
 * Made for tests
 */
public class MockNoteFactory {
    public static IMultimediaEditableNote makeNote() {
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
