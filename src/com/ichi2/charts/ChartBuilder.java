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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki2.R;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskListener;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class ChartBuilder extends Activity {
    public static final String TYPE = "type";
    public static final int ZOOM_MAX = 20;

    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();

    private GraphicalView mChartView;
    private TextView mTitle;
    private double[] mPan;
    private int zoom = 0;

    private boolean mFullScreen;

    private Stats mStats;
    private double[][] mSeriesList;

    private static final int MENU_FULLSCREEN = 0;
    private static final int MENU_ZOOM_IN = 1;
    private static final int MENU_ZOOM_OUT = 2;

	/**
     * Swipe Detection
     */    
 	private GestureDetector gestureDetector;
 	View.OnTouchListener gestureListener;
 	private boolean mSwipeEnabled;

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

    private void zoom() {
        if (mChartView != null) {
            if (zoom > 0) {
                mRenderer.setXAxisMin(mPan[0] / (zoom + 1));
                mRenderer.setXAxisMax(mPan[1] / (zoom + 1));
            } else {
                mRenderer.setXAxisMin(mPan[0]);
                mRenderer.setXAxisMax(mPan[1]);
            }
//            mChartView = ChartFactory.getBarChartView(this, mDataset, mRenderer, BarChart.Type.STACKED);
            LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
            layout.addView(mChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        }
    }


    public void closeChartBuilder() {
        finish();
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
            ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.UP);
        }
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mFullScreen = preferences.getBoolean("fullScreen", false);
		mSwipeEnabled = preferences.getBoolean("swipe", false);
        return preferences;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(Menu.NONE, MENU_FULLSCREEN, Menu.NONE, R.string.statistics_fullscreen);
        item.setIcon(R.drawable.ic_menu_manage);
        item = menu.add(Menu.NONE, MENU_ZOOM_IN, Menu.NONE, R.string.statistics_zoom_in);
        item.setIcon(R.drawable.ic_menu_zoom_in);
        item = menu.add(Menu.NONE, MENU_ZOOM_OUT, Menu.NONE, R.string.statistics_zoom_out);
        item.setIcon(R.drawable.ic_menu_zoom_out);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_ZOOM_IN).setEnabled(zoom < ZOOM_MAX);
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
//                Statistics.sZoom = zoom;
                editor.commit();
                finish();
                Intent intent = new Intent(this, com.ichi2.charts.ChartBuilder.class);
                startActivity(intent);
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
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

    
//    public void setRenderer(int type, int row) {
//        Resources res = getResources();
//        XYSeriesRenderer renderer = new XYSeriesRenderer();
//        if (type <= 2) {
//        	switch (row) {
//        	case 0: 
//                renderer.setColor(res.getColor(R.color.statistics_due_young_cards));
//        		break;
//        	case 1:
//                renderer.setColor(res.getColor(R.color.statistics_due_mature_cards));
//                break;
//        	case 2:
////                renderer.setColor(res.getColor(R.color.statistics_due_failed_cards));
//        		break;
//        	}
//        } else if (type == 3) {
//        	switch (row) {
//        	case 0: 
////                renderer.setColor(res.getColor(R.color.statistics_reps_new_cards));
//        		break;
//        	case 1:
//                renderer.setColor(res.getColor(R.color.statistics_reps_young_cards));
//                break;
//        	case 2:
//                renderer.setColor(res.getColor(R.color.statistics_reps_mature_cards));
//        		break;
//        	}
//        } else {
//            renderer.setColor(res.getColor(R.color.statistics_default));        	
//        }
//        mRenderer.addSeriesRenderer(renderer);
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        restorePreferences();
        Resources res = getResources();

        mStats = Stats.currentStats();
        mSeriesList = mStats.getSeriesList();
        Object[] ob = mStats.getMetaInfo();
        int type = (Integer)ob[0];
        String title = res.getStringArray(R.array.statistics_type_labels)[type];
        int origin = (Integer)ob[1];
        int[] valueLabels = (int[])ob[2];
        int[] barColors = (int[])ob[3];
        int[] axisTitles = (int[])ob[4];

        if (mSeriesList == null || mSeriesList[0].length < 2) {
            Log.i(AnkiDroidApp.TAG, "ChartBuilder - Data variable empty, closing chartbuilder");
        	finish();
        	return;
        }
        if (mFullScreen) {
            getWindow()
                    .setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        View mainView = getLayoutInflater().inflate(R.layout.statistics, null);
        setContentView(mainView);
        mainView.setBackgroundColor(Color.WHITE);
        mTitle = (TextView) findViewById(R.id.statistics_title);
        if (mChartView == null) {
            if (mFullScreen) {
                mTitle.setText(title);
                mTitle.setTextColor(Color.BLACK);
            } else {
                setTitle(title);
                mTitle.setVisibility(View.GONE);
            }
            for (int i = 1; i < mSeriesList.length; i++) {
            	XYSeries series = new XYSeries(res.getString(valueLabels[i - 1]));
            	for (int j = 0; j < mSeriesList[i].length; j++) {
            		series.add(mSeriesList[0][j], mSeriesList[i][j]);
            	}
            	mDataset.addSeries(series);
                XYSeriesRenderer renderer = new XYSeriesRenderer();
                renderer.setColor(res.getColor(barColors[i - 1]));
                mRenderer.addSeriesRenderer(renderer);
            }
            if (mSeriesList.length == 1) {
                mRenderer.setShowLegend(false);
            }
            mPan = new double[] { origin - 0.5, mSeriesList[0][mSeriesList[0].length - 1] + 0.5 };
            mRenderer.setLegendTextSize(17);
            mRenderer.setBarSpacing(0.4);
            mRenderer.setLegendHeight(60);
            mRenderer.setAxisTitleTextSize(17);
            mRenderer.setLabelsTextSize(17);
            mRenderer.setXAxisMin(mPan[0]);
            mRenderer.setXAxisMax(mPan[1]);
            mRenderer.setYAxisMin(0);
            mRenderer.setGridColor(Color.LTGRAY);
            mRenderer.setShowGrid(true);
            mRenderer.setXTitle(res.getStringArray(R.array.due_x_axis_title)[axisTitles[0]]);
            mRenderer.setYTitle(res.getString(axisTitles[1]));
            mRenderer.setBackgroundColor(Color.WHITE);
            mRenderer.setMarginsColor(Color.WHITE);
            mRenderer.setAxesColor(Color.BLACK);
            mRenderer.setLabelsColor(Color.BLACK);
            mRenderer.setYLabelsColor(0, Color.BLACK);
            mRenderer.setYLabelsAngle(-90);
            mRenderer.setXLabelsColor(Color.BLACK);
            mRenderer.setXLabelsAlign(Align.CENTER);
            mRenderer.setYLabelsAlign(Align.CENTER);
            mRenderer.setZoomEnabled(false, false);
            mRenderer.setMargins(new int[] { 15, 40, 30, 10 });
            mRenderer.setAntialiasing(true);
            mRenderer.setPanEnabled(true, false);
            mRenderer.setPanLimits(mPan);
            mChartView = ChartFactory.getBarChartView(this, mDataset, mRenderer, BarChart.Type.STACKED);
            LinearLayout layout = (LinearLayout) findViewById(R.id.chart);
            layout.addView(mChartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        } else {
            mChartView.repaint();
        }
		gestureDetector = new GestureDetector(new MyGestureDetector());
        mChartView.setOnTouchListener(new View.OnTouchListener() {
        	public boolean onTouch(View v, MotionEvent event) {
        		if (gestureDetector.onTouchEvent(event)) {
        			return true;
        		}
        		return false;
        		}
        	});
		zoom = 0;
        if (zoom > 0) {
        	zoom();
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

    class MyGestureDetector extends SimpleOnGestureListener {	
    	@Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mSwipeEnabled) {
                try {
                	if (e1.getY() - e2.getY() > DeckPicker.sSwipeMinDistance && Math.abs(velocityY) > DeckPicker.sSwipeThresholdVelocity && Math.abs(e1.getX() - e2.getX()) < DeckPicker.sSwipeMaxOffPath) {
                		closeChartBuilder();
                    }
       			}
                catch (Exception e) {
                  	Log.e(AnkiDroidApp.TAG, "onFling Exception = " + e.getMessage());
                }
            }	            	
            return false;
    	}
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event))
	        return true;
	    else
	    	return false;
    }


    public static StyledDialog getStatisticsDialog(Context context, DialogInterface.OnClickListener listener) {
    	StyledDialog.Builder builder = new StyledDialog.Builder(context);
		builder.setTitle(context.getString(R.string.statistics_type_title));
		builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
		builder.setItems(
				context.getResources().getStringArray(
						R.array.statistics_type_labels), listener);
		final RadioButton[] statisticRadioButtons = new RadioButton[3];
	    RadioGroup rg = new RadioGroup(context);
	    rg.setOrientation(RadioGroup.HORIZONTAL);
	    RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
	    String[] text = new String[]{"1 month", "1 year", "all"};
	    int height = context.getResources().getDrawable(R.drawable.blue_btn_radio).getIntrinsicHeight();
	    for (int i = 0; i < statisticRadioButtons.length; i++){
	    	statisticRadioButtons[i] = new RadioButton(context);
	    	statisticRadioButtons[i].setClickable(true);
	    	statisticRadioButtons[i].setText("         " + text[i]);
	    	statisticRadioButtons[i].setHeight(height * 2);
	    	statisticRadioButtons[i].setSingleLine();
	    	statisticRadioButtons[i].setBackgroundDrawable(null);
	    	statisticRadioButtons[i].setGravity(Gravity.CENTER_VERTICAL);
	        rg.addView(statisticRadioButtons[i], lp);
	    }
	    rg.setOnCheckedChangeListener(new OnCheckedChangeListener () {
			@Override
			public void onCheckedChanged(RadioGroup arg0, int arg1) {
				int checked = arg0.getCheckedRadioButtonId();
				for (int i = 0; i < 3; i++) {
					if (arg0.getChildAt(i).getId() == checked) {
						PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putInt("statsType", i).commit();
						break;
					}
				}
			}
			});
	    rg.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, height));
	    statisticRadioButtons[Math.min(PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getInt("statsType", Stats.TYPE_MONTH), Stats.TYPE_LIFE)].setChecked(true);
		builder.setView(rg,  false, true);
		return builder.create();
    }
}
