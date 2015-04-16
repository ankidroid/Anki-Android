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

import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v4.view.ViewPager;

import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.Statistics;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.ichi2.libanki.Utils;
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
    private int mStatType = Stats.TYPE_MONTH;

    private static long sSelectedDeckId;
    private static Lock sLock = new ReentrantLock();


    public AnkiStatsTaskHandler(Collection collection){
        sInstance = this;
        mCollectionData = collection;
        sSelectedDeckId = mCollectionData.getDecks().selected();
    }

    public static long getSelectedDeckId() {
        return sSelectedDeckId;
    }

    public static void setsSelectedDeckId(long sSelectedDeckId) {
        AnkiStatsTaskHandler.sSelectedDeckId = sSelectedDeckId;
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
    public static CreateSmallTodayOverview createSmallTodayOverview(Collection col, TextView view){
        CreateSmallTodayOverview createSmallTodayOverview = new CreateSmallTodayOverview();
        createSmallTodayOverview.execute(col, view);
        return createSmallTodayOverview;
    }
    public static CreateSmallDueChart createSmallDueChartChart(Collection col, double[][] seriesList, View... views){
        CreateSmallDueChart createChartTask = new CreateSmallDueChart(col, seriesList);
        createChartTask.execute(views);
        return createChartTask;
    }

    public static CreateFirstStatisticChooserTask createFirstStatisticChooserTask(Collection col, ViewPager viewPager){
        CreateFirstStatisticChooserTask createFirstStatisticChooserTask = new CreateFirstStatisticChooserTask();
        createFirstStatisticChooserTask.execute(col, viewPager);
        return createFirstStatisticChooserTask;
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
                    Timber.d("quiting CreateChartTask (%s) before execution", mChartType.name());
                    return null;
                } else
                    Timber.d("starting Create ChartTask, type: %s", mChartType.name());
                mImageView = (ChartView) params[0];
                mProgressBar = (ProgressBar) params[1];
                ChartBuilder chartBuilder = new ChartBuilder(mImageView, mCollectionData,
                        NavigationDrawerActivity.isWholeCollection(), mChartType);
                return chartBuilder.renderChart(mStatType);
            }finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(PlotSheet plotSheet) {
            if(plotSheet != null && mIsRunning){

                mImageView.setData(plotSheet);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
                mImageView.invalidate();

            }
        }

    }
    private static class CreateSmallDueChart extends AsyncTask<View, Void, PlotSheet>{
        private ChartView mImageView;
        private double[][] mSeriesList;
        private boolean mIsRunning = false;
        private Collection mCollection;


        public CreateSmallDueChart(Collection col, double[][] seriesList){
            super();
            mIsRunning = true;
            mSeriesList = seriesList;
            mCollection = col;
        }

        @Override
        protected PlotSheet doInBackground(View... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            try {
                if (!mIsRunning) {
                    Timber.d("quiting CreateSmallDueChart before execution");
                    return null;
                } else
                    Timber.d("starting CreateSmallDueChart");
                mImageView = (ChartView) params[0];
                ChartBuilder chartBuilder = new ChartBuilder(mImageView, mCollection,
                        NavigationDrawerActivity.isWholeCollection(), Stats.ChartType.OTHER);
                return chartBuilder.createSmallDueChart(mSeriesList);
            }finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(PlotSheet plotSheet) {
            if(plotSheet != null && mIsRunning){

                mImageView.setData(plotSheet);
                mImageView.setVisibility(View.VISIBLE);
                mImageView.invalidate();
                Timber.d("finished CreateSmallDueChart");
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
                    Timber.d("quiting CreateStatisticsOverview before execution");
                    return null;
                } else
                    Timber.d("starting CreateStatisticsOverview" );
                mWebView = (WebView) params[0];
                mProgressBar = (ProgressBar) params[1];
                String html = "";
                InfoStatsBuilder infoStatsBuilder = new InfoStatsBuilder(mWebView, mCollectionData,
                        NavigationDrawerActivity.isWholeCollection());
                html = infoStatsBuilder.createInfoHtmlString();
                return html;
            }finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(String html) {
            if(html != null && mIsRunning){

                try {
                    mWebView.loadData(URLEncoder.encode(html, "UTF-8").replaceAll("\\+"," "), "text/html; charset=utf-8",  "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                mProgressBar.setVisibility(View.GONE);
                mWebView.setVisibility(View.VISIBLE);
                mWebView.invalidate();

            }
        }

    }

    private static class CreateSmallTodayOverview extends AsyncTask<Object, Void, String>{
        private TextView mTextView;

        private boolean mIsRunning = false;

        public CreateSmallTodayOverview(){
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
                    Timber.d("quiting CreateSmallTodayOverview before execution");
                    return null;
                } else
                    Timber.d("starting CreateSmallTodayOverview" );
                mTextView = (TextView) params[1];

                //eventually put this in Stats (in desktop it is not though)
                int cards, thetime;
                Cursor cur = null;
                String query = "select count(), sum(time)/1000 from revlog where id > " + ((collection.getSched().getDayCutoff()-86400)*1000);
                Timber.d("CreateSmallTodayOverview query: " + query);

                try {
                    cur = collection.getDb()
                            .getDatabase()
                            .rawQuery(query, null);

                    cur.moveToFirst();
                    cards = cur.getInt(0);
                    thetime = cur.getInt(1);


                } finally {
                    if (cur != null && !cur.isClosed()) {
                        cur.close();
                    }
                }
                return mTextView.getResources().getQuantityString(R.plurals.stats_today_cards_not_bold, cards, cards, Utils.fmtTimeSpan(thetime, 1));
            }finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(String todayStatString) {
            if(todayStatString != null && mIsRunning){
                mTextView.setText(todayStatString);
                mTextView.setVisibility(View.VISIBLE);
                mTextView.invalidate();
            }
        }

    }

    private static class CreateFirstStatisticChooserTask extends AsyncTask<Object, Void, Integer>{
        private ViewPager mViewPager;

        private boolean mIsRunning = false;

        public CreateFirstStatisticChooserTask(){
            super();
            mIsRunning = true;
        }

        @Override
        protected Integer doInBackground(Object... params) {
            //make sure only one task of CreateChartTask is running, first to run should get sLock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            sLock.lock();
            try {
                Collection collection = (Collection) params[0];
                if (!mIsRunning || collection == null || collection.getDb() == null) {
                    Timber.d("quiting CreateTodayLearnCountOnly before execution");
                    return null;
                } else
                    Timber.d("starting CreateTodayLearnCountOnly" );
                mViewPager = (ViewPager) params[1];

                //eventually put this in Stats (in desktop it is not though)
                int cards;
                Cursor cur = null;
                String query = "select count() from revlog where id > " + ((collection.getSched().getDayCutoff()-86400)*1000);
                Timber.d("CreateSmallTodayOverview query: " + query);

                try {
                    cur = collection.getDb()
                            .getDatabase()
                            .rawQuery(query, null);

                    cur.moveToFirst();
                    cards = cur.getInt(0);



                } finally {
                    if (cur != null && !cur.isClosed()) {
                        cur.close();
                    }
                }
                return cards;
            }finally {
                sLock.unlock();
            }
        }

        @Override
        protected void onCancelled() {
            mIsRunning = false;
        }

        @Override
        protected void onPostExecute(Integer todayStatString) {
            if(todayStatString != null && mIsRunning && mViewPager != null){
                int chosen = todayStatString;
                switch (chosen){
                    case 0:
                        mViewPager.setCurrentItem(Statistics.FORECAST_TAB_POSITION);
                }
            }
        }

    }


    public float getmStandardTextSize() {
        return mStandardTextSize;
    }
    public void setmStandardTextSize(float mStandardTextSize) {
        this.mStandardTextSize = mStandardTextSize;
    }

    public int getStatType() {
        return mStatType;
    }

    public void setStatType(int mStatType) {
        this.mStatType = mStatType;
    }

}
