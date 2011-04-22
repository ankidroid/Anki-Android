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

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.sql.Date;

/**
 * Deck statistics.
 */
public class Stats {

    public static final int STATS_LIFE = 0;
    public static final int STATS_DAY = 1;

    public static final int STATSARRAY_SIZE = 7;
    public static final int STATSARRAY_DAILY_AVERAGE_TIME = 0;
    public static final int STATSARRAY_GLOBAL_AVERAGE_TIME = 1;
    public static final int STATSARRAY_GLOBAL_YOUNG_NO_SHARE = 2;
    public static final int STATSARRAY_DAILY_REPS = 3;
    public static final int STATSARRAY_DAILY_NO = 4;
    public static final int STATSARRAY_GLOBAL_MATURE_YES = 5;
    public static final int STATSARRAY_GLOBAL_MATURE_NO = 6;
    
    public static final int TYPE_ETA = 0;
    public static final int TYPE_YES_SHARES = 1;
    
    // BEGIN: SQL table columns
    private long mId;
    private int mType;
    private Date mDay;
    private int mReps;
    private double mAverageTime;
    private double mReviewTime;
    // Next two columns no longer used
    private double mDistractedTime;
    private int mDistractedReps;
    private int mNewEase0;
    private int mNewEase1;
    private int mNewEase2;
    private int mNewEase3;
    private int mNewEase4;
    private int mYoungEase0;
    private int mYoungEase1;
    private int mYoungEase2;
    private int mYoungEase3;
    private int mYoungEase4;
    private int mMatureEase0;
    private int mMatureEase1;
    private int mMatureEase2;
    private int mMatureEase3;
    private int mMatureEase4;
    // END: SQL table columns

    private Deck mDeck;


    public Stats(Deck deck) {
        mDeck = deck;
        mDay = null;
        mReps = 0;
        mAverageTime = 0;
        mReviewTime = 0;
        mDistractedTime = 0;
        mDistractedReps = 0;
        mNewEase0 = 0;
        mNewEase1 = 0;
        mNewEase2 = 0;
        mNewEase3 = 0;
        mNewEase4 = 0;
        mYoungEase0 = 0;
        mYoungEase1 = 0;
        mYoungEase2 = 0;
        mYoungEase3 = 0;
        mMatureEase0 = 0;
        mMatureEase1 = 0;
        mMatureEase2 = 0;
        mMatureEase3 = 0;
        mMatureEase4 = 0;
    }


    public void fromDB(long id) {
        Cursor cursor = null;

        try {
            // Log.i(AnkiDroidApp.TAG, "Reading stats from DB...");
            cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                    "SELECT * " + "FROM stats WHERE id = " + String.valueOf(id), null);

            if (!cursor.moveToFirst()) {
                return;
            }

            mId = cursor.getLong(0);
            mType = cursor.getInt(1);
            mDay = Date.valueOf(cursor.getString(2));
            mReps = cursor.getInt(3);
            mAverageTime = cursor.getDouble(4);
            mReviewTime = cursor.getDouble(5);
            mDistractedTime = cursor.getDouble(6);
            mDistractedReps = cursor.getInt(7);
            mNewEase0 = cursor.getInt(8);
            mNewEase1 = cursor.getInt(9);
            mNewEase2 = cursor.getInt(10);
            mNewEase3 = cursor.getInt(11);
            mNewEase4 = cursor.getInt(12);
            mYoungEase0 = cursor.getInt(13);
            mYoungEase1 = cursor.getInt(14);
            mYoungEase2 = cursor.getInt(15);
            mYoungEase3 = cursor.getInt(16);
            mYoungEase4 = cursor.getInt(17);
            mMatureEase0 = cursor.getInt(18);
            mMatureEase1 = cursor.getInt(19);
            mMatureEase2 = cursor.getInt(20);
            mMatureEase3 = cursor.getInt(21);
            mMatureEase4 = cursor.getInt(22);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public void create(int type, Date day) {
        // Log.i(AnkiDroidApp.TAG, "Creating new stats for " + day.toString() + "...");
        mType = type;
        mDay = day;

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
        mId = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().insert("stats", null, values);
    }


    public void toDB() {
        ContentValues values = new ContentValues();
        values.put("type", mType);
        values.put("day", mDay.toString());
        values.put("reps", mReps);
        values.put("averageTime", mAverageTime);
        values.put("reviewTime", mReviewTime);
        values.put("newEase0", mNewEase0);
        values.put("newEase1", mNewEase1);
        values.put("newEase2", mNewEase2);
        values.put("newEase3", mNewEase3);
        values.put("newEase4", mNewEase4);
        values.put("youngEase0", mYoungEase0);
        values.put("youngEase1", mYoungEase1);
        values.put("youngEase2", mYoungEase2);
        values.put("youngEase3", mYoungEase3);
        values.put("youngEase4", mYoungEase4);
        values.put("matureEase0", mMatureEase0);
        values.put("matureEase1", mMatureEase1);
        values.put("matureEase2", mMatureEase2);
        values.put("matureEase3", mMatureEase3);
        values.put("matureEase4", mMatureEase4);

        AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().update("stats", values, "id = " + mId, null);
    }


    public static void updateAllStats(Stats global, Stats daily, Card card, int ease, String oldState) {
        updateStats(global, card, ease, oldState);
        updateStats(daily, card, ease, oldState);
    }


    public static void updateStats(Stats stats, Card card, int ease, String oldState) {
        char[] newState = oldState.toCharArray();
        stats.mReps += 1;
        double delay = card.totalTime();
        if (delay >= 60) {
            stats.mReviewTime += 60;
        } else {
            stats.mReviewTime += delay;
            stats.mAverageTime = (stats.mReviewTime / stats.mReps);
        }
        // update eases
        // We want attr to be of the form mYoungEase3
        newState[0] = Character.toUpperCase(newState[0]);
        String attr = "m" + String.valueOf(newState) + String.format("Ease%d", ease);
        try {
            Field f = stats.getClass().getDeclaredField(attr);
            f.setInt(stats, f.getInt(stats) + 1);
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG, "Failed to update " + attr + " : " + e.getMessage());
        }

        stats.toDB();
    }


    public JSONObject bundleJson() {
        JSONObject bundledStat = new JSONObject();

        try {
            bundledStat.put("type", mType);
            bundledStat.put("day", Utils.dateToOrdinal(mDay));
            bundledStat.put("reps", mReps);
            bundledStat.put("averageTime", mAverageTime);
            bundledStat.put("reviewTime", mReviewTime);
            bundledStat.put("distractedTime", mDistractedTime);
            bundledStat.put("distractedReps", mDistractedReps);
            bundledStat.put("newEase0", mNewEase0);
            bundledStat.put("newEase1", mNewEase1);
            bundledStat.put("newEase2", mNewEase2);
            bundledStat.put("newEase3", mNewEase3);
            bundledStat.put("newEase4", mNewEase4);
            bundledStat.put("youngEase0", mYoungEase0);
            bundledStat.put("youngEase1", mYoungEase1);
            bundledStat.put("youngEase2", mYoungEase2);
            bundledStat.put("youngEase3", mYoungEase3);
            bundledStat.put("youngEase4", mYoungEase4);
            bundledStat.put("matureEase0", mMatureEase0);
            bundledStat.put("matureEase1", mMatureEase1);
            bundledStat.put("matureEase2", mMatureEase2);
            bundledStat.put("matureEase3", mMatureEase3);
            bundledStat.put("matureEase4", mMatureEase4);

        } catch (JSONException e) {
            // Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
        }

        return bundledStat;
    }


    public void updateFromJson(JSONObject remoteStat) {
        try {
            mAverageTime = remoteStat.getDouble("averageTime");
            mDay = Utils.ordinalToDate(remoteStat.getInt("day"));
            mDistractedReps = remoteStat.getInt("distractedReps");
            mDistractedTime = remoteStat.getDouble("distractedTime");
            mMatureEase0 = remoteStat.getInt("matureEase0");
            mMatureEase1 = remoteStat.getInt("matureEase1");
            mMatureEase2 = remoteStat.getInt("matureEase2");
            mMatureEase3 = remoteStat.getInt("matureEase3");
            mMatureEase4 = remoteStat.getInt("matureEase4");
            mNewEase0 = remoteStat.getInt("newEase0");
            mNewEase1 = remoteStat.getInt("newEase1");
            mNewEase2 = remoteStat.getInt("newEase2");
            mNewEase3 = remoteStat.getInt("newEase3");
            mNewEase4 = remoteStat.getInt("newEase4");
            mReps = remoteStat.getInt("reps");
            mReviewTime = remoteStat.getDouble("reviewTime");
            mType = remoteStat.getInt("type");
            mYoungEase0 = remoteStat.getInt("youngEase0");
            mYoungEase1 = remoteStat.getInt("youngEase1");
            mYoungEase2 = remoteStat.getInt("youngEase2");
            mYoungEase3 = remoteStat.getInt("youngEase3");
            mYoungEase4 = remoteStat.getInt("youngEase4");

            toDB();
        } catch (JSONException e) {
            // Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
        }
    }


    public static Stats globalStats(Deck deck) {
        // Log.i(AnkiDroidApp.TAG, "Getting global stats...");
        int type = STATS_LIFE;
        Date today = Utils.genToday(deck.getUtcOffset());
        Cursor cursor = null;
        Stats stats = null;

        try {
            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(
                    "SELECT id " + "FROM stats WHERE type = " + String.valueOf(type), null);

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
        stats.mType = type;
        return stats;
    }


    public static Stats dailyStats(Deck deck) {
        // Log.i(AnkiDroidApp.TAG, "Getting daily stats...");
        int type = STATS_DAY;
        Date today = Utils.genToday(deck.getUtcOffset());
        Stats stats = null;
        Cursor cursor = null;

        try {
            // Log.i(AnkiDroidApp.TAG, "Trying to get stats for " + today.toString());
            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(
                    "SELECT id " + "FROM stats "
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
        stats.mType = type;
        return stats;
    }


    public static double[] getStats(Deck deck, Stats globalStats, Stats dailyStats) {
    	double[] stats = new double[STATSARRAY_SIZE];
    	stats[STATSARRAY_DAILY_AVERAGE_TIME] = dailyStats.mAverageTime;
    	stats[STATSARRAY_GLOBAL_AVERAGE_TIME] = globalStats.mAverageTime;
    	stats[STATSARRAY_DAILY_REPS] = dailyStats.mReps;
    	double globalYoungNo = globalStats.mYoungEase0 + globalStats.mYoungEase1;
    	double globalYoungTotal = globalYoungNo + globalStats.mYoungEase2 + globalStats.mYoungEase3 + globalStats.mYoungEase4;
    	if (globalYoungTotal != 0) {
        	stats[STATSARRAY_GLOBAL_YOUNG_NO_SHARE] = globalYoungNo / globalYoungTotal * 100;
    	} else {
        	stats[STATSARRAY_GLOBAL_YOUNG_NO_SHARE] = 0;
    	}
    	stats[STATSARRAY_DAILY_NO] = dailyStats.mNewEase0 + dailyStats.mNewEase1 + dailyStats.mYoungEase0 + dailyStats.mYoungEase1 + dailyStats.mMatureEase0 + dailyStats.mMatureEase1;
    	stats[STATSARRAY_GLOBAL_MATURE_YES] = globalStats.mMatureEase2 + globalStats.mMatureEase3 + globalStats.mMatureEase4;
    	stats[STATSARRAY_GLOBAL_MATURE_NO] = globalStats.mMatureEase0 + globalStats.mMatureEase1;
    	return stats;
    }
    
    /**
     * @return the reps
     */
    public int getReps() {
        return mReps;
    }


    /**
     * @return the day
     */
    public Date getDay() {
        return mDay;
    }


    /**
     * @return the total number of cards marked as new
     */
    public int getNewCardsCount() {
        return mNewEase0 + mNewEase1 + mNewEase2 + mNewEase3 + mNewEase4;
    }
}
