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

package com.ichi2.anki.multimediacard.fields;

import android.os.Bundle;

import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity;

import androidx.annotation.Nullable;

public abstract class FieldControllerBase implements IFieldController {

    protected MultimediaEditFieldActivity mActivity;
    protected IField mField;
    protected IMultimediaEditableNote mNote;
    protected int mIndex;


    @Override
    public void setField(IField field) {
        mField = field;
    }


    @Override
    public void setNote(IMultimediaEditableNote note) {
        mNote = note;
    }


    @Override
    public void setFieldIndex(int index) {
        mIndex = index;
    }


    @Override
    public void setEditingActivity(MultimediaEditFieldActivity activity) {
        mActivity = activity;
    }


    @Override
    public void loadInstanceState(Bundle savedInstancedState) { /* Default implementation does nothing */ }


    @Override
    @Nullable
    public Bundle saveInstanceState() {
        return null;
    }
}
