package com.ichi2.anki.stats;

import android.os.AsyncTask;
import android.view.View;
import android.widget.ProgressBar;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.wildplot.android.rendering.ChartView;
import com.wildplot.android.rendering.PlotSheet;

/**
 * Created by mig on 06.07.2014.
 */
public class AnkiStatsTaskHandler {
    /**
     * Tag for logging messages.
     */
    public static final String TAG = "AnkiDroidStats";
    private static AnkiStatsTaskHandler sInstance;

    private Collection mCollectionData;
    private float mStandardTextSize = 10f;
    private Stats.ChartPeriodType mStatType = Stats.ChartPeriodType.MONTH;

    private static boolean sIsWholeCollection = false;


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

    public void createChart(Stats.ChartType chartType, View... views){
        CreateChartTask createChartTask = new CreateChartTask(chartType);
        createChartTask.execute(views);
    }


    private class CreateChartTask extends AsyncTask<View, Void, PlotSheet>{
        ChartView mImageView;
        ProgressBar mProgressBar;

        private Stats.ChartType mChartType;

        public CreateChartTask(Stats.ChartType chartType){
            super();
            mChartType = chartType;
        }

        @Override
        protected PlotSheet doInBackground(View... params) {
            mImageView = (ChartView)params[0];
            mProgressBar = (ProgressBar) params[1];
            ChartBuilder chartBuilder = new ChartBuilder(mImageView, mCollectionData, sIsWholeCollection, mChartType);
            return chartBuilder.renderChart(mStatType);
        }

        @Override
        protected void onPostExecute(PlotSheet plotSheet) {
            if(plotSheet != null){

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

    public Stats.ChartPeriodType getStatType() {
        return mStatType;
    }

    public void setStatType(Stats.ChartPeriodType mStatType) {
        this.mStatType = mStatType;
    }

}
