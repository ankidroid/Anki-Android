package com.ichi2.anki;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class AnkiDb {
	
	static public SQLiteDatabase mDb;
	static public final int DB_OPEN_OPTS =
		SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS;
	
	static public void openDatabase(String filename) throws SQLException {
		if (mDb != null) {
			mDb.close();
		}
        mDb = SQLiteDatabase.openDatabase(filename, null, DB_OPEN_OPTS);		
	}
	
	static public abstract class AnkiModel {

		//abstract public AnkiModel instanceFromCursor(Cursor cursor);
	}
	
	static public class Card extends AnkiModel {
		
		public Integer id;
		public String question, answer;
		
		static final public String TABLE = "cards";
		static final public String COLUMNS = "id,question,answer";
		
		static public Card byRand() throws SQLException {
			return oneFromCursor(
					AnkiDb.mDb.rawQuery("select " + COLUMNS + " from " + TABLE + " order by random() limit 1", null)
					);
		}
		
		static public Card byId(Integer id) throws SQLException {
			String[] selArgs = new String[1];
			selArgs[0] = id.toString();
			Cursor cursor = AnkiDb.mDb.rawQuery(
					"select " + COLUMNS + " from " + TABLE + " where id=?",
					selArgs);
			return oneFromCursor(cursor);
			/*
			cursor.moveToNext();
			if (cursor.isClosed()) {
				throw new SQLException();
			}
			Card card = instanceFromCursor(cursor);
			cursor.close();
			return card;
			*/
		}
		
		static private Card oneFromCursor(Cursor cursor) {
			if (cursor.isClosed()) {
				throw new SQLException();
			}
			cursor.moveToFirst();
			return instanceFromCursor(cursor);
		}
		
		static Card instanceFromCursor(Cursor cursor) {
			Card card = new Card();
			card.id = cursor.getInt(0);
			card.question = cursor.getString(1);
			card.answer = cursor.getString(2);
			return card;
		}
		
	}
	
	static public class FactModel extends AnkiModel {
		
		public String tableName() { return "facts"; }
		public String columnMatch() { return "*"; }
	}
	
	static public class DeckModel extends AnkiModel {
		
		public String tableName() { return "decks"; }
		public String columnMatch() { return "*"; }
	}
}
