package com.ichi2.anki;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

public class Card {
	
	// TODO: Javadoc.

	// BEGIN SQL table entries
	long id; // Primary key
	long factId; // Foreign key facts.id
	long cardModelId; // Foreign key cardModels.id
	float created = System.currentTimeMillis() / 1000f;
	float modified = System.currentTimeMillis() / 1000f;
	String tags = "";
	int ordinal;
	// Cached - changed on fact update
	String question = "";
	String answer = "";
	// Default to 'normal' priority
	// This is indexed in deck.java as we need to create a reverse index
	int priority = 2;
	float interval = 0;
	float lastInterval = 0;
	float due = System.currentTimeMillis() / 1000f;
	float lastDue = 0;
	float factor = 2.5f;
	float lastFactor = 2.5f;
	float firstAnswered = 0;
	// Stats
	int reps = 0;
	int successive = 0;
	float averageTime = 0;
	float reviewTime = 0;
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
	float spaceUntil = 0;
	float relativeDelay = 0;
	int isDue = 0;
	int type = 2;
	float combinedDue = 0;
	// END SQL table entries
	
	// BEGIN JOINed variables
	CardModel cardModel;
	Fact fact;
	// END JOINed variables
	
	float timerStarted;
	float timerStopped;
	float fuzz;
	
	public Card(Fact fact, CardModel cardModel, float created) {
		tags = "";
		id = Util.genID();
		// New cards start as new & due
		type = 2;
		isDue = 1;
		timerStarted = Float.NaN;
		timerStopped = Float.NaN;
		modified = System.currentTimeMillis() / 1000f;
		if (created != Float.NaN) {
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
			HashMap<String, String> qa = CardModel.formatQA(id, fact.modelId, d, splitTags(), cardModel);
			question = qa.get("question");
			answer = qa.get("answer");
		}
	}
	
	public Card(){
		this(null, null, Float.NaN);
	}
	
	public void setModified() {
		modified = System.currentTimeMillis() / 1000f;
	}
	
	public void startTimer() {
		timerStarted = System.currentTimeMillis() / 1000f;
	}
	
	public void stopTimer() {
		timerStopped = System.currentTimeMillis() / 1000f;
	}
	
	public float thinkingTime() {
		if (timerStopped == Float.NaN)
			return (System.currentTimeMillis() / 1000f) - timerStarted;
		else
			return timerStopped - timerStarted;
	}
	
	public float totalTime() {
		return (System.currentTimeMillis() / 1000f) - timerStarted;
	}
	
	public void genFuzz() {
		Random rand = new Random();
		fuzz = 0.95f + (0.1f * rand.nextFloat());
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
		
		float delay = totalTime();
		// Ignore any times over 60 seconds
		if (delay < 60) {
			reviewTime += delay;
			if (averageTime != 0)
				averageTime = (averageTime + delay) / 2f;
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
			firstAnswered = System.currentTimeMillis() / 1000f;
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
	
	public boolean fromDB(long id) {
		Cursor cursor = AnkiDb.database.rawQuery(
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
		this.created = cursor.getFloat(3);
		this.modified = cursor.getFloat(4);
		this.tags = cursor.getString(5);
		this.ordinal = cursor.getInt(6);
		this.question = cursor.getString(7);
		this.answer = cursor.getString(8);
		this.priority = cursor.getInt(9);
		this.interval = cursor.getFloat(10);
		this.lastInterval = cursor.getFloat(11);
		this.due = cursor.getFloat(12);
		this.lastDue = cursor.getFloat(13);
		this.factor = cursor.getFloat(14);
		this.lastFactor = cursor.getFloat(15);
		this.firstAnswered = cursor.getFloat(16);
		this.reps = cursor.getInt(17);
		this.successive = cursor.getInt(18);
		this.averageTime = cursor.getFloat(19);
		this.reviewTime = cursor.getFloat(20);
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
		this.spaceUntil = cursor.getFloat(33);
		this.isDue = cursor.getInt(34);
		this.type = cursor.getInt(35);
		this.combinedDue = cursor.getFloat(36);
		
		cursor.close();
		
		// TODO: Should also read JOINed entries CardModel and Fact.
		return true;
	}
	
	public void toDB() {
		if (this.reps == 0)
			this.type = 2;
		else if (this.successive != 0)
			this.type = 1;
		else
			this.type = 0;
		
		ContentValues values = new ContentValues();
		values.put("factId", this.factId);
		values.put("cardModelId", this.cardModelId);
		values.put("created", this.created);
		values.put("modified", this.modified);
		values.put("tags", this.tags);
		values.put("ordinal", this.ordinal);
		values.put("question", this.question);
		values.put("answer", this.answer);
		values.put("priority", this.priority);
		values.put("interval", this.interval);
		values.put("lastInterval", this.lastInterval);
		values.put("due", this.due);
		values.put("lastDue", this.lastDue);
		values.put("factor", this.factor);
		values.put("lastFactor", this.lastFactor);
		values.put("firstAnswered", this.firstAnswered);
		values.put("reps", this.reps);
		values.put("successive", this.successive);
		values.put("averageTime", this.averageTime);
		values.put("reviewTime", this.reviewTime);
		values.put("youngEase0", this.youngEase0);
		values.put("youngEase1", this.youngEase1);
		values.put("youngEase2", this.youngEase2);
		values.put("youngEase3", this.youngEase3);
		values.put("youngEase4", this.youngEase4);
		values.put("matureEase0", this.matureEase0);
		values.put("matureEase1", this.matureEase1);
		values.put("matureEase2", this.matureEase2);
		values.put("matureEase3", this.matureEase3);
		values.put("matureEase4", this.matureEase4);
		values.put("yesCount", this.yesCount);
		values.put("noCount", this.noCount);
		values.put("spaceUntil", this.spaceUntil);
		values.put("isDue", this.isDue);
		values.put("type", this.type);
		values.put("combinedDue", Math.max(this.spaceUntil, this.due));
		values.put("relativeDelay", 0f);
		AnkiDb.database.update("cards", values, "id = " + this.id, null);
		
		// TODO: Should also write JOINED entries: CardModel and Fact.
	}
	
}
