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

import android.content.Context;
import android.content.res.Resources;

public class Statistics {
    public static String[] axisLabels = { "", "" };
    public static String sTitle;
    public static String[] Titles;

    public static double[] xAxisData;
    public static double[][] mSeriesList;

    private static Deck mDeck;


    public static void initVariables(Context context, int type, int period, String title) {
        Resources res = context.getResources();
        sTitle = title;
        axisLabels[0] = res.getString(R.string.statistics_period_x_axis);
        axisLabels[1] = res.getString(R.string.statistics_period_y_axis);
        if (type <= StudyOptions.STATISTICS_CUMULATIVE_DUE) {
            Titles = new String[3];
            Titles[0] = res.getString(R.string.statistics_all_cards);
            Titles[1] = res.getString(R.string.statistics_mature_cards);
            Titles[2] = res.getString(R.string.statistics_failed_cards);
            mSeriesList = new double[3][period];
            xAxisData = xAxisData(period, false);
        } else {
            Titles = new String[1];
            Titles[0] = res.getString(R.string.statistics_all_cards);
            mSeriesList = new double[1][period];
            switch (type) {
            	case StudyOptions.STATISTICS_INTERVALS:
                    xAxisData = xAxisData(period, false);
            		break;
            	case StudyOptions.STATISTICS_REVIEWS:
                    xAxisData = xAxisData(period, true);
            		break;
            	case StudyOptions.STATISTICS_REVIEWING_TIME:
                    xAxisData = xAxisData(period, true);
                    axisLabels[1] = context.getResources().getString(R.string.statistics_period_x_axis_minutes);
            		break;
            }
        }
    }


    public static boolean refreshDeckStatistics(Context context, Deck deck, int type, int period, String title) {
        initVariables(context, type, period, title);
        mDeck = deck;
    	mSeriesList = getSeriesList(context, type, period);
        if (mSeriesList != null) {
        	return true;
        } else {
        	return false;
        }
    }


    public static boolean refreshAllDeckStatistics(Context context, String[] deckPaths, int type, int period, String title) {
        initVariables(context, type, period, title);
    	for (String dp : deckPaths) {
            double[][] seriesList;
        	mDeck = Deck.openDeck(dp);
            seriesList = getSeriesList(context, type, period);
            mDeck.closeDeck();
            for (int i = 0; i < mSeriesList.length; i++) {
                for (int j = 0; j < period; j++) {
                	mSeriesList[i][j] += seriesList[i][j];
                }        	
            }
    	}
        if (mSeriesList != null) {
        	return true;
        } else {
        	return false;
        }
    }


    public static double[][] getSeriesList(Context context, int type, int period) {
    	double[][] seriesList;
    	switch (type) {
            case StudyOptions.STATISTICS_DUE:
            	seriesList = new double[3][period];
            	seriesList[0] = getCardsByDue(period, false);
            	seriesList[1] = getMatureCardsByDue(period, false);
            	seriesList[2] = getFailedCardsByDue(period, false);
            	seriesList[0][1] += seriesList[2][1];
            	seriesList[1][1] += seriesList[2][1];
                return seriesList;
            case StudyOptions.STATISTICS_CUMULATIVE_DUE:
            	seriesList = new double[3][period];
            	seriesList[0] = getCardsByDue(period, true);
            	seriesList[1] = getMatureCardsByDue(period, true);
            	seriesList[2] = getFailedCardsByDue(period, true);
                for (int i = 1; i < period; i++) {
                	seriesList[0][i] += seriesList[2][i];
                	seriesList[1][i] += seriesList[2][i];
                }
                return seriesList;
            case StudyOptions.STATISTICS_INTERVALS:
            	seriesList = new double[1][period];
            	seriesList[0] = getCardsByInterval(period);
                return seriesList;
            case StudyOptions.STATISTICS_REVIEWS:
            	seriesList = new double[1][period];
                seriesList[0] = getReviews(period);
                return seriesList;
            case StudyOptions.STATISTICS_REVIEWING_TIME:
            	seriesList = new double[1][period];
                seriesList[0] = getReviewTime(period);
                return seriesList;
            default:
                return null;
        }
    }


    public static double[] xAxisData(int length, boolean backwards) {
        double series[] = new double[length];
        if (backwards) {
            for (int i = 0; i < length; i++) {
                series[i] = i - length + 1;
            }
        } else {
            for (int i = 0; i < length; i++) {
                series[i] = i;
            }
        }
        return series;
    }


    public static double[] getCardsByDue(int length, boolean cumulative) {
        double series[] = new double[length];
        series[0] = mDeck.getDueCount();
        for (int i = 1; i < length; i++) {
            int count = 0;
            count = mDeck.getNextDueCards(i);
            if (cumulative) {
                series[i] = count + series[i - 1];
            } else {
                series[i] = count;
            }
        }
        return series;
    }


    public static double[] getMatureCardsByDue(int length, boolean cumulative) {
        double series[] = new double[length];
        for (int i = 0; i < length; i++) {
            int count = 0;
            count = mDeck.getNextDueMatureCards(i);
            if (cumulative && i > 0) {
                series[i] = count + series[i - 1];
            } else {
                series[i] = count;
            }
        }
        return series;
    }


    public static double[] getFailedCardsByDue(int length, boolean cumulative) {
        double series[] = new double[length];
        series[0] = mDeck.getFailedSoonCount();
        series[1] = mDeck.getFailedDelayedCount();
        if (cumulative) {
            series[1] += series[0];
            for (int i = 2; i < length; i++) {
                series[i] = series[1];
            }
        }
        return series;
    }


    public static double[] getReviews(int length) {
        double series[] = new double[length];
        for (int i = 0; i < length; i++) {
            series[i] = mDeck.getDaysReviewed(i - length + 1);
        }
        return series;
    }


    public static double[] getReviewTime(int length) {
        double series[] = new double[length];
        for (int i = 0; i < length; i++) {
            series[i] = mDeck.getReviewTime(i - length + 1) / 60;
        }
        return series;
    }


    public static double[] getCardsByInterval(int length) {
        double series[] = new double[length];
        for (int i = 0; i < length; i++) {
            series[i] = mDeck.getCardsByInterval(i);
        }
        return series;
    }
}
