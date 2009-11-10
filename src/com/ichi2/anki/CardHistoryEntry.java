package com.ichi2.anki;

import android.content.ContentValues;

public class CardHistoryEntry {
	
	// BEGIN: SQL table columns
	long cardId;
	float time = System.currentTimeMillis() / 1000f;
	float lastInterval;
	float nextInterval;
	int ease;
	float delay;
	float lastFactor;
	float nextFactor;
	float reps;
	float thinkingTime;
	float yesCount;
	float noCount;
	// END: SQL table columns
	
	public CardHistoryEntry(Card card, int ease, float delay)
	{
		if (card == null)
			return;
		
		this.cardId = card.id;
		this.lastInterval = card.lastInterval;
		this.nextInterval = card.interval;
		this.lastFactor = card.lastFactor;
		this.nextFactor = card.factor;
		this.reps = card.reps;
		this.yesCount = card.yesCount;
		this.noCount = card.noCount;
		this.ease = ease;
		this.delay = delay;
		this.thinkingTime = card.thinkingTime();
	}
	
	public void writeSQL()
	{
	ContentValues values = new ContentValues();
	values.put("cardId", cardId);
	values.put("lastInterval", lastInterval);
	values.put("nextInterval", nextInterval);
	values.put("ease", ease);
	values.put("delay", delay);
	values.put("lastFactor", lastFactor);
	values.put("nextFactor", nextFactor);
	values.put("reps", reps);
	values.put("thinkingTime", thinkingTime);
	values.put("yesCount", yesCount);
	values.put("noCount", noCount);
	values.put("time", System.currentTimeMillis() / 1000f);
	
	AnkiDb.database.insert("reviewHistory", null, values);
	}
	
}
