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
import android.database.Cursor;
import android.util.Log;

import java.lang.reflect.Field;
import java.sql.Date;
import java.util.Calendar;

/**
 * Deck statistics.
 */
public class Stats {

    /**
     * Tag for logging messages
     */
    private static String TAG = "AnkiDroid";

    public static final int STATS_LIFE = 0;

    public static final int STATS_DAY = 1;

    // BEGIN: SQL table columns
    long id;

    int type;

    Date day;

    int reps;

    double averageTime;

    double reviewTime;

    // Next two columns no longer used
    double distractedTime;

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

    Deck deck;


    public Stats(Deck deck) {
        this.deck = deck;
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


    public void fromDB(long id) {
        Cursor cursor = null;

        try {
            Log.i(TAG, "Reading stats from DB...");
            cursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery("SELECT * " + "FROM stats "
                    + "WHERE id = " + String.valueOf(id), null);

            if (!cursor.moveToFirst()) {
                return;
            }

            this.id = cursor.getLong(0);
            type = cursor.getInt(1);
            day = Date.valueOf(cursor.getString(2));
            reps = cursor.getInt(3);
            averageTime = cursor.getDouble(4);
            reviewTime = cursor.getDouble(5);
            distractedTime = cursor.getDouble(6);
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
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public void create(int type, Date day) {
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
        id = AnkiDatabaseManager.getDatabase(deck.deckPath).database.insert("stats", null, values);
    }


    public void toDB() {
        ContentValues values = new ContentValues();
        values.put("type", type);
        values.put("day", day.toString());
        values.put("reps", reps);
        values.put("averageTime", averageTime);
        values.put("reviewTime", reviewTime);
        values.put("newEase0", newEase0);
        values.put("newEase1", newEase1);
        values.put("newEase2", newEase2);
        values.put("newEase3", newEase3);
        values.put("newEase4", newEase4);
        values.put("youngEase0", youngEase0);
        values.put("youngEase1", youngEase1);
        values.put("youngEase2", youngEase2);
        values.put("youngEase3", youngEase3);
        values.put("youngEase4", youngEase4);
        values.put("matureEase0", matureEase0);
        values.put("matureEase1", matureEase1);
        values.put("matureEase2", matureEase2);
        values.put("matureEase3", matureEase3);
        values.put("matureEase4", matureEase4);

        AnkiDatabaseManager.getDatabase(deck.deckPath).database.update("stats", values, "id = " + id, null);
    }


    /**
     * Returns the effective date of the present moment
     * If the time is prior the cut-off time (9:00am by default as of 11/02/10) return yesterday,
     * otherwise today
     * Note that the Date class is java.sql.Date whose constructor sets hours, minutes etc to zero
     *
     * @param deck A deck whose cut-off time we are going to use to determine today or yesterday
     * @return The date (with time set to 00:00:00) that corresponds to today in Anki terms
     */
    public static Date genToday(Deck deck) {
        // The result is not adjusted for timezone anymore, following libanki model
        // Timezone adjustment happens explicitly in Deck.updateCutoff(), but not in Deck.checkDailyStats()
        Date today = new Date(System.currentTimeMillis() - (long) deck.utcOffset * 1000l);
        return today;
    }


    public static void updateAllStats(Stats global, Stats daily, Card card, int ease, String oldState) {
        updateStats(global, card, ease, oldState);
        updateStats(daily, card, ease, oldState);
    }


    public static void updateStats(Stats stats, Card card, int ease, String oldState) {
        stats.reps += 1;
        double delay = card.totalTime();
        if (delay >= 60) {
            stats.reviewTime += 60;
        } else {
            stats.reviewTime += delay;
            stats.averageTime = (stats.reviewTime / stats.reps);
        }
        // update eases
        String attr = oldState + String.format("Ease%d", ease);
        try {
            Field f = stats.getClass().getDeclaredField(attr);
            f.setInt(stats, f.getInt(stats) + 1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        stats.toDB();
    }


    public static Stats globalStats(Deck deck) {
        Log.i(TAG, "Getting global stats...");
        int type = STATS_LIFE;
        Date today = genToday(deck);
        Cursor cursor = null;
        Stats stats = null;

        try {
            cursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery("SELECT id " + "FROM stats "
                    + "WHERE type = " + String.valueOf(type), null);

            if (cursor.moveToFirst()) {
                stats = new Stats(deck);
                stats.fromDB(cursor.getLong(0));
                return stats;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        stats = new Stats(deck);
        stats.create(type, today);
        stats.type = type;
        return stats;
    }


    public static Stats dailyStats(Deck deck) {
        Log.i(TAG, "Getting daily stats...");
        int type = STATS_DAY;
        Date today = genToday(deck);
        Stats stats = null;
        Cursor cursor = null;

        try {
            Log.i(TAG, "Trying to get stats for " + today.toString());
            cursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery("SELECT id " + "FROM stats "
                    + "WHERE type = " + String.valueOf(type) + " and day = \"" + today.toString() + "\"", null);

            if (cursor.moveToFirst()) {
                stats = new Stats(deck);
                stats.fromDB(cursor.getLong(0));
                return stats;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        stats = new Stats(deck);
        stats.create(type, today);
        stats.type = type;
        return stats;
    }
}
