/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
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

package com.ichi2.anki;

import android.database.Cursor;

import java.util.Comparator;
import java.util.TreeMap;

/**
 * Fields are the different pieces of data which make up a fact.
 *
 * @see http://ichi2.net/anki/wiki/ModelProperties#Fields
 */
public class FieldModel implements Comparator<FieldModel> {

    // BEGIN SQL table entries
    private long mId;
    private int mOrdinal;
    private long mModelId;
    private String mName = "";
    private String mDescription = "";
    // Reused as RTL marker
    private String mFeatures = "";
    private int mRequired = 1;
    private int mUnique = 1;
    private int mNumeric = 0;
    // Display
    private String mQuizFontFamily;
    private int mQuizFontSize;
    private String mQuizFontColour;
    private String mEditFontFamily;
    private int mEditFontSize = 20;
    // END SQL table entries

    /**
     * Backward reference
     */
    private Model mModel;


    public FieldModel(long id, int ordinal, long modelId, String name, String description) {
        mId = id;
        mOrdinal = ordinal;
        mModelId = modelId;
        mName = name;
        mDescription = description;
    }


    public FieldModel(String name, boolean required, boolean unique) {
        mName = name;
        mRequired = required ? 1 : 0;
        mUnique = unique ? 1 : 0;
        mId = Utils.genID();
    }


    public FieldModel() {
        this("", true, true);
    }

    /** SELECT string with only those fields, which are used in AnkiDroid */
    private final static String SELECT_STRING = "SELECT id, ordinal, modelId, name, description"
            + ", quizFontSize, quizFontColour"
            + " FROM fieldModels";


    /**
     * Return all field models.
     * @param modelId
     * @param models will be changed by adding all found FieldModels into it
     * @return unordered FieldModels which are related to a given Model put into the parameter "models"
     */
    protected static final void fromDb(Deck deck, long modelId, TreeMap<Long, FieldModel> models) {
        Cursor cursor = null;
        FieldModel myFieldModel = null;
        try {
            StringBuffer query = new StringBuffer(SELECT_STRING);
            query.append(" WHERE modelId = ");
            query.append(modelId);

            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(query.toString(), null);

            if (cursor.moveToFirst()) {
                do {
                    myFieldModel = new FieldModel();

                    myFieldModel.mId = cursor.getLong(0);
                    myFieldModel.mOrdinal = cursor.getInt(1);
                    myFieldModel.mModelId = cursor.getLong(2);
                    myFieldModel.mName = cursor.getString(3);
                    myFieldModel.mDescription = cursor.getString(4);
                    myFieldModel.mQuizFontSize = cursor.getInt(5);
                    myFieldModel.mQuizFontColour = cursor.getString(6);
                    models.put(myFieldModel.mId, myFieldModel);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }


    public FieldModel copy() {
        FieldModel fieldModel = new FieldModel(mName, (mRequired == 1) ? true : false, (mUnique == 1) ? true : false);
        fieldModel.mOrdinal = mOrdinal;
        fieldModel.mModelId = mModelId;
        fieldModel.mDescription = mDescription;
        fieldModel.mFeatures = mFeatures;
        fieldModel.mNumeric = mNumeric;
        fieldModel.mQuizFontFamily = mQuizFontFamily;
        fieldModel.mQuizFontSize = mQuizFontSize;
        fieldModel.mQuizFontColour = mQuizFontColour;
        fieldModel.mEditFontFamily = mEditFontFamily;
        fieldModel.mEditFontSize = mEditFontSize;
        fieldModel.mModel = null;

        return fieldModel;
    }


    /**
     * Implements Comparator by comparing the field "ordinal".
     * @param object1
     * @param object2
     * @return
     */
    public int compare(FieldModel object1, FieldModel object2) {
        return object1.mOrdinal - object2.mOrdinal;
    }


    /**
     * @return the name
     */
    public String getName() {
        return mName;
    }


    /**
     * @return the id
     */
    public long getId() {
        return mId;
    }


    /**
     * @return the ordinal
     */
    public int getOrdinal() {
        return mOrdinal;
    }


    /**
     * @return the quizFontFamily
     */
    public String getQuizFontFamily() {
        return mQuizFontFamily;
    }


    /**
     * @return the quizFontSize
     */
    public int getQuizFontSize() {
        return mQuizFontSize;
    }


    /**
     * @return the quizFontColour
     */
    public String getQuizFontColour() {
        return mQuizFontColour;
    }

}
