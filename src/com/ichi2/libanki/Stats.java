/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.libanki;

import android.database.Cursor;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Deck statistics.
 */
public class Stats {

    public static final int TYPE_MONTH = 0;
    public static final int TYPE_YEAR = 1;
    public static final int TYPE_LIFE = 2;

    public static final int TYPE_FORECAST = 0;
    public static final int TYPE_REVIEW_COUNT = 1;
    public static final int TYPE_REVIEW_TIME = 2;
    // public static final int TYPE_INTERVALS = 2;
    // public static final int TYPE_REVIEWS = 3;
    // public static final int TYPE_REVIEWING_TIME = 4;
    // public static final int TYPE_DECK_SUMMARY = 5;

    private static Stats sCurrentInstance;

    private Collection mCol;
    private boolean mWholeCollection;
    private double[][] mSeriesList;

    private int mType;
    private int mTitle;
    private boolean mBackwards;
    private int[] mValueLabels;
    private int[] mColors;
    private int[] mAxisTitles;


    public Stats(Collection col, boolean wholeCollection) {
        mCol = col;
        mWholeCollection = wholeCollection;
        sCurrentInstance = this;
    }


    public static Stats currentStats() {
        return sCurrentInstance;
    }


    public double[][] getSeriesList() {
        return mSeriesList;
    }


    public Object[] getMetaInfo() {
        String title;
        if (mWholeCollection) {
            title = AnkiDroidApp.getInstance().getResources().getString(R.string.card_browser_all_decks);
        } else {
            try {
                title = mCol.getDecks().current().getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return new Object[] { mType, mTitle, mBackwards, mValueLabels, mColors, mAxisTitles, title };
    }


    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    public boolean calculateDue(int type) {
        mType = type;
        mBackwards = false;
        mTitle = R.string.stats_forecast;
        mValueLabels = new int[] { R.string.statistics_young, R.string.statistics_mature };
        mColors = new int[] { R.color.stats_young, R.color.stats_mature };
        mAxisTitles = new int[] { type, R.string.stats_cards };
        int end = 0;
        int chunk = 0;
        switch (type) {
            case TYPE_MONTH:
                end = 31;
                chunk = 1;
                break;
            case TYPE_YEAR:
                end = 52;
                chunk = 7;
                break;
            case TYPE_LIFE:
                end = -1;
                chunk = 30;
                break;
        }
        String lim = "";// AND due - " + mCol.getSched().getToday() + " >= " + start; // leave this out in order to show
                        // card too which were due the days before
        if (end != -1) {
            lim += " AND day <= " + end;
        }

        ArrayList<int[]> dues = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            "SELECT (due - " + mCol.getSched().getToday() + ")/" + chunk
                                    + " AS day, " // day
                                    + "count(), " // all cards
                                    + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                                    + "FROM cards WHERE did IN " + _limit() + " AND queue IN (2,3)" + lim
                                    + " GROUP BY day ORDER BY day", null);
            while (cur.moveToNext()) {
                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // small adjustment for a proper chartbuilding with achartengine
        if (dues.size() == 0 || dues.get(0)[0] > 0) {
            dues.add(0, new int[] { 0, 0, 0 });
        }
        if (end == -1 && dues.size() < 2) {
            end = 31;
        }
        if (type != TYPE_LIFE && dues.get(dues.size() - 1)[0] < end) {
            dues.add(new int[] { end, 0, 0 });
        } else if (type == TYPE_LIFE && dues.size() < 2) {
            dues.add(new int[] { Math.max(12, dues.get(dues.size() - 1)[0] + 1), 0, 0 });
        }

        mSeriesList = new double[3][dues.size()];
        for (int i = 0; i < dues.size(); i++) {
            int[] data = dues.get(i);
            mSeriesList[0][i] = data[0];
            mSeriesList[1][i] = data[1];
            mSeriesList[2][i] = data[2];
        }
        return dues.size() > 0;
    }


    /* only needed for studyoptions small chart */
    public static double[][] getSmallDueStats(Collection col) {
        ArrayList<int[]> dues = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            cur = col
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            "SELECT (due - " + col.getSched().getToday()
                                    + ") AS day, " // day
                                    + "count(), " // all cards
                                    + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                                    + "FROM cards WHERE did IN " + col.getSched()._deckLimit()
                                    + " AND queue IN (2,3) AND day <= 7 GROUP BY day ORDER BY day", null);
            while (cur.moveToNext()) {
                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // small adjustment for a proper chartbuilding with achartengine
        if (dues.size() == 0 || dues.get(0)[0] > 0) {
            dues.add(0, new int[] { 0, 0, 0 });
        }
        if (dues.get(dues.size() - 1)[0] < 7) {
            dues.add(new int[] { 7, 0, 0 });
        }
        double[][] serieslist = new double[3][dues.size()];
        for (int i = 0; i < dues.size(); i++) {
            int[] data = dues.get(i);
            serieslist[0][i] = data[0];
            serieslist[1][i] = data[1];
            serieslist[2][i] = data[2];
        }
        return serieslist;
    }


    /**
     * Reps and time spent
     * ***********************************************************************************************
     */

    public boolean calculateDone(int type, boolean reps) {
        mType = type;
        mBackwards = true;
        if (reps) {
            mTitle = R.string.stats_review_count;
            mAxisTitles = new int[] { type, R.string.stats_answers };
        } else {
            mTitle = R.string.stats_review_time;
        }
        mValueLabels = new int[] { R.string.statistics_learn, R.string.statistics_relearn, R.string.statistics_young,
                R.string.statistics_mature, R.string.statistics_cram };
        mColors = new int[] { R.color.stats_learn, R.color.stats_relearn, R.color.stats_young, R.color.stats_mature,
                R.color.stats_cram };
        int num = 0;
        int chunk = 0;
        switch (type) {
            case TYPE_MONTH:
                num = 31;
                chunk = 1;
                break;
            case TYPE_YEAR:
                num = 52;
                chunk = 7;
                break;
            case TYPE_LIFE:
                num = -1;
                chunk = 30;
                break;
        }
        ArrayList<String> lims = new ArrayList<String>();
        if (num != -1) {
            lims.add("id > " + ((mCol.getSched().getDayCutoff() - ((num + 1) * chunk * 86400)) * 1000));
        }
        String lim = _revlogLimit().replaceAll("[\\[\\]]", "");
        if (lim.length() > 0) {
            lims.add(lim);
        }
        if (lims.size() > 0) {
            lim = "WHERE ";
            while (lims.size() > 1) {
                lim += lims.remove(0) + " AND ";
            }
            lim += lims.remove(0);
        } else {
            lim = "";
        }
        String ti;
        String tf;
        if (!reps) {
            ti = "time/1000";
            if (mType == 0) {
                tf = "/60.0"; // minutes
                mAxisTitles = new int[] { type, R.string.stats_minutes };
            } else {
                tf = "/3600.0"; // hours
                mAxisTitles = new int[] { type, R.string.stats_hours };
            }
        } else {
            ti = "1";
            tf = "";
        }
        ArrayList<double[]> list = new ArrayList<double[]>();
        Cursor cur = null;
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            "SELECT (cast((id/1000 - " + mCol.getSched().getDayCutoff() + ") / 86400.0 AS INT))/"
                                    + chunk + " AS day, " + "sum(CASE WHEN type = 0 THEN " + ti + " ELSE 0 END)"
                                    + tf
                                    + ", " // lrn
                                    + "sum(CASE WHEN type = 1 AND lastIvl < 21 THEN " + ti + " ELSE 0 END)" + tf
                                    + ", " // yng
                                    + "sum(CASE WHEN type = 1 AND lastIvl >= 21 THEN " + ti + " ELSE 0 END)" + tf
                                    + ", " // mtr
                                    + "sum(CASE WHEN type = 2 THEN " + ti + " ELSE 0 END)" + tf + ", " // lapse
                                    + "sum(CASE WHEN type = 3 THEN " + ti + " ELSE 0 END)" + tf // cram
                                    + " FROM revlog " + lim + " GROUP BY day ORDER BY day", null);
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(1), cur.getDouble(4), cur.getDouble(2),
                        cur.getDouble(3), cur.getDouble(5) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        // small adjustment for a proper chartbuilding with achartengine
        if (type != TYPE_LIFE && (list.size() == 0 || list.get(0)[0] > -num)) {
            list.add(0, new double[] { -num, 0, 0, 0, 0, 0 });
        } else if (type == TYPE_LIFE && list.size() == 0) {
            list.add(0, new double[] { -12, 0, 0, 0, 0, 0 });
        }
        if (list.get(list.size() - 1)[0] < 0) {
            list.add(new double[] { 0, 0, 0, 0, 0, 0 });
        }

        mSeriesList = new double[6][list.size()];
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            mSeriesList[0][i] = data[0]; // day
            mSeriesList[1][i] = data[1] + data[2] + data[3] + data[4] + data[5]; // lrn
            mSeriesList[2][i] = data[2] + data[3] + data[4] + data[5]; // relearn
            mSeriesList[3][i] = data[3] + data[4] + data[5]; // young
            mSeriesList[4][i] = data[4] + data[5]; // mature
            mSeriesList[5][i] = data[5]; // cram
        }
        return list.size() > 0;
    }


    /**
     * Intervals ***********************************************************************************************
     */

    // public int[][] intervals(int limit, int chunk) {
    // ArrayList<int[]> ivls = new ArrayList<int[]>();
    // Cursor cur = null;
    // try {
    // cur = mDeck.getDB().getDatabase().rawQuery(
    // "SELECT ivl / " + chunk + " AS grp, count() FROM cards " + "WHERE queue = 2 " + _limit()
    // + " AND grp <= " + limit + " GROUP BY grp ORDER BY grp", null);
    // while (cur.moveToNext()) {
    // ivls.add(new int[] { cur.getInt(0), cur.getInt(1) });
    // }
    // } finally {
    // if (cur != null && !cur.isClosed()) {
    // cur.close();
    // }
    // }
    // return (int[][]) ivls.toArray(new int[ivls.size()][ivls.get(0).length]);
    // }
    //
    //
    /**
     * Tools ***********************************************************************************************
     */

    private String _limit() {
        if (mWholeCollection) {
            ArrayList<Long> ids = new ArrayList<Long>();
            for (JSONObject d : mCol.getDecks().all()) {
                try {
                    ids.add(d.getLong("id"));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            return Utils.ids2str(Utils.arrayList2array(ids));
        } else {
            return mCol.getSched()._deckLimit();
        }
    }


    private String _revlogLimit() {
        if (mWholeCollection) {
            return "";
        } else {
            return "cid IN (SELECT id FROM cards WHERE did IN " + Utils.ids2str(mCol.getDecks().active()) + ")";
        }
    }
}
