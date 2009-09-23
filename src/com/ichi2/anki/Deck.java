package com.ichi2.anki;

import android.database.Cursor;
import android.database.SQLException;

public class Deck {
	// BEGIN: SQL table columns
	int id;
	float created;
	float modified;
	String description;
	int version;
	int currentModelId;
	String syncName;
	float lastSync;
	// Scheduling
	// Initial intervals
	float hardIntervalMin;
	float hardIntervalMax;
	float midIntervalMin;
	float midIntervalMax;
	float easyIntervalMin;
	float easyIntervalMax;
	// Delays on failure
	int delay0;
	int delay1;
	int delay2;
	// Collapsing future cards
	float collapseTime;
	// Priorities and postponing
	String highPriority;
	String medPriority;
	String lowPriority;
	String suspended;
	// 0 is random, 1 is by input date
	int newCardOrder;
	// When to show new cards
	int newCardSpacing;
	// Limit the number of failed cards in play
	int failedCardMax;
	// Number of new cards to show per day
	int newCardsPerDay;
	// Currently unused
	int sessionRepLimit;
	int sessionTimeLimit;
	// Stats offset
	float utcOffset;
	// Count cache
	int cardCount;
	int factCount;
	int failedNowCount;
	int failedSoonCount;
	int revCount;
	int newCount;
	// Review order
	int revCardOrder;
	// END: SQL table columns
	
	public static Deck openDeck(String path) throws SQLException {
		Deck deck = new Deck();
		
		AnkiDb.openDatabase(path);
		
		// Read in deck table columns
		Cursor cursor = AnkiDb.database.rawQuery(
				"SELECT id,modified,cardCount,failedSoonCount,"
				+ "revCount,newCount"
				+ " FROM decks"
				+ " LIMIT 1", null);
		
		if (cursor.isClosed())
			throw new SQLException();
		cursor.moveToFirst();
		
		deck.id 			 = cursor.getInt(0);
		deck.modified 		 = cursor.getFloat(1);
		deck.cardCount 		 = cursor.getInt(2);
		deck.failedSoonCount = cursor.getInt(3);
		deck.revCount 		 = cursor.getInt(4);
		deck.newCount 		 = cursor.getInt(5);
		
		return deck;
	}
	
	public void closeDeck() {
		AnkiDb.closeDatabase();
	}

}