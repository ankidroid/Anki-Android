/*****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.				                 *
 *									 		                                             *
 * This program is free software; you can redistribute it and/or modify it under 	     *
 * the terms of the GNU General Public License as published by the Free Software 	     *
 * Foundation; either version 3 of the License, or (at your option) any later            *
 * version.										                                         *
 *											                                             *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY       *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A       *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.              *
 * 											                                             *
 * You should have received a copy of the GNU General Public License along with          *
 * this program. If not, see <http://www.gnu.org/licenses/>.                             *
 ****************************************************************************************/

package com.ichi2.anki;

import com.ichi2.anki.R;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Stats;

import android.content.Context;
import android.content.res.Resources;

public class Statistics {
    public static String[] axisLabels = { "", "" };
    public static String sTitle;
    public static String[] Titles;

    public static int[] xAxisData;
    public static double[][] sSeriesList;

    private static Deck mDeck;
    public static int sType;
    public static int sStart = 0;
    public static int sEnd = 0;
    public static int sChunk = 1;

    public static int sZoom = 0;


    /**
     * Types
     */
    public static final int TYPE_DUE = 0;
    public static final int TYPE_CUMULATIVE_DUE = 1;
    public static final int TYPE_INTERVALS = 2;
    public static final int TYPE_REVIEWS = 3;
    public static final int TYPE_REVIEWING_TIME = 4;


    public static void initVariables(Context context, int type, int period, String title) {
        Resources res = context.getResources();
        sType = type;
        sTitle = title;
        sStart = 0;
        if (period >= 365) {
            sChunk = 30;
            sEnd = period / 30;
            axisLabels[0] = res.getString(R.string.statistics_period_x_axis_months);
        } else if (period >= 90) {
            sChunk = 7;
            sEnd = period / 7;
            axisLabels[0] = res.getString(R.string.statistics_period_x_axis_weeks);
        } else {
            sChunk = 1;
            sEnd = period;
            axisLabels[0] = res.getString(R.string.statistics_period_x_axis_days);
        }
        axisLabels[1] = context.getResources().getString(R.string.statistics_period_y_axis);
        if (type <= TYPE_CUMULATIVE_DUE) {
            Titles = new String[4];
            Titles[0] = res.getString(R.string.statistics_young_cards);
            Titles[1] = res.getString(R.string.statistics_mature_cards);
            Titles[2] = res.getString(R.string.statistics_failed_cards);
            Titles[2] = ("average");
        } else if (type == TYPE_REVIEWS) {
            Titles = new String[3];
            Titles[0] = res.getString(R.string.statistics_new_cards);
            Titles[1] = res.getString(R.string.statistics_young_cards);
            Titles[2] = res.getString(R.string.statistics_mature_cards);
            int temp = sStart;
            sStart = -sEnd;
            sEnd = temp;
        } else {
            Titles = new String[1];
            if (type == TYPE_REVIEWING_TIME) {
                int temp = sStart;
                sStart = -sEnd;
                sEnd = temp;
                axisLabels[1] = context.getResources().getString(R.string.statistics_period_y_axis_minutes);
            }
        }
        sZoom = 0;
    }


    public static boolean refreshDeckStatistics(Context context, Deck deck, int type, int period, String title) {
        initVariables(context, type, period, title);
        mDeck = deck;
        sSeriesList = getSeriesList();
        if (sSeriesList != null) {
            return true;
        } else {
            return false;
        }
    }


    public static boolean refreshAllDeckStatistics(Context context, String[] deckPaths, int type, int period,
            String title) {
        initVariables(context, type, period, title);
        for (String dp : deckPaths) {
            double[][] seriesList;
            mDeck = Deck.openDeck(dp);
            seriesList = getSeriesList();
            mDeck.closeDeck(false);
            for (int i = 0; i < sSeriesList.length; i++) {
                for (int j = 0; j < period; j++) {
                    sSeriesList[i][j] += seriesList[i][j];
                }
            }
        }
        if (sSeriesList != null) {
            return true;
        } else {
            return false;
        }
    }


    public static double[][] getSeriesList() {
        double[][] seriesList;
        Stats stats = new Stats(mDeck);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
            switch (sType) {
                case TYPE_DUE:
                case TYPE_CUMULATIVE_DUE:
                    seriesList = stripXAxisData(stats.due(sStart, sEnd * sChunk, sChunk), 2);
                    // add learn cards
                    seriesList[2][0] = mDeck.getSched().counts()[1];
                    seriesList[0][0] += seriesList[2][0];
                    seriesList[1][0] += seriesList[2][0];
                    if (sChunk == 1) {
                        seriesList[2][1] = mDeck.getSched().lrnTomorrow() - seriesList[2][0];
                        seriesList[0][1] += seriesList[2][1];
                        seriesList[1][1] += seriesList[2][1];
                    }
                    double average = 0;
                    for (double d : seriesList[0]) {
                        average += d;
                    }
                    average /= seriesList[1].length;
                    for (int i = 0; i < seriesList[3].length; i++) {
                        seriesList[3][i] = average;
                    }
                    if (sType == TYPE_CUMULATIVE_DUE) {
                        for (int i = 1; i < seriesList[0].length; i++) {
                            seriesList[0][i] += seriesList[0][i - 1];
                            seriesList[1][i] += seriesList[1][i - 1];
                            seriesList[2][i] += seriesList[2][i - 1];
                        }
                    }
                    break;
                case TYPE_INTERVALS:
                    seriesList = stripXAxisData(stats.intervals(sEnd * sChunk, sChunk), 0);
                    break;
//                 case TYPE_REVIEWS:
//                     seriesList = getReviews(period);
//                 break;
                // case TYPE_REVIEWING_TIME:
                // seriesList = new double[1][sEnd - sStart];
                // seriesList[0] = getReviewTime(period);
                // break;
                default:
                    seriesList = null;
            }
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }
        return seriesList;
    }


    public static double[][] stripXAxisData(int[][] inputStream, int extraRows) {
        xAxisData = new int[inputStream.length];
        double[][] result = new double[inputStream[0].length - 1 + extraRows][inputStream.length];
        for (int i = 0; i < inputStream.length; i++) {
            xAxisData[i] = inputStream[i][0];
            for (int j = 0; j < inputStream[0].length - 1; j++) {
                result[j][i] = inputStream[i][j + 1];
            }
        }
        return result;
    }


    public static double[][] getReviews(int length) {
        double series[][] = new double[3][length];
        for (int i = 0; i < length; i++) {
            // int result[] = mDeck.getDaysReviewed(i - length + 1);
            // series[0][i] = result[0];
            // series[1][i] = result[1];
            // series[2][i] = result[2];
        }
        return series;
    }


    public static double[] getReviewTime(int length) {
        double series[] = new double[length];
        for (int i = 0; i < length; i++) {
            // series[i] = mDeck.getReviewTime(i - length + 1) / 60;
        }
        return series;
    }


    public static double[] getCardsByInterval(int length) {
        double series[] = new double[length];
        for (int i = 0; i < length; i++) {
            // series[i] = mDeck.getCardsByInterval(i);
        }
        return series;
    }
}
