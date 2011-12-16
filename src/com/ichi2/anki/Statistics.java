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

import java.util.Map.Entry;

import com.ichi2.themes.Themes;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;

public class Statistics {
    public static String[] axisLabels = { "", "" };
    public static String sTitle;
    public static String[] Titles;

    public static int[] xAxisData;
    public static double[][] sSeriesList;

    private static Deck sDeck;
    private static ContentValues sDeckSummaryValues;
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
	public static final int TYPE_DECK_SUMMARY = 5;


    public static void initVariables(Context context, int type, int period, String title) {
        Resources res = context.getResources();
        if (type == TYPE_DECK_SUMMARY) {
        	sDeckSummaryValues = new ContentValues();
        	return;
        }
        sType = type;
        sTitle = title;
        sStart = 0;
        if (period >= 365 * 2) {
            sChunk = 30;
            sEnd = period / 30;
            axisLabels[0] = res.getString(R.string.statistics_period_x_axis_months);
        } else if (period >= 180) {
            sChunk = 7;
            sEnd = period / 7;
            axisLabels[0] = res.getString(R.string.statistics_period_x_axis_weeks);
        } else {
            sChunk = 1;
            sEnd = period;
            axisLabels[0] = res.getString(R.string.statistics_period_x_axis_days);
        }
        axisLabels[1] = context.getResources().getString(R.string.statistics_period_y_axis_cards);
        if (type <= TYPE_CUMULATIVE_DUE) {
            Titles = new String[4];
            Titles[0] = res.getString(R.string.statistics_young_cards);
            Titles[1] = res.getString(R.string.statistics_mature_cards);
            Titles[2] = res.getString(R.string.statistics_failed_cards);
            Titles[3] = ("average");
        } else if (type <= TYPE_REVIEWING_TIME) {
            Titles = new String[6];
            Titles[0] = res.getString(R.string.statistics_cram_cards);
            Titles[1] = res.getString(R.string.statistics_learn_cards);
            Titles[2] = res.getString(R.string.statistics_relearn_cards);
            Titles[3] = res.getString(R.string.statistics_young_cards);
            Titles[4] = res.getString(R.string.statistics_mature_cards);
            Titles[5] = res.getString(R.string.statistics_average);
            int temp = sStart;
            sStart = -sEnd;
            sEnd = temp;
            if (type == TYPE_REVIEWING_TIME) {
                if (sChunk < 30) {
                    axisLabels[1] = context.getResources().getString(R.string.statistics_period_y_axis_minutes);                    
                } else {
                    axisLabels[1] = context.getResources().getString(R.string.statistics_period_y_axis_hours);                    
                }
            }
        } else {
            Titles = new String[1];
            sSeriesList = new double[1][period];
            switch (type) {
            	case TYPE_INTERVALS:
                    xAxisData = xAxisData(period, false);
            		break;
            	case TYPE_REVIEWING_TIME:
                    xAxisData = xAxisData(period, true);
                    axisLabels[1] = context.getResources().getString(R.string.statistics_period_x_axis_minutes);
            		break;
            }
        }
        sZoom = 0;
    }


    public static boolean refreshDeckStatistics(Context context, Deck deck, int type, int period, String title) {
        initVariables(context, type, period, title);
        sDeck = deck;
        if (type == TYPE_DECK_SUMMARY) {
        	sDeckSummaryValues = sDeck.getDeckSummary();
        	return sDeckSummaryValues != null ? true : false;
        } else {
            sSeriesList = getSeriesList();
        	return sSeriesList != null ? true : false;
        }
    }


    public static boolean refreshAllDeckStatistics(Context context, String[] deckPaths, int type, int period,
            String title) {
        initVariables(context, type, period, title);
     	for (String dp : deckPaths) {
    		sDeck = DeckManager.getDeck(dp, DeckManager.REQUESTING_ACTIVITY_STATISTICS);
            if (sDeck == null) {
                continue;
            }
    		if (type == TYPE_DECK_SUMMARY) {
    			sDeckSummaryValues.put("title", context.getResources().getString(R.string.deck_summary_all_decks));
    			ContentValues values = sDeck.getDeckSummary();
    			if (values == null) {
                    continue;
                }
    			for (Entry<String, Object> entry : values.valueSet()) {
    				if (entry.getKey().equals("deckAge")) {
    					if (sDeckSummaryValues.containsKey(entry.getKey())) {
    						sDeckSummaryValues.put(entry.getKey(), Math.max(sDeckSummaryValues.getAsInteger(entry.getKey()), (Integer) entry.getValue()));
    					} else {
    						sDeckSummaryValues.put(entry.getKey(), (Integer) entry.getValue());   
    					}
    				} else {
    					if (sDeckSummaryValues.containsKey(entry.getKey())) {
    						sDeckSummaryValues.put(entry.getKey(), sDeckSummaryValues.getAsInteger(entry.getKey()) + (Integer) entry.getValue());
    					} else {
    						sDeckSummaryValues.put(entry.getKey(), (Integer) entry.getValue());   
    					}    					
    				}
				}
    		} else {
                double[][] seriesList;
                seriesList = getSeriesList(context, type, period);
                for (int i = 0; i < sSeriesList.length; i++) {
                    for (int j = 0; j < period; j++) {
                    	sSeriesList[i][j] += seriesList[i][j];
                    }        	
                }    			
    		}
            DeckManager.closeDeck(dp, DeckManager.REQUESTING_ACTIVITY_STATISTICS, false);
    	}
        if (type == TYPE_DECK_SUMMARY) {
        	return (sDeckSummaryValues != null && sDeckSummaryValues.size() > 0) ? true : false;
        } else {
        	return sSeriesList != null ? true : false;        	
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
                    seriesList = stripXAxisData(stats.due(sStart, sEnd * sChunk, sChunk), sType == TYPE_DUE ? 2 : 1);
                    // add learn cards
                    if (sChunk == 1) {
                        seriesList[2][0] = mDeck.getSched().counts()[1];
                        seriesList[0][0] += seriesList[2][0];
                        seriesList[1][0] += seriesList[2][0];
                        seriesList[2][1] = mDeck.getSched().lrnTomorrow() - seriesList[2][0];
                        seriesList[0][1] += seriesList[2][1];
                        seriesList[1][1] += seriesList[2][1];
                    } else {
                        seriesList[2][0] = mDeck.getSched().lrnTomorrow();
                        seriesList[0][0] += seriesList[2][0];
                        seriesList[1][0] += seriesList[2][0];
                    }
                    if (sType == TYPE_DUE) {
                        double average = seriesList[2][0] + seriesList[2][1];
                        for (double d : seriesList[0]) {
                            average += d;
                        }
                        average /= seriesList[1].length;
                        for (int i = 0; i < seriesList[3].length; i++) {
                            seriesList[3][i] = average;
                        }
                    } else {
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
                case TYPE_REVIEWS:
                case TYPE_REVIEWING_TIME:
                    seriesList = stripXAxisData(stats.reps(-sStart, sChunk, sType == TYPE_REVIEWING_TIME), 1);
                    double averageReps = 0;
                    for (double d : seriesList[0]) {
                        averageReps += d;
                    }
                    averageReps /= seriesList[0].length;
                    for (int i = 0; i < seriesList[5].length; i++) {
                        seriesList[5][i] = averageReps;
                    }
                    break;
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


    public static double getFraction(double numerator, double denominator) {
    	if (denominator == 0) {
    		return 0;
    	} else {
    		return numerator / denominator;
    	}
    }


    private static String getHtmlDeckSummary(Context context) {
    	Resources res = context.getResources();

       	int cardCount = sDeckSummaryValues.getAsInteger("cardCount");
       	int matureCount = sDeckSummaryValues.getAsInteger("matureCount");
       	int unseenCount = sDeckSummaryValues.getAsInteger("unseenCount");
       	int youngCount = cardCount - unseenCount - matureCount;
       	int intervalSum = sDeckSummaryValues.getAsInteger("intervalSum");
       	int repsMatCount = sDeckSummaryValues.getAsInteger("repsMatCount");
       	int repsMatNoCount = sDeckSummaryValues.getAsInteger("repsMatNoCount");
       	int repsYoungCount = sDeckSummaryValues.getAsInteger("repsYoungCount");
       	int repsYoungNoCount = sDeckSummaryValues.getAsInteger("repsYoungNoCount");
       	int repsFirstCount = sDeckSummaryValues.getAsInteger("repsFirstCount");
       	int repsFirstNoCount = sDeckSummaryValues.getAsInteger("repsFirstNoCount");
       	int reviewsLastWeek = sDeckSummaryValues.getAsInteger("reviewsLastWeek");
       	int newsLastWeek = sDeckSummaryValues.getAsInteger("newsLastWeek");
       	int reviewsLastMonth = sDeckSummaryValues.getAsInteger("reviewsLastMonth");
       	int newsLastMonth = sDeckSummaryValues.getAsInteger("newsLastMonth");
       	int reviewsLastYear = sDeckSummaryValues.getAsInteger("reviewsLastYear");
       	int newsLastYear = sDeckSummaryValues.getAsInteger("newsLastYear");
       	int deckAge = sDeckSummaryValues.getAsInteger("deckAge");
       	int revTomorrow = sDeckSummaryValues.getAsInteger("revTomorrow");
       	int newTomorrow = sDeckSummaryValues.getAsInteger("newTomorrow");
       	int timeTomorrow = sDeckSummaryValues.getAsInteger("timeTomorrow");

    	StringBuilder builder = new StringBuilder();
       	builder.append("<html><body text=\"#FFFFFF\">");
       	builder.append(res.getString(R.string.deck_summary_deck_age, deckAge)).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_cards)).append(" <b>").append(Integer.toString(cardCount)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_facts)).append(" <b>").append(Integer.toString(sDeckSummaryValues.getAsInteger("factCount"))).append("</b><br>");
       	builder.append("<br><b>").append(res.getString(R.string.deck_summary_card_age)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_cards_mature)).append(" <b>").append(Integer.toString(matureCount)).append("</b> (").append(String.format("%.1f &#37;", 100.0d * getFraction(matureCount, cardCount))).append(")<br>");
       	builder.append(res.getString(R.string.deck_summary_cards_young)).append(" <b>").append(Integer.toString(youngCount)).append("</b> (").append(String.format("%.1f &#37;", 100.0d * getFraction(youngCount, cardCount))).append(")<br>");
       	builder.append(res.getString(R.string.deck_summary_cards_unseen)).append(" <b>").append(Integer.toString(unseenCount)).append("</b> (").append(String.format("%.1f &#37;", 100.0d * getFraction(unseenCount, cardCount))).append(")<br>");
       	builder.append(res.getString(R.string.deck_summary_average_interval, getFraction(intervalSum, (cardCount - unseenCount)))).append("<br>");
       	builder.append("<br><b>").append(res.getString(R.string.deck_summary_answers_correct)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_answers_mature, 100.0d * getFraction(repsMatCount - repsMatNoCount, repsMatCount), "&#37;", repsMatCount - repsMatNoCount, repsMatCount)).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_answers_young, 100.0d * getFraction(repsYoungCount - repsYoungNoCount, repsYoungCount), "&#37;", repsYoungCount - repsYoungNoCount, repsYoungCount)).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_answers_firstseen, 100.0d * getFraction(repsFirstCount - repsFirstNoCount, repsFirstCount), "&#37;", repsFirstCount - repsFirstNoCount, repsFirstCount)).append("<br>");
       	builder.append("<br><b>").append(res.getString(R.string.deck_summary_forecast)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_tomorrow_due, revTomorrow)).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_tomorrow_new, newTomorrow)).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_tomorrow_time, timeTomorrow)).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_finished_in, (int)Math.round(getFraction(unseenCount, newTomorrow)))).append("<br>");
       	builder.append("<br><b>").append(res.getString(R.string.deck_summary_average_week)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_reviews)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(reviewsLastWeek, Math.min((double)deckAge, 7.0d)))).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_news)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(newsLastWeek, Math.min((double)deckAge, 7.0d)))).append("<br>");
       	builder.append("<br><b>").append(res.getString(R.string.deck_summary_average_month)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_reviews)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(reviewsLastMonth, Math.min((double)deckAge, 30.0d)))).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_news)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(newsLastMonth, Math.min((double)deckAge, 30.0d)))).append("<br>");
       	builder.append("<br><b>").append(res.getString(R.string.deck_summary_average_year)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_reviews)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(reviewsLastYear, Math.min((double)deckAge, 365.0d)))).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_news)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(newsLastYear, Math.min((double)deckAge, 365.0d)))).append("<br>");
       	builder.append("<br><b>").append(res.getString(R.string.deck_summary_average_total)).append("</b><br>");
       	builder.append(res.getString(R.string.deck_summary_reviews)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(repsMatCount + repsYoungCount + repsFirstCount, deckAge))).append("<br>");
       	builder.append(res.getString(R.string.deck_summary_news)).append(" ").append(res.getString(R.string.deck_summary_cards_per_day, getFraction(matureCount + youngCount, deckAge))).append("<br>");
       	builder.append("</body></html>");
       	return builder.toString();
    }


    public static void showDeckSummary(Context context) {
        Themes.htmlOkDialog(context, sDeckSummaryValues.containsKey("title") ? sDeckSummaryValues.getAsString("title") : sDeck.getDeckName(), getHtmlDeckSummary(context)).show();
    }
}
