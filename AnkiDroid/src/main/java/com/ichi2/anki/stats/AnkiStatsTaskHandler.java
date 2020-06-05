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
import android.os.AsyncTask;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.ichi2.themes.Themes;
import com.wildplot.android.rendering.PlotSheet;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class AnkiStatsTaskHandler {

    private static AnkiStatsTaskHandler sInstance;

    private Collection mCollectionData;
    private float mStandardTextSize = 10f;
    private Stats.AxisType mStatType = Stats.AxisType.TYPE_MONTH;
    private long mDeckId;
    private static Lock sLock = new ReentrantLock();


    public AnkiStatsTaskHandler(Collection collection){
        sInstance = this;
        mCollectionData = collection;
    }

    public void setDeckId(long deckId) {
        mDeckId = deckId;
    }

    public static AnkiStatsTaskHandler getInstance() {
        return sInstance;
    }

    public CreateChartTask createChart(Stats.ChartType chartType, View... views){
        CreateChartTask createChartTask = new CreateChartTask(chartType);
        createChartTask.execute(views);
        return createChartTask;
    }
    public CreateStatisticsOverview createStatisticsOverview(View... views){
        CreateStatisticsOverview createChartTask = new CreateStatisticsOverview();
        createChartTask.execute(views);
        return createChartTask;
    }
    public static DeckPreviewStatistics createReviewSummaryStatistics(Collection col, TextView view){
        DeckPreviewStatistics deckPreviewStatistics = new DeckPreviewStatistics();
        deckPreviewStatistics.execute(col, view);
        return deckPreviewStatistics;
    }

    private class CreateChartTask extends AsyncTask<View, Void, PlotSheet>{
        private ChartView mImageView;
        private ProgressBar mProgressBar;

        private boolean mIsRunning = false;
        private Stats.ChartType mChartType;

        public CreateChartTask(Stats.ChartType chartType){
            super();
            mIsRunning = true;
            mChartType = chartType;
        }

        @Override
        protected PlotSheet doInBackground(View... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            try {
                if (!mIsRunning) {
                    Timber.d("Quitting CreateChartTask (%s) before execution", mChartType.name());
                    return null;
                } else {
                    Timber.d("Starting CreateChartTask, type: %s", mChartType.name());
                }
                mImageView = (ChartView) params[0];
                mProgressBar = (ProgressBar) params[1];
                ChartBuilder chartBuilder = new ChartBuilder(mImageView, mCollectionData,
                        mDeckId, mChartType);
                return chartBuilder.renderChart(mStatType);
            } finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(PlotSheet plotSheet) {
            if (plotSheet != null && mIsRunning) {
                mImageView.setData(plotSheet);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
                mImageView.invalidate();
            }
        }
    }

    private class CreateStatisticsOverview extends AsyncTask<View, Void, String>{
        private WebView mWebView;
        private ProgressBar mProgressBar;

        private boolean mIsRunning = false;

        public CreateStatisticsOverview(){
            super();
            mIsRunning = true;
        }

        @Override
        protected String doInBackground(View... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            try {
                if (!mIsRunning) {
                    Timber.d("Quitting CreateStatisticsOverview before execution");
                    return null;
                } else {
                    Timber.d("Starting CreateStatisticsOverview");
                }
                mWebView = (WebView) params[0];
                mProgressBar = (ProgressBar) params[1];
                OverviewStatsBuilder overviewStatsBuilder = new OverviewStatsBuilder(mWebView, mCollectionData, mDeckId, mStatType);
                return overviewStatsBuilder.createInfoHtmlString();
            } finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(String html) {
            if (html != null && mIsRunning) {
                try {
                    mWebView.loadData(URLEncoder.encode(html, "UTF-8").replaceAll("\\+", " "), "text/html; charset=utf-8", "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                mProgressBar.setVisibility(View.GONE);
                int backgroundColor = Themes.getColorFromAttr(mWebView.getContext(), android.R.attr.colorBackground);
                mWebView.setBackgroundColor(backgroundColor);
                mWebView.setVisibility(View.VISIBLE);
                mWebView.invalidate();
            }
        }
    }

    private static class DeckPreviewStatistics extends AsyncTask<Object, Void, String> {
        private TextView mTextView;

        private boolean mIsRunning = false;

        public DeckPreviewStatistics() {
            super();
            mIsRunning = true;
        }

        @Override
        protected String doInBackground(Object... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            try {
                Collection collection = (Collection) params[0];
                if (!mIsRunning || collection == null || collection.getDb() == null) {
                    Timber.d("Quitting DeckPreviewStatistics before execution");
                    return null;
                } else {
                    Timber.d("Starting DeckPreviewStatistics");
                }
                mTextView = (TextView) params[1];

                //eventually put this in Stats (in desktop it is not though)
                int cards;
                int minutes;
                Cursor cur = null;
                String query = "select count(), sum(time)/1000 from revlog where id > " + ((collection.getSched().getDayCutoff() - 86400) * 1000);
                Timber.d("DeckPreviewStatistics query: " + query);

                try {
                    cur = collection.getDb()
                            .getDatabase()
                            .query(query, null);

                    cur.moveToFirst();
                    cards = cur.getInt(0);
                    minutes = (int) Math.round(cur.getInt(1) / 60.0);
                } finally {
                    if (cur != null && !cur.isClosed()) {
                        cur.close();
                    }
                }
                Resources res = collection.getContext().getResources();
                final String span = res.getQuantityString(R.plurals.in_minutes, minutes, minutes);
                return res.getQuantityString(R.plurals.studied_cards_today, cards, cards, span);
            } finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(String todayStatString) {
            if (todayStatString != null && mIsRunning) {
                mTextView.setText(todayStatString);
                mTextView.setVisibility(View.VISIBLE);
                mTextView.invalidate();
            }
        }
    }



    public float getmStandardTextSize() {
        return mStandardTextSize;
    }

    public void setmStandardTextSize(float mStandardTextSize) {
        this.mStandardTextSize = mStandardTextSize;
    }

    public Stats.AxisType getStatType() {
        return mStatType;
    }

    public void setStatType(Stats.AxisType mStatType) {
        this.mStatType = mStatType;
    }

}
