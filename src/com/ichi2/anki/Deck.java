package com.ichi2.anki;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

public class Deck {
	// Auto priorities
	private static final int PRIORITY_HIGH = 4;
	private static final int PRIORITY_MED = 3;
	private static final int PRIORITY_NORM = 2;
	private static final int PRIORITY_LOW = 1;
	private static final int PRIORITY_NONE = 0;
	// Manual priorities
	private static final int PRIORITY_REVEARLY = -1;
	private static final int PRIORITY_BURIED = -2;
	private static final int PRIORITY_SUSPENDED = -3;
	// Rest
	private static final int MATURE_THRESHOLD = 21;
	private static final int NEW_CARDS_DISTRIBUTE = 0;
	private static final int NEW_CARDS_LAST = 1;
	private static final int NEW_CARDS_FIRST = 2;
	private static final int NEW_CARDS_RANDOM = 0;
	private static final int NEW_CARDS_OLD_FIRST = 1;
	private static final int NEW_CARDS_NEW_FIRST = 2;
	private static final int REV_CARDS_OLD_FIRST = 0;
	private static final int REV_CARDS_NEW_FIRST = 1;
	private static final int REV_CARDS_DUE_FIRST = 2;
	private static final int REV_CARDS_RANDOM = 3;
	private static final int SEARCH_TAG = 0;
	private static final int SEARCH_TYPE = 1;
	private static final int SEARCH_PHRASE = 2;
	private static final int SEARCH_FID = 3;
	private static final int DECK_VERSION = 43;
	
	private static final float initialFactor = 2.5f;
	
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
	
	float averageFactor;
	int newCardModulus;
	int newCountToday;
	
	float lastLoaded;
	
	private Stats globalStats;
	private Stats dailyStats;
	
	private void initVars() {
//		tmpMediaDir = null;
//		forceMediaDir = null;
//		lastTags = "";
		lastLoaded = (float) System.currentTimeMillis() / 1000;
//		undoEnabled = false;
//		sessionStartReps = 0;
//		sessionStartTime = 0;
//		lastSessionStart = 0;
//		newEarly = false;
//		reviewEarly = false;
	}
	
	public static Deck openDeck(String path) throws SQLException {
		Deck deck = new Deck();
		Log.i("anki", "Opening database " + path);
		AnkiDb.openDatabase(path);
		
		// Read in deck table columns
		Cursor cursor = AnkiDb.database.rawQuery(
				"SELECT *"
				+ " FROM decks"
				+ " LIMIT 1", null);
		
		if (cursor.isClosed())
			throw new SQLException();
		cursor.moveToFirst();
		
		deck.id 			 = cursor.getInt(0);
		deck.created		 = cursor.getFloat(1);
		deck.modified 		 = cursor.getFloat(2);
		deck.description	 = cursor.getString(3);
		deck.version		 = cursor.getInt(4);
		deck.currentModelId	 = cursor.getInt(5);
		deck.syncName		 = cursor.getString(6);
		deck.lastSync		 = cursor.getFloat(7);
		deck.hardIntervalMin = cursor.getFloat(8);
		deck.hardIntervalMax = cursor.getFloat(9);
		deck.midIntervalMin	 = cursor.getFloat(10);
		deck.midIntervalMax	 = cursor.getFloat(11);
		deck.easyIntervalMin = cursor.getFloat(12);
		deck.easyIntervalMax = cursor.getFloat(13);
		deck.delay0			 = cursor.getInt(14);
		deck.delay1			 = cursor.getInt(15);
		deck.delay2			 = cursor.getInt(16);
		deck.collapseTime	 = cursor.getFloat(17);
		deck.highPriority 	 = cursor.getString(18);
		deck.medPriority  	 = cursor.getString(19);
		deck.lowPriority  	 = cursor.getString(20);
		deck.suspended	 	 = cursor.getString(21);
		deck.newCardOrder	 = cursor.getInt(22);
		deck.newCardSpacing	 = cursor.getInt(23);
		deck.failedCardMax	 = cursor.getInt(24);
		deck.newCardsPerDay	 = cursor.getInt(25);
		deck.sessionRepLimit = cursor.getInt(26);
		deck.sessionTimeLimit= cursor.getInt(27);
		deck.utcOffset		 = cursor.getFloat(28);
		deck.cardCount		 = cursor.getInt(29);
		deck.factCount		 = cursor.getInt(30);
		deck.failedNowCount	 = cursor.getInt(31);
		deck.failedSoonCount = cursor.getInt(32);
		deck.revCount		 = cursor.getInt(33);
		deck.newCount		 = cursor.getInt(34);
		deck.revCardOrder	 = cursor.getInt(35);
		
		Log.i("anki", "Read " + cursor.getColumnCount() + " columns from decks table.");
		cursor.close();
		
		deck.initVars();
		
		// Ensure necessary indices are available
		deck.updateDynamicIndices();
		// Save counts to determine if we should save deck after check
		int oldCount = deck.failedSoonCount + deck.revCount + deck.newCount;
		// Update counts
		deck.rebuildQueue();
		// Unsuspend reviewed early & buried
		cursor = AnkiDb.database.rawQuery(
				"SELECT id " +
				"FROM cards " +
				"WHERE type in (0,1,2) and " +
				"isDue = 0 and " +
				"priority in (-1,-2)", null);
		
		if (cursor.isClosed())
			throw new SQLException();
		
		if (cursor.moveToFirst()) {
			int count = cursor.getCount();
			int [] ids = new int[count];
			for (int i = 0; i < count; i++) {
				ids[i] = cursor.getInt(0);
				cursor.moveToNext();
			}
			deck.updatePriorities(ids);
			deck.checkDue();
		}
		cursor.close();
		
		// Save deck to database if it has been modified
		if ((oldCount != (deck.failedSoonCount + deck.revCount + deck.newCount)) 
				|| deck.modifiedSinceSave())
			deck.commitToDB();
		
		return deck;
	}
	
	public void closeDeck() {
		AnkiDb.closeDatabase();
	}
	
	private boolean modifiedSinceSave() {
		return this.modified > this.lastLoaded;
	}
	
	private void commitToDB() {
		Log.i("anki", "Saving deck to DB...");
		ContentValues values = new ContentValues();
		values.put("id", id);
		values.put("created", created);
		values.put("modified", modified);
		values.put("description", description);
		values.put("version", version);
		values.put("currentModelId", currentModelId);
		values.put("syncName", syncName);
		values.put("lastSync", lastSync);
		values.put("hardIntervalMin", hardIntervalMin);
		values.put("hardIntervalMax", hardIntervalMax);
		values.put("midIntervalMin", midIntervalMin);
		values.put("midIntervalMax", midIntervalMax);
		values.put("easyIntervalMin", easyIntervalMin);
		values.put("easyIntervalMax", easyIntervalMax);
		values.put("delay0", delay0);
		values.put("delay1", delay1);
		values.put("delay2", delay2);
		values.put("collapseTime", collapseTime);
		values.put("highPriority", highPriority);
		values.put("medPriority", medPriority);
		values.put("lowPriority", lowPriority);
		values.put("suspended", suspended);
		values.put("newCardOrder", newCardOrder);
		values.put("newCardSpacing", newCardSpacing);
		values.put("failedCardMax", failedCardMax);
		values.put("newCardsPerDay", newCardsPerDay);
		values.put("sessionRepLimit", sessionRepLimit);
		values.put("sessionTimeLimit", sessionTimeLimit);
		values.put("utcOffset", utcOffset);
		values.put("cardCount", cardCount);
		values.put("factCount", factCount);
		values.put("failedNowCount", failedNowCount);
		values.put("failedSoonCount", failedSoonCount);
		values.put("revCount", revCount);
		values.put("newCount", newCount);
		values.put("revCardOrder", revCardOrder);
		
		AnkiDb.database.update("decks", values, "id = " + this.id, null);
	}
	
	/* Queue/cache management
	 ***********************************************************/
	
	private void rebuildCounts(boolean full) {
		Log.i("anki", "Rebuilding global and due counts...");
		// Need to check due first, so new due cards are not added later
		checkDue();
		// Global counts
		if (full) {
			cardCount = AnkiDb.queryScalar("SELECT count(id) FROM cards");
			factCount = AnkiDb.queryScalar("SELECT count(id) FROM facts");
		}
		
		// Due counts
		failedSoonCount = AnkiDb.queryScalar("SELECT count(id) FROM failedCards");
		failedNowCount = AnkiDb.queryScalar(
				"SELECT count(id) " +
				"FROM cards " +
				"WHERE type = 0 and " +
				"isDue = 1 and " +
				"combinedDue <= " +
				String.format("%f", (float) (System.currentTimeMillis() / 1000f)));
		revCount = AnkiDb.queryScalar(
				"SELECT count(id) " +
				"FROM cards " +
				"WHERE type = 1 and " +
				"priority in (1,2,3,4) and " +
				"isDue = 1");
		newCount = AnkiDb.queryScalar(
				"SELECT count(id) " +
				"FROM cards " +
				"WHERE type = 2 and " +
				"priority in (1,2,3,4) and " +
				"isDue = 1");
	}
	
	/**
	 * Mark expired cards due and update counts.
	 */
	private void checkDue() {
		Log.i("anki", "Checking due cards...");
		checkDailyStats();
		
		// Failed cards
		ContentValues val = new ContentValues(1);
		val.put("isDue", 1);
		
		failedSoonCount += AnkiDb.database.update(
				"cards", 
				val, 
				"type = 0 and " +
				"isDue = 0 and " +
				"priority in (1,2,3,4) and " +
				String.format("combinedDue <= %f", (float)
						((System.currentTimeMillis() / 1000f) + delay0)),
				null);
		
		failedNowCount = AnkiDb.queryScalar(
				"SELECT count(id) " +
				"FROM cards " +
				"WHERE type = 0 and " +
				"isDue = 1 and " +
				String.format("combinedDue <= %f", (float)
						(System.currentTimeMillis() / 1000f)));
		
		// Review
		val.clear();
		val.put("isDue", 1);
		revCount += AnkiDb.database.update(
				"cards", 
				val, 
				"type = 1 and " +
				"isDue = 0 and " +
				"priority in (1,2,3,4) and " +
				String.format("combinedDue <= %f", (float)
						(System.currentTimeMillis() / 1000f)),
				null);
		
		// New
		val.clear();
		val.put("isDue", 1);
		newCount += AnkiDb.database.update(
				"cards", 
				val, 
				"type = 2 and " +
				"isDue = 0 and " +
				"priority in (1,2,3,4) and " +
				String.format("combinedDue <= %f", (float)
						(System.currentTimeMillis() / 1000f)),
				null);
		
		newCountToday = Math.max(Math.min(
				newCount, 
				newCardsPerDay - newCardsToday()),
				0);
	}
	
	/**
	 * Update relative delays based on current time.
	 */
	private void rebuildQueue() {
		Log.i("anki", "Rebuilding query...");
		// Setup global/daily stats
		globalStats = Stats.globalStats(this);
		dailyStats = Stats.dailyStats(this);
		
		// Mark due cards and update counts
		checkDue();
		
		// Invalid card count
		// Determine new card distribution
		if (newCardSpacing == NEW_CARDS_DISTRIBUTE) {
			if (newCountToday > 0) {
				newCardModulus = (newCountToday + revCount) / newCountToday;
				// If there are cards to review, ensure modulo >= 2
				if (revCount > 0)
					newCardModulus = Math.max(2, newCardModulus);
			} else {
				newCardModulus = 0;
			}
		} else
			newCardModulus = 0;
		Log.i("anki", "newCardModulus set to " + newCardModulus);
		
		Cursor cursor = AnkiDb.database.rawQuery(
				"SELECT avg(factor) " +
				"FROM cards " +
				"WHERE type = 1", 
				null);
		if (cursor.isClosed())
			throw new SQLException();
		
		if (!cursor.moveToFirst())
			averageFactor = Deck.initialFactor;
		else
			averageFactor = cursor.getFloat(0);
		cursor.close();
		
		// Recache CSS
		//rebuildCSS();
	}
	
	/**
	 * Checks if the day has rolled over.
	 */
	private void checkDailyStats() {
		if (!Stats.genToday(this).toString().equals(dailyStats.day.toString()))
			dailyStats = Stats.dailyStats(this);
	}
	
	/* Priorities
	 ***********************************************************/
	
	private void updatePriorities(int[] cardIds) {
		updatePriorities(cardIds, null, true);
	}
	
	private void updatePriorities(int[] cardIds, String[] suspend, boolean dirty) {
		Log.i("ank", "Updating priorities...");
		// Any tags to suspend
		if (suspend != null) {
			int[] ids = tagIds(suspend);
			AnkiDb.database.execSQL(
					"UPDATE tags " +
					"SET priority = 0 " +
					"WHERE id in " +
					ids2str(ids));
		}
		
		String limit = "";
		if (cardIds.length <= 1000)
			limit = "and cardTags.cardId in " + ids2str(cardIds);
		String query = "SELECT cardTags.cardId, " +
				"CASE " +
				"WHEN min(tags.priority) = 0 THEN 0 " +
				"WHEN max(tags.priority) > 2 THEN max(tags.priority) " +
				"WHEN min(tags.priority) = 1 THEN 1 " +
				"ELSE 2 END " +
				"FROM cardTags,tags " +
				"WHERE cardTags.tagId = tags.id " +
				limit + " " +
				"GROUP BY cardTags.cardId";
		Cursor cursor = AnkiDb.database.rawQuery(query, null);
		if (!cursor.moveToFirst())
			throw new SQLException("No result for query: " + query);
		
		int len = cursor.getCount();
		int[][] cards = new int[len][2];
		for (int i = 0; i < len; i++) {
			cards[i][0] = cursor.getInt(0);
			cards[i][1] = cursor.getInt(1);
		}
		cursor.close();
		
		String extra = "";
		if (dirty)
			extra = ", modified = " + String.format("%f", 
					(float) (System.currentTimeMillis() / 1000f));
		for (int pri = 0; pri < 5; pri++) {
			int count = 0;
			for (int i = 0; i < len; i++) {
				if (cards[i][1] == pri)
					count++;
			}
			int[] cs = new int[count];
			int j = 0;
			for (int i = 0; i < len; i++) {
				if (cards[i][1] == pri) {
					cs[j] = cards[i][0];
					j++;
				}
			}
			// Catch review early & buried but not suspended cards
			AnkiDb.database.execSQL(
					"UPDATE cards " +
					"SET priority = " + pri + extra +
					"WHERE id in " + ids2str(cs) + " and " +
					"priority != " + pri + " and " +
					"priority >= -2");
		}
		
		ContentValues val = new ContentValues(1);
		val.put("isDue", 0);
		int cnt = AnkiDb.database.update(
				"cards", 
				val, 
				"type in (0,1,2) and " +
				"priority = 0 and " +
				"isDue = 1", 
				null);
		if (cnt > 0)
			rebuildCounts(false);
	}
	
	/* Counts related to due cards
	 ***********************************************************/
	
	private int newCardsToday() {
		return (dailyStats.newEase0 +
				dailyStats.newEase1 +
				dailyStats.newEase2 +
				dailyStats.newEase3 +
				dailyStats.newEase4);
	}
	
	/* Dynamic indices
	 ***********************************************************/
	
	private void updateDynamicIndices() {
		Log.i("anki", "Updating indices...");
		HashMap<String,String> indices = new HashMap<String,String>();
		indices.put("intervalDesc", "(type, isDue, priority desc, interval desc)");
		indices.put("intervalAsc", "(type, isDue, priority desc, interval)");
		indices.put("randomOrder", "(type, isDue, priority desc, factId, ordinal)");
		indices.put("dueAsc", "(type, isDue, priority desc, due)");
		indices.put("dueDesc", "(type, isDue, priority desc, due desc)");
		
		ArrayList<String> required = new ArrayList<String>();
		if (revCardOrder == REV_CARDS_OLD_FIRST)
			required.add("intervalDesc");
		if (revCardOrder == REV_CARDS_NEW_FIRST)
			required.add("intervalAsc");
		if (revCardOrder == REV_CARDS_RANDOM)
			required.add("randomOrder");
		if (revCardOrder == REV_CARDS_DUE_FIRST ||
				newCardOrder == NEW_CARDS_OLD_FIRST ||
				newCardOrder == NEW_CARDS_RANDOM)
			required.add("dueAsc");
		if (newCardOrder == NEW_CARDS_NEW_FIRST)
			required.add("dueDesc");
		
		Set<Entry<String,String>> entries = indices.entrySet();
		Iterator<Entry<String,String>> iter = entries.iterator();
		while (iter.hasNext()) {
			Entry<String, String> entry = iter.next();
			if (required.contains(entry.getKey()))
				AnkiDb.database.execSQL(
						"CREATE INDEX IF NOT EXISTS " +
						"ix_cards_" +
						entry.getKey() +
						" ON cards " +
						entry.getValue());
			else
				AnkiDb.database.execSQL(
						"DROP INDEX IF EXISTS " +
						"ix_cards_" +
						entry.getKey());
		}
	}
	
	/* Utility functions (might be better in a separate classes)
	 ***********************************************************/
	
	/**
	 * Returns a SQL string from an array of integers.
	 * @param ids The array of integers to include in the list.
	 * @return An SQL compatible string in the format (ids[0],ids[1],..).
	 */
	private static String ids2str(int[] ids) {
		String str = "(";
		int len = ids.length;
		for (int i = 0; i < len; i++ ) {
			if (i == (len - 1))
				str += ids[i];
			else
				str += ids[i] + ",";
		}
		str += ")";
		return str;
	}
	
	/**
	 * Gets the IDs of the specified tags.
	 * @param tags An array of the tags to get IDs for.
	 * @return An array of IDs of the tags.
	 */
	private static int[] tagIds(String[] tags) {
		// TODO: Finish porting this method from tags.py.
		return null;
	}
}
