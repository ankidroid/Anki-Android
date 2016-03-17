/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@m-goldbach.net>                         *
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
package com.ichi2.anki.stats;


import android.content.res.Resources;
import android.database.Cursor;
import android.webkit.WebView;

import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;

import java.util.ArrayList;

import timber.log.Timber;

public class OverviewStatsBuilder {
    private final int CARDS_INDEX = 0;
    private final int THETIME_INDEX = 1;
    private final int FAILED_INDEX = 2;
    private final int LRN_INDEX = 3;
    private final int REV_INDEX = 4;
    private final int RELRN_INDEX = 5;
    private final int FILT_INDEX = 6;
    private final int MCNT_INDEX = 7;
    private final int MSUM_INDEX = 8
            ;
    private final WebView mWebView; //for resources access
    private final Collection mCollectionData;
    private final boolean mWholeCollection;
    private final Stats.AxisType mType;


    public class OverviewStats {
        public double reviewsPerDayOnAll;
        public double reviewsPerDayOnStudyDays;
        public int allDays;
        public int daysStudied;
        public double timePerDayOnAll;
        public double timePerDayOnStudyDays;
        public double totalTime;
        public int totalReviews;
        public double newCardsPerDay;
        public int totalNewCards;
        public double averageInterval;
        public double longestInterval;
    }

    public OverviewStatsBuilder(WebView chartView, Collection collectionData, boolean isWholeCollection, Stats.AxisType mStatType){
        mWebView = chartView;
        mCollectionData = collectionData;
        mWholeCollection = isWholeCollection;
        mType = mStatType;
    }

    public String createInfoHtmlString(){

        int textColorInt = Themes.getColorFromAttr(mWebView.getContext(), android.R.attr.textColor);
        String textColor = String.format("#%06X", (0xFFFFFF & textColorInt)); // Color to hex string

        String css = "<style>\n" +
                "h1 { margin-bottom: 0; margin-top: 1em; }\n" +
                ".pielabel { text-align:center; padding:0px; color:white; }\n" +
                "body {color:"+textColor+";}\n" +
                "</style>";

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<center>");
        stringBuilder.append(css);
        appendTodaysStats(stringBuilder);

        appendOverViewStats(stringBuilder);

        stringBuilder.append("</center>");
        return stringBuilder.toString();
    }

    private void appendOverViewStats(StringBuilder stringBuilder) {
        Stats stats = new Stats(mCollectionData, mWholeCollection);

        OverviewStats oStats = new OverviewStats();
        stats.calculateOverviewStatistics(mType, oStats, mWebView.getContext());
        Resources res = mWebView.getResources();

        stringBuilder.append(_title(res.getString(mType.descriptionId)));


        boolean allDaysStudied = oStats.daysStudied == oStats.allDays;

        stringBuilder.append(_subtitle(res.getString(R.string.stats_review_count).toUpperCase()));
        stringBuilder.append(res.getString(R.string.stats_overview_days_studied,(int)((float)oStats.daysStudied/(float)oStats.allDays*100), oStats.daysStudied, oStats.allDays));
        stringBuilder.append(res.getString(R.string.stats_overview_total_reviews,oStats.totalReviews));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_reviews_per_day_studydays,oStats.reviewsPerDayOnStudyDays));
        if (!allDaysStudied){
            stringBuilder.append("<br>");
            stringBuilder.append(res.getString(R.string.stats_overview_reviews_per_day_all,oStats.reviewsPerDayOnAll));
        }

        stringBuilder.append("<br><br>");
        stringBuilder.append(_subtitle(res.getString(R.string.stats_review_time).toUpperCase()));
        stringBuilder.append(res.getString(R.string.stats_overview_time_per_day_studydays,oStats.timePerDayOnStudyDays));
        if (!allDaysStudied){
            stringBuilder.append("<br>");
            stringBuilder.append(res.getString(R.string.stats_overview_time_per_day_all,oStats.timePerDayOnAll));
        }

        stringBuilder.append("<br><br>");
        stringBuilder.append(_subtitle(res.getString(R.string.stats_progress).toUpperCase()));
        stringBuilder.append(res.getString(R.string.stats_overview_total_new_cards,oStats.totalNewCards));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_new_cards_per_day,oStats.newCardsPerDay));

        stringBuilder.append("<br><br>");
        stringBuilder.append(_subtitle(res.getString(R.string.stats_review_intervals).toUpperCase()));
        stringBuilder.append(res.getString(R.string.stats_overview_average_interval));

        stringBuilder.append(Utils.roundedTimeSpan(mWebView.getContext(), (int)Math.round(oStats.averageInterval*Stats.SECONDS_PER_DAY)));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_longest_interval));
        stringBuilder.append(Utils.roundedTimeSpan(mWebView.getContext(), (int)Math.round(oStats.longestInterval*Stats.SECONDS_PER_DAY)));

    }

    private void appendTodaysStats(StringBuilder stringBuilder){
        Stats stats = new Stats(mCollectionData, mWholeCollection);
        int[] todayStats = stats.calculateTodayStats();
        stringBuilder.append(_title(mWebView.getResources().getString(R.string.stats_today)));
        Resources res = mWebView.getResources();
        final int minutes = (int) Math.round(todayStats[THETIME_INDEX]/60.0);
        final String span = res.getQuantityString(R.plurals.time_span_minutes, minutes, minutes);
        stringBuilder.append(res.getQuantityString(R.plurals.stats_today_cards,
                                                   todayStats[CARDS_INDEX], todayStats[CARDS_INDEX], span));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_today_again_count, todayStats[FAILED_INDEX]));
        if (todayStats[CARDS_INDEX] > 0) {
            stringBuilder.append(" ");
            stringBuilder.append(res.getString(R.string.stats_today_correct_count, (((1 - todayStats[FAILED_INDEX] / (float) (todayStats[CARDS_INDEX])) * 100.0))));
        }
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_today_type_breakdown, todayStats[LRN_INDEX], todayStats[REV_INDEX], todayStats[RELRN_INDEX], todayStats[FILT_INDEX]));
        stringBuilder.append("<br>");
        if (todayStats[MCNT_INDEX] != 0) {
            stringBuilder.append(res.getString(R.string.stats_today_mature_cards, todayStats[MSUM_INDEX], todayStats[MCNT_INDEX], (todayStats[MSUM_INDEX] / (float)(todayStats[MCNT_INDEX]) * 100.0)));
        } else {
            stringBuilder.append(res.getString(R.string.stats_today_no_mature_cards));
        }


    }


    private String _title(String title){
        return "<h1>" + title + "</h1>";
    }

    private String _subtitle(String title){
        return "<h3>" + title + "</h3>";
    }

    private String  bold(String s) {
        return "<b>" + s + "</b>";
    }

}
