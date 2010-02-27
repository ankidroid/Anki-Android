/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
* Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
* Copyright (c) 2009 Andrew <andrewdubya@gmail.com>                                    *
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

import java.util.ArrayList;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Database layer for AnkiDroid.
 * Can read the native Anki format through Android's SQLite driver.
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
	private static final String TAG = "AnkiDroid";

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
		Log.i(TAG, "AnkiDb - openDatabase, database " + ankiFilename + " opened = " + database.isOpen());
	}

	/**
	 * Closes a previously opened database connection.
	 */
	static public void closeDatabase()
	{
		if (database != null)
		{
			database.close();
			Log.i(TAG, "AnkiDb - closeDatabase, database " + database.getPath() + " closed = " + !database.isOpen());
			database = null;
		}
	}

	/**
	 * Convenience method for querying the database for a single integer result.
	 *
	 * @param query
	 *            The raw SQL query to use.
	 * @return The integer result of the query.
	 */
	static public long queryScalar(String query) throws SQLException
	{
		Cursor cursor = null;
		long scalar;
		try {
			cursor = AnkiDb.database.rawQuery(query, null);
			if (!cursor.moveToFirst())
				throw new SQLException("No result for query: " + query);
	
			scalar = cursor.getLong(0);
		} finally {
			if (cursor != null) cursor.close();
		}

		return scalar;
	}
	
	/**
	 * Convenience method for querying the database for an entire column. The column
	 * will be returned as an ArrayList of the specified class.
	 * 
	 * See Deck.initUndo() for a usage example.
	 * 
	 * @param type The class of the column's data type. Example: int.class, String.class.
	 * @param query The SQL query statement.
	 * @param column The column id in the result set to return.
	 * @return An ArrayList with the contents of the specified column.
	 */
	static public <T> ArrayList<T> queryColumn(Class<T> type, String query, int column) {
		ArrayList<T> results = new ArrayList<T>();
		Cursor cursor = null;
		
		try {
			cursor = AnkiDb.database.rawQuery(query, null);
			cursor.moveToFirst();
			String methodName = getCursorMethodName(type.getSimpleName());
			do {
				// The magical line. Almost as illegible as python code ;)
				results.add(type.cast(Cursor.class.getMethod(methodName, int.class).invoke(cursor, column)));
			} while (cursor.moveToNext());
		} catch (Exception e) {
			Log.e(TAG, "queryColumn: Got Exception: " + e.getMessage());
			return null;
		} finally {
			if (cursor != null) cursor.close();
		}
		
		return results;
	}
	
	/**
	 * Mapping of Java type names to the corresponding Cursor.get method.
	 * 
	 * @param typeName The simple name of the type's class. Example: String.class.getSimpleName().
	 * @return The name of the Cursor method to be called.
	 */
	static private String getCursorMethodName(String typeName) {
		if (typeName.equals("String"))
			return "getString";
		else if (typeName.equals("Long"))
			return "getLong";
		else if (typeName.equals("Integer"))
			return "getInt";
		else if (typeName.equals("Float"))
			return "getFloat";
		else if (typeName.equals("Double"))
			return "getDouble";
		else
			return null;
	}
}