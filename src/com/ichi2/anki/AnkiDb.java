package com.ichi2.anki;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Database layer for Ankidroid. Can read the native Anki format through
 * Android's SQLite driver.
 * 
 * @author Andrew Dubya, Nicolas Raoul
 */
public class AnkiDb
{

	/**
	 * The deck, which is actually an SQLite database.
	 */
	static public SQLiteDatabase database;

	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "Ankidroid";
	
	/**
	 * Open a database connection to an ".anki" SQLite file.
	 */
	static public void openDatabase(String ankiFilename) throws SQLException
	{

		if (database != null)
		{
			database.close();
		}

		database = SQLiteDatabase.openDatabase(ankiFilename, null, SQLiteDatabase.OPEN_READWRITE
		        | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
	}

	/**
	 * Closes a previously opened database connection.
	 */
	static public void closeDatabase()
	{
		if (database != null)
		{
			database.close();
		}
	}

	/**
	 * Convenience method for querying the database for a single integer result.
	 * 
	 * @param query
	 *            The raw SQL query to use.
	 * @return The integer result of the query.
	 */
	static public int queryScalar(String query) throws SQLException
	{
		Cursor cursor = AnkiDb.database.rawQuery(query, null);
		if (!cursor.moveToFirst())
			throw new SQLException("No result for query: " + query);

		int scalar = cursor.getInt(0);
		cursor.close();

		return scalar;
	}

	/**
	 * A card is Anki's question-answer entity.
	 * 
	 * @see "http://www.ichi2.net/anki/wiki/AddItems"
	 */
	static public class Card
	{

		/**
		 * Identifier of the card, can be negative.
		 */
		public long id;

		/**
		 * Question and answer. Answer contains the reading.
		 */
		public String question, answer;

		/**
		 * Interval is right now used as a ranking in Ankidroid, which is quite
		 * different from Anki where it is used as an interval.
		 */
		public double interval;

		/**
		 * Get a random card from the deck.
		 */
		static public Card randomCard() throws SQLException
		{
			Card card = oneFromCursor(AnkiDb.database.rawQuery("SELECT id,interval,question,answer" + " FROM cards"
			        + " ORDER BY random()" + " LIMIT 1", null));
			Log.d(TAG, "Selected card id " + card.id + " with interval " + card.interval);
			return card;
		}

		/**
		 * From the deck, get the card with the smallest interval.
		 */
		static public Card smallestIntervalCard() throws SQLException
		{
			Card card = oneFromCursor(AnkiDb.database.rawQuery("SELECT id,interval,question,answer" + " FROM cards"
			        + " WHERE priority > 0" + " ORDER BY interval" + " LIMIT 1", null));
			Log.i(TAG, "Selected card id " + card.id + " with interval " + card.interval);
			return card;
		}

		/**
		 * Return one card starting from the given cursor.
		 */
		static private Card oneFromCursor(Cursor cursor)
		{
			if (cursor.isClosed())
			{
				throw new SQLException();
			}
			cursor.moveToFirst();
			Card card = new Card();
			card.id = cursor.getLong(0);
			card.interval = cursor.getDouble(1);
			card.question = cursor.getString(2);
			card.answer = cursor.getString(3);
			return card;
		}

		/**
		 * Space this card because it has been successfully remembered.
		 */
		public void space()
		{
			double newInterval = 1;
			if (interval != 0)
				newInterval = 2 * interval; // Very basic spaced repetition.
			ContentValues values = new ContentValues();
			values.put("interval", newInterval);
			AnkiDb.database.update("cards", values, "id='" + id + "'", null);
			Log.d(TAG, "Spaced card to interval " + newInterval);
		}

		/**
		 * Reset this card because it has not been successfully remembered.
		 */
		public void reset()
		{
			// This lame implementation does not actually reset the interval, it
			// grows it a little bit.
			// This way, a failed card will not reappear immediately, but as
			// soon as new cards and cards with similar interval are answered.
			// It is not anymore an "interval" in the sense of the desktop Anki
			// software.
			// Hopefully, someone will implement real SRS based on the desktop
			// Anki software.
			double newInterval = 0.3;
			if (interval != 0)
				newInterval = 1.01 * interval;
			ContentValues values = new ContentValues();
			values.put("interval", newInterval);
			AnkiDb.database.update("cards", values, "id='" + id + "'", null);
			Log.d(TAG, "Reset card with interval " + newInterval);
		}
	}
}