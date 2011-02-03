/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Anki fact.
 * A fact is a single piece of information, made up of a number of fields.
 * See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Facts
 */
public class Fact {

    // TODO: Javadoc.
    // TODO: Finish porting from facts.py.
    // TODO: Methods to read/write from/to DB.

    private long mId;
    private long mModelId;
    private double mCreated;
    private double mModified;
    private String mTags;
    private String mSpaceUntil; // Once obsolete, under libanki1.1 spaceUntil is reused as a html-stripped cache of the fields

    private Model mModel;
    private TreeSet<Field> mFields;
    private Deck mDeck;


    // Generate fact object from its ID
    public Fact(Deck deck, long id) {
        mDeck = deck;
        fromDb(id);
        // TODO: load fields associated with this fact.
    }


    public Fact(Deck deck, Model model) {
        mDeck = deck;
        mModel = model;
        mId = Utils.genID();
        if (model == null) {
            mModelId = deck.getCurrentModelId();
        } else {
            mModelId = model.getId();
        }
        TreeMap<Long, FieldModel> mFieldModels = new TreeMap<Long, FieldModel>();
        FieldModel.fromDb(deck, mModelId, mFieldModels);
        mFields = new TreeSet<Field>(new FieldOrdinalComparator());
        for (Long i : mFieldModels.keySet()) {
            mFields.add(new Field(mId, mFieldModels.get(i)));
        }
    }


    /**
     * @return the mId
     */
    public long getId() {
        return mId;
    }


    /**
     * @return the mTags
     */
    public String getTags() {
        return mTags;
    }


    /**
     * @param tags the tags to set
     */
    public void setTags(String tags) {
        mTags = tags;
    }


    /**
     * @return the mModelId
     */
    public long getModelId() {
        return mModelId;
    }


    /**
     * @return the fields
     */
    public TreeSet<Field> getFields() {
        return mFields;
    }


    private boolean fromDb(long id) {
        mId = id;
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());
        Cursor cursor = null;

        try {
            cursor = ankiDB.getDatabase().rawQuery("SELECT id, modelId, created, modified, tags, spaceUntil "
                    + "FROM facts " + "WHERE id = " + id, null);
            if (!cursor.moveToFirst()) {
                Log.w(AnkiDroidApp.TAG, "Fact.java (constructor): No result from query.");
                return false;
            }

            mId = cursor.getLong(0);
            mModelId = cursor.getLong(1);
            mCreated = cursor.getDouble(2);
            mModified = cursor.getDouble(3);
            mTags = cursor.getString(4);
            mSpaceUntil = cursor.getString(5);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Cursor fieldsCursor = null;
        try {
            fieldsCursor = ankiDB.getDatabase().rawQuery("SELECT id, factId, fieldModelId, value " + "FROM fields "
                    + "WHERE factId = " + id, null);

            mFields = new TreeSet<Field>(new FieldOrdinalComparator());
            while (fieldsCursor.moveToNext()) {
                long fieldId = fieldsCursor.getLong(0);
                long fieldModelId = fieldsCursor.getLong(2);
                String fieldValue = fieldsCursor.getString(3);

                Cursor fieldModelCursor = null;
                FieldModel currentFieldModel = null;
                try {
                    // Get the field model for this field
                    fieldModelCursor = ankiDB.getDatabase().rawQuery("SELECT id, ordinal, modelId, name, description "
                            + "FROM fieldModels " + "WHERE id = " + fieldModelId, null);

                    fieldModelCursor.moveToFirst();
                    currentFieldModel = new FieldModel(fieldModelCursor.getLong(0), fieldModelCursor.getInt(1),
                            fieldModelCursor.getLong(2), fieldModelCursor.getString(3), fieldModelCursor.getString(4));
                } finally {
                    if (fieldModelCursor != null) {
                        fieldModelCursor.close();
                    }
                }
                mFields.add(new Field(fieldId, id, currentFieldModel, fieldValue));
            }
        } finally {
            if (fieldsCursor != null) {
                fieldsCursor.close();
            }
        }
        // Read Fields
        return true;
    }


    public void toDb() {
        double now = Utils.now();

        // update facts table
        ContentValues updateValues = new ContentValues();
        updateValues.put("modified", now);
        updateValues.put("tags", mTags);
        updateValues.put("spaceUntil", mSpaceUntil);
        AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().update("facts", updateValues, "id = ?",
                new String[] { "" + mId });

        // update fields table
        for (Field f : mFields) {
            updateValues = new ContentValues();
            updateValues.put("value", f.mValue);
            AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().update("fields", updateValues, "id = ?",
                    new String[] { "" + f.mFieldId });
        }
    }


    public String getFieldValue(String fieldModelName) {
        for (Field f : mFields) {
            if (f.mFieldModel.getName().equals(fieldModelName)) {
                return f.mValue;
            }
        }
        return null;
    }


    public long getFieldModelId(String fieldModelName) {
        for (Field f : mFields) {
            if (f.mFieldModel.getName().equals(fieldModelName)) {
                return f.mFieldModel.getId();
            }
        }
        return 0;
    }


    public LinkedList<Card> getUpdatedRelatedCards() {
        // TODO return instances of each card that is related to this fact
        LinkedList<Card> returnList = new LinkedList<Card>();

        Cursor cardsCursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT id, factId FROM cards " + "WHERE factId = " + mId, null);

        while (cardsCursor.moveToNext()) {
            Card newCard = new Card(mDeck);
            newCard.fromDB(cardsCursor.getLong(0));
            newCard.loadTags();
            HashMap<String, String> newQA = CardModel.formatQA(this, newCard.getCardModel(), newCard.splitTags());
            newCard.setQuestion(newQA.get("question"));
            newCard.setAnswer(newQA.get("answer"));

            returnList.add(newCard);
        }
        if (cardsCursor != null) {
            cardsCursor.close();
        }

        return returnList;
    }


    public void setModified() {
        setModified(false, null, true);
    }
    public void setModified(boolean textChanged) {
        setModified(textChanged, null, true);
    }
    public void setModified(boolean textChanged, Deck deck) {
        setModified(textChanged, deck, true);
    }
    public void setModified(boolean textChanged, Deck deck, boolean media) {
        mModified = Utils.now();
        if (textChanged) {
            assert (deck != null);
            mSpaceUntil = "";
            StringBuilder str = new StringBuilder(1024);
            for (Field f : getFields()) {
                str.append(f.getValue()).append(" ");
            }
            mSpaceUntil = str.toString();
            mSpaceUntil.substring(0, mSpaceUntil.length() - 1);
            mSpaceUntil = Utils.stripHTMLMedia(mSpaceUntil);
            // Log.d(AnkiDroidApp.TAG, "spaceUntil = " + mSpaceUntil);
            for (Card card : getUpdatedRelatedCards()) {
                card.setModified();
                card.toDB();
                // card.rebuildQA(deck);
            }
        }
    }


    public static final class FieldOrdinalComparator implements Comparator<Field> {
        @Override
        public int compare(Field object1, Field object2) {
            return object1.mOrdinal - object2.mOrdinal;
        }
    }

    public class Field {

        // TODO: Javadoc.
        // Methods for reading/writing from/to DB.

        // BEGIN SQL table entries
        private long mFieldId; // Primary key id, but named fieldId to no hide Fact.id
        private long mFactId; // Foreign key facts.id
        private long mFieldModelId; // Foreign key fieldModel.id
        private int mOrdinal;
        private String mValue;
        // END SQL table entries

        // BEGIN JOINed entries
        private FieldModel mFieldModel;
        // END JOINed entries

        // Backward reference
        private Fact mFact;


        // for creating instances of existing fields
        public Field(long id, long factId, FieldModel fieldModel, String value) {
            mFieldId = id;
            mFactId = factId;
            mFieldModelId = fieldModel.getId();
            mValue = value;
            mFieldModel = fieldModel;
            mOrdinal = fieldModel.getOrdinal();
        }


        // For creating new fields
        public Field(long factId, FieldModel fieldModel) {
            if (fieldModel != null) {
                mFieldModel = fieldModel;
                mOrdinal = fieldModel.getOrdinal();
            }
            mFactId = factId;
            mFieldModelId = fieldModel.getId();
            mValue = "";
            mFieldId = Utils.genID();
        }


        /**
         * @return the FactId
         */
        public long getFactId() {
            return mFactId;
        }


        /**
         * @return the FieldModelId
         */
        public long getFieldModelId() {
            return mFieldModelId;
        }


        /**
         * @return the Ordinal
         */
        public int getOrdinal() {
            return mOrdinal;
        }


        /**
         * @param value the value to set
         */
        public void setValue(String value) {
            mValue = value;
        }


        /**
         * @return the value
         */
        public String getValue() {
            return mValue;
        }


        /**
         * @return the Field's Id
         */
        public long getId() {
            return mFieldId;
        }


        /**
         * @return the fieldModel
         */
        public FieldModel getFieldModel() {
            return mFieldModel;
        }
    }
}
