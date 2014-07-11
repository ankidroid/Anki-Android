package com.ichi2.anki.stats;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.ichi2.anki.R;
import com.ichi2.libanki.Stats;

import java.io.File;
import java.util.Locale;


public class AnkiStatsActivity extends ActionBarActivity implements ActionBar.TabListener {

    SectionsPagerAdapter mSectionsPagerAdapter;
    ViewPager mViewPager;
    AnkiStatsTaskHandler mTaskHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTaskHandler = new AnkiStatsTaskHandler();


        setContentView(R.layout.activity_anki_stats);
        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(3);

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


        float size = new TextView(this).getTextSize();


        mTaskHandler.setmStandardTextSize(size);
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
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return ChartFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 8;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();

            switch (position) {
                case 0:
                    return getString(R.string.stats_forecast).toUpperCase(l);
                case 1:
                    return getString(R.string.stats_review_count).toUpperCase(l);
                case 2:
                    return getString(R.string.stats_review_time).toUpperCase(l);
                case 3:
                    return getString(R.string.stats_review_intervals).toUpperCase(l);
                case 4:
                    return getString(R.string.stats_breakdown).toUpperCase(l);
                case 5:
                    return getString(R.string.stats_weekly_breakdown).toUpperCase(l);
                case 6:
                    return getString(R.string.stats_answer_buttons).toUpperCase(l);
                case 7:
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
        private ImageView mChart;
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
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            setHasOptionsMenu(true);
            Bundle bundle = getArguments();
            mSectionNumber = bundle.getInt(ARG_SECTION_NUMBER);
            //int sectionNumber = 0;
            System.err.println("sectionNumber: " + mSectionNumber);
            View rootView = inflater.inflate(R.layout.fragment_anki_stats, container, false);
            mChart = (ImageView) rootView.findViewById(R.id.image_view_chart);
            if(mChart == null)
                Log.d(AnkiStatsTaskHandler.TAG, "mChart null!!!");
            else
                Log.d(AnkiStatsTaskHandler.TAG, "mChart is not null!");
            mProgressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar_stats);

            mProgressBar.setVisibility(View.VISIBLE);
            //mChart.setVisibility(View.GONE);
            createChart();
            mHeight = mChart.getMeasuredHeight();
            mWidth = mChart.getMeasuredWidth();
            mInstance = this;
            mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mActivityPager = ((AnkiStatsActivity)getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((AnkiStatsActivity)getActivity()).getSectionsPagerAdapter();
            return rootView;
        }

        private void createChart(){
            if(mSectionNumber == 1) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createForecastChart(mChart, mProgressBar);
            } else if(mSectionNumber == 2) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createReviewCountChart(mChart, mProgressBar);
            } else if(mSectionNumber == 3) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createReviewTimeChart(mChart, mProgressBar);
            } else if(mSectionNumber == 4) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createIntervalChart(mChart, mProgressBar);
            } else if(mSectionNumber == 5) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createBreakdownChart(mChart, mProgressBar);
            } else if(mSectionNumber == 6) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createWeeklyBreakdownChart(mChart, mProgressBar);
            } else if(mSectionNumber == 7) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createAnswerButtonTask(mChart, mProgressBar);
            } else if(mSectionNumber == 8) {
                (((AnkiStatsActivity)getActivity()).getTaskHandler()).createCardsTypesTask(mChart, mProgressBar);
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
            if(height != 0 && width != 0){
                if(mHeight != height || mWidth != width || mType != (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType()){
                    mHeight = height;
                    mWidth = width;
                    mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
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
    }

}
