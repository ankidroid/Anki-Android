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
import android.os.Bundle;

import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.*;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.R;
import com.ichi2.libanki.Stats;
import com.wildplot.android.rendering.ChartView;

import java.util.Locale;


public class AnkiStatsActivity extends NavigationDrawerActivity implements ActionBar.TabListener {

    public static final int FORECAST_TAB_POSITION = 0;
    public static final int REVIEW_COUNT_TAB_POSITION = 1;
    public static final int REVIEW_TIME_TAB_POSITION = 2;
    public static final int INTERVALS_TAB_POSITION = 3;
    public static final int HOURLY_BREAKDOWN_TAB_POSITION = 4;
    public static final int WEEKLY_BREAKDOWN_TAB_POSITION = 5;
    public static final int ANSWER_BUTTONS_TAB_POSITION = 6;
    public static final int CARDS_TYPES_TAB_POSITION = 7;


    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private AnkiStatsTaskHandler mTaskHandler = null;
    private ActionBar mActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTaskHandler = new AnkiStatsTaskHandler();


        View mainLayout = getLayoutInflater().inflate(R.layout.activity_anki_stats, null);
        initNavigationDrawer(mainLayout);
        setContentView(mainLayout);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        mActionBar = actionBar;
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(8);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }


        //Dirty way to get text size from a TextView with current style, change if possible
        float size = new TextView(this).getTextSize();


        mTaskHandler.setmStandardTextSize(size);
    }

    @Override
    protected void onResume() {
        super.onResume();
        selectNavigationItem(NavigationDrawerActivity.DRAWER_STATISTICS);
    }
    @Override
    public void onDestroy() {
        if(mActionBar != null) {
            mActionBar.removeAllTabs();
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public AnkiStatsTaskHandler getTaskHandler(){
        return mTaskHandler;
    }

    public ViewPager getViewPager(){
        return mViewPager;
    }

    public SectionsPagerAdapter getSectionsPagerAdapter() {
        return mSectionsPagerAdapter;
    }



    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
        ChartFragment currentFragment = (ChartFragment) mSectionsPagerAdapter.getItem(tab.getPosition());
        currentFragment.checkAndUpdate();
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.GINGERBREAD) {
            currentFragment.invalidateView();
        }
        //System.err.println("!!!!!<<<<onTabSelected" + tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        ChartFragment currentFragment = (ChartFragment) mSectionsPagerAdapter.getItem(tab.getPosition());
        currentFragment.checkAndUpdate();
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getItemPosition(Object object) {
            if (object instanceof ChartFragment) {
                ((ChartFragment) object).checkAndUpdate();
            }
            //don't return POSITION_NONE, avoid fragment recreation.
            return super.getItemPosition(object);
        }

        @Override
        public Fragment getItem(int position) {
            return ChartFragment.newInstance(position);
        }

        @Override
        public int getCount() {
            return 8;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();

            switch (position) {
                case FORECAST_TAB_POSITION:
                    return getString(R.string.stats_forecast).toUpperCase(l);
                case REVIEW_COUNT_TAB_POSITION:
                    return getString(R.string.stats_review_count).toUpperCase(l);
                case REVIEW_TIME_TAB_POSITION:
                    return getString(R.string.stats_review_time).toUpperCase(l);
                case INTERVALS_TAB_POSITION:
                    return getString(R.string.stats_review_intervals).toUpperCase(l);
                case HOURLY_BREAKDOWN_TAB_POSITION:
                    return getString(R.string.stats_breakdown).toUpperCase(l);
                case WEEKLY_BREAKDOWN_TAB_POSITION:
                    return getString(R.string.stats_weekly_breakdown).toUpperCase(l);
                case ANSWER_BUTTONS_TAB_POSITION:
                    return getString(R.string.stats_answer_buttons).toUpperCase(l);
                case CARDS_TYPES_TAB_POSITION:
                    return getString(R.string.stats_cards_types).toUpperCase(l);
            }
            return null;
        }
    }

    /**
     * A chart fragment containing a ImageView.
     */
    public static class ChartFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";
        private ChartView mChart;
        private ProgressBar mProgressBar;
        private int mHeight = 0;
        private int mWidth = 0;
        private ChartFragment mInstance = null;
        private int mSectionNumber;
        private Menu mMenu;
        private int mType  = Stats.TYPE_MONTH;
        private boolean mIsCreated = false;
        private ViewPager mActivityPager;
        private SectionsPagerAdapter mActivitySectionPagerAdapter;
        private AsyncTask mCreateChartTask;

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static ChartFragment newInstance(int sectionNumber) {
            ChartFragment fragment = new ChartFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public ChartFragment() {
            super();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            setHasOptionsMenu(true);
            Bundle bundle = getArguments();
            mSectionNumber = bundle.getInt(ARG_SECTION_NUMBER);
            //int sectionNumber = 0;
            //System.err.println("sectionNumber: " + mSectionNumber);
            View rootView = inflater.inflate(R.layout.fragment_anki_stats, container, false);
            mChart = (ChartView) rootView.findViewById(R.id.image_view_chart);
            if(mChart == null)
                Log.d(AnkiDroidApp.TAG, "mChart null!!!");
            else
                Log.d(AnkiDroidApp.TAG, "mChart is not null!");

            //mChart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_stats);

            mProgressBar.setVisibility(View.VISIBLE);
            //mChart.setVisibility(View.GONE);
            createChart();
            mHeight = mChart.getMeasuredHeight();
            mWidth = mChart.getMeasuredWidth();
            mChart.addFragment(this);

            mInstance = this;
            mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mActivityPager = ((AnkiStatsActivity)getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((AnkiStatsActivity)getActivity()).getSectionsPagerAdapter();
            return rootView;
        }

        private void createChart(){

            switch (mSectionNumber){
                case FORECAST_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.FORECAST, mChart, mProgressBar);
                    break;
                case REVIEW_COUNT_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.REVIEW_COUNT, mChart, mProgressBar);
                    break;
                case REVIEW_TIME_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.REVIEW_TIME, mChart, mProgressBar);
                    break;
                case INTERVALS_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.INTERVALS, mChart, mProgressBar);
                    break;
                case HOURLY_BREAKDOWN_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.HOURLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case WEEKLY_BREAKDOWN_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.WEEKLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case ANSWER_BUTTONS_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.ANSWER_BUTTONS, mChart, mProgressBar);
                    break;
                case CARDS_TYPES_TAB_POSITION:
                    mCreateChartTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.CARDS_TYPES, mChart, mProgressBar);
                    break;

            }
        }

        @Override
        public void onResume() {
            super.onResume();
            checkAndUpdate();

        }

        public void checkAndUpdate(){
            //System.err.println("<<<<<<<checkAndUpdate" + mSectionNumber);
            if(!mIsCreated)
                return;
            int height = mChart.getMeasuredHeight();
            int width = mChart.getMeasuredWidth();

            //are height and width checks still necessary without bitmaps?
            if(height != 0 && width != 0){
                if(mHeight != height || mWidth != width || mType != (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType()){
                    mHeight = height;
                    mWidth = width;
                    mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
                    mProgressBar.setVisibility(View.VISIBLE);
                    mChart.setVisibility(View.GONE);
                    if(mCreateChartTask != null && !mCreateChartTask.isCancelled()){
                        mCreateChartTask.cancel(true);
                    }
                    createChart();
                }
            }
        }

        //This seems to be called on every tab change, so using it to update
        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);
            mMenu = menu;
            //System.err.println("in onCreateOptionsMenu");
            inflater.inflate(R.menu.anki_stats, menu);
            checkAndUpdate();
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            //System.err.println("in onOptionsItemSelected");
            AnkiStatsTaskHandler ankiStatsTaskHandler = (((AnkiStatsActivity)getActivity()).getTaskHandler());

            MenuItem monthItem  = (MenuItem)mMenu.findItem(R.id.action_month);
            MenuItem yearItem = (MenuItem)mMenu.findItem(R.id.action_year);
            MenuItem allItem = (MenuItem)mMenu.findItem(R.id.action_life_time);

            int id = item.getItemId();
            if(id == R.id.action_month) {
                if(ankiStatsTaskHandler.getStatType() != Stats.TYPE_MONTH){
                    ankiStatsTaskHandler.setStatType(Stats.TYPE_MONTH);
                    monthItem.setChecked(true);
                    yearItem.setChecked(false);
                    allItem.setChecked(false);
                    mActivitySectionPagerAdapter.notifyDataSetChanged();
                    //createChart();
                    //mActivityPager.invalidate();
                }

            } else if(id == R.id.action_year) {
                if(ankiStatsTaskHandler.getStatType() != Stats.TYPE_YEAR){
                    ankiStatsTaskHandler.setStatType(Stats.TYPE_YEAR);
                    monthItem.setChecked(false);
                    yearItem.setChecked(true);
                    allItem.setChecked(false);
                    mActivitySectionPagerAdapter.notifyDataSetChanged();
                    //createChart();
                    //mActivityPager.invalidate();
                }
            } else if(id == R.id.action_life_time) {
                if(ankiStatsTaskHandler.getStatType() != Stats.TYPE_LIFE){
                    ankiStatsTaskHandler.setStatType(Stats.TYPE_LIFE);
                    monthItem.setChecked(false);
                    yearItem.setChecked(false);
                    allItem.setChecked(true);
                    mActivitySectionPagerAdapter.notifyDataSetChanged();
                    //createChart();
                    //mActivityPager.invalidate();
                }
            }
            return true;
        }

        @Override
        public void onPrepareOptionsMenu(Menu menu) {
            super.onPrepareOptionsMenu(menu);
            MenuItem monthItem  = (MenuItem)menu.findItem(R.id.action_month);
            MenuItem yearItem = (MenuItem)menu.findItem(R.id.action_year);
            MenuItem allItem = (MenuItem)menu.findItem(R.id.action_life_time);
            AnkiStatsTaskHandler ankiStatsTaskHandler = (((AnkiStatsActivity)getActivity()).getTaskHandler());

            monthItem.setChecked(ankiStatsTaskHandler.getStatType() == Stats.TYPE_MONTH);
            yearItem.setChecked(ankiStatsTaskHandler.getStatType() == Stats.TYPE_YEAR);
            allItem.setChecked(ankiStatsTaskHandler.getStatType() == Stats.TYPE_LIFE);

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }
        public void invalidateView(){
            if(mChart != null)
                 mChart.invalidate();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if(mCreateChartTask != null && !mCreateChartTask.isCancelled()){
                mCreateChartTask.cancel(true);
            }
        }
    }

}
