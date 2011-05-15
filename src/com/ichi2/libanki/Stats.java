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
import java.util.TreeMap;

import android.database.Cursor;

/**
 * Deck statistics.
 */
public class Stats {

    private Deck mDeck;
    private int mType;
    private boolean mSelective = true;


    public Stats(Deck deck) {
        mDeck = deck;
    }


    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    public int[][] due(int start, int end, int chunk) {
        String lim = "";
        if (start != 10000) {
            lim += " AND due - " + mDeck.getSched().getToday() + " >= " + start;
        }
        if (end != 10000) {
            lim += " AND day < " + end;
        }

        ArrayList<int[]> dues = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            String sql = 
                "SELECT (due - " + mDeck.getSched().getToday() + ")/" + chunk + " AS day, " // day
                + "count(), " // all cards
                + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                + "FROM cards WHERE queue = 2 " + _limit() + lim + " GROUP BY day ORDER BY day";
            cur = mDeck.getDB().getDatabase().rawQuery(
                    "SELECT (due - " + mDeck.getSched().getToday() + ")/" + chunk + " AS day, " // day
                            + "count(), " // all cards
                            + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                            + "FROM cards WHERE queue = 2 " + _limit() + lim + " GROUP BY day ORDER BY day", null);
            while (cur.moveToNext()) {
                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return (int[][]) dues.toArray(new int[dues.size()][dues.get(0).length]);
    }


    /**
     * Reps and time spent
     * ***********************************************************************************************
     */

//    public int[][] reps(int start, int end, int chunk) {
//       
//        
//        ArrayList<int[]> dues = new ArrayList<int[]>();
//        Cursor cur = null;
//        try {
//            cur = mDeck.getDB().getDatabase().rawQuery(
//                    "SELECT cast((time/1000 - "
//                            + mDeck.getSched().getDayCutoff() + ") / 86400.0 AS INT))/" + chunk
//                            + " AS day, "
//                            + "sum(CASE WHEN type = 0 THEN 1 ELSE 0 END), " // lrn count
//                            + "sum(CASE WHEN type = 1 AND lastIvl < 21 THEN 1 ELSE 0 END), " // yng count
//                            + "sum(CASE WHEN type = 1 AND lastIvl >= 21 THEN 1 ELSE 0 END), " // mtr count
//                            + "sum(CASE WHEN type = 2  THEN 1 ELSE 0 END), " // lapse count
//                            + "sum(case when type = 3 then 1 else 0 end), " // cram count
//                            + "sum(CASE WHEN  = 0 then taken/1000 else 0 end)/"
//                            + "FROM revlog " + lim + "GROUP BY day ORDER BY day", null);
//            while (cur.moveToNext()) {
//                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//
//        return null;
//    }
//
//
//    public int[][] repsTime(int start, int end, int chunk) {
//       
//        
//        ArrayList<int[]> dues = new ArrayList<int[]>();
//        Cursor cur = null;
//        try {
//            cur = mDeck.getDB().getDatabase().rawQuery(
//                    "SELECT cast((time/1000 - "
//                            + mDeck.getSched().dayCutoff
//                            + ") / 86400.0 AS INT))/"
//                            + chunk
//                            + " AS day, "
//                            + ", " // lrn time
//                            // yng + mtr time
//                            + "sum(CASE WHEN = 1 and lastIvl < 21 then taken/1000 else 0 end)/" + tf + ", "
//                            + "sum(CASE WHEN = 1 and lastIvl >= 21 then taken/1000 else 0 end)/" + tf + ", "
//                            + "sum(CASE WHEN = 2 then taken/1000 else 0 end)/" + tf + "," // lapse time
//                            + "sum(CASE WHEN = 3 then taken/1000 else 0 end)/" + tf // cram time
//                            + "FROM revlog " + lim + "GROUP BY day ORDER BY day", null);
//            while (cur.moveToNext()) {
//                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//
//        return null;
//    }


    /**
     * Intervals ***********************************************************************************************
     */

    public int[][] intervals(int limit, int chunk) {
        ArrayList<int[]> ivls = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            cur = mDeck.getDB().getDatabase().rawQuery(
                    "SELECT ivl / " + chunk + " AS grp, count() FROM cards " + "WHERE queue = 2 " + _limit()
                            + " AND grp <= " + limit + " GROUP BY grp ORDER BY grp", null);
            while (cur.moveToNext()) {
                ivls.add(new int[] { cur.getInt(0), cur.getInt(1) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return (int[][]) ivls.toArray(new int[ivls.size()][ivls.get(0).length]);
    }


    /**
     * Tools ***********************************************************************************************
     */

    private String _limit() {
        if (mSelective) {
            return mDeck.getSched()._groupLimit();
        } else {
            return "";
        }
    }
}
