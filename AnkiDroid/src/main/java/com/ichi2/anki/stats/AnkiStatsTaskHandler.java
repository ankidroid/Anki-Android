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
import android.util.Pair;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.stats.Stats;
import com.ichi2.themes.Themes;
import com.wildplot.android.rendering.PlotSheet;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;

public class AnkiStatsTaskHandler {

    private static AnkiStatsTaskHandler sInstance;

    private final Collection mCollectionData;
    private float mStandardTextSize = 10f;
    private Stats.AxisType mStatType = Stats.AxisType.TYPE_MONTH;
    private long mDeckId;
    private static final Lock sLock = new ReentrantLock();


    private AnkiStatsTaskHandler(Collection collection){
        mCollectionData = collection;
    }

    public void setDeckId(long deckId) {
        mDeckId = deckId;
    }

    public static AnkiStatsTaskHandler getInstance() {
        return sInstance;
    }

    public synchronized static AnkiStatsTaskHandler getInstance(Collection collection) {
        if (sInstance == null || sInstance.mCollectionData != collection) {
            sInstance = new AnkiStatsTaskHandler(collection);
        }
        return sInstance;
    }

    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public CreateChartTask createChart(Stats.ChartType chartType, View... views){
        CreateChartTask createChartTask = new CreateChartTask(chartType, mCollectionData, mStatType, mDeckId);
        createChartTask.execute(views);
        return createChartTask;
    }

    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public CreateStatisticsOverview createStatisticsOverview(View... views){
        CreateStatisticsOverview createChartTask = new CreateStatisticsOverview(mCollectionData, mStatType, mDeckId);
        createChartTask.execute(views);
        return createChartTask;
    }

    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public static DeckPreviewStatistics createReviewSummaryStatistics(Collection col, TextView view){
        DeckPreviewStatistics deckPreviewStatistics = new DeckPreviewStatistics();
        deckPreviewStatistics.execute(new Pair<>(col, view));
        return deckPreviewStatistics;
    }

    private static class CreateChartTask extends StatsAsyncTask<PlotSheet>{
        private WeakReference<ChartView> mImageView;
        private WeakReference<ProgressBar> mProgressBar;
        private final WeakReference<Collection> mCollectionData;
        private final Stats.AxisType mStatType;
        private final long mDeckId;

        private boolean mIsRunning = false;
        private final Stats.ChartType mChartType;

        public CreateChartTask(Stats.ChartType chartType, Collection collection, Stats.AxisType statType, long deckId){
            super();
            mIsRunning = true;
            mChartType = chartType;
            mCollectionData = new WeakReference<>(collection);
            mStatType = statType;
            mDeckId = deckId;
        }

        @Override
        protected PlotSheet doInBackgroundSafe(View... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            Collection collectionData = mCollectionData.get();
            try {
                if (!mIsRunning || (collectionData == null)) {
                    Timber.d("Quitting CreateChartTask (%s) before execution", mChartType.name());
                    return null;
                } else {
                    Timber.d("Starting CreateChartTask, type: %s", mChartType.name());
                }
                ChartView imageView = (ChartView) params[0];
                mImageView = new WeakReference<>(imageView);
                mProgressBar = new WeakReference<>((ProgressBar) params[1]);

                ChartBuilder chartBuilder = new ChartBuilder(imageView, collectionData,
                        mDeckId, mChartType);
                return chartBuilder.renderChart(mStatType);
            } finally {
                sLock.unlock();
            }
        }

        @SuppressWarnings("deprecation") // #7108: AsyncTask
        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @SuppressWarnings("deprecation") // #7108: AsyncTask
        @Override
        protected void onPostExecute(PlotSheet plotSheet) {
            ChartView imageView = mImageView.get();
            ProgressBar progressBar = mProgressBar.get();

            if ((plotSheet != null) && mIsRunning && (imageView != null) && (progressBar != null)) {
                imageView.setData(plotSheet);
                progressBar.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                imageView.invalidate();
            }
        }
    }

    private static class CreateStatisticsOverview extends StatsAsyncTask<String>{
        private WeakReference<WebView> mWebView;
        private WeakReference<ProgressBar> mProgressBar;
        private final WeakReference<Collection> mCollectionData;
        private final Stats.AxisType mStatType;
        private final long mDeckId;

        private boolean mIsRunning = false;

        public CreateStatisticsOverview(Collection collection, Stats.AxisType statType, long deckId){
            super();
            mIsRunning = true;
            mCollectionData = new WeakReference<>(collection);
            mStatType = statType;
            mDeckId = deckId;
        }

        @Override
        protected String doInBackgroundSafe(View... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            Collection collectionData = mCollectionData.get();
            try {
                if (!mIsRunning || (collectionData == null)) {
                    Timber.d("Quitting CreateStatisticsOverview before execution");
                    return null;
                } else {
                    Timber.d("Starting CreateStatisticsOverview");
                }

                WebView webView = (WebView) params[0];
                mWebView = new WeakReference<>(webView);
                mProgressBar = new WeakReference<>((ProgressBar) params[1]);

                OverviewStatsBuilder overviewStatsBuilder = new OverviewStatsBuilder(webView, collectionData, mDeckId, mStatType);
                return overviewStatsBuilder.createInfoHtmlString();
            } finally {
                sLock.unlock();
            }
        }

        @SuppressWarnings("deprecation") // #7108: AsyncTask
        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @SuppressWarnings("deprecation") // #7108: AsyncTask
        @Override
        protected void onPostExecute(String html) {
            WebView webView = mWebView.get();
            ProgressBar progressBar = mProgressBar.get();

            if ((html != null) && mIsRunning && (webView != null) && (progressBar != null)) {
                try {
                    webView.loadData(URLEncoder.encode(html, "UTF-8").replaceAll("\\+", " "), "text/html; charset=utf-8", "utf-8");
                } catch (UnsupportedEncodingException e) {
                    Timber.w(e);
                }
                progressBar.setVisibility(View.GONE);
                int backgroundColor = Themes.getColorFromAttr(webView.getContext(), android.R.attr.colorBackground);
                webView.setBackgroundColor(backgroundColor);
                webView.setVisibility(View.VISIBLE);
                webView.invalidate();
            }
        }
    }

    @SuppressWarnings("deprecation") // #7108: AsyncTask
    private static class DeckPreviewStatistics extends android.os.AsyncTask<Pair<Collection, TextView>, Void, String> {
        private WeakReference<TextView> mTextView;

        private boolean mIsRunning = true;

        public DeckPreviewStatistics() {
            super();
        }

        @Override
        protected String doInBackground(Pair<Collection, TextView>... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            try {
                Collection collection = params[0].first;

                TextView textView = params[0].second;
                mTextView = new WeakReference<>(textView);

                if (!mIsRunning || collection == null || collection.getDb() == null) {
                    Timber.d("Quitting DeckPreviewStatistics before execution");
                    return null;
                } else {
                    Timber.d("Starting DeckPreviewStatistics");
                }

                //eventually put this in Stats (in desktop it is not though)
                int cards;
                int minutes;
                String query = "select sum(case when ease > 0 then 1 else 0 end), "+ /* cards, excludes rescheduled cards https://github.com/ankidroid/Anki-Android/issues/8592 */
                "sum(time)/1000 from revlog where id > " + ((collection.getSched().getDayCutoff() - SECONDS_PER_DAY) * 1000);
                Timber.d("DeckPreviewStatistics query: %s", query);

                try (Cursor cur = collection.getDb()
                            .query(query)) {

                    cur.moveToFirst();
                    cards = cur.getInt(0);
                    minutes = (int) Math.round(cur.getInt(1) / 60.0);
                }
                Resources res = textView.getResources();
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
            TextView textView = mTextView.get();

            if ((todayStatString != null) && mIsRunning && (textView != null)) {
                textView.setText(todayStatString);
                textView.setVisibility(View.VISIBLE);
                textView.invalidate();
            }
        }
    }



    public float getmStandardTextSize() {
        return mStandardTextSize;
    }

    public void setmStandardTextSize(float standardTextSize) {
        this.mStandardTextSize = standardTextSize;
    }

    public Stats.AxisType getStatType() {
        return mStatType;
    }

    public void setStatType(Stats.AxisType statType) {
        this.mStatType = statType;
    }

}
