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
import android.webkit.WebView;

import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.ichi2.themes.Themes;

public class InfoStatsBuilder {
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
    private final boolean mIsWholeCollection;

    public InfoStatsBuilder(WebView chartView, Collection collectionData, boolean isWholeCollection){
        mWebView = chartView;
        mCollectionData = collectionData;
        mIsWholeCollection = isWholeCollection;
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

        stringBuilder.append("</center>");
        return stringBuilder.toString();
    }

    private void appendTodaysStats(StringBuilder stringBuilder){
        Stats stats = new Stats(mCollectionData, mIsWholeCollection);
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
        return _title(title, "");
    }

    private String _title(String title, String subtitle){
        return "<h1>" + title + "</h1>" + subtitle;
    }

    private String  bold(String s) {
        return "<b>" + s + "</b>";
    }

}
