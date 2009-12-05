package com.ichi2.anki;

import android.content.ContentValues;

public class CardHistoryEntry {
	
	// BEGIN: SQL table columns
	long cardId;
	double time;
	double lastInterval;
	double nextInterval;
	int ease;
	double delay;
	double lastFactor;
	double nextFactor;
	float reps;
	double thinkingTime;
	float yesCount;
	float noCount;
	// END: SQL table columns
	
	public CardHistoryEntry(Card card, int ease, double delay)
	{
		if (card == null)
			return;
		
		cardId = card.id;
		lastInterval = card.lastInterval;
		nextInterval = card.interval;
		lastFactor = card.lastFactor;
		nextFactor = card.factor;
		reps = card.reps;
		yesCount = card.yesCount;
		noCount = card.noCount;
		this.ease = ease;
		this.delay = delay;
		thinkingTime = card.thinkingTime();
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
	values.put("time", System.currentTimeMillis() / 1000.0);
	
	AnkiDb.database.insert("reviewHistory", null, values);
	}
	
}
