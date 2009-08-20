package com.ichi2.anki;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class AnkiDb {
	
	static public SQLiteDatabase mDb;
	static public final int DB_OPEN_OPTS =
		SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS;
	
	static public void openDatabase(String filename) throws SQLException {
		if (mDb != null) {
			mDb.close();
		}
        mDb = SQLiteDatabase.openDatabase(filename, null, DB_OPEN_OPTS);		
	}
	
	static public class Card {
		
		public String id;
		public String question, answer;
		public double interval;
		
		static final public String TABLE = "cards";
		static final public String COLUMNS = "id,interval,question,answer";
		
		static public Card smallestIntervalCard() throws SQLException {
			Card card = oneFromCursor(
					AnkiDb.mDb.rawQuery("select " + COLUMNS + " from " + TABLE + " order by /*interval*/ random() limit 1", null)
					);
			Log.d("db", "Selected card id " + card.id + " with interval " + card.interval);
			return card;
		}
		
		static private Card oneFromCursor(Cursor cursor) {
			if (cursor.isClosed()) {
				throw new SQLException();
			}
			Log.d("db", "Nb of results:" + cursor.getCount());
			cursor.moveToFirst();
			return instanceFromCursor(cursor);
		}
		
		static Card instanceFromCursor(Cursor cursor) {
			Card card = new Card();
			String s = cursor.getString(0);
			Log.d("db", s);
			card.id = s;//cursor.getInt(0);
			card.interval = cursor.getDouble(1);
			card.question = cursor.getString(2);
			card.answer = cursor.getString(3);
			Log.d("db", "id=" + card.id + ", interval=" + card.interval);
			return card;
		}
		
		// Space this card because it has been successfully remembered.
		public void space() {
			double newInterval = 1;
			if (interval != 0)
				newInterval = 2*interval; // Very basic spaced repetition.
			String query = "UPDATE " + TABLE + " SET interval=" + newInterval + " where id='" + id + "'";
			Log.d("db", query);
			Cursor cursor = AnkiDb.mDb.rawQuery(query, null);
			Log.d("db", "cursor=" + cursor.toString());
			// Just output the interval to be sure the update worked.
			Card card = oneFromCursor(
					AnkiDb.mDb.rawQuery("select " + COLUMNS + " from " + TABLE + " where id='" + id + "'", null)
					);
			Log.d("db", "Updated card id " + card.id + " with interval " + card.interval);
		}
		
		// Reset this card because it has not been successfully remembered.
		public void reset() {
			String query = "UPDATE " + TABLE + " SET interval=0.1 where id=" + id;
			Log.d("db", query);
			AnkiDb.mDb.rawQuery(query, null);
			// Just debug the interval to be sure the update worked.
			Card card = oneFromCursor(
					AnkiDb.mDb.rawQuery("select " + COLUMNS + " from " + TABLE + " where id='" + id + "'", null)
					);
			Log.d("db", "Updated card id " + card.id + " with interval " + card.interval);
		}
	}
}
