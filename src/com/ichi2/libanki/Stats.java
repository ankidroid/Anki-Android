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

import java.util.ArrayList;

import android.database.Cursor;

/**
 * Deck statistics.
 */
public class Stats {

    public static final int TYPE_MONTH = 0;
    public static final int TYPE_YEAR = 1;
    public static final int TYPE_LIFE = 2;

    private static Stats sCurrentInstance;

    private Collection mCol;
    private Object mStats;
    private int mType;
    private boolean mWholeCollection;
    public double[][] mSeriesList;
    public int[] mXAxisList;

    public Stats(Collection col) {
    	mCol = col;
    	mStats = null;
    	mWholeCollection = false;
    	sCurrentInstance = this;
    }

    public static Stats currentStats() {
    	return sCurrentInstance;
    }

    public double[][] getSeriesList() {
    	return mSeriesList;
    }

    public int[] getXAxisList() {
    	return mXAxisList;
    }

    public void prepareSeriesList() {
    	int[][] dues = (int[][]) mStats;
    	mSeriesList = new double[dues.length][2];
    	for (int i = 0; i < dues.length; i++) {
    		mSeriesList[i][0] = dues[i][0];
    		mSeriesList[i][1] = dues[i][1];
    	}
    }

    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    public void calculateDue(int type) {
    	mType = type;
    	int start = 0;
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
            String lim = " AND due - " + mCol.getSched().getToday() + " >= " + start;
            if (end != -1) {
                lim += " AND day < " + end;
            }

            ArrayList<int[]> dues = new ArrayList<int[]>();
            Cursor cur = null;
            try {
                cur = mCol.getDb().getDatabase().rawQuery(
                        "SELECT (due - " + mCol.getSched().getToday() + ")/" + chunk + " AS day, " // day
                                + "count(), " // all cards
                                + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                                //+ "FROM cards WHERE did IN " + _limit() + " AND queue = 2" + lim + " GROUP BY day ORDER BY day", null);
                				+ "FROM cards WHERE queue = 2" + lim + " GROUP BY day ORDER BY day", null);
                while (cur.moveToNext()) {
                    dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            mSeriesList = new double[2][dues.size()];
            mXAxisList = new int[dues.size()];
            for (int i = 0; i < dues.size(); i++) {
            	int[] data = dues.get(i);
            	mXAxisList[i] = data[0];
            	mSeriesList[0][i] = data[1];
            	mSeriesList[1][i] = data[2];
            }
    }


    /**
     * Reps and time spent
     * ***********************************************************************************************
     */

    public int[][] reps(int type) {
    	mType = type;
    	int days;
    	int chunk;
    	switch (type) {
    	case TYPE_MONTH:
    		days = 30;
    		chunk = 1;
    		break;
    	case TYPE_YEAR:
    		days = 52;
    		chunk = 7;
    		break;
    	case TYPE_LIFE:
    		days = -1;
    		chunk = 30;
    		break;
    	}

//            long interval = (mCol.getSched().getDayCutoff() - (num * chunk * 86400)) * 1000;
//            String lim = "WHERE time > " + interval + _revlogLimit();
//        String tk;
//        String td;
//        if (time) {
//            tk = "taken/1000";
//            if (chunk < 30) {
//                td = "/60.0"; // minutes
//            } else {
//                td = "/3600.0"; // hours
//            }
//        } else {
//            tk = "1";
//            td = "";
//        }
//        ArrayList<int[]> reps = new ArrayList<int[]>();
//        Cursor cur = null;
//        try {
//            cur = mDeck.getDB().getDatabase().rawQuery(
//                    "SELECT (cast((time/1000 - " + mDeck.getSched().getDayCutoff() + ") / 86400.0 AS INT))/" + chunk
//                            + " AS day, " + "sum(" + tk + ")" + td + ", " // all --> cram
//                            + "sum(CASE WHEN type < 3 THEN " + tk + " ELSE 0 END)" + td + ", " // all but cram count -->
//                            // lrn
//                            + "sum(CASE WHEN type BETWEEN 1 AND 2 THEN " + tk + " ELSE 0 END)" + td + ", " // all but
//                                                                                                           // count -->
//                                                                                                           // relrn
//                            + "sum(CASE WHEN type = 1 THEN " + tk + " ELSE 0 END)" + td + ", " // mtr + yng count -->
//                            // yng
//                            + "sum(CASE WHEN type = 1 AND lastIvl >= 21 THEN " + tk + " ELSE 0 END)" + td // mtr
//                                                                                                                // count
//                            + " FROM revlog " + lim + " GROUP BY day ORDER BY day", null);
//            while (cur.moveToNext()) {
//                reps.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3), cur.getInt(4),
//                        cur.getInt(5) });
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//        return (int[][]) reps.toArray(new int[reps.size()][reps.get(0).length]);
    	return null;
    }


    /**
     * Intervals ***********************************************************************************************
     */

//    public int[][] intervals(int limit, int chunk) {
//        ArrayList<int[]> ivls = new ArrayList<int[]>();
//        Cursor cur = null;
//        try {
//            cur = mDeck.getDB().getDatabase().rawQuery(
//                    "SELECT ivl / " + chunk + " AS grp, count() FROM cards " + "WHERE queue = 2 " + _limit()
//                            + " AND grp <= " + limit + " GROUP BY grp ORDER BY grp", null);
//            while (cur.moveToNext()) {
//                ivls.add(new int[] { cur.getInt(0), cur.getInt(1) });
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//        return (int[][]) ivls.toArray(new int[ivls.size()][ivls.get(0).length]);
//    }
//
//
    /**
     * Tools ***********************************************************************************************
     */

    private String _limit() {
        if (mWholeCollection) {
            return mCol.getSched()._deckLimit();
        } else {
            return "";
        }
    }


    private static String _revlogLimit() {
//        try {
//            JSONArray lim = mCol.getQconf().getJSONArray("groups");
//            if (mSelective && lim.length() != 0) {
//                return " AND cid IN (SELECT id FROM cards WHERE gid in " + Utils.ids2str(lim) + ")";
//            }
//        } catch (JSONException e) {
//            throw new RuntimeException(e);
//        }
        return "";
    }
}
