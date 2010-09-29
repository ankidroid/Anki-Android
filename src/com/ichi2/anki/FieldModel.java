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
    long id;
    int ordinal;
    long modelId;
    String name = "";
    String description = "";
    // Reused as RTL marker
    String features = "";
    int required = 1;
    int unique = 1;
    int numeric = 0;
    // Display
    String quizFontFamily;
    int quizFontSize;
    String quizFontColour;
    String editFontFamily;
    int editFontSize = 20;
    // END SQL table entries

    /**
     * Backward reference
     */
    Model model;


    public FieldModel(long id, int ordinal, long modelId, String name, String description) {
        this.id = id;
        this.ordinal = ordinal;
        this.modelId = modelId;
        this.name = name;
        this.description = description;
    }


    public FieldModel(String name, boolean required, boolean unique) {
        this.name = name;
        this.required = required ? 1 : 0;
        this.unique = unique ? 1 : 0;
        id = Utils.genID();
    }


    public FieldModel() {
        this("", true, true);
    }

    /** SELECT string with only those fields, which are used in AnkiDroid */
    private final static String SELECT_STRING = "SELECT id, ordinal, modelId, name, description"
    // features, required, unique, numeric left out
            + ", quizFontSize, quizFontColour" // quizFontFamily
            // editFontFamily, editFontSize left out
            + " FROM fieldModels";


    /**
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

            cursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery(query.toString(), null);

            if (cursor.moveToFirst()) {
                do {
                    myFieldModel = new FieldModel();

                    myFieldModel.id = cursor.getLong(0);
                    myFieldModel.ordinal = cursor.getInt(1);
                    myFieldModel.modelId = cursor.getLong(2);
                    myFieldModel.name = cursor.getString(3);
                    myFieldModel.description = cursor.getString(4);
                    myFieldModel.quizFontSize = cursor.getInt(5);
                    myFieldModel.quizFontColour = cursor.getString(6);
                    models.put(myFieldModel.id, myFieldModel);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }


    public FieldModel copy() {
        FieldModel fieldModel = new FieldModel(name, (required == 1) ? true : false, (unique == 1) ? true : false);
        fieldModel.ordinal = ordinal;
        fieldModel.modelId = modelId;
        fieldModel.description = description;
        fieldModel.features = features;
        fieldModel.numeric = numeric;
        fieldModel.quizFontFamily = quizFontFamily;
        fieldModel.quizFontSize = quizFontSize;
        fieldModel.quizFontColour = quizFontColour;
        fieldModel.editFontFamily = editFontFamily;
        fieldModel.editFontSize = editFontSize;
        fieldModel.model = null;

        return fieldModel;
    }


    /**
     * Implements Comparator by comparing the field "ordinal".
     * 
     * @param object1
     * @param object2
     * @return
     */
    @Override
    public int compare(FieldModel object1, FieldModel object2) {
        return object1.ordinal - object2.ordinal;
    }

}
