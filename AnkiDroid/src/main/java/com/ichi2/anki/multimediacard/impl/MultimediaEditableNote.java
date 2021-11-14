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

package com.ichi2.anki.multimediacard.impl;

import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.IField;

import java.util.ArrayList;

import androidx.annotation.Nullable;

import static org.acra.util.IOUtils.deserialize;
import static org.acra.util.IOUtils.serialize;

/**
 * Implementation of the editable note.
 * <p>
 * Has to be translate to and from anki db format.
 */

public class MultimediaEditableNote implements IMultimediaEditableNote {
    private static final long serialVersionUID = -6161821367135636659L;
    private boolean mIsModified = false;
    private ArrayList<IField> mFields;
    private long mModelId;
    /**
     * Field values in the note editor, before any editing has taken place
     * These values should not be modified
     */
    public ArrayList<IField> mInitialFields;


    private void setThisModified() {
        mIsModified = true;
    }


    @Override
    public boolean isModified() {
        return mIsModified;
    }


    // package
    public void setNumFields(int numberOfFields) {
        getFieldsPrivate().clear();
        for (int i = 0; i < numberOfFields; ++i) {
            getFieldsPrivate().add(null);
        }
    }


    private ArrayList<IField> getFieldsPrivate() {
        if (mFields == null) {
            mFields = new ArrayList<>(0);
        }

        return mFields;
    }


    @Override
    public int getNumberOfFields() {
        return getFieldsPrivate().size();
    }


    @Override
    public IField getField(int index) {
        if (index >= 0 && index < getNumberOfFields()) {
            return getFieldsPrivate().get(index);
        }
        return null;
    }


    @Override
    public boolean setField(int index, IField field) {
        if (index >= 0 && index < getNumberOfFields()) {
            // If the same unchanged field is set.
            if (getField(index) == field) {
                if (field.isModified()) {
                    setThisModified();
                }
            } else {
                setThisModified();
            }

            getFieldsPrivate().set(index, field);

            return true;
        }
        return false;
    }


    public void setModelId(long modelId) {
        mModelId = modelId;
    }


    public long getModelId() {
        return mModelId;
    }

    public void freezeInitialFieldValues() {
        mInitialFields = new ArrayList<>();
        for (IField f : mFields) {
            mInitialFields.add(cloneField(f));
        }
    }

    public int getInitialFieldCount() {
        return mInitialFields.size();
    }

    public IField getInitialField(int index) {
        return cloneField(mInitialFields.get(index));
    }

    @Nullable
    private IField cloneField(IField f) {
        return deserialize(IField.class, serialize(f));
    }
}
