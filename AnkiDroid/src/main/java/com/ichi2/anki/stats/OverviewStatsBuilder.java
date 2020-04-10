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
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Stats;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

public class OverviewStatsBuilder {
    private static final int CARDS_INDEX = 0;
    private static final int THETIME_INDEX = 1;
    private static final int FAILED_INDEX = 2;
    private static final int LRN_INDEX = 3;
    private static final int REV_INDEX = 4;
    private static final int RELRN_INDEX = 5;
    private static final int FILT_INDEX = 6;
    private static final int MCNT_INDEX = 7;
    private static final int MSUM_INDEX = 8;

    private final WebView mWebView; //for resources access
    private final Collection mCol;
    private final long mDeckId;
    private final Stats.AxisType mType;


    public class OverviewStats {
        public int forecastTotalReviews;
        public double forecastAverageReviews;
        public int forecastDueTomorrow;
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

    public OverviewStatsBuilder(WebView chartView, Collection collectionData, long deckId, Stats.AxisType mStatType) {
        mWebView = chartView;
        mCol = collectionData;
        mDeckId = deckId;
        mType = mStatType;
    }

    public String createInfoHtmlString() {
        int textColorInt = Themes.getColorFromAttr(mWebView.getContext(), android.R.attr.textColor);
        String textColor = String.format("#%06X", (0xFFFFFF & textColorInt)); // Color to hex string

        String css = "<style>\n" +
                "h1, h3 { margin-bottom: 0; margin-top: 1em; text-transform: capitalize; }\n" +
                ".pielabel { text-align:center; padding:0px; color:white; }\n" +
                "body {color:" + textColor + ";}\n" +
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
        Stats stats = new Stats(mCol, mDeckId);

        OverviewStats oStats = new OverviewStats();
        stats.calculateOverviewStatistics(mType, oStats);
        Resources res = mWebView.getResources();

        stringBuilder.append(_title(res.getString(mType.descriptionId)));

        boolean allDaysStudied = oStats.daysStudied == oStats.allDays;
        String daysStudied = res.getString(R.string.stats_overview_days_studied,
                (int) ((float) oStats.daysStudied / (float) oStats.allDays * 100),
                oStats.daysStudied, oStats.allDays);


        // FORECAST
        // Fill in the forecast summaries first
        calculateForecastOverview(mType, oStats);

        stringBuilder.append(_subtitle(res.getString(R.string.stats_forecast).toUpperCase()));
        stringBuilder.append(res.getString(R.string.stats_overview_forecast_total, oStats.forecastTotalReviews));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_forecast_average, oStats.forecastAverageReviews));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_forecast_due_tomorrow, oStats.forecastDueTomorrow));

        stringBuilder.append("<br>");

        // REVIEW COUNT
        stringBuilder.append(_subtitle(res.getString(R.string.stats_review_count).toUpperCase()));
        stringBuilder.append(daysStudied);
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_total_reviews, oStats.totalReviews));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_reviews_per_day_studydays, oStats.reviewsPerDayOnStudyDays));
        if (!allDaysStudied) {
            stringBuilder.append("<br>");
            stringBuilder.append(res.getString(R.string.stats_overview_reviews_per_day_all, oStats.reviewsPerDayOnAll));
        }

        stringBuilder.append("<br>");

        //REVIEW TIME
        stringBuilder.append(_subtitle(res.getString(R.string.stats_review_time).toUpperCase()));
        stringBuilder.append(daysStudied);
        stringBuilder.append("<br>");
        // TODO: Total: x minutes
        stringBuilder.append(res.getString(R.string.stats_overview_time_per_day_studydays, oStats.timePerDayOnStudyDays));
        if (!allDaysStudied) {
            stringBuilder.append("<br>");
            stringBuilder.append(res.getString(R.string.stats_overview_time_per_day_all, oStats.timePerDayOnAll));
        }
        // TODO: Average answer time: x.xs (x.x cards/minute)

        stringBuilder.append("<br>");

        // ADDED
        stringBuilder.append(_subtitle(res.getString(R.string.stats_added).toUpperCase()));
        stringBuilder.append(res.getString(R.string.stats_overview_total_new_cards, oStats.totalNewCards));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_new_cards_per_day, oStats.newCardsPerDay));

        stringBuilder.append("<br>");

        // INTERVALS
        stringBuilder.append(_subtitle(res.getString(R.string.stats_review_intervals).toUpperCase()));
        stringBuilder.append(res.getString(R.string.stats_overview_average_interval));
        stringBuilder.append(Utils.roundedTimeSpan(mWebView.getContext(), (int) Math.round(oStats.averageInterval * Stats.SECONDS_PER_DAY)));
        stringBuilder.append("<br>");
        stringBuilder.append(res.getString(R.string.stats_overview_longest_interval));
        stringBuilder.append(Utils.roundedTimeSpan(mWebView.getContext(), (int) Math.round(oStats.longestInterval * Stats.SECONDS_PER_DAY)));
    }

    private void appendTodaysStats(StringBuilder stringBuilder) {
        Stats stats = new Stats(mCol, mDeckId);
        int[] todayStats = stats.calculateTodayStats();
        stringBuilder.append(_title(mWebView.getResources().getString(R.string.stats_today)));
        Resources res = mWebView.getResources();
        final int minutes = (int) Math.round(todayStats[THETIME_INDEX] / 60.0);
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
            stringBuilder.append(res.getString(R.string.stats_today_mature_cards, todayStats[MSUM_INDEX], todayStats[MCNT_INDEX], (todayStats[MSUM_INDEX] / (float) (todayStats[MCNT_INDEX]) * 100.0)));
        } else {
            stringBuilder.append(res.getString(R.string.stats_today_no_mature_cards));
        }
    }


    private String _title(String title) {
        return "<h1>" + title + "</h1>";
    }


    private String _subtitle(String title) {
        return "<h3>" + title + "</h3>";
    }

    // This is a copy of Stats#calculateDue that is more similar to the original desktop version which
    // allows us to easily fetch the values required for the summary. In the future, this version
    // should replace the one in Stats.java.
    private void calculateForecastOverview(Stats.AxisType type, OverviewStats oStats) {
        Integer start = null;
        Integer end = null;
        int chunk = 0;
        switch (type) {
            case TYPE_MONTH:
                start = 0; end = 31; chunk = 1;
                break;
            case TYPE_YEAR:
                start = 0; end = 52; chunk = 7;
                break;
            case TYPE_LIFE:
                start = 0; end = null; chunk = 30;
                break;
        }
        List<int[]> d = _due(start, end, chunk);
        List<int[]> yng = new ArrayList<>();
        List<int[]> mtr = new ArrayList<>();
        int tot = 0;
        List<int[]> totd = new ArrayList<>();
        for (int[] day : d) {
            yng.add(new int[] {day[0], day[1]});
            mtr.add(new int[] {day[0], day[2]});
            tot += day[1]+day[2];
            totd.add(new int[] {day[0], tot});
        }

        // Fill in the overview stats
        oStats.forecastTotalReviews = tot;
        oStats.forecastAverageReviews = totd.size() == 0 ? 0 : (double) tot / (totd.size() * chunk);
        oStats.forecastDueTomorrow = mCol.getDb().queryScalar(
                "select count() from cards where did in " + _limit() + " and queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") " +
                        "and due = ?", new Object[]{mCol.getSched().getToday() + 1});
    }

    private List<int[]> _due(Integer start, Integer end, int chunk) {
        String lim = "";
        if (start != null) {
            lim += String.format(Locale.US, " and due-%d >= %d", mCol.getSched().getToday(), start);
        }
        if (end != null) {
            lim += String.format(Locale.US, " and day < %d", end);
        }

        List<int[]> d = new ArrayList<>();
        Cursor cur = null;
        try {
            String query;
            query = String.format(Locale.US,
                    "select (due-%d)/%d as day,\n" +
                    "sum(case when ivl < 21 then 1 else 0 end), -- yng\n" +
                    "sum(case when ivl >= 21 then 1 else 0 end) -- mtr\n" +
                    "from cards\n" +
                    "where did in %s and queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")\n" +
                    "%s\n" +
                    "group by day order by day",
                    mCol.getSched().getToday(), chunk, _limit(), lim);
            cur = mCol.getDb().getDatabase().query(query, null);
            while (cur.moveToNext()) {
                d.add(new int[]{cur.getInt(0), cur.getInt(1), cur.getInt(2)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return d;
    }


    private String _limit() {
        return Stats.deckLimit(mDeckId, mCol);
    }
}
