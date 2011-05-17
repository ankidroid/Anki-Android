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

import org.json.JSONArray;
import org.json.JSONException;

import android.database.Cursor;

/**
 * Deck statistics.
 */
public class Stats {

    private Deck mDeck;
    private boolean mSelective = true;


    public Stats(Deck deck) {
        mDeck = deck;
    }


    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    public int[][] due(int start, int end, int chunk) {
        String lim = " AND due - " + mDeck.getSched().getToday() + " >= " + start;
        if (end != 10000) {
            lim += " AND day < " + end;
        }

        ArrayList<int[]> dues = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            String sql = "SELECT (due - " + mDeck.getSched().getToday() + ")/" + chunk + " AS day, " // day
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

    public int[][] reps(int num, int chunk, boolean time) {
        long interval = (mDeck.getSched().getDayCutoff() - (num * chunk * 86400));
        interval *= 1000;
        String lim = "WHERE time > " + interval + _revlogLimit();
        String tk;
        String td;
        if (time) {
            tk = "taken/1000";
            if (chunk < 30) {
                td = "/60.0"; // minutes
            } else {
                td = "/3600.0"; // hours
            }
        } else {
            tk = "1";
            td = "";
        }
        ArrayList<int[]> reps = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            cur = mDeck.getDB().getDatabase().rawQuery(
                    "SELECT (cast((time/1000 - " + mDeck.getSched().getDayCutoff() + ") / 86400.0 AS INT))/" + chunk
                            + " AS day, " + "sum(" + tk + ")" + td + ", " // all --> cram
                            + "sum(CASE WHEN type < 3 THEN " + tk + " ELSE 0 END)" + td + ", " // all but cram count -->
                            // lrn
                            + "sum(CASE WHEN type BETWEEN 1 AND 2 THEN " + tk + " ELSE 0 END)" + td + ", " // all but
                                                                                                           // count -->
                                                                                                           // relrn
                            + "sum(CASE WHEN type = 1 THEN " + tk + " ELSE 0 END)" + td + ", " // mtr + yng count -->
                            // yng
                            + "sum(CASE WHEN type = 1 AND lastIvl >= 21 THEN " + tk + " ELSE 0 END)" + td // mtr
                                                                                                                // count
                            + " FROM revlog " + lim + " GROUP BY day ORDER BY day", null);
            while (cur.moveToNext()) {
                reps.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3), cur.getInt(4),
                        cur.getInt(5) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return (int[][]) reps.toArray(new int[reps.size()][reps.get(0).length]);
    }


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


    private String _revlogLimit() {
        try {
            JSONArray lim = mDeck.getQconf().getJSONArray("groups");
            if (mSelective && lim.length() != 0) {
                return " AND cid IN (SELECT id FROM cards WHERE gid in " + Utils.ids2str(lim) + ")";
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return "";
    }
}
