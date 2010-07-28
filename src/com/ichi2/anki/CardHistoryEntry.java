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

/**
 * Review history of a card.
 */
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
	double reps;
	double thinkingTime;
	double yesCount;
	double noCount;
	// END: SQL table columns

	Deck deck;
	
	/**
	 * Constructor
	 */
	public CardHistoryEntry(Deck deck, Card card, int ease, double delay)
	{
		this.deck = deck;
		
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

	/**
	 * Write review history to the database.
	 */
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
	
		AnkiDatabaseManager.getDatabase(deck.deckPath).database.insert("reviewHistory", null, values);
	}
}
