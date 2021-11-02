/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@m-goldbach.net>                         *
 * Copyright (c) 2021 Mike Hardy <github@mikehardy.net>                                 *
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
import android.os.Bundle;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.ProgressBar;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.DeckSelectionDialog;
import com.ichi2.anki.runtimetools.TaskOperations;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;
import com.ichi2.anki.stats.ChartView;
import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.stats.Stats;
import com.ichi2.ui.FixedTextView;

import java.util.Locale;

import timber.log.Timber;


public class Statistics extends NavigationDrawerActivity implements
        DeckSelectionDialog.DeckSelectionListener,
        DeckDropDownAdapter.SubtitleListener {

    public static final int TODAYS_STATS_TAB_POSITION = 0;
    public static final int FORECAST_TAB_POSITION = 1;
    public static final int REVIEW_COUNT_TAB_POSITION = 2;
    public static final int REVIEW_TIME_TAB_POSITION = 3;
    public static final int INTERVALS_TAB_POSITION = 4;
    public static final int HOURLY_BREAKDOWN_TAB_POSITION = 5;
    public static final int WEEKLY_BREAKDOWN_TAB_POSITION = 6;
    public static final int ANSWER_BUTTONS_TAB_POSITION = 7;
    public static final int CARDS_TYPES_TAB_POSITION = 8;

    private ViewPager2 mViewPager;
    private TabLayout mSlidingTabLayout;
    private AnkiStatsTaskHandler mTaskHandler = null;
    private DeckSpinnerSelection mDeckSpinnerSelection;
    private static boolean sIsSubtitle;
    private long mStatsDeckId;


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

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.pager);
        mViewPager.setAdapter(new Statistics.StatsPagerAdapter((this)));
        mViewPager.setOffscreenPageLimit(8);
        mSlidingTabLayout = findViewById(R.id.sliding_tabs);

        // Fixes #8984: scroll to position 0 in RTL layouts
        ViewTreeObserver tabObserver = mSlidingTabLayout.getViewTreeObserver();
        tabObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            // Note: we can't use a lambda as we use 'this' to refer to the class.
            @Override
            public void onGlobalLayout() {
                // we need this here: If we select tab 0 before in an RTL context the layout has been drawn,
                // then it doesn't perform a scroll animation and selects the wrong element
                mSlidingTabLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mSlidingTabLayout.selectTab(mSlidingTabLayout.getTabAt(0));
            }
        });

        // Setup Task Handler
        mTaskHandler = AnkiStatsTaskHandler.getInstance(col);

        // Dirty way to get text size from a TextView with current style, change if possible
        float size = new FixedTextView(this).getTextSize();
        mTaskHandler.setmStandardTextSize(size);
        // Prepare options menu only after loading everything
        supportInvalidateOptionsMenu();
//        StatisticFragment.updateAllFragments();
        mStatsDeckId = getCol().getDecks().selected();
        mDeckSpinnerSelection = new DeckSpinnerSelection(this, col, this.findViewById(R.id.toolbar_spinner), true, true);
        mDeckSpinnerSelection.initializeActionBarDeckSpinner(this.getSupportActionBar());
        mDeckSpinnerSelection.selectDeckById(mStatsDeckId, false);
        mTaskHandler.setDeckId(mStatsDeckId);
        mViewPager.getAdapter().notifyDataSetChanged();
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

        // exit if mTaskHandler not initialized yet
        if (mTaskHandler != null) {
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
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.item_time_month) {
            item.setChecked(!item.isChecked());
            if (mTaskHandler.getStatType() != Stats.AxisType.TYPE_MONTH) {
                mTaskHandler.setStatType(Stats.AxisType.TYPE_MONTH);
                mViewPager.getAdapter().notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.item_time_year) {
            item.setChecked(!item.isChecked());
            if (mTaskHandler.getStatType() != Stats.AxisType.TYPE_YEAR) {
                mTaskHandler.setStatType(Stats.AxisType.TYPE_YEAR);
                mViewPager.getAdapter().notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.item_time_all) {
            item.setChecked(!item.isChecked());
            if (mTaskHandler.getStatType() != Stats.AxisType.TYPE_LIFE) {
                mTaskHandler.setStatType(Stats.AxisType.TYPE_LIFE);
                mViewPager.getAdapter().notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.action_time_chooser) {
            return true;
        }
        return super.onOptionsItemSelected(item);
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


    public ViewPager2 getViewPager(){
        return mViewPager;
    }

    public TabLayout getSlidingTabLayout() {
        return mSlidingTabLayout;
    }



    @Override
    public void onDeckSelected(@Nullable DeckSelectionDialog.SelectableDeck deck) {
        if (deck == null) {
            return;
        }
        mDeckSpinnerSelection.initializeActionBarDeckSpinner(this.getSupportActionBar());
        mStatsDeckId = deck.getDeckId();
        mDeckSpinnerSelection.selectDeckById(mStatsDeckId, true);
        mTaskHandler.setDeckId(mStatsDeckId);
        mViewPager.getAdapter().notifyDataSetChanged();
    }


    /**
     * A {@link FragmentStateAdapter} that returns a fragment corresponding to
     * one of the tabs.
     */
    public static class StatsPagerAdapter extends FragmentStateAdapter {

        private StatsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            StatisticFragment item = StatisticFragment.newInstance(position);
            item.checkAndUpdate();
            return item;
        }

        @Override
        public int getItemCount() {
            return 9;
        }
    }

    public static abstract class StatisticFragment extends Fragment {

        //track current settings for each individual fragment
        protected long mDeckId;
        @SuppressWarnings("deprecation") // #7108: AsyncTask
        protected android.os.AsyncTask mStatisticsTask;
        @SuppressWarnings("deprecation") // #7108: AsyncTask
        protected android.os.AsyncTask mStatisticsOverviewTask;
        private ViewPager2 mActivityPager;
        private TabLayout mSlidingTabLayout;
        private TabLayoutMediator mTabLayoutMediator;
        private final RecyclerView.AdapterDataObserver mDataObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                checkAndUpdate();
                super.onChanged();
            }
        };

        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        protected static final String ARG_SECTION_NUMBER = "section_number";


        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @NonNull
        @CheckResult
        public static StatisticFragment newInstance(int sectionNumber) {
            StatisticFragment fragment;
            Bundle args = new Bundle();
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
                    break;
                case TODAYS_STATS_TAB_POSITION:
                    fragment = new OverviewStatisticsFragment();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown section number: " + sectionNumber);
            }
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onResume() {
            checkAndUpdate();
            super.onResume();
        }

        @Override
        public void onDestroy() {
            cancelTasks();
            if (mActivityPager.getAdapter() != null) {
                mActivityPager.getAdapter().unregisterAdapterDataObserver(mDataObserver);
            }
            super.onDestroy();
        }

        protected void cancelTasks() {
            Timber.w("canceling tasks");
            TaskOperations.stopTaskGracefully(mStatisticsTask);
            TaskOperations.stopTaskGracefully(mStatisticsOverviewTask);
        }


        private String getTabTitle(int position) {
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
            return "";
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            mActivityPager = ((Statistics) requireActivity()).getViewPager();
            if (mActivityPager.getAdapter() != null) {
                mActivityPager.getAdapter().registerAdapterDataObserver(mDataObserver);
            }
            mSlidingTabLayout = ((Statistics) requireActivity()).getSlidingTabLayout();
            initTabLayoutMediator();
        }

        private void initTabLayoutMediator() {
            if (mTabLayoutMediator != null) {
                mTabLayoutMediator.detach();
            }
            mTabLayoutMediator = new TabLayoutMediator(mSlidingTabLayout, mActivityPager,
                    (tab, position) -> tab.setText(getTabTitle(position))
            );
            mTabLayoutMediator.attach();
        }

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
            if ((((Statistics) requireActivity()).getTaskHandler()) == null) {
                // Close statistics if the TaskHandler hasn't been loaded yet
                Timber.e("Statistics.ChartFragment.onCreateView() TaskHandler not found");
                requireActivity().finish();
                return rootView;
            }

            createChart();
            mHeight = mChart.getMeasuredHeight();
            mWidth = mChart.getMeasuredWidth();
            mChart.addFragment(this);

            mType = (((Statistics) requireActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mDeckId = ((Statistics) requireActivity()).mStatsDeckId;
            if (mDeckId != Stats.ALL_DECKS_ID) {
                Collection col = CollectionHelper.getInstance().getCol(requireActivity());
                String baseName = Decks.basename(col.getDecks().current().getString("name"));
                if (sIsSubtitle) {
                    ((AppCompatActivity) requireActivity()).getSupportActionBar().setSubtitle(baseName);
                } else {
                    requireActivity().setTitle(baseName);
                }
            } else {
                if (sIsSubtitle) {
                    ((AppCompatActivity) requireActivity()).getSupportActionBar().setSubtitle(R.string.stats_deck_collection);
                } else {
                    requireActivity().setTitle(getResources().getString(R.string.stats_deck_collection));
                }
            }
            return rootView;
        }

        private void createChart() {
            Statistics statisticsActivity = (Statistics) requireActivity();
            if (statisticsActivity == null) {
                return;
            }
            AnkiStatsTaskHandler taskHandler = statisticsActivity.getTaskHandler();
            switch (mSectionNumber) {
                case FORECAST_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.FORECAST, mChart, mProgressBar);
                    break;
                case REVIEW_COUNT_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.REVIEW_COUNT, mChart, mProgressBar);
                    break;
                case REVIEW_TIME_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.REVIEW_TIME, mChart, mProgressBar);
                    break;
                case INTERVALS_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.INTERVALS, mChart, mProgressBar);
                    break;
                case HOURLY_BREAKDOWN_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.HOURLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case WEEKLY_BREAKDOWN_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.WEEKLY_BREAKDOWN, mChart, mProgressBar);
                    break;
                case ANSWER_BUTTONS_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.ANSWER_BUTTONS, mChart, mProgressBar);
                    break;
                case CARDS_TYPES_TAB_POSITION:
                    mStatisticsTask = taskHandler.createChart(
                            Stats.ChartType.CARDS_TYPES, mChart, mProgressBar);
                    break;
            }
        }


        @Override
        public void checkAndUpdate() {
            if (!mIsCreated) {
                return;
            }
            int height = mChart.getMeasuredHeight();
            int width = mChart.getMeasuredWidth();

            //are height and width checks still necessary without bitmaps?
            if (height != 0 && width != 0) {
                Collection col = CollectionHelper.getInstance().getCol(requireActivity());
                if (mHeight != height || mWidth != width ||
                        mType != (((Statistics) requireActivity()).getTaskHandler()).getStatType() ||
                        mDeckId != ((Statistics) requireActivity()).mStatsDeckId) {
                    mHeight = height;
                    mWidth = width;
                    mType = (((Statistics) requireActivity()).getTaskHandler()).getStatType();
                    mProgressBar.setVisibility(View.VISIBLE);
                    mChart.setVisibility(View.GONE);
                    mDeckId = ((Statistics) requireActivity()).mStatsDeckId;
                    cancelTasks();
                    createChart();
                }
            }
        }


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }
    }

    public static class OverviewStatisticsFragment extends StatisticFragment {

        private WebView mWebView;
        private ProgressBar mProgressBar;
        private Stats.AxisType mType  = Stats.AxisType.TYPE_MONTH;
        private boolean mIsCreated = false;

        public OverviewStatisticsFragment() {
            super();
        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            setHasOptionsMenu(true);
            View rootView = inflater.inflate(R.layout.fragment_anki_stats_overview, container, false);
            AnkiStatsTaskHandler handler = (((Statistics) requireActivity()).getTaskHandler());
            // Workaround for issue 2406 -- crash when resuming after app is purged from RAM
            // TODO: Implementing loader for Collection in Fragment itself would be a better solution.
            if (handler == null) {
                Timber.e("Statistics.OverviewStatisticsFragment.onCreateView() TaskHandler not found");
                requireActivity().finish();
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
            Collection col = CollectionHelper.getInstance().getCol(requireActivity());
            mDeckId = ((Statistics) requireActivity()).mStatsDeckId;
            if (mDeckId != Stats.ALL_DECKS_ID) {
                String basename = Decks.basename(col.getDecks().current().getString("name"));
                if (sIsSubtitle) {
                    ((AppCompatActivity) requireActivity()).getSupportActionBar().setSubtitle(basename);
                } else {
                    requireActivity().setTitle(basename);
                }
            } else {
                if (sIsSubtitle) {
                    ((AppCompatActivity) requireActivity()).getSupportActionBar().setSubtitle(R.string.stats_deck_collection);
                } else {
                    requireActivity().setTitle(R.string.stats_deck_collection);
                }
            }
            return rootView;
        }

        private void createStatisticOverview(){
            AnkiStatsTaskHandler handler = (((Statistics)requireActivity()).getTaskHandler());
            mStatisticsOverviewTask = handler.createStatisticsOverview(mWebView, mProgressBar);
        }


        @Override
        public void checkAndUpdate() {
            if (!mIsCreated) {
                return;
            }
            if (mType != (((Statistics) requireActivity()).getTaskHandler()).getStatType() ||
                    mDeckId != ((Statistics) requireActivity()).mStatsDeckId) {
                mType = (((Statistics) requireActivity()).getTaskHandler()).getStatType();
                mProgressBar.setVisibility(View.VISIBLE);
                mWebView.setVisibility(View.GONE);
                mDeckId = ((Statistics) requireActivity()).mStatsDeckId;
                cancelTasks();
                createStatisticOverview();
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
            finishWithAnimation(ActivityTransitionAnimation.Direction.END);
        }
    }
}
