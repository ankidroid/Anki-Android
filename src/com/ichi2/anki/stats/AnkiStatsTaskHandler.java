package com.ichi2.anki.stats;

import android.annotation.TargetApi;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;

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
    private int mStatType = Stats.TYPE_MONTH;
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


    public static void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        if(android.os.Build.VERSION.SDK_INT >= 16)
            disableDatabaseWriteAheadLogging(db, true);
    }
    @TargetApi(16)
    public static void disableDatabaseWriteAheadLogging(SQLiteDatabase db, boolean is16) {
        db.disableWriteAheadLogging();
    }


    public void createForecastChart(View... views){
        CreateForecastChartTask createForecastChartTask = new CreateForecastChartTask();
        createForecastChartTask.execute(views);
    }

    public void createReviewCountChart(View... views){
        CreateReviewCountTask createReviewCountTask = new CreateReviewCountTask();
        createReviewCountTask.execute(views);
    }
    public void createReviewTimeChart(View... views){
        CreateReviewTimeTask createReviewTimeTask = new CreateReviewTimeTask();
        createReviewTimeTask.execute(views);
    }
    public void createIntervalChart(View... views){
        CreateIntervalTask createIntervalTask = new CreateIntervalTask();
        createIntervalTask.execute(views);
    }
    public void createBreakdownChart(View... views){
        CreateBreakdownTask createBreakdownTask = new CreateBreakdownTask();
        createBreakdownTask.execute(views);
    }
    public void createWeeklyBreakdownChart(View... views){
        CreateWeeklyBreakdownTask createWeeklyBreakdownTask = new CreateWeeklyBreakdownTask();
        createWeeklyBreakdownTask.execute(views);
    }
    public void createAnswerButtonTask(View... views){
        CreateAnswerButtonTask createAnswerButtonTask = new CreateAnswerButtonTask();
        createAnswerButtonTask.execute(views);
    }
    public void createCardsTypesTask(View... views){
        CreateCardsTypesChart createCardsTypesChart = new CreateCardsTypesChart();
        createCardsTypesChart.execute(views);
    }

    private class CreateForecastChartTask extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            Forecast forecast = new Forecast(mImageView, mCollectionData, sIsWholeCollection);
            return forecast.renderChart(mStatType);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){

                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
                mImageView.setImageBitmap(bitmap);
            }
        }

    }
    private class CreateReviewCountTask extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            ReviewCount reviewCount = new ReviewCount(mImageView, mCollectionData, sIsWholeCollection);
            return reviewCount.renderChart(mStatType, true);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){
                mImageView.setImageBitmap(bitmap);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
            }
        }

    }
    private class CreateReviewTimeTask extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            //int tag = (Integer)mImageView.getTag();
            ReviewCount reviewCount = new ReviewCount(mImageView, mCollectionData, sIsWholeCollection);
            return reviewCount.renderChart(mStatType, false);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){
                mImageView.setImageBitmap(bitmap);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
            }
        }

    }
    private class CreateIntervalTask extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            //int tag = (Integer)mImageView.getTag();

            Intervals intervals = new Intervals(mImageView, mCollectionData, sIsWholeCollection);
            return intervals.renderChart(mStatType);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){
                mImageView.setImageBitmap(bitmap);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
            }
        }

    }
    private class CreateBreakdownTask extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            //int tag = (Integer)mImageView.getTag();

            HourlyBreakdown hourlyBreakdown = new HourlyBreakdown(mImageView, mCollectionData, sIsWholeCollection);
            return hourlyBreakdown.renderChart(mStatType);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){
                mImageView.setImageBitmap(bitmap);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
            }
        }

    }
    private class CreateWeeklyBreakdownTask extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            //int tag = (Integer)mImageView.getTag();

            WeeklyBreakdown weeklyBreakdown = new WeeklyBreakdown(mImageView, mCollectionData, sIsWholeCollection);
            return weeklyBreakdown.renderChart(mStatType);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){
                mImageView.setImageBitmap(bitmap);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
            }
        }

    }
    private class CreateAnswerButtonTask extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            //int tag = (Integer)mImageView.getTag();

            AnswerButton answerButton = new AnswerButton(mImageView, mCollectionData, sIsWholeCollection);
            return answerButton.renderChart(mStatType);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){
                mImageView.setImageBitmap(bitmap);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
            }
        }

    }
    private class CreateCardsTypesChart extends AsyncTask<View, Void, Bitmap>{
        ImageView mImageView;
        ProgressBar mProgressBar;
        @Override
        protected Bitmap doInBackground(View... params) {
            mImageView = (ImageView)params[0];
            mProgressBar = (ProgressBar) params[1];
            //int tag = (Integer)mImageView.getTag();

            CardsTypes cardsTypes = new CardsTypes(mImageView, mCollectionData, sIsWholeCollection);
            return cardsTypes.renderChart(mStatType);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null){
                mImageView.setImageBitmap(bitmap);
                mProgressBar.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
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
