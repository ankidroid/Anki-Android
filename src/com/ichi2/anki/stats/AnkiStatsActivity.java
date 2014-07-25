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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;

import android.support.v4.app.*;
import android.support.v4.view.*;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.R;
import com.ichi2.libanki.Stats;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;


public class AnkiStatsActivity extends NavigationDrawerActivity implements ActionBar.TabListener {

    public static final int TODAYS_STATS_TAB_POSITION = 0;
    public static final int FORECAST_TAB_POSITION = 1;
    public static final int REVIEW_COUNT_TAB_POSITION = 2;
    public static final int REVIEW_TIME_TAB_POSITION = 3;
    public static final int INTERVALS_TAB_POSITION = 4;
    public static final int HOURLY_BREAKDOWN_TAB_POSITION = 5;
    public static final int WEEKLY_BREAKDOWN_TAB_POSITION = 6;
    public static final int ANSWER_BUTTONS_TAB_POSITION = 7;
    public static final int CARDS_TYPES_TAB_POSITION = 8;

    private static final int MONTH_TYPE = 0;
    private static final int YEAR_TYPE = 1;
    private static final int ALL_TYPE = 2;

    private Spinner mTimeSpinner;
    private RelativeLayout mRelativeLayoutTime;
    private ArrayList<JSONObject> mDropDownDecks;
    private DeckDropDownAdapter mDropDownAdapter;
    private RelativeLayout mRelativeLayoutDeck;
    private Spinner mDeckSpinner;

    protected long mDeckId;
    protected boolean mIsWholeCollection;
    private MenuItem mTimeItem;
    private MenuItem mDeckItem;

    private Menu mMenu;


    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private AnkiStatsTaskHandler mTaskHandler = null;
    private ActionBar mActionBar;
    private View mMainLayout;
    private int mSelectedStatType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTaskHandler = new AnkiStatsTaskHandler();


        mMainLayout = getLayoutInflater().inflate(R.layout.activity_anki_stats, null);
        initNavigationDrawer(mMainLayout);
        setContentView(mMainLayout);

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

    private void showDeckDialog(){
        mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View view = layoutInflater.inflate(R.layout.action_view_spinner, null);
        builder.setIcon(android.R.drawable.ic_input_get);
        builder.setView(view);
        builder.setTitle(getResources().getString(R.string.stats_select_decks));
        mDropDownDecks = AnkiDroidApp.getCol().getDecks().allSorted();
        mDropDownAdapter = new DeckDropDownAdapter(this, mDropDownDecks);
        mDeckSpinner = (Spinner)((RelativeLayout)view).getChildAt(0);
        mDeckSpinner.setAdapter(mDropDownAdapter);

        if (!AnkiStatsTaskHandler.isWholeCollection()) {
            String currentDeckName;
            try {
                currentDeckName = AnkiDroidApp.getCol().getDecks().current().getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            for (int dropDownDeckIdx = 0; dropDownDeckIdx < mDropDownDecks.size(); dropDownDeckIdx++) {
                JSONObject deck = mDropDownDecks.get(dropDownDeckIdx);
                String deckName;
                try {
                    deckName = deck.getString("name");
                } catch (JSONException e) {
                    throw new RuntimeException();
                }
                if (deckName.equals(currentDeckName)) {
                    mDeckSpinner.setSelection(dropDownDeckIdx + 1, false);
                    break;
                }
            }
        } else {
            mDeckSpinner.setSelection(0, false);
        }

        final boolean isWholeCollection = mIsWholeCollection;
        final long currentDeckId = mDeckId;
        mDeckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position == 0) {
                    mIsWholeCollection = true;
                } else {
                    JSONObject deck = mDropDownDecks.get(position - 1);
                    Long deckId;
                    try {
                        deckId = deck.getLong("id");
                    } catch (JSONException e) {
                        throw new RuntimeException();
                    }
                    mIsWholeCollection = false;
                    mDeckId = deckId;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                //popupWindow.dismiss();
            }

        });

        builder.setPositiveButton(getResources().getString(R.string.dialog_ok),new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                AnkiStatsTaskHandler.setIsWholeCollection(mIsWholeCollection);
                if(!mIsWholeCollection)
                    AnkiDroidApp.getCol().getDecks().select(mDeckId);
                mSectionsPagerAdapter.notifyDataSetChanged();
            }});

        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                mIsWholeCollection = isWholeCollection;
                mDeckId = currentDeckId;
                dialog.cancel();
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void showTimeDialog(){

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View view = layoutInflater.inflate(R.layout.action_view_spinner, null);
        builder.setIcon(android.R.drawable.ic_input_get);
        builder.setView(view);
        builder.setTitle(getResources().getString(R.string.stats_select_time_scale));
        mTimeSpinner = (Spinner)((RelativeLayout)view).getChildAt(0);

        ArrayAdapter<String> timeFrameAdapter =new ArrayAdapter<String>(this,R.layout.drawer_list_item,
                R.id.drawer_list_item_text, getResources().getStringArray(R.array.stats_period));
        mTimeSpinner.setAdapter(timeFrameAdapter);

        mTimeSpinner.setSelection(mTaskHandler.getStatType(), false);
        final int currentStatType = mSelectedStatType;
        mTimeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

                switch (position) {
                    case Stats.TYPE_MONTH:
                        mSelectedStatType = Stats.TYPE_MONTH;
                        break;
                    case Stats.TYPE_YEAR:
                        mSelectedStatType = Stats.TYPE_YEAR;
                        break;
                    case Stats.TYPE_LIFE:
                        mSelectedStatType = Stats.TYPE_LIFE;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }

        });

        builder.setPositiveButton(getResources().getString(R.string.dialog_ok),new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                if (mTaskHandler.getStatType() != mSelectedStatType) {
                    mTaskHandler.setStatType(mSelectedStatType);
                    mSectionsPagerAdapter.notifyDataSetChanged();
                }
                mSectionsPagerAdapter.notifyDataSetChanged();
            }});

        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                mSelectedStatType = currentStatType;
                dialog.cancel();
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
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
            case R.id.action_time_spinner:
                showTimeDialog();
                return true;
            case R.id.action_deck_spinner:
                showDeckDialog();
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
        StatisticFragment currentFragment = (StatisticFragment) mSectionsPagerAdapter.getItem(tab.getPosition());
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
        StatisticFragment currentFragment = (StatisticFragment) mSectionsPagerAdapter.getItem(tab.getPosition());
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

        //this is called when mSectionsPagerAdapter.notifyDataSetChanged() is called, so checkAndUpdate() here
        //works best for updating all tabs
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
            return StatisticFragment.newInstance(position);
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
        private ChartFragment mInstance = null;
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

            mInstance = this;
            mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mActivityPager = ((AnkiStatsActivity)getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((AnkiStatsActivity)getActivity()).getSectionsPagerAdapter();
            mDeckId = AnkiDroidApp.getCol().getDecks().selected();
            mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();
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
        public void checkAndUpdate(){
            //System.err.println("<<<<<<<checkAndUpdate" + mSectionNumber);
            if(!mIsCreated)
                return;
            int height = mChart.getMeasuredHeight();
            int width = mChart.getMeasuredWidth();

            //are height and width checks still necessary without bitmaps?
            if(height != 0 && width != 0){
                if(mHeight != height || mWidth != width ||
                        mType != (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType() ||
                        mDeckId != AnkiDroidApp.getCol().getDecks().selected() ||
                        mIsWholeCollection != AnkiStatsTaskHandler.isWholeCollection()){
                    mHeight = height;
                    mWidth = width;
                    mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
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
        private int mHeight = 0;
        private int mWidth = 0;
        private OverviewStatisticsFragment mInstance = null;
        private int mSectionNumber;
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
            Bundle bundle = getArguments();
            mSectionNumber = bundle.getInt(ARG_SECTION_NUMBER);
            //int sectionNumber = 0;
            //System.err.println("sectionNumber: " + mSectionNumber);
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
            mHeight = mWebView.getMeasuredHeight();
            mWidth = mWebView.getMeasuredWidth();

            mInstance = this;
            mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
            mIsCreated = true;
            mActivityPager = ((AnkiStatsActivity)getActivity()).getViewPager();
            mActivitySectionPagerAdapter = ((AnkiStatsActivity)getActivity()).getSectionsPagerAdapter();
            mDeckId = AnkiDroidApp.getCol().getDecks().selected();
            mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();
            mDeckId = AnkiDroidApp.getCol().getDecks().selected();
            mIsWholeCollection = AnkiStatsTaskHandler.isWholeCollection();
            return rootView;
        }

        private void createStatisticOverview(){
            mCreateStatisticsOverviewTask = (((AnkiStatsActivity)getActivity()).getTaskHandler()).createStatisticsOverview(
                    mWebView, mProgressBar);
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
            int height = mWebView.getMeasuredHeight();
            int width = mWebView.getMeasuredWidth();
            if(mType != (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType() ||
                    mDeckId != AnkiDroidApp.getCol().getDecks().selected() ||
                    mIsWholeCollection != AnkiStatsTaskHandler.isWholeCollection()){
                mHeight = height;
                mWidth = width;
                mType = (((AnkiStatsActivity)getActivity()).getTaskHandler()).getStatType();
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

    private static class DeckDropDownViewHolder {
        public TextView deckNameView;
    }


    private final static class DeckDropDownAdapter extends BaseAdapter {

        private Context context;
        private ArrayList<JSONObject> decks;


        public DeckDropDownAdapter(Context context, ArrayList<JSONObject> decks) {
            this.context = context;
            this.decks = decks;
        }


        @Override
        public int getCount() {
            return decks.size() + 1;
        }


        @Override
        public Object getItem(int position) {
            if (position == 0) {
                return null;
            } else {
                return decks.get(position + 1);
            }
        }


        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeckDropDownViewHolder viewHolder;
            TextView deckNameView;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_item, parent, false);
                deckNameView = (TextView) convertView.findViewById(R.id.dropdown_deck_name);
                viewHolder = new DeckDropDownViewHolder();
                viewHolder.deckNameView = deckNameView;
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (DeckDropDownViewHolder) convertView.getTag();
                deckNameView = (TextView) viewHolder.deckNameView;
            }
            if (position == 0) {
                deckNameView.setText(context.getResources().getString(R.string.deck_summary_all_decks));
            } else {
                JSONObject deck = decks.get(position - 1);
                try {
                    String deckName = deck.getString("name");
                    deckNameView.setText(deckName);
                } catch (JSONException ex) {
                    new RuntimeException();
                }
            }
            return convertView;
        }


        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView deckNameView;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_item, parent, false);
                deckNameView = (TextView) convertView.findViewById(R.id.dropdown_deck_name);
                convertView.setTag(deckNameView);
            } else {
                deckNameView = (TextView) convertView.getTag();
            }
            if (position == 0) {
                deckNameView.setText(context.getResources().getString(R.string.deck_summary_all_decks));
            } else {
                JSONObject deck = decks.get(position - 1);
                try {
                    String deckName = deck.getString("name");
                    deckNameView.setText(deckName);
                } catch (JSONException ex) {
                    new RuntimeException();
                }
            }
            return convertView;
        }

    }

}
