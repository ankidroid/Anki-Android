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

import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.wildplot.android.rendering.ChartView;
import com.wildplot.android.rendering.PlotSheet;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AnkiStatsTaskHandler {

    private static AnkiStatsTaskHandler sInstance;

    private Collection mCollectionData;
    private float mStandardTextSize = 10f;
    private int mStatType = Stats.TYPE_MONTH;

    private static boolean sIsWholeCollection = false;
    private Lock lock = new ReentrantLock();


    public AnkiStatsTaskHandler(){
        sInstance = this;
        mCollectionData = AnkiDroidApp.getCol();
    }

    public static void setIsWholeCollection(boolean isWholeCollection){
        sIsWholeCollection = isWholeCollection;
    }

    public static AnkiStatsTaskHandler getInstance() {
        return sInstance;
    }

    public CreateChartTask createChart(Stats.ChartType chartType, View... views){
        CreateChartTask createChartTask = new CreateChartTask(chartType);
        createChartTask.execute(views);
        return createChartTask;
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
            //make sure only one task of CreateChartTask is running, first to run should get lock
            //only necessary on lower APIs because after honeycomb only one thread is used for all asynctasks
            lock.lock();
            try {
                if (!mIsRunning) {
                    Log.d(AnkiDroidApp.TAG, "quiting CreateChartTask(" + mChartType.name() + ") before execution");
                    return null;
                } else
                    Log.d(AnkiDroidApp.TAG, "starting Create ChartTask, type: " + mChartType.name());
                mImageView = (ChartView) params[0];
                mProgressBar = (ProgressBar) params[1];
                ChartBuilder chartBuilder = new ChartBuilder(mImageView, mCollectionData, sIsWholeCollection, mChartType);
                return chartBuilder.renderChart(mStatType);
            }finally {
                lock.unlock();
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
