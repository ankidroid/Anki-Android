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

    public static final int STATS_LIFE = 0;
    public static final int STATS_DAY = 1;

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
            Log.i(AnkiDroidApp.TAG, "Reading stats from DB...");
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
        Log.i(AnkiDroidApp.TAG, "Creating new stats for " + day.toString() + "...");
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


    public static Date genToday(Deck deck) {
        // Get timezone offset in milliseconds
        Calendar now = Calendar.getInstance();
        int timezoneOffset = (now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET));

        return new Date((long) (System.currentTimeMillis() - deck.getUtcOffset() * 1000 - timezoneOffset));
    }


    public static void updateAllStats(Stats global, Stats daily, Card card, int ease, String oldState) {
        updateStats(global, card, ease, oldState);
        updateStats(daily, card, ease, oldState);
    }


    public static void updateStats(Stats stats, Card card, int ease, String oldState) {
        stats.mReps += 1;
        double delay = card.totalTime();
        if (delay >= 60) {
            stats.mReviewTime += 60;
        } else {
            stats.mReviewTime += delay;
            stats.mAverageTime = (stats.mReviewTime / stats.mReps);
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
        Log.i(AnkiDroidApp.TAG, "Getting global stats...");
        int type = STATS_LIFE;
        Date today = genToday(deck);
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
        Log.i(AnkiDroidApp.TAG, "Getting daily stats...");
        int type = STATS_DAY;
        Date today = genToday(deck);
        Stats stats = null;
        Cursor cursor = null;

        try {
            Log.i(AnkiDroidApp.TAG, "Trying to get stats for " + today.toString());
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


    /**
     * @param reps the reps to set
     */
    public void setReps(int reps) {
        mReps = reps;
    }


    /**
     * @return the reps
     */
    public int getReps() {
        return mReps;
    }


    /**
     * @param type the type to set
     */
    public void setType(int type) {
        mType = type;
    }


    /**
     * @return the type
     */
    public int getType() {
        return mType;
    }


    /**
     * @param day the day to set
     */
    public void setDay(Date day) {
        mDay = day;
    }


    /**
     * @return the day
     */
    public Date getDay() {
        return mDay;
    }


    /**
     * @param distractedReps the distractedReps to set
     */
    public void setDistractedReps(int distractedReps) {
        mDistractedReps = distractedReps;
    }


    /**
     * @return the distractedReps
     */
    public int getDistractedReps() {
        return mDistractedReps;
    }


    /**
     * @param distractedTime the distractedTime to set
     */
    public void setDistractedTime(double distractedTime) {
        mDistractedTime = distractedTime;
    }


    /**
     * @return the distractedTime
     */
    public double getDistractedTime() {
        return mDistractedTime;
    }


    /**
     * @param averageTime the averageTime to set
     */
    public void setAverageTime(double averageTime) {
        mAverageTime = averageTime;
    }


    /**
     * @return the averageTime
     */
    public double getAverageTime() {
        return mAverageTime;
    }


    /**
     * @param reviewTime the reviewTime to set
     */
    public void setReviewTime(double reviewTime) {
        mReviewTime = reviewTime;
    }


    /**
     * @return the reviewTime
     */
    public double getReviewTime() {
        return mReviewTime;
    }


    /**
     * @param newEase0 the newEase0 to set
     */
    public void setNewEase0(int newEase0) {
        mNewEase0 = newEase0;
    }


    /**
     * @return the newEase0
     */
    public int getNewEase0() {
        return mNewEase0;
    }


    /**
     * @param newEase1 the newEase1 to set
     */
    public void setNewEase1(int newEase1) {
        mNewEase1 = newEase1;
    }


    /**
     * @return the newEase1
     */
    public int getNewEase1() {
        return mNewEase1;
    }


    /**
     * @param newEase2 the newEase2 to set
     */
    public void setNewEase2(int newEase2) {
        mNewEase2 = newEase2;
    }


    /**
     * @return the newEase2
     */
    public int getNewEase2() {
        return mNewEase2;
    }


    /**
     * @param newEase3 the newEase3 to set
     */
    public void setNewEase3(int newEase3) {
        mNewEase3 = newEase3;
    }


    /**
     * @return the newEase3
     */
    public int getNewEase3() {
        return mNewEase3;
    }


    /**
     * @param newEase4 the newEase4 to set
     */
    public void setNewEase4(int newEase4) {
        mNewEase4 = newEase4;
    }


    /**
     * @return the newEase4
     */
    public int getNewEase4() {
        return mNewEase4;
    }


    /**
     * @param youngEase0 the youngEase0 to set
     */
    public void setYoungEase0(int youngEase0) {
        mYoungEase0 = youngEase0;
    }


    /**
     * @return the youngEase0
     */
    public int getYoungEase0() {
        return mYoungEase0;
    }


    /**
     * @param youngEase1 the youngEase1 to set
     */
    public void setYoungEase1(int youngEase1) {
        mYoungEase1 = youngEase1;
    }


    /**
     * @return the youngEase1
     */
    public int getYoungEase1() {
        return mYoungEase1;
    }


    /**
     * @param youngEase2 the youngEase2 to set
     */
    public void setYoungEase2(int youngEase2) {
        mYoungEase2 = youngEase2;
    }


    /**
     * @return the youngEase2
     */
    public int getYoungEase2() {
        return mYoungEase2;
    }


    /**
     * @param youngEase3 the youngEase3 to set
     */
    public void setYoungEase3(int youngEase3) {
        mYoungEase3 = youngEase3;
    }


    /**
     * @return the youngEase3
     */
    public int getYoungEase3() {
        return mYoungEase3;
    }


    /**
     * @param youngEase4 the youngEase4 to set
     */
    public void setYoungEase4(int youngEase4) {
        mYoungEase4 = youngEase4;
    }


    /**
     * @return the youngEase4
     */
    public int getYoungEase4() {
        return mYoungEase4;
    }


    /**
     * @param matureEase0 the matureEase0 to set
     */
    public void setMatureEase0(int matureEase0) {
        mMatureEase0 = matureEase0;
    }


    /**
     * @return the matureEase0
     */
    public int getMatureEase0() {
        return mMatureEase0;
    }


    /**
     * @param matureEase1 the matureEase1 to set
     */
    public void setMatureEase1(int matureEase1) {
        mMatureEase1 = matureEase1;
    }


    /**
     * @return the matureEase1
     */
    public int getMatureEase1() {
        return mMatureEase1;
    }


    /**
     * @param matureEase2 the matureEase2 to set
     */
    public void setMatureEase2(int matureEase2) {
        mMatureEase2 = matureEase2;
    }


    /**
     * @return the matureEase2
     */
    public int getMatureEase2() {
        return mMatureEase2;
    }


    /**
     * @param matureEase3 the matureEase3 to set
     */
    public void setMatureEase3(int matureEase3) {
        mMatureEase3 = matureEase3;
    }


    /**
     * @return the matureEase3
     */
    public int getMatureEase3() {
        return mMatureEase3;
    }


    /**
     * @param matureEase4 the matureEase4 to set
     */
    public void setMatureEase4(int matureEase4) {
        mMatureEase4 = matureEase4;
    }


    /**
     * @return the matureEase4
     */
    public int getMatureEase4() {
        return mMatureEase4;
    }
}
