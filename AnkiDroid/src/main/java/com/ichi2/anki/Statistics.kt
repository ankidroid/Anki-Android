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
@file:Suppress("DEPRECATION") // #7108: AsyncTask deprecation

package com.ichi2.anki

import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.*
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.annotation.CheckResult
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.runtimetools.TaskOperations.stopTaskGracefully
import com.ichi2.anki.stats.AnkiStatsTaskHandler
import com.ichi2.anki.stats.AnkiStatsTaskHandler.Companion.getInstance
import com.ichi2.anki.stats.ChartView
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Decks
import com.ichi2.libanki.stats.Stats
import com.ichi2.libanki.stats.Stats.AxisType
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*

@KotlinCleanup("attempt to make fields not null with lateinit")
class Statistics : NavigationDrawerActivity(), DeckSelectionListener, SubtitleListener {
    var viewPager: ViewPager2? = null
        private set
    var slidingTabLayout: TabLayout? = null
        private set
    @KotlinCleanup("maybe lateinit after AnkiStatsTaskHandler.getInstance() kotlin cleanup")
    var taskHandler: AnkiStatsTaskHandler? = null
        private set
    private var mDeckSpinnerSelection: DeckSpinnerSelection? = null
    private var mStatsDeckId: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        Timber.d("onCreate()")
        sIsSubtitle = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anki_stats)
        initNavigationDrawer(findViewById(android.R.id.content))
        startLoadingCollection()
    }

    override fun onCollectionLoaded(col: Collection) {
        Timber.d("onCollectionLoaded()")
        super.onCollectionLoaded(col)

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        // Set up the ViewPager with the sections adapter.
        @KotlinCleanup("cleanup: scope function")
        viewPager = findViewById(R.id.pager)
        viewPager!!.adapter = StatsPagerAdapter(this)
        viewPager!!.offscreenPageLimit = 8
        slidingTabLayout = findViewById(R.id.sliding_tabs)

        // Fixes #8984: scroll to position 0 in RTL layouts
        val tabObserver = slidingTabLayout!!.viewTreeObserver
        tabObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            // Note: we can't use a lambda as we use 'this' to refer to the class.
            override fun onGlobalLayout() {
                // we need this here: If we select tab 0 before in an RTL context the layout has been drawn,
                // then it doesn't perform a scroll animation and selects the wrong element
                slidingTabLayout!!.viewTreeObserver.removeOnGlobalLayoutListener(this)
                slidingTabLayout!!.selectTab(slidingTabLayout!!.getTabAt(0))
            }
        })

        // Setup Task Handler
        taskHandler = getInstance(col)

        // Dirty way to get text size from a TextView with current style, change if possible
        val size = FixedTextView(this).textSize
        taskHandler!!.standardTextSize = size
        // Prepare options menu only after loading everything
        invalidateOptionsMenu()
        //        StatisticFragment.updateAllFragments();
        val defaultDeck = AnkiDroidApp.getSharedPrefs(this).getString("stats_default_deck", "current")
        when (defaultDeck) {
            "current" -> mStatsDeckId = getCol().decks.selected()
            "all" -> mStatsDeckId = Stats.ALL_DECKS_ID
            else -> Timber.w("Unknown defaultDeck: %s", defaultDeck)
        }
        mDeckSpinnerSelection = DeckSpinnerSelection(this, col, findViewById(R.id.toolbar_spinner), true, true)
        mDeckSpinnerSelection!!.initializeActionBarDeckSpinner(this.supportActionBar!!)
        mDeckSpinnerSelection!!.selectDeckById(mStatsDeckId, false)
        taskHandler!!.setDeckId(mStatsDeckId)
        viewPager!!.adapter!!.notifyDataSetChanged()
    }

    override fun onResume() {
        Timber.d("onResume()")
        selectNavigationItem(R.id.nav_stats)
        super.onResume()
    }

    @KotlinCleanup("cleanup: findItem -> isChecked to nested function")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        // System.err.println("in onCreateOptionsMenu");
        val inflater = menuInflater
        inflater.inflate(R.menu.anki_stats, menu)

        // exit if mTaskHandler not initialized yet
        if (taskHandler != null) {
            when (taskHandler!!.statType) {
                AxisType.TYPE_MONTH -> {
                    val monthItem = menu.findItem(R.id.item_time_month)
                    monthItem.isChecked = true
                }
                AxisType.TYPE_YEAR -> {
                    val yearItem = menu.findItem(R.id.item_time_year)
                    yearItem.isChecked = true
                }
                AxisType.TYPE_LIFE -> {
                    val lifeItem = menu.findItem(R.id.item_time_all)
                    lifeItem.isChecked = true
                }
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    @KotlinCleanup("simplify with a when statement instead of if")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        val itemId = item.itemId
        if (itemId == R.id.item_time_month) {
            item.isChecked = !item.isChecked
            if (taskHandler!!.statType != AxisType.TYPE_MONTH) {
                taskHandler!!.statType = AxisType.TYPE_MONTH
                viewPager!!.adapter!!.notifyDataSetChanged()
            }
            return true
        } else if (itemId == R.id.item_time_year) {
            item.isChecked = !item.isChecked
            if (taskHandler!!.statType != AxisType.TYPE_YEAR) {
                taskHandler!!.statType = AxisType.TYPE_YEAR
                viewPager!!.adapter!!.notifyDataSetChanged()
            }
            return true
        } else if (itemId == R.id.item_time_all) {
            item.isChecked = !item.isChecked
            if (taskHandler!!.statType != AxisType.TYPE_LIFE) {
                taskHandler!!.statType = AxisType.TYPE_LIFE
                viewPager!!.adapter!!.notifyDataSetChanged()
            }
            return true
        } else if (itemId == R.id.action_time_chooser) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * @return text to be used in the subtitle of the drop-down deck selector
     */
    override val subtitleText: String
        get() = resources.getString(R.string.statistics)

    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) {
            return
        }
        mDeckSpinnerSelection!!.initializeActionBarDeckSpinner(this.supportActionBar!!)
        mStatsDeckId = deck.deckId
        mDeckSpinnerSelection!!.selectDeckById(mStatsDeckId, true)
        taskHandler!!.setDeckId(mStatsDeckId)
        viewPager!!.adapter!!.notifyDataSetChanged()
    }

    /**
     * A [FragmentStateAdapter] that returns a fragment corresponding to
     * one of the tabs.
     */
    class StatsPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        override fun createFragment(position: Int): Fragment {
            val item = StatisticFragment.newInstance(position)
            item.checkAndUpdate()
            return item
        }

        override fun getItemCount(): Int {
            return 9
        }
    }

    @KotlinCleanup("attempt to make fields non null with lateinit")
    abstract class StatisticFragment : Fragment() {
        // track current settings for each individual fragment
        protected var deckId: Long = 0

        // #7108: AsyncTask
        protected var statisticsTask: AsyncTask<*, *, *>? = null

        // #7108: AsyncTask
        protected var statisticsOverviewTask: AsyncTask<*, *, *>? = null
        private var mActivityPager: ViewPager2? = null
        private var mSlidingTabLayout: TabLayout? = null
        private var mTabLayoutMediator: TabLayoutMediator? = null
        private val mDataObserver: AdapterDataObserver = object : AdapterDataObserver() {
            override fun onChanged() {
                checkAndUpdate()
                super.onChanged()
            }
        }

        override fun onResume() {
            checkAndUpdate()
            super.onResume()
        }

        override fun onDestroy() {
            cancelTasks()
            if (mActivityPager!!.adapter != null) {
                mActivityPager!!.adapter!!.unregisterAdapterDataObserver(mDataObserver)
            }
            super.onDestroy()
        }

        protected fun cancelTasks() {
            Timber.w("canceling tasks")
            stopTaskGracefully(statisticsTask)
            stopTaskGracefully(statisticsOverviewTask)
        }

        @KotlinCleanup("simplify return")
        private fun getTabTitle(position: Int): String {
            val l = Locale.getDefault()
            when (position) {
                TODAYS_STATS_TAB_POSITION -> return getString(R.string.stats_overview).toUpperCase(l)
                FORECAST_TAB_POSITION -> return getString(R.string.stats_forecast).toUpperCase(l)
                REVIEW_COUNT_TAB_POSITION -> return getString(R.string.stats_review_count).toUpperCase(l)
                REVIEW_TIME_TAB_POSITION -> return getString(R.string.stats_review_time).toUpperCase(l)
                INTERVALS_TAB_POSITION -> return getString(R.string.stats_review_intervals).toUpperCase(l)
                HOURLY_BREAKDOWN_TAB_POSITION -> return getString(R.string.stats_breakdown).toUpperCase(l)
                WEEKLY_BREAKDOWN_TAB_POSITION -> return getString(R.string.stats_weekly_breakdown).toUpperCase(l)
                ANSWER_BUTTONS_TAB_POSITION -> return getString(R.string.stats_answer_buttons).toUpperCase(l)
                CARDS_TYPES_TAB_POSITION -> return getString(R.string.title_activity_template_editor).toUpperCase(l)
            }
            return ""
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            mActivityPager = (requireActivity() as Statistics).viewPager
            if (mActivityPager!!.adapter != null) {
                mActivityPager!!.adapter!!.registerAdapterDataObserver(mDataObserver)
            }
            mSlidingTabLayout = (requireActivity() as Statistics).slidingTabLayout
            initTabLayoutMediator()
        }

        private fun initTabLayoutMediator() {
            if (mTabLayoutMediator != null) {
                mTabLayoutMediator!!.detach()
            }
            mTabLayoutMediator = TabLayoutMediator(
                mSlidingTabLayout!!, mActivityPager!!
            ) { tab: TabLayout.Tab, position: Int -> tab.text = getTabTitle(position) }
            mTabLayoutMediator!!.attach()
        }

        abstract fun checkAndUpdate()

        companion object {
            /**
             * The fragment argument representing the section number for this
             * fragment.
             */
            const val ARG_SECTION_NUMBER = "section_number"

            /**
             * Returns a new instance of this fragment for the given section
             * number.
             */
            @CheckResult
            @KotlinCleanup("function could be simplified(look at bundleOf function)")
            fun newInstance(sectionNumber: Int): StatisticFragment {
                val fragment: StatisticFragment
                val args = Bundle()
                fragment = when (sectionNumber) {
                    FORECAST_TAB_POSITION, REVIEW_COUNT_TAB_POSITION, REVIEW_TIME_TAB_POSITION, INTERVALS_TAB_POSITION, HOURLY_BREAKDOWN_TAB_POSITION, WEEKLY_BREAKDOWN_TAB_POSITION, ANSWER_BUTTONS_TAB_POSITION, CARDS_TYPES_TAB_POSITION -> ChartFragment()
                    TODAYS_STATS_TAB_POSITION -> OverviewStatisticsFragment()
                    else -> throw IllegalArgumentException("Unknown section number: $sectionNumber")
                }
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                fragment.arguments = args
                return fragment
            }
        }
    }

    /**
     * A chart fragment containing a ChartView.
     */
    @KotlinCleanup("attempt to make fields non null with lateinit")
    class ChartFragment : StatisticFragment() {
        private var mChart: ChartView? = null
        private var mProgressBar: ProgressBar? = null
        private var mHeight = 0
        private var mWidth = 0
        private var mSectionNumber = 0
        private var mType = AxisType.TYPE_MONTH
        private var mIsCreated = false

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            setHasOptionsMenu(true)
            val bundle = arguments
            mSectionNumber = bundle!!.getInt(ARG_SECTION_NUMBER)
            // int sectionNumber = 0;
            // System.err.println("sectionNumber: " + mSectionNumber);
            val rootView = inflater.inflate(R.layout.fragment_anki_stats, container, false)
            mChart = rootView.findViewById(R.id.image_view_chart)
            if (mChart == null) {
                Timber.d("mChart null!")
            } else {
                Timber.d("mChart is not null!")
            }

            // mChart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            mProgressBar = rootView.findViewById(R.id.progress_bar_stats)
            mProgressBar!!.setVisibility(View.VISIBLE)
            // mChart.setVisibility(View.GONE);

            // TODO: Implementing loader for Collection in Fragment itself would be a better solution.
            if ((requireActivity() as Statistics).taskHandler == null) {
                // Close statistics if the TaskHandler hasn't been loaded yet
                Timber.e("Statistics.ChartFragment.onCreateView() TaskHandler not found")
                requireActivity().finish()
                return rootView
            }
            createChart()
            mHeight = mChart!!.getMeasuredHeight()
            mWidth = mChart!!.getMeasuredWidth()
            mChart!!.addFragment(this)
            mType = (requireActivity() as Statistics).taskHandler!!.statType
            mIsCreated = true
            deckId = (requireActivity() as Statistics).mStatsDeckId
            if (deckId != Stats.ALL_DECKS_ID) {
                val col = CollectionHelper.getInstance().getCol(requireActivity())
                val baseName = Decks.basename(col.decks.current().getString("name"))
                if (sIsSubtitle) {
                    (requireActivity() as AppCompatActivity).supportActionBar!!.setSubtitle(baseName)
                } else {
                    requireActivity().title = baseName
                }
            } else {
                if (sIsSubtitle) {
                    (requireActivity() as AppCompatActivity).supportActionBar!!.setSubtitle(R.string.stats_deck_collection)
                } else {
                    requireActivity().title = resources.getString(R.string.stats_deck_collection)
                }
            }
            return rootView
        }

        @KotlinCleanup("nested function: taskHandler!!.createChart, or return ChartType from when")
        private fun createChart() {
            val statisticsActivity = requireActivity() as Statistics
            val taskHandler = statisticsActivity.taskHandler
            when (mSectionNumber) {
                FORECAST_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.FORECAST, mChart, mProgressBar
                )
                REVIEW_COUNT_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.REVIEW_COUNT, mChart, mProgressBar
                )
                REVIEW_TIME_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.REVIEW_TIME, mChart, mProgressBar
                )
                INTERVALS_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.INTERVALS, mChart, mProgressBar
                )
                HOURLY_BREAKDOWN_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.HOURLY_BREAKDOWN, mChart, mProgressBar
                )
                WEEKLY_BREAKDOWN_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.WEEKLY_BREAKDOWN, mChart, mProgressBar
                )
                ANSWER_BUTTONS_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.ANSWER_BUTTONS, mChart, mProgressBar
                )
                CARDS_TYPES_TAB_POSITION -> statisticsTask = taskHandler!!.createChart(
                    Stats.ChartType.CARDS_TYPES, mChart, mProgressBar
                )
            }
        }

        override fun checkAndUpdate() {
            if (!mIsCreated) {
                return
            }
            val height = mChart!!.measuredHeight
            val width = mChart!!.measuredWidth

            // are height and width checks still necessary without bitmaps?
            if (height != 0 && width != 0) {
                if (mHeight != height || mWidth != width || mType != (requireActivity() as Statistics).taskHandler!!.statType || deckId != (requireActivity() as Statistics).mStatsDeckId) {
                    mHeight = height
                    mWidth = width
                    mType = (requireActivity() as Statistics).taskHandler!!.statType
                    mProgressBar!!.visibility = View.VISIBLE
                    mChart!!.visibility = View.GONE
                    deckId = (requireActivity() as Statistics).mStatsDeckId
                    cancelTasks()
                    createChart()
                }
            }
        }
    }

    @KotlinCleanup("attempt to make fields non null with lateinit")
    class OverviewStatisticsFragment : StatisticFragment() {
        private var mWebView: WebView? = null
        private var mProgressBar: ProgressBar? = null
        private var mType = AxisType.TYPE_MONTH
        private var mIsCreated = false

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            setHasOptionsMenu(true)
            val rootView = inflater.inflate(R.layout.fragment_anki_stats_overview, container, false)
            val handler = (requireActivity() as Statistics).taskHandler
            // Workaround for issue 2406 -- crash when resuming after app is purged from RAM
            // TODO: Implementing loader for Collection in Fragment itself would be a better solution.
            if (handler == null) {
                Timber.e("Statistics.OverviewStatisticsFragment.onCreateView() TaskHandler not found")
                requireActivity().finish()
                return rootView
            }
            mWebView = rootView.findViewById(R.id.web_view_stats)
            if (mWebView == null) {
                Timber.d("mChart null!")
            } else {
                Timber.d("mChart is not null!")
                // Set transparent color to prevent flashing white when night mode enabled
                mWebView!!.setBackgroundColor(Color.argb(1, 0, 0, 0))
            }

            // mChart.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            mProgressBar = rootView.findViewById(R.id.progress_bar_stats_overview)
            mProgressBar!!.visibility = View.VISIBLE
            // mChart.setVisibility(View.GONE);
            createStatisticOverview()
            mType = handler.statType
            mIsCreated = true
            val col = CollectionHelper.getInstance().getCol(requireActivity())
            deckId = (requireActivity() as Statistics).mStatsDeckId
            if (deckId != Stats.ALL_DECKS_ID) {
                val basename = Decks.basename(col.decks.current().getString("name"))
                if (sIsSubtitle) {
                    (requireActivity() as AppCompatActivity).supportActionBar!!.setSubtitle(basename)
                } else {
                    requireActivity().title = basename
                }
            } else {
                if (sIsSubtitle) {
                    (requireActivity() as AppCompatActivity).supportActionBar!!.setSubtitle(R.string.stats_deck_collection)
                } else {
                    requireActivity().setTitle(R.string.stats_deck_collection)
                }
            }
            return rootView
        }

        private fun createStatisticOverview() {
            val handler = (requireActivity() as Statistics).taskHandler
            statisticsOverviewTask = handler!!.createStatisticsOverview(mWebView, mProgressBar)
        }

        override fun checkAndUpdate() {
            if (!mIsCreated) {
                return
            }
            if (mType != (requireActivity() as Statistics).taskHandler!!.statType ||
                deckId != (requireActivity() as Statistics).mStatsDeckId
            ) {
                mType = (requireActivity() as Statistics).taskHandler!!.statType
                mProgressBar!!.visibility = View.VISIBLE
                mWebView!!.visibility = View.GONE
                deckId = (requireActivity() as Statistics).mStatsDeckId
                cancelTasks()
                createStatisticOverview()
            }
        }
    }

    override fun onBackPressed() {
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            val data = Intent()
            if (intent.hasExtra("selectedDeck")) {
                data.putExtra("originalDeck", intent.getLongExtra("selectedDeck", 0L))
            }
            setResult(RESULT_CANCELED, data)
            finishWithAnimation(ActivityTransitionAnimation.Direction.END)
        }
    }

    companion object {
        const val TODAYS_STATS_TAB_POSITION = 0
        const val FORECAST_TAB_POSITION = 1
        const val REVIEW_COUNT_TAB_POSITION = 2
        const val REVIEW_TIME_TAB_POSITION = 3
        const val INTERVALS_TAB_POSITION = 4
        const val HOURLY_BREAKDOWN_TAB_POSITION = 5
        const val WEEKLY_BREAKDOWN_TAB_POSITION = 6
        const val ANSWER_BUTTONS_TAB_POSITION = 7
        const val CARDS_TYPES_TAB_POSITION = 8
        private var sIsSubtitle = false
    }
}
