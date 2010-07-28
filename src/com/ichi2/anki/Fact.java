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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

/**
 * Anki fact.
 * A fact is a single piece of information, made up of a number of fields.
 * See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Facts
 */
public class Fact {

    // TODO: Javadoc.
    // TODO: Finish porting from facts.py.
    // TODO: Methods to read/write from/to DB.

    long id;
    long modelId;
    double created;
    double modified;
    String tags;
    double spaceUntil;

    Model model;
    TreeSet<Field> fields;
    Deck deck;

    public Fact(Deck deck, Model model) {
    	this.deck = deck;
        this.model = model;
        this.id = Utils.genID();
        if (model != null) {
            Iterator<FieldModel> iter = model.fieldModels.iterator();
            while (iter.hasNext()) {
                this.fields.add(new Field(iter.next()));
            }
        }
    }

    // Generate fact object from its ID
    public Fact(Deck deck, long id)
    {
    	this.deck = deck;
        fromDb(id);
        //TODO: load fields associated with this fact.
    }



    /**
     * @return the fields
     */
    public TreeSet<Field> getFields() {
        return fields;
    }

    /**
     * @param fields the fields to set
     */
    public void setFields(TreeSet<Field> fields) {
        this.fields = fields;
    }

    public boolean fromDb(long id)
    {
        this.id = id;
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.deckPath);
        Cursor cursor = null;
        
        try {
	        cursor = ankiDB.database.rawQuery(
	                "SELECT id, modelId, created, modified, tags, spaceUntil " +
	                "FROM facts " +
	                "WHERE id = " +
	                id,
	                null);
	        if (!cursor.moveToFirst()) {
	            Log.w("anki", "Fact.java (constructor): No result from query.");
	            return false;
	        }
	
	        this.id = cursor.getLong(0);
	        this.modelId = cursor.getLong(1);
	        this.created = cursor.getDouble(2);
	        this.modified = cursor.getDouble(3);
	        this.tags = cursor.getString(4);
        } finally {
        	if (cursor != null) cursor.close();
        }

        Cursor fieldsCursor = null;
        try {
	        fieldsCursor = ankiDB.database.rawQuery(
	                "SELECT id, factId, fieldModelId, value " +
	                "FROM fields " +
	                "WHERE factId = " +
	                id, 
	                null);
	
	        fields = new TreeSet<Field>(new FieldOrdinalComparator());
	        while (fieldsCursor.moveToNext())
	        {
	            long fieldId = fieldsCursor.getLong(0);
	            long fieldModelId = fieldsCursor.getLong(2);
	            String fieldValue = fieldsCursor.getString(3);
	
	            Cursor fieldModelCursor = null;
	            FieldModel currentFieldModel = null;
	            try {
		            // Get the field model for this field
		            fieldModelCursor = ankiDB.database.rawQuery(
		                    "SELECT id, ordinal, modelId, name, description " +
		                    "FROM fieldModels " +
		                    "WHERE id = " +
		                    fieldModelId,
		                    null);
		
		            fieldModelCursor.moveToFirst();
		            currentFieldModel = new FieldModel(fieldModelCursor.getLong(0), 
		                    fieldModelCursor.getInt(1), fieldModelCursor.getLong(2),
		                    fieldModelCursor.getString(3), fieldModelCursor.getString(4));
	            } finally {
	            	if (fieldModelCursor != null) fieldModelCursor.close();
	            }
	            fields.add(new Field(fieldId, id, currentFieldModel, fieldValue));
	        }
        } finally {
        	if (fieldsCursor != null) fieldsCursor.close();
        }
        // Read Fields
        return true;
    }

    public String getFieldValue(String fieldModelName) {
        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            Field f = iter.next();
            if (f.fieldModel.name.equals(fieldModelName)) {
                return f.value;
            }
        }
        return null;
    }
    
    public long getFieldModelId(String fieldModelName) {
        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            Field f = iter.next();
            if (f.fieldModel.name.equals(fieldModelName)) {
                return f.fieldModel.id;
            }
        }
        return 0;
    }

    public void toDb()
    {
        double now = System.currentTimeMillis() / 1000.0;

        // update facts table
        ContentValues updateValues = new ContentValues();
        updateValues.put("modified", now);


        //update fields table
        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            Field f = iter.next();

            updateValues = new ContentValues();
            updateValues.put("value", f.value);
            AnkiDatabaseManager.getDatabase(deck.deckPath).database.update("fields", updateValues, "id = ?", new String[] {"" + f.id});
        }
    }

    public LinkedList<Card> getUpdatedRelatedCards() {
        // TODO return instances of each card that is related to this fact
        LinkedList<Card> returnList = new LinkedList<Card>();


        Cursor cardsCursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery(
                "SELECT id, factId " +
                "FROM cards " +
                "WHERE factId = " +
                id, 
                null);

        while (cardsCursor.moveToNext())
        {
            Card newCard = new Card(deck);
            newCard.fromDB(cardsCursor.getLong(0));
            HashMap<String,String> newQA = CardModel.formatQA(this, newCard.getCardModel());
            newCard.question = newQA.get("question");
            newCard.answer = newQA.get("answer");
            
            returnList.add(newCard);
        }
        return returnList;
    }

    public static final class FieldOrdinalComparator implements Comparator<Field> {
        public int compare(Field object1, Field object2) {
            return object1.ordinal - object2.ordinal;
        }
    }

    public class Field {

        // TODO: Javadoc.
        // Methods for reading/writing from/to DB.

        // BEGIN SQL table entries
        long id; // Primary key
        long factId; // Foreign key facts.id
        long fieldModelId; // Foreign key fieldModel.id
        int ordinal;
        String value;
        // END SQL table entries

        // BEGIN JOINed entries
        FieldModel fieldModel;
        // END JOINed entries

        // Backward reference
        Fact fact;

        // for creating instances of existing fields
        public Field(long id, long factId, FieldModel fieldModel, String value)
        {
            this.id = id;
            this.factId = factId;
            this.fieldModel = fieldModel;
            this.value = value;
            this.fieldModel = fieldModel;
            this.ordinal = fieldModel.ordinal;
        }


        // For creating new fields
        public Field(FieldModel fieldModel) {
            if (fieldModel != null) {
                this.fieldModel = fieldModel;
                this.ordinal = fieldModel.ordinal;
            }
            this.value = "";
            this.id = Utils.genID();
        }
    }




}
