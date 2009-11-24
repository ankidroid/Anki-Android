/****************************************************************************************
* Copyright (c) 2009 Name <email@email.com>                                            *
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

import java.sql.Date;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

public class Stats
{
	
	/**
	 * Tag for logging messages
	 */
	private static String TAG = "Ankidroid";
	
	private static final int STATS_LIFE = 0;

	private static final int STATS_DAY = 1;

	// BEGIN: SQL table columns
	int id;

	int type;

	Date day;

	int reps;

	float averageTime;

	float reviewTime;

	// Next two columns no longer used
	float distractedTime;

	int distractedReps;

	int newEase0;

	int newEase1;

	int newEase2;

	int newEase3;

	int newEase4;

	int youngEase0;

	int youngEase1;

	int youngEase2;

	int youngEase3;

	int youngEase4;

	int matureEase0;

	int matureEase1;

	int matureEase2;

	int matureEase3;

	int matureEase4;

	// END: SQL table columns

	public Stats()
	{
		day = null;
		reps = 0;
		averageTime = 0;
		reviewTime = 0;
		distractedTime = 0;
		distractedReps = 0;
		newEase0 = 0;
		newEase1 = 0;
		newEase2 = 0;
		newEase3 = 0;
		newEase4 = 0;
		youngEase0 = 0;
		youngEase1 = 0;
		youngEase2 = 0;
		youngEase3 = 0;
		matureEase0 = 0;
		matureEase1 = 0;
		matureEase2 = 0;
		matureEase3 = 0;
		matureEase4 = 0;
	}

	private void fromDB(int id)
	{
		Log.i(TAG, "Reading stats from DB...");
		Cursor cursor = AnkiDb.database
		        .rawQuery("SELECT * " + "FROM stats " + "WHERE id = " + String.valueOf(id), null);
		if (cursor.isClosed())
			throw new SQLException();
		cursor.moveToFirst();

		this.id = cursor.getInt(0);
		type = cursor.getInt(1);
		day = Date.valueOf(cursor.getString(2));
		reps = cursor.getInt(3);
		averageTime = cursor.getFloat(4);
		reviewTime = cursor.getFloat(5);
		distractedTime = cursor.getFloat(6);
		distractedReps = cursor.getInt(7);
		newEase0 = cursor.getInt(8);
		newEase1 = cursor.getInt(9);
		newEase2 = cursor.getInt(10);
		newEase3 = cursor.getInt(11);
		newEase4 = cursor.getInt(12);
		youngEase0 = cursor.getInt(13);
		youngEase1 = cursor.getInt(14);
		youngEase2 = cursor.getInt(15);
		youngEase3 = cursor.getInt(16);
		youngEase4 = cursor.getInt(17);
		matureEase0 = cursor.getInt(18);
		matureEase1 = cursor.getInt(19);
		matureEase2 = cursor.getInt(20);
		matureEase3 = cursor.getInt(21);
		matureEase4 = cursor.getInt(22);

		cursor.close();
	}

	private void create(int type, Date day)
	{
		Log.i(TAG, "Creating new stats for " + day.toString() + "...");
		this.type = type;
		this.day = day;

		ContentValues values = new ContentValues();
		values.put("type", type);
		values.put("day", day.toString());
		values.put("reps", 0);
		values.put("averageTime", 0);
		values.put("reviewTime", 0);
		values.put("distractedTime", 0);
		values.put("distractedReps", 0);
		values.put("newEase0", 0);
		values.put("newEase1", 0);
		values.put("newEase2", 0);
		values.put("newEase3", 0);
		values.put("newEase4", 0);
		values.put("youngEase0", 0);
		values.put("youngEase1", 0);
		values.put("youngEase2", 0);
		values.put("youngEase3", 0);
		values.put("youngEase4", 0);
		values.put("matureEase0", 0);
		values.put("matureEase1", 0);
		values.put("matureEase2", 0);
		values.put("matureEase3", 0);
		values.put("matureEase4", 0);
		this.id = (int) AnkiDb.database.insert("stats", null, values);
	}

	public static Date genToday(Deck deck)
	{
		return new Date((long) (System.currentTimeMillis() - deck.utcOffset * 1000));
	}

	public static Stats globalStats(Deck deck) throws SQLException
	{
		Log.i(TAG, "Getting global stats...");
		int type = STATS_LIFE;
		Date today = genToday(deck);

		Cursor cursor = AnkiDb.database.rawQuery("SELECT id " + "FROM stats " + "WHERE type = " + String.valueOf(type),
		        null);
		if (cursor.isClosed())
			throw new SQLException();

		Stats stats = new Stats();
		if (cursor.moveToFirst())
		{
			stats.fromDB(cursor.getInt(0));
			cursor.close();
			return stats;
		} else
			stats.create(type, today);
		cursor.close();
		stats.type = type;
		return stats;
	}

	public static Stats dailyStats(Deck deck) throws SQLException
	{
		Log.i(TAG, "Getting daily stats...");
		int type = STATS_DAY;
		Date today = genToday(deck);

		Log.i(TAG, "Trying to get stats for " + today.toString());
		Cursor cursor = AnkiDb.database.rawQuery("SELECT id " + "FROM stats " + "WHERE type = " + String.valueOf(type)
		        + " and day = \"" + today.toString() + "\"", null);
		if (cursor.isClosed())
			throw new SQLException();

		Stats stats = new Stats();
		if (cursor.moveToFirst())
		{
			stats.fromDB(cursor.getInt(0));
			cursor.close();
			return stats;
		} else
			stats.create(type, today);
		cursor.close();
		stats.type = type;
		return stats;
	}
}
