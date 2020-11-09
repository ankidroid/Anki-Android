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

import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;
import com.ichi2.anki.stats.ChartView;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.stats.Stats;
import com.ichi2.libanki.Deck;
import com.ichi2.ui.FixedTextView;
import com.ichi2.ui.SlidingTabLayout;

import com.ichi2.utils.JSONException;

import java.util.ArrayList;
import java.util.Locale;

import timber.log.Timber;


public class Statistics extends NavigationDrawerActivity implements DeckDropDownAdapter.SubtitleListener {

    public static final int TODAYS_STATS_TAB_POSITION = 0;
    public static final int FORECAST_TAB_POSITION = 1;
    public static final int REVIEW_COUNT_TAB_POSITION = 2;
    public static final int REVIEW_TIME_TAB_POSITION = 3;
    public static final int INTERVALS_TAB_POSITION = 4;
    public static final int HOURLY_BREAKDOWN_TAB_POSITION = 5;
    public static final int WEEKLY_BREAKDOWN_TAB_POSITION = 6;
    public static final int ANSWER_BUTTONS_TAB_POSITION = 7;
    public static final int CARDS_TYPES_TAB_POSITION = 8;

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private AnkiStatsTaskHandler mTaskHandler = null;
    private long mDeckId;
    private ArrayList<Deck> mDropDownDecks;
    private Spinner mActionBarSpinner;
    private static boolean sIsSubtitle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        Timber.d("onCreate()");
        sIsSubtitle = true;
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_anki_stats);
        initNavigationDrawer(findViewById(android.R.id.content));
        startLoadingCollection();
    }

    @Override
    protected void onCollectionLoaded(Collection col) {
        Timber.d("onCollectionLoaded()");
        super.onCollectionLoaded(col);
        // Add drop-down menu to select deck to action bar.
        mDropDownDecks = getCol().getDecks().allSorted();
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        mActionBarSpinner = findViewById(R.id.toolbar_spinner);
        mActionBarSpinner.setAdapter(new DeckDropDownAdapter(this, mDropDownDecks));
        mActionBarSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectDropDownItem(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
        mActionBarSpinner.setVisibility(View.VISIBLE);

        // Setup Task Handler
        mTaskHandler = new AnkiStatsTaskHandler(col);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(8);
        SlidingTabLayout slidingTabLayout = findViewById(R.id.sliding_tabs);
        slidingTabLayout.setViewPager(mViewPager);

        // Dirty way to get text size from a TextView with current style, change if possible
        float size = new FixedTextView(this).getTextSize();
        mTaskHandler.setmStandardTextSize(size);
        // Prepare options menu only after loading everything
        supportInvalidateOptionsMenu();
        mSectionsPagerAdapter.notifyDataSetChanged();

        // Default to libanki's selected deck
        selectDeckById(getCol().getDecks().selected());
    }

    @Override
    protected void onResume() {
        Timber.d("onResume()");
        selectNavigationItem(R.id.nav_stats);
        super.onResume();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        //System.err.println("in onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.anki_stats, menu);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // exit if mTaskHandler not initialized yet
        if (mTaskHandler == null) {
            return true;
        }
        switch (mTaskHandler.getStatType()) {
            case TYPE_MONTH:
                MenuItem monthItem = menu.findItem(R.id.item_time_month);
                monthItem.setChecked(true);
                break;
            case TYPE_YEAR:
                MenuItem yearItem = menu.findItem(R.id.item_time_year);
                yearItem.setChecked(true);
                break;
            case TYPE_LIFE:
                MenuItem lifeItem = menu.findItem(R.id.item_time_all);
                lifeItem.setChecked(true);
                break;
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.item_time_month) {
            if (item.isChecked()) item.setChecked(false);
            else item.setChecked(true);
            if (mTaskHandler.getStatType() != Stats.AxisType.TYPE_MONTH) {
                mTaskHandler.setStatType(Stats.AxisType.TYPE_MONTH);
                mSectionsPagerAdapter.notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.item_time_year) {
            if (item.isChecked()) item.setChecked(false);
            else item.setChecked(true);
            if (mTaskHandler.getStatType() != Stats.AxisType.TYPE_YEAR) {
                mTaskHandler.setStatType(Stats.AxisType.TYPE_YEAR);
                mSectionsPagerAdapter.notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.item_time_all) {
            if (item.isChecked()) item.setChecked(false);
            else item.setChecked(true);
            if (mTaskHandler.getStatType() != Stats.AxisType.TYPE_LIFE) {
                mTaskHandler.setStatType(Stats.AxisType.TYPE_LIFE);
                mSectionsPagerAdapter.notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.action_time_chooser) {//showTimeDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void selectDropDownItem(int position) {
        mActionBarSpinner.setSelection(position);
        if (position == 0) {
            mDeckId = Stats.ALL_DECKS_ID;
        } else {
            Deck deck = mDropDownDecks.get(position - 1);
            try {
                mDeckId = deck.getLong("id");
            } catch (JSONException e) {
                Timber.e(e, "Could not get ID from deck");
            }
        }
        mTaskHandler.setDeckId(mDeckId);
        mSectionsPagerAdapter.notifyDataSetChanged();
    }

    // Iterates the drop down decks, and selects the one matching the given id
    private boolean selectDeckById(long deckId) {
        for (int dropDownDeckIdx = 0; dropDownDeckIdx < mDropDownDecks.size(); dropDownDeckIdx++) {
            if (mDropDownDecks.get(dropDownDeckIdx).getLong("id") == deckId) {
                selectDropDownItem(dropDownDeckIdx + 1);
                return true;
            }
        }
        return false;
    }

    /**
     * @return text to be used in the subtitle of the drop-down deck selector
     */
    public String getSubtitleText() {
        return getResources().getString(R.string.statistics);
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

    private long getDeckId() { return mDeckId; }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        //this is called when mSectionsPagerAdapter.notifyDataSetChanged() is called, so checkAndUpdate() here
        //works best for updating all tabs
        @Override
        public int getItemPosition(@NonNull Object object) {
            if (object instanceof StatisticFragment) {
                ((StatisticFragment) object).checkAndUpdate();
            }
            //don't return POSITION_NONE, avoid fragment recreation.
            return super.getItemPosition(object);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            StatisticFragment item = StatisticFragment.newInstance(position);
            item.checkAndUpdate();
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
                    return getString(R.string.stats_overview).toUpperCase(l);
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
                    return getString(R.string.title_activity_template_editor).toUpperCase(l);
            }
            return null;
        }
    }

    public static abstract class StatisticFragment extends Fragment {

        //track current settings for each individual fragment
        protected long mDeckId;
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
            switch (sectionNumber) {
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
                    return (ChartFragment) fragment;
                case TODAYS_STATS_TAB_POSITION:
                    fragment = new OverviewStatisticsFragment();
                    args = new Bundle();
                    args.putInt(ARG_SECTION_NUMBER, sectionNumber);
                    fragment.setArguments(args);
                    return (OverviewStatisticsFragment) fragment;
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
        private Stats.AxisType mType  = Stats.AxisType.TYPE_MONTH;
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
            mChart = rootView.findViewById(R.id.image_view_chart);
            if (mChart == null) {
                Timber.d("mChart null!");
            } else {
                Timber.d("mChart is not null!");
            }

            //mChart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            mProgressBar = rootView.findViewById(R.id.progress_bar_stats);

            mProgressBar.setVisibility(View.VISIBLE);
            //mChart.setVisibility(View.GONE);

            // TODO: Implementing loader for Collection in Fragment itself would be a better solution.
            if ((((Statistics) getActivity()).getTaskHandler()) == null) {
                // Close statistics if the TaskHandler hasn't been loaded yet
                Timber.e("Statistics.ChartFragment.onCreateView() TaskHandler not found");
                getActivity().finish();
                return rootView;
            }

            createChart();
            mHeight = mChart.getMeasuredHeight();
            mWidth = mChart.getMeasuredWidth();
            mChart.addFragment(this);

            mType = (((Statistics) getActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mActivityPager = ((Statistics) getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((Statistics) getActivity()).getSectionsPagerAdapter();
            mDeckId = ((Statistics) getActivity()).getDeckId();
            if (mDeckId != Stats.ALL_DECKS_ID) {
                Collection col = CollectionHelper.getInstance().getCol(getActivity());
                String baseName = Decks.basename(col.getDecks().current().getString("name"));
                if (sIsSubtitle) {
                    ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(baseName);
                } else {
                    getActivity().setTitle(baseName);
                }
            } else {
                if (sIsSubtitle) {
                    ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(R.string.stats_deck_collection);
                } else {
                    getActivity().setTitle(getResources().getString(R.string.stats_deck_collection));
                }
            }
            return rootView;
        }

        private void createChart() {
            switch (mSectionNumber) {
                case FORECAST_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.FORECAST, mChart, mProgressBar);
                    break;
                case REVIEW_COUNT_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.REVIEW_COUNT, mChart, mProgressBar);
                    break;
                case REVIEW_TIME_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.REVIEW_TIME, mChart, mProgressBar);
                    break;
                case INTERVALS_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.INTERVALS, mChart, mProgressBar);
                    break;
                case HOURLY_BREAKDOWN_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.HOURLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case WEEKLY_BREAKDOWN_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.WEEKLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case ANSWER_BUTTONS_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.ANSWER_BUTTONS, mChart, mProgressBar);
                    break;
                case CARDS_TYPES_TAB_POSITION:
                    mCreateChartTask = (((Statistics) getActivity()).getTaskHandler()).createChart(
                            Stats.ChartType.CARDS_TYPES, mChart, mProgressBar);
                    break;
            }
        }


        @Override
        public void checkAndUpdate() {
            //System.err.println("<<<<<<<checkAndUpdate" + mSectionNumber);
            if (!mIsCreated) {
                return;
            }
            int height = mChart.getMeasuredHeight();
            int width = mChart.getMeasuredWidth();

            //are height and width checks still necessary without bitmaps?
            if (height != 0 && width != 0) {
                Collection col = CollectionHelper.getInstance().getCol(getActivity());
                if (mHeight != height || mWidth != width ||
                        mType != (((Statistics) getActivity()).getTaskHandler()).getStatType() ||
                        mDeckId != ((Statistics) getActivity()).getDeckId()) {
                    mHeight = height;
                    mWidth = width;
                    mType = (((Statistics) getActivity()).getTaskHandler()).getStatType();
                    mProgressBar.setVisibility(View.VISIBLE);
                    mChart.setVisibility(View.GONE);
                    mDeckId = ((Statistics) getActivity()).getDeckId();
                    if (mCreateChartTask != null && !mCreateChartTask.isCancelled()) {
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
        public void invalidateView() {
            if (mChart != null) {
                mChart.invalidate();
            }
        }


        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mCreateChartTask != null && !mCreateChartTask.isCancelled()) {
                mCreateChartTask.cancel(true);
            }
        }
    }

    public static class OverviewStatisticsFragment extends StatisticFragment {

        private WebView mWebView;
        private ProgressBar mProgressBar;
        private Stats.AxisType mType  = Stats.AxisType.TYPE_MONTH;
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
            AnkiStatsTaskHandler handler = (((Statistics) getActivity()).getTaskHandler());
            // Workaround for issue 2406 -- crash when resuming after app is purged from RAM
            // TODO: Implementing loader for Collection in Fragment itself would be a better solution.
            if (handler == null) {
                Timber.e("Statistics.OverviewStatisticsFragment.onCreateView() TaskHandler not found");
                getActivity().finish();
                return rootView;
            }
            mWebView = rootView.findViewById(R.id.web_view_stats);
            if (mWebView == null) {
                Timber.d("mChart null!");
            } else {
                Timber.d("mChart is not null!");
                // Set transparent color to prevent flashing white when night mode enabled
                mWebView.setBackgroundColor(Color.argb(1, 0, 0, 0));
            }

            //mChart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            mProgressBar = rootView.findViewById(R.id.progress_bar_stats_overview);

            mProgressBar.setVisibility(View.VISIBLE);
            //mChart.setVisibility(View.GONE);
            createStatisticOverview();
            mType = handler.getStatType();
            mIsCreated = true;
            mActivityPager = ((Statistics) getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((Statistics) getActivity()).getSectionsPagerAdapter();
            Collection col = CollectionHelper.getInstance().getCol(getActivity());
            mDeckId = ((Statistics) getActivity()).getDeckId();
            if (mDeckId != Stats.ALL_DECKS_ID) {
                String basename = Decks.basename(col.getDecks().current().getString("name"));
                if (sIsSubtitle) {
                    ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(basename);
                } else {
                    getActivity().setTitle(basename);
                }
            } else {
                if (sIsSubtitle) {
                    ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(R.string.stats_deck_collection);
                } else {
                    getActivity().setTitle(R.string.stats_deck_collection);
                }
            }
            return rootView;
        }

        private void createStatisticOverview(){
            AnkiStatsTaskHandler handler = (((Statistics)getActivity()).getTaskHandler());
            mCreateStatisticsOverviewTask = handler.createStatisticsOverview(mWebView, mProgressBar);
        }


        @Override
        public void invalidateView() {
            if (mWebView != null) {
                mWebView.invalidate();
            }
        }


        @Override
        public void checkAndUpdate() {
            if (!mIsCreated) {
                return;
            }
            Collection col = CollectionHelper.getInstance().getCol(getActivity());
            if (mType != (((Statistics) getActivity()).getTaskHandler()).getStatType() ||
                    mDeckId != ((Statistics) getActivity()).getDeckId()) {
                mType = (((Statistics) getActivity()).getTaskHandler()).getStatType();
                mProgressBar.setVisibility(View.VISIBLE);
                mWebView.setVisibility(View.GONE);
                mDeckId = ((Statistics) getActivity()).getDeckId();
                if (mCreateStatisticsOverviewTask != null && !mCreateStatisticsOverviewTask.isCancelled()) {
                    mCreateStatisticsOverviewTask.cancel(true);
                }
                createStatisticOverview();
            }
        }


        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mCreateStatisticsOverviewTask != null && !mCreateStatisticsOverviewTask.isCancelled()) {
                mCreateStatisticsOverviewTask.cancel(true);
            }
        }
    }


    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            super.onBackPressed();
        } else {
            Timber.i("Back key pressed");
            Intent data = new Intent();
            if (getIntent().hasExtra("selectedDeck")) {
                data.putExtra("originalDeck", getIntent().getLongExtra("selectedDeck", 0L));
            }
            setResult(RESULT_CANCELED, data);
            finishWithAnimation(ActivityTransitionAnimation.Direction.RIGHT);
        }
    }
}
