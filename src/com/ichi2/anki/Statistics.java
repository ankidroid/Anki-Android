/***************************************************************************************
* Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>						   *
* 																					   *
* This program is free software; you can redistribute it and/or modify it under 	   *
* the terms of the GNU General Public License as published by the Free Software 	   *
* Foundation; either version 3 of the License, or (at your option) any later           *
* version. 																			   *
* 																					   *
* This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
* PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
* 																					   *
* You should have received a copy of the GNU General Public License along with         *
* this program. If not, see <http://www.gnu.org/licenses/>.                            *
****************************************************************************************/


package com.ichi2.anki;

import android.content.Context;
import android.content.res.Resources;

public class Statistics {
	public static String[] axisLabels = {"", ""};
	public static String sTitle;
    public static String[] Titles;
    public static String CHART_TITLE = "Cards due";
    
    public static double[] xAxisData;
    public static double[][] SeriesList;
    
    public static boolean refreshStatistics(Context context, int type, int period, String title) {
    	Resources res = context.getResources();
    	sTitle = title;
    	axisLabels[0] = res.getString(R.string.statistics_period_x_axis);
    	axisLabels[1] = res.getString(R.string.statistics_period_y_axis);

    	if (type <= StudyOptions.STATISTICS_CUMULATIVE_DUE) {
        	Titles = new String[3];
        	Titles[0] = res.getString(R.string.statistics_all_cards);
        	Titles[1] = res.getString(R.string.statistics_mature_cards);
        	Titles[2] = res.getString(R.string.statistics_failed_cards);
        	SeriesList = new double[3][period];    		
        	xAxisData = xAxisData(period, false);
    	} else {
        	Titles = new String[1]; //should be [1], but crashes ChartDroid
        	Titles[0] = res.getString(R.string.statistics_all_cards);
        	SeriesList = new double[1][period]; //should be [1], but crashes ChartDroid
    	}

    	switch (type) {
    	case StudyOptions.STATISTICS_DUE:
        	SeriesList[0] = getCardsByDue(period, false, false);
            SeriesList[1] = getCardsByDue(period, true, false);
            SeriesList[2] = getFailedCardsByDue(period, false);
            SeriesList[0][1] += SeriesList[2][1];
            SeriesList[1][1] += SeriesList[2][1];
    		return true;
    	case StudyOptions.STATISTICS_CUMULATIVE_DUE:
        	SeriesList[0] = getCardsByDue(period, false, true);
            SeriesList[1] = getCardsByDue(period, true, true);
            SeriesList[2] = getFailedCardsByDue(period, true);
            for (int i = 1; i < period; i++) {
                SeriesList[0][i] += SeriesList[2][i];                
                SeriesList[1][i] += SeriesList[2][i];                
            }
    		return true;
    	case StudyOptions.STATISTICS_INTERVALS:
        	xAxisData = xAxisData(period, false);
    		SeriesList[0] = getCardsByInterval(period);
    		return true;    		
    	case StudyOptions.STATISTICS_REVIEWS:
        	xAxisData = xAxisData(period, true);
    		SeriesList[0] = getReviews(period);
    		return true;
    	case StudyOptions.STATISTICS_REVIEWING_TIME:
        	xAxisData = xAxisData(period, true);
    		axisLabels[1] = res.getString(R.string.statistics_period_x_axis_minutes);
    		SeriesList[0] = getReviewTime(period);
    		return true;
    	default:
    		return false;
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


    public static double[] getCardsByDue(int length, boolean matureCards, boolean cumulative) {
    	Deck deck = AnkiDroidApp.deck();
    	double series[] = new double[length];
    	series[0] = deck.getDueCount();
    	for (int i = 1; i < length; i++) {
    		int count = 0;
    		if (!matureCards) {
                count = deck.getNextDueCards(i);
    		} else {
    		    count = deck.getNextDueMatureCards(i);
    		}
    		if (cumulative) {
    			series[i] = count + series[i - 1];
    		} else {
        		series[i] = count;
    		}
    	}
    	return series;
    }    


    public static double[] getFailedCardsByDue(int length, boolean cumulative) {
        Deck deck = AnkiDroidApp.deck();
        double series[] = new double[length];
        series[0] = deck.getFailedSoonCount();
        series[1] = deck.getFailedDelayedCount();
        if (cumulative) {
            series[1] += series[0];
            for (int i = 2; i < length; i++) {
                series[i] = series[1];
            }
        }
    return series;
    }    


    public static double[] getReviews(int length) {
        Deck deck = AnkiDroidApp.deck();
        double series[] = new double[length];
        for (int i = 0; i < length; i++) {
            series[i] = deck.getDaysReviewed(i - length + 1);
        }
    return series;
    }


    public static double[] getReviewTime(int length) {
    	Deck deck = AnkiDroidApp.deck();
    	double series[] = new double[length];
    	for (int i = 0; i < length; i++) {
    		series[i] = deck.getReviewTime(i - length + 1) / 60;
    	}
    return series;
    }    


    public static double[] getCardsByInterval(int length) {
    	Deck deck = AnkiDroidApp.deck();
    	double series[] = new double[length];
    	for (int i = 0; i < length; i++) {
    		series[i] = deck.getCardsByInterval(i);
    	}
    return series;
    }
}
