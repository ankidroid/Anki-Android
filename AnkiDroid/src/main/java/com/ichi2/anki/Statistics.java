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
package com.ichi2.anki;

import android.os.AsyncTask;
import android.os.Bundle;

import android.support.v4.app.*;
import android.support.v4.view.*;
import android.util.Log;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;

import com.example.android.common.view.SlidingTabLayout;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.stats.*;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import org.json.JSONException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class Statistics extends NavigationDrawerActivity {

    public static final int TODAYS_STATS_TAB_POSITION = 0;
    public static final int FORECAST_TAB_POSITION = 1;
    public static final int REVIEW_COUNT_TAB_POSITION = 2;
    public static final int REVIEW_TIME_TAB_POSITION = 3;
    public static final int INTERVALS_TAB_POSITION = 4;
    public static final int HOURLY_BREAKDOWN_TAB_POSITION = 5;
    public static final int WEEKLY_BREAKDOWN_TAB_POSITION = 6;
    public static final int ANSWER_BUTTONS_TAB_POSITION = 7;
    public static final int CARDS_TYPES_TAB_POSITION = 8;

    private Menu mMenu;
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private SlidingTabLayout mSlidingTabLayout;
    private AnkiStatsTaskHandler mTaskHandler = null;
    private View mMainLayout;
    private static boolean sIsSubtitle;
    private static boolean sIsWholeCollectionOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(AnkiDroidApp.TAG, "Statistics -- onCreate()");
        sIsWholeCollectionOnly = AnkiStatsTaskHandler.isWholeCollection();  //if it starts with true, do not let user select deck
        sIsSubtitle = true;
        super.onCreate(savedInstanceState);

        mMainLayout = getLayoutInflater().inflate(R.layout.activity_anki_stats, null);
        initNavigationDrawer(mMainLayout);
        setContentView(mMainLayout);
        startLoadingCollection();
    }
    
    @Override
    protected void onCollectionLoaded(Collection col) {
        Log.i(AnkiDroidApp.TAG, "Statistics -- onCollectionLoaded()");
        // Setup Task Handler
        mTaskHandler = new AnkiStatsTaskHandler();

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(8);
        mSlidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);
        AnkiStatsTaskHandler.createFirstStatisticChooserTask(col, mViewPager);

        // Dirty way to get text size from a TextView with current style, change if possible
        float size = new TextView(this).getTextSize();
        mTaskHandler.setmStandardTextSize(size);
        // Prepare options menu only after loading everything
        supportInvalidateOptionsMenu();
        mSectionsPagerAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        Log.i(AnkiDroidApp.TAG, "Statistics -- onResume()");
        super.onResume();
        selectNavigationItem(NavigationDrawerActivity.DRAWER_STATISTICS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        mMenu = menu;
        //System.err.println("in onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.anki_stats, mMenu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // exit if mTaskHandler not initialized yet
        if (mTaskHandler == null) {
            return true;
        }
        //hide deck/collection selector when comming from deck picker without a selected deck
        switch (mTaskHandler.getStatType()){
            case Stats.TYPE_MONTH:
                MenuItem monthItem = menu.findItem(R.id.item_time_month);
                monthItem.setChecked(true);
                break;
            case Stats.TYPE_YEAR:
                MenuItem yearItem = menu.findItem(R.id.item_time_year);
                yearItem.setChecked(true);
                break;
            case Stats.TYPE_LIFE:
                MenuItem lifeItem = menu.findItem(R.id.item_time_all);
                lifeItem.setChecked(true);
                break;
        }



        if(sIsWholeCollectionOnly){
            MenuItem deckItem = menu.findItem(R.id.action_deck_chooser);
            deckItem.setVisible(false);
        } else {
            if(AnkiStatsTaskHandler.isWholeCollection()){
                MenuItem collectionItem = menu.findItem(R.id.item_deck_life);
                collectionItem.setChecked(true);
            } else {
                MenuItem deckOnlyItem = menu.findItem(R.id.item_deck_current);
                deckOnlyItem.setChecked(true);
            }
        }
        return super.onPrepareOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }
        int itemId =item.getItemId();
        switch (itemId) {
            case R.id.item_time_month:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                if (mTaskHandler.getStatType() != Stats.TYPE_MONTH) {
                    mTaskHandler.setStatType(Stats.TYPE_MONTH);
                    mSectionsPagerAdapter.notifyDataSetChanged();
                }
                return true;
            case R.id.item_time_year:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                if (mTaskHandler.getStatType() != Stats.TYPE_YEAR) {
                    mTaskHandler.setStatType(Stats.TYPE_YEAR);
                    mSectionsPagerAdapter.notifyDataSetChanged();
                }
                return true;
            case R.id.item_time_all:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                if (mTaskHandler.getStatType() != Stats.TYPE_LIFE) {
                    mTaskHandler.setStatType(Stats.TYPE_LIFE);
                    mSectionsPagerAdapter.notifyDataSetChanged();
                }
                return true;
            case R.id.item_deck_current:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                if(AnkiStatsTaskHandler.isWholeCollection()){
                    AnkiStatsTaskHandler.setIsWholeCollection(false);
                    try {
                        List<String> parts = Arrays.asList(AnkiDroidApp.getCol().getDecks().current().getString("name").split("::"));
                        if(sIsSubtitle)
                            AnkiDroidApp.getCompat().setSubtitle(this, parts.get(parts.size() - 1));
                        else
                            setTitle(parts.get(parts.size() - 1));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    mSectionsPagerAdapter.notifyDataSetChanged();
                }
                return true;
            case R.id.item_deck_life:
                if (item.isChecked()) item.setChecked(false);
                else item.setChecked(true);
                if(!AnkiStatsTaskHandler.isWholeCollection()){
                    AnkiStatsTaskHandler.setIsWholeCollection(true);
                    if(sIsSubtitle)
                        AnkiDroidApp.getCompat().setSubtitle(this, getResources().getString(R.string.stats_deck_collection));
                    else
                        setTitle(getResources().getString(R.string.stats_deck_collection));
                    mSectionsPagerAdapter.notifyDataSetChanged();
                }
                return true;
            case R.id.action_time_chooser:
                //showTimeDialog();
                return true;
            case R.id.action_deck_chooser:
                //showDeckDialog();
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


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        //this is called when mSectionsPagerAdapter.notifyDataSetChanged() is called, so checkAndUpdate() here
        //works best for updating all tabs
        @Override
        public int getItemPosition(Object object) {
            if (object instanceof StatisticFragment) {
                ((StatisticFragment) object).checkAndUpdate();
            }
            //don't return POSITION_NONE, avoid fragment recreation.
            return super.getItemPosition(object);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment item = StatisticFragment.newInstance(position);
            ((StatisticFragment) item).checkAndUpdate();
            return item;
        }

        @Override
        public int getCount() {
            return 9;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();

            switch (position) {
                case TODAYS_STATS_TAB_POSITION:
                    return getString(R.string.stats_today).toUpperCase(l);
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

    public static abstract class StatisticFragment extends Fragment{

        //track current settings for each individual fragment
        protected long mDeckId;
        protected boolean mIsWholeCollection;

        protected ViewPager mActivityPager;
        protected SectionsPagerAdapter mActivitySectionPagerAdapter;

        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        protected static final String ARG_SECTION_NUMBER = "section_number";



        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static StatisticFragment newInstance(int sectionNumber) {
            Fragment fragment;
            Bundle args;
            switch (sectionNumber){
                case FORECAST_TAB_POSITION:
                case REVIEW_COUNT_TAB_POSITION:
                case REVIEW_TIME_TAB_POSITION:
                case INTERVALS_TAB_POSITION:
                case HOURLY_BREAKDOWN_TAB_POSITION:
                case WEEKLY_BREAKDOWN_TAB_POSITION:
                case ANSWER_BUTTONS_TAB_POSITION:
                case CARDS_TYPES_TAB_POSITION:
                    fragment = new ChartFragment();
                    args = new Bundle();
                    args.putInt(ARG_SECTION_NUMBER, sectionNumber);
                    fragment.setArguments(args);
                    return (ChartFragment)fragment;
                case TODAYS_STATS_TAB_POSITION:
                    fragment = new OverviewStatisticsFragment();
                    args = new Bundle();
                    args.putInt(ARG_SECTION_NUMBER, sectionNumber);
                    fragment.setArguments(args);
                    return (OverviewStatisticsFragment)fragment;

                default:
                    return null;
            }

        }

        @Override
        public void onResume() {
            super.onResume();
            checkAndUpdate();

        }
        public abstract void invalidateView();
        public abstract void checkAndUpdate();



    }

    /**
     * A chart fragment containing a ChartView.
     */
    public static class ChartFragment extends StatisticFragment {

        private ChartView mChart;
        private ProgressBar mProgressBar;
        private int mHeight = 0;
        private int mWidth = 0;
        private int mSectionNumber;

        private int mType  = Stats.TYPE_MONTH;
        private boolean mIsCreated = false;

        private AsyncTask mCreateChartTask;



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

            mType = (((Statistics)getActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mActivityPager = ((Statistics)getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((Statistics)getActivity()).getSectionsPagerAdapter();
            mDeckId = AnkiDroidApp.getCol().getDecks().selected();
            mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();

            if(!AnkiStatsTaskHandler.isWholeCollection()) {
                try {
                    List<String> parts = Arrays.asList(AnkiDroidApp.getCol().getDecks().current().getString("name").split("::"));
                    if(sIsSubtitle)
                        AnkiDroidApp.getCompat().setSubtitle(getActivity(), parts.get(parts.size() - 1));
                    else
                        getActivity().setTitle(parts.get(parts.size() - 1));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else {
                if(sIsSubtitle)
                    AnkiDroidApp.getCompat().setSubtitle(getActivity(), getResources().getString(R.string.stats_deck_collection));
                else
                    getActivity().setTitle(getResources().getString(R.string.stats_deck_collection));
            }
            return rootView;
        }

        private void createChart(){

            switch (mSectionNumber){
                case FORECAST_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.FORECAST, mChart, mProgressBar);
                    break;
                case REVIEW_COUNT_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.REVIEW_COUNT, mChart, mProgressBar);
                    break;
                case REVIEW_TIME_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.REVIEW_TIME, mChart, mProgressBar);
                    break;
                case INTERVALS_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.INTERVALS, mChart, mProgressBar);
                    break;
                case HOURLY_BREAKDOWN_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.HOURLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case WEEKLY_BREAKDOWN_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.WEEKLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case ANSWER_BUTTONS_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.ANSWER_BUTTONS, mChart, mProgressBar);
                    break;
                case CARDS_TYPES_TAB_POSITION:
                    mCreateChartTask = (((Statistics)getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.CARDS_TYPES, mChart, mProgressBar);
                    break;

            }
        }



        @Override
        public void checkAndUpdate(){
            //System.err.println("<<<<<<<checkAndUpdate" + mSectionNumber);
            if(!mIsCreated)
                return;
            int height = mChart.getMeasuredHeight();
            int width = mChart.getMeasuredWidth();

            //are height and width checks still necessary without bitmaps?
            if(height != 0 && width != 0){
                if(mHeight != height || mWidth != width ||
                        mType != (((Statistics)getActivity()).getTaskHandler()).getStatType() ||
                        mDeckId != AnkiDroidApp.getCol().getDecks().selected() ||
                        mIsWholeCollection != AnkiStatsTaskHandler.isWholeCollection()){
                    mHeight = height;
                    mWidth = width;
                    mType = (((Statistics)getActivity()).getTaskHandler()).getStatType();
                    mProgressBar.setVisibility(View.VISIBLE);
                    mChart.setVisibility(View.GONE);
                    mDeckId = AnkiDroidApp.getCol().getDecks().selected();
                    mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();
                    if(mCreateChartTask != null && !mCreateChartTask.isCancelled()){
                        mCreateChartTask.cancel(true);
                    }
                    createChart();
                }
            }
        }



        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }
        @Override
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

    public static class OverviewStatisticsFragment extends StatisticFragment{

        private WebView mWebView;
        private ProgressBar mProgressBar;
        private int mType  = Stats.TYPE_MONTH;
        private boolean mIsCreated = false;
        private AsyncTask mCreateStatisticsOverviewTask;



        public OverviewStatisticsFragment() {
            super();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            setHasOptionsMenu(true);
            View rootView = inflater.inflate(R.layout.fragment_anki_stats_overview, container, false);
            mWebView = (WebView) rootView.findViewById(R.id.web_view_stats);
            if(mWebView == null)
                Log.d(AnkiDroidApp.TAG, "mChart null!!!");
            else
                Log.d(AnkiDroidApp.TAG, "mChart is not null!");

            //mChart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_stats_overview);

            mProgressBar.setVisibility(View.VISIBLE);
            //mChart.setVisibility(View.GONE);
            createStatisticOverview();
            mType = (((Statistics)getActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mActivityPager = ((Statistics)getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((Statistics)getActivity()).getSectionsPagerAdapter();
            mDeckId = AnkiDroidApp.getCol().getDecks().selected();
            mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();
            if(!AnkiStatsTaskHandler.isWholeCollection()) {
                try {
                    List<String> parts = Arrays.asList(AnkiDroidApp.getCol().getDecks().current().getString("name").split("::"));
                    if(sIsSubtitle)
                        AnkiDroidApp.getCompat().setSubtitle(getActivity(), parts.get(parts.size() - 1));
                    else
                        getActivity().setTitle(parts.get(parts.size() - 1));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else {
                if(sIsSubtitle)
                    AnkiDroidApp.getCompat().setSubtitle(getActivity(), getResources().getString(R.string.stats_deck_collection));
                else
                    getActivity().setTitle(getResources().getString(R.string.stats_deck_collection));
            }
            return rootView;
        }

        private void createStatisticOverview(){
            AnkiStatsTaskHandler handler = (((Statistics)getActivity()).getTaskHandler());
            // Workaround for issue 2406.
            // TODO: Implementing loader for Collection in Fragment itself would be a better solution.
            if (handler != null) {
                mCreateStatisticsOverviewTask = handler.createStatisticsOverview(mWebView, mProgressBar);
            } else {
                Log.e(AnkiDroidApp.TAG, "Statistics.createStatisticsOverview() TaskHandler not found");
            }
        }

        @Override
        public void invalidateView() {
            if(mWebView != null)
                mWebView.invalidate();
        }

        @Override
        public void checkAndUpdate() {
            if(!mIsCreated)
                return;
            if(mType != (((Statistics)getActivity()).getTaskHandler()).getStatType() ||
                    mDeckId != AnkiDroidApp.getCol().getDecks().selected() ||
                    mIsWholeCollection != AnkiStatsTaskHandler.isWholeCollection()){
                mType = (((Statistics)getActivity()).getTaskHandler()).getStatType();
                mProgressBar.setVisibility(View.VISIBLE);
                mWebView.setVisibility(View.GONE);
                mDeckId = AnkiDroidApp.getCol().getDecks().selected();
                mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();
                if(mCreateStatisticsOverviewTask != null && !mCreateStatisticsOverviewTask.isCancelled()){
                    mCreateStatisticsOverviewTask.cancel(true);
                }
                createStatisticOverview();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if(mCreateStatisticsOverviewTask != null && !mCreateStatisticsOverviewTask.isCancelled()){
                mCreateStatisticsOverviewTask.cancel(true);
            }
        }


    }

}
