package com.ichi2.anki;

import android.util.Log;

public class Statistics {
	public static String[] AXES_LABELS = { "Month", "Cards due" };
    public static String CHART_TITLE = "Cards due";
    
    public static String[] Titles = { "All cards", "Mature cards" };

    public static void refreshStatistics() {
    	xAxisData(30);
    	getAllCards(30, true, false);
    	getAllCards(30, false, false);
    }


    public static double[] xAxisData() {
    	return xAxisData(30);
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


    public static double[][] SeriesList = {getAllCards(30, false, false), getAllCards(30, true, false)};

}
