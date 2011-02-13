package com.ichi2.anki;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

public class Statistics {
	public static String[] axesLabels = { "Days", "Cards" };
    public static String[] Titles = { "All cards", "Mature cards" };
    public static String CHART_TITLE = "Cards due";
    
    public static double[] xAxisData;
    public static double[][] SeriesList;
    
    public static boolean refreshStatistics(Context context, int type, int period) {
    	Resources res = context.getResources();
    	axesLabels[0] = res.getString(R.string.statistics_period_x_axis);
    	axesLabels[1] = res.getString(R.string.statistics_period_y_axis);
    	Titles[0] = res.getString(R.string.statistics_all_cards);
    	Titles[1] = res.getString(R.string.statistics_mature_cards);
    	
    	xAxisData = xAxisData(period);
    	SeriesList = new double[2][period];

    	switch (type) {
    	case StudyOptions.STATISTICS_DUE:
        	SeriesList[0] = getAllCards(period, false, false);
        	SeriesList[1] = getAllCards(period, true, false);
    		return true;
    	case StudyOptions.STATISTICS_CUMULATIVE_DUE:
        	SeriesList[0] = getAllCards(period, false, true);
        	SeriesList[1] = getAllCards(period, true, true);
    		return true;
    	case StudyOptions.STATISTICS_INTERVALS:
    		return false;
    	case StudyOptions.STATISTICS_REVIEWS:
    		return false;
    	case StudyOptions.STATISTICS_REVIEWING_TIME:
    		return false;
    	default:
    		return false;
    	} 
    }


    public static double[] xAxisData(int length) {
    	double series[] = new double[length];
    	for (int i = 0; i < length; i++) {
    		series[i] = i;
    	}
    return series;
    }


    public static double[] getAllCards(int length, boolean matureCards, boolean cumulative) {
    	Deck deck = AnkiDroidApp.deck();
    	double series[] = new double[length];
    	for (int i = 0; i < length; i++) {
    		int count = 0;
    		if (!matureCards) {
    			count = deck.getNextDueCards(i);
    		} else {
    			count = deck.getNextDueMatureCards(i);    			
    		}
    		if (i > 0 && cumulative) {
    			series[i] = count + series[i - 1];
    		} else {
        		series[i] = count;
    		}
    	}
    return series;
    }
}
