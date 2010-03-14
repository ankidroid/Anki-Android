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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

/**
 * 
 * A card is a presentation of a fact, and has two sides: a question and an answer.
 * Any number of fields can appear on each side.
 * When you add a fact to Anki, cards which show that fact are generated.
 * Some models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class Card {

    // TODO: Javadoc.

    // BEGIN SQL table entries
    long id; // Primary key
    long factId; // Foreign key facts.id
    long cardModelId; // Foreign key cardModels.id
    double created = System.currentTimeMillis() / 1000.0;
    double modified = System.currentTimeMillis() / 1000.0;
    String tags = "";
    int ordinal;
    // Cached - changed on fact update
    String question = "";
    String answer = "";
    // Default to 'normal' priority
    // This is indexed in deck.java as we need to create a reverse index
    int priority = 2;
    double interval = 0;
    double lastInterval = 0;
    double due = System.currentTimeMillis() / 1000.0;
    double lastDue = 0;
    double factor = 2.5;
    double lastFactor = 2.5;
    double firstAnswered = 0;
    // Stats
    int reps = 0;
    int successive = 0;
    double averageTime = 0;
    double reviewTime = 0;
    int youngEase0 = 0;
    int youngEase1 = 0;
    int youngEase2 = 0;
    int youngEase3 = 0;
    int youngEase4 = 0;
    int matureEase0 = 0;
    int matureEase1 = 0;
    int matureEase2 = 0;
    int matureEase3 = 0;
    int matureEase4 = 0;
    // This duplicates the above data, because there's no way to map imported
    // data to the above
    int yesCount = 0;
    int noCount = 0;
    double spaceUntil = 0;
    double relativeDelay = 0;
    int isDue = 0;
    int type = 2;
    double combinedDue = 0;
    // END SQL table entries

    // BEGIN JOINed variables
    CardModel cardModel;
    Fact fact;
    // END JOINed variables

    double timerStarted;
    double timerStopped;
    double fuzz;

    public Card(Fact fact, CardModel cardModel, double created) {
        tags = "";
        id = Util.genID();
        // New cards start as new & due
        type = 2;
        isDue = 1;
        timerStarted = Double.NaN;
        timerStopped = Double.NaN;
        modified = System.currentTimeMillis() / 1000.0;
        if (created != Double.NaN) {
            this.created = created;
            this.due = created;
        }
        else
            due = modified;
        combinedDue = due;
        this.fact = fact;
        this.cardModel = cardModel;
        if (cardModel != null) {
            cardModelId = cardModel.id;
            ordinal = cardModel.ordinal;
            HashMap<String, HashMap<Long, String>> d = new HashMap<String, HashMap<Long, String>>();
            Iterator<FieldModel> iter = fact.model.fieldModels.iterator();
            while (iter.hasNext()) {
                FieldModel fm = iter.next();
                HashMap<Long, String> field = new HashMap<Long, String>();
                field.put(fm.id, fact.getFieldValue(fm.name));
                d.put(fm.name, field);
            }
            //			HashMap<String, String> qa = CardModel.formatQA(id, fact.modelId, d, splitTags(), cardModel);
            //			question = qa.get("question");
            //			answer = qa.get("answer");
        }
    }

    public Card(){
        this(null, null, Double.NaN);
    }

    public Fact getFact() 
    {
        if (fact != null)
        {
            return fact;
        } 
        else
        {
            fact = new Fact(factId);
            return fact;
        }
    }

    public void setModified() {
        modified = System.currentTimeMillis() / 1000.0;
    }

    public void startTimer() {
        timerStarted = System.currentTimeMillis() / 1000.0;
    }

    public void stopTimer() {
        timerStopped = System.currentTimeMillis() / 1000.0;
    }

    public double thinkingTime() {
        if (Double.isNaN(timerStopped))
            return (System.currentTimeMillis() / 1000.0) - timerStarted;
        else
            return timerStopped - timerStarted;
    }

    public double totalTime() {
        return (System.currentTimeMillis() / 1000.0) - timerStarted;
    }

    public void genFuzz() {
        Random rand = new Random();
        fuzz = 0.95 + (0.1 * rand.nextDouble());
    }

    public String htmlQuestion(String type, boolean align) {
        return null;
    }

    public String htmlAnswer(boolean align) {
        return htmlQuestion("answer", align);
    }

    public void updateStats(int ease, String state) {
        reps += 1;
        if (ease > 1)
            successive += 1;
        else
            successive = 0;

        double delay = totalTime();
        // Ignore any times over 60 seconds
        if (delay < 60) {
            reviewTime += delay;
            if (averageTime != 0)
                averageTime = (averageTime + delay) / 2.0;
            else
                averageTime = delay;
        }
        // We don't track first answer for cards
        if (state == "new")
            state = "young";
        // Update ease and yes/no count
        String attr = state + String.format("Ease%d", ease);
        try {
            Field f = this.getClass().getDeclaredField(attr);
            f.setInt(this, f.getInt(this) + 1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ease < 2)
            noCount += 1;
        else
            yesCount += 1;
        if (firstAnswered == 0)
            firstAnswered = System.currentTimeMillis() / 1000.0;
        setModified();
    }

    public String[] splitTags() {
        return null;
    }

    public String allTags() {
        return null;
    }

    public boolean hasTag(String tag) {
        return true;
    }
    
    public CardModel getCardModel()
    {
        CardModel returnModel = new CardModel();
        returnModel.fromDb(cardModelId);
        return returnModel;
    }

    public boolean fromDB(long id) {
    	Cursor cursor = null;
    	
    	try {
	        cursor = AnkiDb.database.rawQuery(
	                "SELECT id, factId, cardModelId, created, modified, tags, " +
	                "ordinal, question, answer, priority, interval, lastInterval, " +
	                "due, lastDue, factor, lastFactor, firstAnswered, reps, " +
	                "successive, averageTime, reviewTime, youngEase0, youngEase1, " +
	                "youngEase2, youngEase3, youngEase4, matureEase0, matureEase1, " +
	                "matureEase2, matureEase3, matureEase4, yesCount, noCount, " +
	                "spaceUntil, isDue, type, combinedDue " +
	                "FROM cards " +
	                "WHERE id = " +
	                id,
	                null);
	        if (!cursor.moveToFirst()) {
	            Log.w("anki", "Card.java (fromDB(id)): No result from query.");
	            return false;
	        }
	
	        this.id = cursor.getLong(0);
	        this.factId = cursor.getLong(1);
	        this.cardModelId = cursor.getLong(2);
	        this.created = cursor.getDouble(3);
	        this.modified = cursor.getDouble(4);
	        this.tags = cursor.getString(5);
	        this.ordinal = cursor.getInt(6);
	        this.question = cursor.getString(7);
	        this.answer = cursor.getString(8);
	        this.priority = cursor.getInt(9);
	        this.interval = cursor.getDouble(10);
	        this.lastInterval = cursor.getDouble(11);
	        this.due = cursor.getDouble(12);
	        this.lastDue = cursor.getDouble(13);
	        this.factor = cursor.getDouble(14);
	        this.lastFactor = cursor.getDouble(15);
	        this.firstAnswered = cursor.getDouble(16);
	        this.reps = cursor.getInt(17);
	        this.successive = cursor.getInt(18);
	        this.averageTime = cursor.getDouble(19);
	        this.reviewTime = cursor.getDouble(20);
	        this.youngEase0 = cursor.getInt(21);
	        this.youngEase1 = cursor.getInt(22);
	        this.youngEase2 = cursor.getInt(23);
	        this.youngEase3 = cursor.getInt(24);
	        this.youngEase4 = cursor.getInt(25);
	        this.matureEase0 = cursor.getInt(26);
	        this.matureEase1 = cursor.getInt(27);
	        this.matureEase2 = cursor.getInt(28);
	        this.matureEase3 = cursor.getInt(29);
	        this.matureEase4 = cursor.getInt(30);
	        this.yesCount = cursor.getInt(31);
	        this.noCount = cursor.getInt(32);
	        this.spaceUntil = cursor.getDouble(33);
	        this.isDue = cursor.getInt(34);
	        this.type = cursor.getInt(35);
	        this.combinedDue = cursor.getDouble(36);
    	} finally {
    		if (cursor != null) cursor.close();
    	}

        // TODO: Should also read JOINed entries CardModel and Fact.

        return true;
    }

    public void toDB() {
        if (reps == 0)
            type = 2;
        else if (successive != 0)
            type = 1;
        else
            type = 0;

        ContentValues values = new ContentValues();
        values.put("factId", factId);
        values.put("cardModelId", cardModelId);
        values.put("created", created);
        values.put("modified", modified);
        values.put("tags", tags);
        values.put("ordinal", ordinal);
        values.put("question", question);
        values.put("answer", answer);
        values.put("priority", priority);
        values.put("interval", interval);
        values.put("lastInterval", lastInterval);
        values.put("due", due);
        values.put("lastDue", lastDue);
        values.put("factor", factor);
        values.put("lastFactor", lastFactor);
        values.put("firstAnswered", firstAnswered);
        values.put("reps", reps);
        values.put("successive", successive);
        values.put("averageTime", averageTime);
        values.put("reviewTime", reviewTime);
        values.put("youngEase0", youngEase0);
        values.put("youngEase1", youngEase1);
        values.put("youngEase2", youngEase2);
        values.put("youngEase3", youngEase3);
        values.put("youngEase4", youngEase4);
        values.put("matureEase0", matureEase0);
        values.put("matureEase1", matureEase1);
        values.put("matureEase2", matureEase2);
        values.put("matureEase3", matureEase3);
        values.put("matureEase4", matureEase4);
        values.put("yesCount", yesCount);
        values.put("noCount", noCount);
        values.put("spaceUntil", spaceUntil);
        values.put("isDue", isDue);
        values.put("type", type);
        values.put("combinedDue", Math.max(spaceUntil, due));
        values.put("relativeDelay", 0.0);
        AnkiDb.database.update("cards", values, "id = " + id, null);

        // TODO: Should also write JOINED entries: CardModel and Fact.
    }

}
