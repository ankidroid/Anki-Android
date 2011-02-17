/**
 * Copyright (C) 2009, 2010 SC 4ViewSoft SRL
 * Copyright (C) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ichi2.charts;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.BarChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.graphics.Paint.Align;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.MyAnimation;
import com.ichi2.anki.R;
import com.ichi2.anki.Statistics;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class ChartBuilder extends Activity {
    public static final String TYPE = "type";

    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();

    private GraphicalView mChartView;
    private TextView mTitle;
    private double[] mPan;
    private int zoom = 0;

    private boolean mFullScreen;

    private static final int MENU_FULLSCREEN = 0;
    private static final int MENU_ZOOM_IN = 1;
    private static final int MENU_ZOOM_OUT = 2;


    @Override
    protected void onRestoreInstanceState(Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        mDataset = (XYMultipleSeriesDataset) savedState.getSerializable("dataset");
        mRenderer = (XYMultipleSeriesRenderer) savedState.getSerializable("renderer");
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("dataset", mDataset);
        outState.putSerializable("renderer", mRenderer);
    }


    public void setDataset(int row) {
        XYSeries series = new XYSeries(Statistics.Titles[row]);
        for (int i = 0; i < Statistics.xAxisData.length; i++) {
            series.add(Statistics.xAxisData[i], Statistics.mSeriesList[row][i]);
        }
        mDataset.addSeries(series);
    }


    public void setRenderer(int row) {
        Resources res = getResources();
        XYSeriesRenderer renderer = new XYSeriesRenderer();
        if (row == 0) {
            renderer.setColor(res.getColor(R.color.statistics_all_cards));
        } else if (row == 1) {
            renderer.setColor(res.getColor(R.color.statistics_mature_cards));
        } else {
            renderer.setColor(res.getColor(R.color.next_time_failed_color));
        }
        mRenderer.addSeriesRenderer(renderer);
    }


    private void zoom() {
        if (mChartView != null) {
            if (zoom > 0) {
                mRenderer.setXAxisMin(mPan[0] / (zoom + 1));
                mRenderer.setXAxisMax(mPan[1] / (zoom + 1));
            } else {
                mRenderer.setXAxisMin(mPan[0]);
                mRenderer.setXAxisMax(mPan[1]);
            }
            mChartView = ChartFactory.getBarChartView(this, mDataset, mRenderer, BarChart.Type.STACKED);
            LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
            layout.addView(mChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        }
    }


    public void closeChartBuilder() {
        finish();
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
            MyAnimation.slide(this, MyAnimation.UP);
        }
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mFullScreen = preferences.getBoolean("fullScreen", false);
        return preferences;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(Menu.NONE, MENU_FULLSCREEN, Menu.NONE, R.string.statistics_fullscreen);
        item.setIcon(R.drawable.ic_menu_manage);
        item = menu.add(Menu.NONE, MENU_ZOOM_IN, Menu.NONE, R.string.statistics_zoom_in);
        item.setIcon(R.drawable.ic_menu_zoom);
        item = menu.add(Menu.NONE, MENU_ZOOM_OUT, Menu.NONE, R.string.statistics_zoom_out);
        item.setIcon(R.drawable.ic_menu_zoom);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_ZOOM_IN).setEnabled(zoom < 10);
        menu.findItem(MENU_ZOOM_OUT).setEnabled(zoom > 0);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_FULLSCREEN:
                SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
                Editor editor = preferences.edit();
                editor.putBoolean("fullScreen", !mFullScreen);
                editor.commit();
                finish();
                Intent intent = new Intent(this, com.ichi2.charts.ChartBuilder.class);
                startActivity(intent);
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    MyAnimation.slide(this, MyAnimation.FADE);
                }
                return true;
            case MENU_ZOOM_IN:
                zoom += 1;
                zoom();
                return true;
            case MENU_ZOOM_OUT:
                if (zoom > 0) {
                    zoom -= 1;
                }
                zoom();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restorePreferences();
        if (mFullScreen) {
            getWindow()
                    .setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        setContentView(R.layout.statistics);
        mTitle = (TextView) findViewById(R.id.statistics_title);
        if (mChartView == null) {
            if (mFullScreen) {
                mTitle.setText(Statistics.sTitle);
            } else {
                setTitle(Statistics.sTitle);
                mTitle.setVisibility(View.GONE);
            }
            for (int i = 0; i < Statistics.mSeriesList.length; i++) {
                setDataset(i);
                setRenderer(i);
            }
            if (Statistics.mSeriesList.length == 1) {
                mRenderer.setShowLegend(false);
            }
            mPan = new double[] { Statistics.xAxisData[0] - 1,
                    Statistics.xAxisData[Statistics.xAxisData.length - 1] + 1 };
            mRenderer.setLegendTextSize(17);
            mRenderer.setLegendHeight(60);
            mRenderer.setAxisTitleTextSize(17);
            mRenderer.setLabelsTextSize(17);
            mRenderer.setXAxisMin(mPan[0]);
            mRenderer.setXAxisMax(mPan[1]);
            mRenderer.setYAxisMin(0);
            mRenderer.setXTitle(Statistics.axisLabels[0]);
            mRenderer.setYTitle(Statistics.axisLabels[1]);
            mRenderer.setZoomEnabled(false, false);
            if (Statistics.mSeriesList[0][0] > 100 || Statistics.mSeriesList[0][1] > 100 || Statistics.mSeriesList[0][Statistics.mSeriesList[0].length - 1] > 100) {
                mRenderer.setMargins(new int[] { 15, 50, 25, 0 });
            } else {
                mRenderer.setMargins(new int[] { 15, 42, 25, 0 });
            }
            mRenderer.setPanEnabled(true, false);
            mRenderer.setPanLimits(mPan);
            mRenderer.setXLabelsAlign(Align.CENTER);
            mRenderer.setYLabelsAlign(Align.RIGHT);
            mChartView = ChartFactory.getBarChartView(this, mDataset, mRenderer, BarChart.Type.STACKED);
            LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
            layout.addView(mChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        } else {
            mChartView.repaint();
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "ChartBuilder - onBackPressed()");
            closeChartBuilder();
        }
        return super.onKeyDown(keyCode, event);
    }
}
