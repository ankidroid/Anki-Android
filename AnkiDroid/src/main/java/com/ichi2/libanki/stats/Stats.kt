/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
package com.ichi2.libanki.stats

import android.content.Context
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.preferences.Preferences
import com.ichi2.anki.stats.OverviewStatsBuilder.OverviewStats
import com.ichi2.anki.stats.OverviewStatsBuilder.OverviewStats.AnswerButtonsOverview
import com.ichi2.anki.stats.StatsMetaInfo
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.Time.Companion.gregorianCalendar
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*

class Stats(private val col: Collection, did: Long) {
    enum class AxisType(val days: Int, val descriptionId: Int) {
        TYPE_MONTH(30, R.string.stats_period_month), TYPE_YEAR(
            365,
            R.string.stats_period_year
        ),
        TYPE_LIFE(-1, R.string.stats_period_lifetime);
    }

    enum class ChartType {
        FORECAST, REVIEW_COUNT, REVIEW_TIME, INTERVALS, HOURLY_BREAKDOWN, WEEKLY_BREAKDOWN, ANSWER_BUTTONS, CARDS_TYPES, OTHER
    }

    private val mWholeCollection: Boolean
    private val mDeckId: Long
    private var mDynamicAxis = false
    var seriesList: Array<DoubleArray>? = null
        private set
    private var mHasColoredCumulative = false
    private var mType: AxisType? = null
    private var mTitle = 0
    private var mBackwards = false
    private var mValueLabels: IntArray? = null
    private var mColors: IntArray? = null
    private var mAxisTitles: IntArray? = null
    private var mMaxCards = 0
    private var mMaxElements = 0
    private var mFirstElement = 0.0
    private var mLastElement = 0.0
    private var mZeroIndex = 0
    private var mFoundLearnCards = false
    private var mFoundCramCards = false
    private var mFoundRelearnCards = false
    var cumulative: Array<DoubleArray>? = null
        private set
    private var mAverage: String? = null
    private var mLongest: String? = null
    private var mPeak = 0.0
    private var mMcount = 0.0

    val metaInfo: Array<Any?>
        get() {
            val title: String
            title = if (mWholeCollection) {
                AnkiDroidApp.instance.resources.getString(R.string.card_browser_all_decks)
            } else {
                col.decks.get(mDeckId).getString("name")
            }
            return arrayOf(
                /*0*/
                mType, /*1*/
                mTitle, /*2*/
                mBackwards, /*3*/
                mValueLabels, /*4*/
                mColors, /*5*/
                mAxisTitles, /*6*/
                title, /*7*/
                mMaxCards, /*8*/
                mMaxElements, /*9*/
                mFirstElement, /*10*/
                mLastElement, /*11*/
                mZeroIndex, /*12*/
                mFoundLearnCards, /*13*/
                mFoundCramCards, /*14*/
                mFoundRelearnCards, /*15*/
                mAverage, /*16*/
                mLongest, /*17*/
                mPeak, /*18*/
                mMcount, /*19*/
                mHasColoredCumulative, /*20*/
                mDynamicAxis
            )
        }

    /**
     * Today's statistics
     */
    fun calculateTodayStats(): IntArray {
        var lim = _getDeckFilter()
        if (lim.length > 0) {
            lim = " and $lim"
        }
        var query =
            "select sum(case when ease > 0 then 1 else 0 end), " + /* cards, excludes rescheduled cards https://github.com/ankidroid/Anki-Android/issues/8592 */
                "sum(time)/1000, " + /*time*/
                "sum(case when ease = 1 then 1 else 0 end), " + /* failed */
                "sum(case when type = " + Consts.CARD_TYPE_NEW + " then 1 else 0 end), " + /* learning */
                "sum(case when type = " + Consts.CARD_TYPE_LRN + " then 1 else 0 end), " + /* review */
                "sum(case when type = " + Consts.CARD_TYPE_REV + " then 1 else 0 end), " + /* relearn */
                "sum(case when type = " + Consts.CARD_TYPE_RELEARNING + " then 1 else 0 end) " + /* filter */
                "from revlog " +
                "where ease > 0 " + // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
                "and id > " + (col.sched.dayCutoff() - SECONDS_PER_DAY) * 1000 + " " + lim
        Timber.d("todays statistics query: %s", query)
        var cards: Int
        var thetime: Int
        var failed: Int
        var lrn: Int
        var rev: Int
        var relrn: Int
        var filt: Int
        col.db
            .query(query).use { cur ->
                cur.moveToFirst()
                cards = cur.getInt(0)
                thetime = cur.getInt(1)
                failed = cur.getInt(2)
                lrn = cur.getInt(3)
                rev = cur.getInt(4)
                relrn = cur.getInt(5)
                filt = cur.getInt(6)
            }
        query =
            "select sum(case when ease > 0 then 1 else 0 end), " + /* cards, excludes rescheduled cards https://github.com/ankidroid/Anki-Android/issues/8592 */
            "sum(case when ease = 1 then 0 else 1 end) from revlog " +
            "where ease > 0 " + // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
            "and lastIvl >= 21 and id > " + (col.sched.dayCutoff() - SECONDS_PER_DAY) * 1000 + " " + lim
        Timber.d("todays statistics query 2: %s", query)
        var mcnt: Int
        var msum: Int
        col.db
            .query(query).use { cur ->
                cur.moveToFirst()
                mcnt = cur.getInt(0)
                msum = cur.getInt(1)
            }
        return intArrayOf(cards, thetime, failed, lrn, rev, relrn, filt, mcnt, msum)
    }

    private fun getRevlogTimeFilter(timespan: AxisType, inverse: Boolean): String {
        return if (timespan == AxisType.TYPE_LIFE) {
            ""
        } else {
            val operator: String
            operator = if (inverse) {
                "<= "
            } else {
                "> "
            }
            "id " + operator + (col.sched.dayCutoff() - timespan.days * SECONDS_PER_DAY) * 1000
        }
    }

    fun getNewCards(timespan: AxisType): Pair<Int, Double> {
        val chunk = getChunk(timespan)
        val num = getNum(timespan)
        val lims: MutableList<String?> = ArrayList(2)
        if (timespan != AxisType.TYPE_LIFE) {
            lims.add("id > " + (col.sched.dayCutoff() - num * chunk * SECONDS_PER_DAY) * 1000)
        }
        lims.add("did in " + _limit())
        val lim: String
        lim = if (!lims.isEmpty()) {
            "where " + lims.joinToString(" and ")
        } else {
            ""
        }
        // PORTING: tf appears unused, but was passed into the SQL query
        @Suppress("UNUSED_VARIABLE")
        val tf = if (timespan == AxisType.TYPE_MONTH) {
            60.0 // minutes
        } else {
            3600.0 // hours
        }

        @Suppress("UNUSED_VARIABLE")
        val cut = col.sched.dayCutoff()
        val cardCount = col.db.queryScalar("select count(id) from cards $lim")
        var periodDays = _periodDays(timespan).toLong() // 30|365|-1
        if (periodDays == -1L) {
            periodDays = _deckAge(DeckAgeType.ADD)
        }
        // Porting - being safe to avoid DIV0
        if (periodDays == 0L) {
            Timber.w("periodDays should not be 0")
            periodDays = 1
        }
        return Pair(cardCount, cardCount.toDouble() / periodDays.toDouble())
    }

    private enum class DeckAgeType {
        ADD, REVIEW
    }

    private fun _deckAge(by: DeckAgeType): Long {
        var lim = _revlogLimit()
        if (lim.isNotEmpty()) {
            lim += " where $lim"
        }
        var t = 0.0
        if (by == DeckAgeType.REVIEW) {
            t = col.db.queryLongScalar("select id from revlog $lim order by id limit 1").toDouble()
        } else if (by == DeckAgeType.ADD) {
            lim = "where did in " + Utils.ids2str(col.decks.active())
            t = col.db.queryLongScalar("select id from cards $lim order by id limit 1").toDouble()
        }
        val period: Long
        period = if (t == 0.0) {
            1
        } else {
            Math.max(1, (1 + (col.sched.dayCutoff() - t / 1000) / SECONDS_PER_DAY).toInt()).toLong()
        }
        return period
    }

    private fun _revlogLimit(): String {
        return if (mWholeCollection) {
            ""
        } else {
            "cid in (select id from cards where did in " + Utils.ids2str(col.decks.active()) + ")"
        }
    }

    private fun getRevlogFilter(timespan: AxisType, inverseTimeSpan: Boolean): String {
        val lims = ArrayList<String>(2)
        val dayFilter = getRevlogTimeFilter(timespan, inverseTimeSpan)
        if (dayFilter.isNotEmpty()) {
            lims.add(dayFilter)
        }
        var lim = _getDeckFilter().replace("[\\[\\]]".toRegex(), "")
        if (lim.length > 0) {
            lims.add(lim)
        }

        // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
        lims.add("ease > 0")
        lim = "WHERE "
        lim += lims.toTypedArray().joinToString(" AND ")
        return lim
    }

    fun calculateOverviewStatistics(timespan: AxisType, oStats: OverviewStats) {
        oStats.allDays = timespan.days
        val lim = getRevlogFilter(timespan, false)
        col.db.query(
            "SELECT COUNT(*) as num_reviews, sum(case when type = " + Consts.CARD_TYPE_NEW + " then 1 else 0 end) as new_cards FROM revlog " + lim
        ).use { cur ->
            while (cur.moveToNext()) {
                oStats.totalReviews = cur.getInt(0)
            }
        }
        val cntquery =
            (
                "SELECT  COUNT(*) numDays, MIN(day) firstDay, SUM(time_per_day) sum_time  from (" +
                    " SELECT (cast((id/1000 - " + col.sched.dayCutoff() + ") / " + SECONDS_PER_DAY + " AS INT)) AS day,  sum(time/1000.0/60.0) AS time_per_day" +
                    " FROM revlog " + lim + " GROUP BY day ORDER BY day)"
                )
        Timber.d("Count cntquery: %s", cntquery)
        col.db.query(cntquery).use { cur ->
            while (cur.moveToNext()) {
                oStats.daysStudied = cur.getInt(0)
                oStats.totalTime = cur.getDouble(2)
                if (timespan == AxisType.TYPE_LIFE) {
                    oStats.allDays = Math.abs(cur.getInt(1)) + 1 // +1 for today
                }
            }
        }
        col.db.query(
            "select avg(ivl), max(ivl) from cards where did in " + _limit() + " and queue = " + Consts.QUEUE_TYPE_REV + ""
        ).use { cur ->
            cur.moveToFirst()
            oStats.averageInterval = cur.getDouble(0)
            oStats.longestInterval = cur.getDouble(1)
        }
        oStats.reviewsPerDayOnAll = oStats.totalReviews.toDouble() / oStats.allDays
        oStats.reviewsPerDayOnStudyDays =
            if (oStats.daysStudied == 0) 0.0 else oStats.totalReviews.toDouble() / oStats.daysStudied
        oStats.timePerDayOnAll = oStats.totalTime / oStats.allDays
        oStats.timePerDayOnStudyDays =
            if (oStats.daysStudied == 0) 0.0 else oStats.totalTime / oStats.daysStudied
        val newCardStats = getNewCards(timespan)
        oStats.totalNewCards = newCardStats.first
        oStats.newCardsPerDay = newCardStats.second
        val list = eases(timespan)
        oStats.newCardsOverview = toOverview(0, list)
        oStats.youngCardsOverview = toOverview(1, list)
        oStats.matureCardsOverview = toOverview(2, list)
        val totalCountQuery =
            "select count(id), count(distinct nid) from cards where did in " + _limit()
        col.db.query(totalCountQuery).use { cur ->
            if (cur.moveToFirst()) {
                oStats.totalCards = cur.getLong(0)
                oStats.totalNotes = cur.getLong(1)
            }
        }
        val factorQuery = """select
min(factor) / 10.0,
avg(factor) / 10.0,
max(factor) / 10.0
from cards where did in ${_limit()} and queue = ${Consts.QUEUE_TYPE_REV}"""
        col.db.query(factorQuery).use { cur ->
            if (cur.moveToFirst()) {
                oStats.lowestEase = cur.getLong(0).toDouble()
                oStats.averageEase = cur.getLong(1).toDouble()
                oStats.highestEase = cur.getLong(2).toDouble()
            }
        }
    }

    @KotlinCleanup("list is likely an ArrayList<Int>")
    private fun toOverview(type: Int, list: ArrayList<DoubleArray>): AnswerButtonsOverview {
        val answerButtonsOverview = AnswerButtonsOverview()
        val INDEX_TYPE = 0 // 0:learn; 1:young; 2:mature
        val INDEX_EASE = 1 // 1...4 - AGAIN - EASY
        val INDEX_COUNT = 2
        val EASE_AGAIN = 1.0
        for (elements in list) {
            // if we're not of the type we're looking for, continue
            if (elements[INDEX_TYPE].toInt() != type) {
                continue
            }
            val answersCountForTypeAndEase = elements[INDEX_COUNT].toInt()
            val isAgain = elements[INDEX_EASE] == EASE_AGAIN
            answerButtonsOverview.total += answersCountForTypeAndEase
            answerButtonsOverview.correct += if (isAgain) 0 else answersCountForTypeAndEase
        }
        return answerButtonsOverview
    }

    fun calculateDue(context: Context?, type: AxisType): Boolean {
        // Not in libanki
        var metaInfo = StatsMetaInfo()
        metaInfo = AdvancedStatistics().calculateDueAsMetaInfo(metaInfo, type, context, _limit())
        return if (metaInfo.isStatsCalculated) {
            mDynamicAxis = metaInfo.dynamicAxis
            mHasColoredCumulative = metaInfo.hasColoredCumulative
            mType = metaInfo.type
            mTitle = metaInfo.title
            mBackwards = metaInfo.backwards
            mValueLabels = metaInfo.valueLabels
            mColors = metaInfo.colors
            mAxisTitles = metaInfo.axisTitles
            mMaxCards = metaInfo.maxCards
            mMaxElements = metaInfo.maxElements
            mFirstElement = metaInfo.firstElement
            mLastElement = metaInfo.lastElement
            mZeroIndex = metaInfo.zeroIndex
            cumulative = metaInfo.cumulative
            mMcount = metaInfo.mcount
            seriesList = metaInfo.seriesList
            metaInfo.isDataAvailable
        } else {
            calculateDue(type)
        }
    }

    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    private fun calculateDue(type: AxisType): Boolean {
        mHasColoredCumulative = false
        mType = type
        mDynamicAxis = true
        mBackwards = true
        mTitle = R.string.stats_forecast
        mValueLabels = intArrayOf(R.string.statistics_young, R.string.statistics_mature)
        mColors = intArrayOf(R.attr.stats_young, R.attr.stats_mature)
        mAxisTitles =
            intArrayOf(type.ordinal, R.string.stats_cards, R.string.stats_cumulative_cards)
        var end = 0
        var chunk = 0
        when (type) {
            AxisType.TYPE_MONTH -> {
                end = 31
                chunk = 1
            }
            AxisType.TYPE_YEAR -> {
                end = 52
                chunk = 7
            }
            AxisType.TYPE_LIFE -> {
                end = -1
                chunk = 30
            }
        }
        var lim =
            "" // AND due - " + mCol.getSched().getToday() + " >= " + start; // leave this out in order to show
        // card too which were due the days before
        if (end != -1) {
            lim += " AND day <= $end"
        }
        val dues = ArrayList<IntArray>()
        val query = (
            "SELECT (due - " + col.sched.today() + ")/" + chunk +
                " AS day, " + // day
                "count(), " + // all cards
                "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " + // mature cards
                "FROM cards WHERE did IN " + _limit() + " AND queue IN (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")" + lim +
                " GROUP BY day ORDER BY day"
            )
        Timber.d("Forecast query: %s", query)
        col
            .db.query(query).use { cur ->
                while (cur.moveToNext()) {
                    dues.add(intArrayOf(cur.getInt(0), cur.getInt(1), cur.getInt(2)))
                }
            }
        // small adjustment for a proper chartbuilding with achartengine
        if (dues.isEmpty() || dues[0][0] > 0) {
            dues.add(0, intArrayOf(0, 0, 0))
        }
        if (end == -1 && dues.size < 2) {
            end = 31
        }
        if (type != AxisType.TYPE_LIFE && dues[dues.size - 1][0] < end) {
            dues.add(intArrayOf(end, 0, 0))
        } else if (type == AxisType.TYPE_LIFE && dues.size < 2) {
            dues.add(intArrayOf(Math.max(12, dues[dues.size - 1][0] + 1), 0, 0))
        }
        seriesList = Array(3) { DoubleArray(dues.size) }
        for (i in dues.indices) {
            val data = dues[i]
            if (data[1] > mMaxCards) {
                mMaxCards = data[1]
            }
            seriesList!![0][i] = data[0].toDouble()
            seriesList!![1][i] = data[1].toDouble()
            seriesList!![2][i] = data[2].toDouble()
            if (data[0] > mLastElement) {
                mLastElement = data[0].toDouble()
            }
            if (data[0] == 0) {
                mZeroIndex = i
            }
        }
        mMaxElements = dues.size - 1
        when (mType) {
            AxisType.TYPE_MONTH -> mLastElement = 31.0
            AxisType.TYPE_YEAR -> mLastElement = 52.0
            else -> {}
        }
        mFirstElement = 0.0
        mHasColoredCumulative = false
        cumulative = createCumulative(
            arrayOf(
                seriesList!![0],
                seriesList!![1]
            ),
            mZeroIndex
        )
        mMcount = cumulative!![1][cumulative!![1].size - 1]
        // some adjustments to not crash the chartbuilding with empty data
        if (mMaxElements == 0) {
            mMaxElements = 10
        }
        if (mMcount == 0.0) {
            mMcount = 10.0
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0.0
            mLastElement = 6.0
        }
        if (mMaxCards == 0) {
            mMaxCards = 10
        }
        return !dues.isEmpty()
    }

    fun calculateReviewCount(type: AxisType): Boolean {
        return calculateDone(type, ChartType.REVIEW_COUNT)
    }

    fun calculateReviewTime(type: AxisType): Boolean {
        return calculateDone(type, ChartType.REVIEW_TIME)
    }

    /**
     * Calculation of Review count or Review time
     * @param type Type
     * @param charType CharType.REVIEW_COUNT or Chartype.REVIEW_TIME
     */
    private fun calculateDone(type: AxisType, charType: ChartType): Boolean {
        mHasColoredCumulative = true
        mDynamicAxis = true
        mType = type
        mBackwards = true
        if (charType == ChartType.REVIEW_COUNT) {
            mTitle = R.string.stats_review_count
            mAxisTitles =
                intArrayOf(type.ordinal, R.string.stats_answers, R.string.stats_cumulative_answers)
        } else if (charType == ChartType.REVIEW_TIME) {
            mTitle = R.string.stats_review_time
        }
        mValueLabels = intArrayOf(
            R.string.statistics_cram,
            R.string.statistics_learn,
            R.string.statistics_relearn,
            R.string.statistics_young,
            R.string.statistics_mature
        )
        mColors = intArrayOf(
            R.attr.stats_cram,
            R.attr.stats_learn,
            R.attr.stats_relearn,
            R.attr.stats_young,
            R.attr.stats_mature
        )
        var num = 0
        var chunk = 0
        when (type) {
            AxisType.TYPE_MONTH -> {
                num = 31
                chunk = 1
            }
            AxisType.TYPE_YEAR -> {
                num = 52
                chunk = 7
            }
            AxisType.TYPE_LIFE -> {
                num = -1
                chunk = 30
            }
        }
        val lims = ArrayList<String>(2)
        if (num != -1) {
            lims.add("id > " + (col.sched.dayCutoff() - (num + 1) * chunk * SECONDS_PER_DAY) * 1000)
        }
        var lim = _getDeckFilter().replace("[\\[\\]]".toRegex(), "")
        if (lim.length > 0) {
            lims.add(lim)
        }
        if (!lims.isEmpty()) {
            lim = "WHERE "
            while (lims.size > 1) {
                lim += lims.removeAt(0) + " AND "
            }
            lim += lims.removeAt(0)
        } else {
            lim = ""
        }
        val ti: String
        val tf: String
        if (charType == ChartType.REVIEW_TIME) {
            ti = "time/1000.0"
            if (mType == AxisType.TYPE_MONTH) {
                tf = "/60.0" // minutes
                mAxisTitles = intArrayOf(
                    type.ordinal,
                    R.string.stats_minutes,
                    R.string.stats_cumulative_time_minutes
                )
            } else {
                tf = "/3600.0" // hours
                mAxisTitles = intArrayOf(
                    type.ordinal,
                    R.string.stats_hours,
                    R.string.stats_cumulative_time_hours
                )
            }
        } else {
            ti = "1"
            tf = ""
        }
        val list = ArrayList<DoubleArray>()
        val query =
            (
                "SELECT (cast((id/1000 - " + col.sched.dayCutoff() + ") / " + SECONDS_PER_DAY + " AS INT))/" +
                    chunk + " AS day, " + "sum(CASE WHEN type = " + Consts.CARD_TYPE_NEW + " THEN " + ti + " ELSE 0 END)" +
                    tf +
                    ", " + // lrn
                    "sum(CASE WHEN type = " + Consts.CARD_TYPE_LRN + " AND lastIvl < 21 THEN " + ti + " ELSE 0 END)" + tf +
                    ", " + // yng
                    "sum(CASE WHEN type = " + Consts.CARD_TYPE_LRN + " AND lastIvl >= 21 THEN " + ti + " ELSE 0 END)" + tf +
                    ", " + // mtr
                    "sum(CASE WHEN type = 2 THEN " + ti + " ELSE 0 END)" + tf + ", " + // lapse
                    "sum(CASE WHEN type = " + Consts.CARD_TYPE_RELEARNING + " THEN " + ti + " ELSE 0 END)" + tf + // cram
                    " FROM revlog " + lim + " GROUP BY day ORDER BY day"
                )
        Timber.d("ReviewCount query: %s", query)
        col
            .db
            .query(query).use { cur ->
                while (cur.moveToNext()) {
                    list.add(
                        doubleArrayOf(
                            cur.getDouble(0),
                            cur.getDouble(5),
                            cur.getDouble(1),
                            cur.getDouble(4),
                            cur.getDouble(2),
                            cur.getDouble(3)
                        )
                    )
                }
            }

        // small adjustment for a proper chartbuilding with achartengine
        if (type != AxisType.TYPE_LIFE && (list.isEmpty() || list[0][0] > -num)) {
            list.add(0, doubleArrayOf(-num.toDouble(), 0.0, 0.0, 0.0, 0.0, 0.0))
        } else if (type == AxisType.TYPE_LIFE && list.isEmpty()) {
            list.add(0, doubleArrayOf(-12.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        }
        if (list[list.size - 1][0] < 0) {
            list.add(doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        }
        seriesList = Array(6) { DoubleArray(list.size) }
        for (i in list.indices) {
            val data = list[i]
            seriesList!![0][i] = data[0] // day
            seriesList!![1][i] = data[1] + data[2] + data[3] + data[4] + data[5] // cram
            seriesList!![2][i] = data[2] + data[3] + data[4] + data[5] // learn
            seriesList!![3][i] = data[3] + data[4] + data[5] // relearn
            seriesList!![4][i] = data[4] + data[5] // young
            seriesList!![5][i] = data[5] // mature
            if (seriesList!![1][i] > mMaxCards) {
                mMaxCards = Math.round(data[1] + data[2] + data[3] + data[4] + data[5]).toInt()
            }
            if (data[5] >= 0.999) {
                mFoundCramCards = true
            }
            if (data[1] >= 0.999) {
                mFoundLearnCards = true
            }
            if (data[2] >= 0.999) {
                mFoundRelearnCards = true
            }
            if (data[0] > mLastElement) {
                mLastElement = data[0]
            }
            if (data[0] < mFirstElement) {
                mFirstElement = data[0]
            }
            if (data[0].toInt() == 0) {
                mZeroIndex = i
            }
        }
        mMaxElements = list.size - 1
        cumulative = Array(6) { DoubleArray(0) } // 0 length should be replaced
        cumulative!![0] = seriesList!![0]
        for (i in 1 until seriesList!!.size) {
            cumulative!![i] = createCumulative(seriesList!![i])
            if (i > 1) {
                for (j in 0 until cumulative!![i - 1].size) {
                    cumulative!![i - 1][j] -= cumulative!![i][j]
                }
            }
        }
        when (mType) {
            AxisType.TYPE_MONTH -> mFirstElement = -31.0
            AxisType.TYPE_YEAR -> mFirstElement = -52.0
            else -> {}
        }
        mMcount = 0.0
        // we could assume the last element to be the largest,
        // but on some collections that may not be true due some negative values
        // so we search for the largest element:
        for (i in 1 until cumulative!!.size) {
            for (j in 0 until cumulative!![i].size) {
                if (mMcount < cumulative!![i][j]) mMcount = cumulative!![i][j]
            }
        }

        // some adjustments to not crash the chartbuilding with empty data
        if (mMaxCards == 0) {
            mMaxCards = 10
        }
        if (mMaxElements == 0) {
            mMaxElements = 10
        }
        if (mMcount == 0.0) {
            mMcount = 10.0
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = -10.0
            mLastElement = 0.0
        }
        return !list.isEmpty()
    }

    private fun getChunk(axisType: AxisType): Int {
        return when (axisType) {
            AxisType.TYPE_MONTH -> 1
            AxisType.TYPE_YEAR -> 7
            AxisType.TYPE_LIFE -> 30
        }
    }

    private fun getNum(axisType: AxisType): Int {
        return when (axisType) {
            AxisType.TYPE_MONTH -> 31
            AxisType.TYPE_YEAR -> 52
            AxisType.TYPE_LIFE -> -1 // Note: can also be 'None'
        }
    }

    /**
     * Intervals ***********************************************************************************************
     */
    fun calculateIntervals(context: Context, type: AxisType): Boolean {
        mDynamicAxis = true
        mType = type
        var all: Double
        var avg: Double
        var max_: Double
        mBackwards = false
        mTitle = R.string.stats_review_intervals
        mAxisTitles = intArrayOf(type.ordinal, R.string.stats_cards, R.string.stats_percentage)
        mValueLabels = intArrayOf(R.string.stats_cards_intervals)
        mColors = intArrayOf(R.attr.stats_interval)
        var num = 0
        var lim = ""
        var chunk = 0
        when (type) {
            AxisType.TYPE_MONTH -> {
                num = 31
                chunk = 1
                lim = " and grp <= 30"
            }
            AxisType.TYPE_YEAR -> {
                num = 52
                chunk = 7
                lim = " and grp <= 52"
            }
            AxisType.TYPE_LIFE -> {
                num = -1
                chunk = 30
                lim = ""
            }
        }
        val list =
            ArrayList<DoubleArray>(52) // Max of `num`, given that we probably won't have card with more than 52 year interval
        col
            .db
            .query(
                "select ivl / " + chunk + " as grp, count() from cards " +
                    "where did in " + _limit() + " and queue = " + Consts.QUEUE_TYPE_REV + " " + lim + " " +
                    "group by grp " +
                    "order by grp"
            ).use { cur ->
                while (cur.moveToNext()) {
                    list.add(doubleArrayOf(cur.getDouble(0), cur.getDouble(1)))
                }
            }
        col
            .db
            .query(
                "select count(), avg(ivl), max(ivl) from cards where did in " + _limit() +
                    " and queue = " + Consts.QUEUE_TYPE_REV + ""
            ).use { cur ->
                cur.moveToFirst()
                all = cur.getDouble(0)
                avg = cur.getDouble(1)
                max_ = cur.getDouble(2)
            }

        // small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty() || list[0][0] > 0) {
            list.add(0, doubleArrayOf(0.0, 0.0, 0.0))
        }
        if (num == -1 && list.size < 2) {
            num = 31
        }
        if (type != AxisType.TYPE_LIFE && list[list.size - 1][0] < num) {
            list.add(doubleArrayOf(num.toDouble(), 0.0))
        } else if (type == AxisType.TYPE_LIFE && list.size < 2) {
            list.add(doubleArrayOf(Math.max(12.0, list[list.size - 1][0] + 1), 0.0))
        }
        mLastElement = 0.0
        seriesList = Array(2) { DoubleArray(list.size) }
        for (i in list.indices) {
            val data = list[i]
            seriesList!![0][i] = data[0] // grp
            seriesList!![1][i] = data[1] // cnt
            if (seriesList!![1][i] > mMaxCards) mMaxCards = Math.round(data[1]).toInt()
            if (data[0] > mLastElement) mLastElement = data[0]
        }
        cumulative = createCumulative(seriesList!!)
        for (i in list.indices) {
            cumulative!![1][i] /= all / 100
        }
        mMcount = 100.0
        when (mType) {
            AxisType.TYPE_MONTH -> mLastElement = 31.0
            AxisType.TYPE_YEAR -> mLastElement = 52.0
            else -> {}
        }
        mFirstElement = 0.0
        mMaxElements = list.size - 1
        mAverage = Utils.timeSpan(context, Math.round(avg * SECONDS_PER_DAY))
        mLongest = Utils.timeSpan(context, Math.round(max_ * SECONDS_PER_DAY))

        // some adjustments to not crash the chartbuilding with empty data
        if (mMaxElements == 0) {
            mMaxElements = 10
        }
        if (mMcount == 0.0) {
            mMcount = 10.0
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0.0
            mLastElement = 6.0
        }
        if (mMaxCards == 0) {
            mMaxCards = 10
        }
        return !list.isEmpty()
    }

    /**
     * Hourly Breakdown
     */
    fun calculateBreakdown(type: AxisType?): Boolean {
        mTitle = R.string.stats_breakdown
        mBackwards = false
        mAxisTitles = intArrayOf(
            R.string.stats_time_of_day,
            R.string.stats_percentage_correct,
            R.string.stats_reviews
        )
        mValueLabels = intArrayOf(R.string.stats_percentage_correct, R.string.stats_answers)
        mColors = intArrayOf(R.attr.stats_counts, R.attr.stats_hours)
        mType = type
        var lim = _getDeckFilter().replace("[\\[\\]]".toRegex(), "")
        if (lim.length > 0) {
            lim = " and $lim"
        }
        val rolloverHour = getDayOffset(col)
        val pd = _periodDays()
        if (pd > 0) {
            lim += " and id > " + (col.sched.dayCutoff() - SECONDS_PER_DAY * pd) * 1000
        }
        val cutoff = col.sched.dayCutoff()
        val cut = cutoff - rolloverHour * 3600
        val list = ArrayList<DoubleArray>(24) // number of hours
        for (i in 0..23) {
            list.add(doubleArrayOf(i.toDouble(), 0.0, 0.0))
        }
        val query = "select " +
            "23 - ((cast((" + cut + " - id/1000) / 3600.0 as int)) % 24) as hour, " +
            "sum(case when ease = 1 then 0 else 1 end) / " +
            "cast(count() as float) * 100, " +
            "count() " +
            "from revlog where type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_LRN + "," + Consts.CARD_TYPE_REV + ") " + lim + " " +
            "group by hour having count() > 30 order by hour"
        Timber.d("%d : %d breakdown query: %s", rolloverHour, cutoff, query)
        col.db
            .query(query).use { cur ->
                while (cur.moveToNext()) {
                    val hourData =
                        doubleArrayOf(cur.getDouble(0), cur.getDouble(1), cur.getDouble(2))
                    list[(hourData[0].toInt() % 24 + 24) % 24] =
                        hourData // Force the data to be positive int in 0-23 range
                }
            }

        // TODO adjust for breakdown, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty()) {
            list.add(0, doubleArrayOf(0.0, 0.0, 0.0))
        }
        for (i in list.indices) {
            val data = list[i]
            val intHour = data[0].toInt()
            var hour = (intHour - 4) % 24
            if (hour < 0) {
                hour += 24
            }
            data[0] = hour.toDouble()
            list[i] = data
        }
        Collections.sort(list) { s1: DoubleArray, s2: DoubleArray ->
            java.lang.Double.compare(
                s1[0],
                s2[0]
            )
        }
        seriesList = Array(4) { DoubleArray(list.size) }
        mPeak = 0.0
        mMcount = 0.0
        var minHour = Double.MAX_VALUE
        var maxHour = 0.0
        for (i in list.indices) {
            val data = list[i]
            val hour = data[0].toInt()

            // double hour = data[0];
            if (hour < minHour) {
                minHour = hour.toDouble()
            }
            if (hour > maxHour) {
                maxHour = hour.toDouble()
            }
            val pct = data[1]
            if (pct > mPeak) {
                mPeak = pct
            }
            seriesList!![0][i] = hour.toDouble()
            seriesList!![1][i] = pct
            seriesList!![2][i] = data[2]
            if (i == 0) {
                seriesList!![3][i] = pct
            } else {
                val prev = seriesList!![3][i - 1]
                var diff = pct - prev
                diff /= 3.0
                diff = Math.round(diff * 10.0) / 10.0
                seriesList!![3][i] = prev + diff
            }
            if (data[2] > mMcount) {
                mMcount = data[2]
            }
            if (seriesList!![1][i] > mMaxCards) {
                mMaxCards = seriesList!![1][i].toInt()
            }
        }
        mFirstElement = seriesList!![0][0]
        mLastElement = seriesList!![0][seriesList!![0].size - 1]
        mMaxElements = (maxHour - minHour).toInt()

        // some adjustments to not crash the chartbuilding with empty data
        if (mMaxElements == 0) {
            mMaxElements = 10
        }
        if (mMcount == 0.0) {
            mMcount = 10.0
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0.0
            mLastElement = 23.0
        }
        if (mMaxCards == 0) {
            mMaxCards = 10
        }
        return !list.isEmpty()
    }

    /**
     * Weekly Breakdown
     */
    fun calculateWeeklyBreakdown(type: AxisType?): Boolean {
        mTitle = R.string.stats_weekly_breakdown
        mBackwards = false
        mAxisTitles = intArrayOf(
            R.string.stats_day_of_week,
            R.string.stats_percentage_correct,
            R.string.stats_reviews
        )
        mValueLabels = intArrayOf(R.string.stats_percentage_correct, R.string.stats_answers)
        mColors = intArrayOf(R.attr.stats_counts, R.attr.stats_hours)
        mType = type
        var lim = _getDeckFilter().replace("[\\[\\]]".toRegex(), "")
        if (lim.length > 0) {
            lim = " and $lim"
        }
        val sd: Calendar = gregorianCalendar(col.sched.dayCutoff() * 1000)
        var pd = _periodDays()
        if (pd > 0) {
            pd = Math.round((pd / 7).toFloat()) * 7
            lim += " and id > " + (col.sched.dayCutoff() - SECONDS_PER_DAY * pd) * 1000
        }
        val cutoff = col.sched.dayCutoff()
        val list = ArrayList<DoubleArray>(7) // one by day of the week
        val query =
            "SELECT strftime('%w',datetime( cast(id/ 1000  -" + sd[Calendar.HOUR_OF_DAY] * 3600 +
                " as int), 'unixepoch')) as wd, " +
                "sum(case when ease = 1 then 0 else 1 end) / " +
                "cast(count() as float) * 100, " +
                "count() " +
                "from revlog " +
                "where type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_LRN + "," + Consts.CARD_TYPE_REV + ") " + lim + " " +
                "group by wd " +
                "order by wd"
        Timber.d(
            sd[Calendar.HOUR_OF_DAY].toString() + " : " + cutoff + " weekly breakdown query: %s",
            query
        )
        col.db
            .query(query).use { cur ->
                while (cur.moveToNext()) {
                    list.add(doubleArrayOf(cur.getDouble(0), cur.getDouble(1), cur.getDouble(2)))
                }
            }

        // TODO adjust for breakdown, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty()) {
            list.add(0, doubleArrayOf(0.0, 0.0, 0.0))
        }
        seriesList = Array(4) { DoubleArray(list.size) }
        mPeak = 0.0
        mMcount = 0.0
        var minHour = Double.MAX_VALUE
        var maxHour = 0.0
        for (i in list.indices) {
            val data = list[i]
            val hour = data[0].toInt()

            // double hour = data[0];
            if (hour < minHour) {
                minHour = hour.toDouble()
            }
            if (hour > maxHour) {
                maxHour = hour.toDouble()
            }
            val pct = data[1]
            if (pct > mPeak) {
                mPeak = pct
            }
            seriesList!![0][i] = hour.toDouble()
            seriesList!![1][i] = pct
            seriesList!![2][i] = data[2]
            if (i == 0) {
                seriesList!![3][i] = pct
            } else {
                val prev = seriesList!![3][i - 1]
                var diff = pct - prev
                diff /= 3.0
                diff = Math.round(diff * 10.0) / 10.0
                seriesList!![3][i] = prev + diff
            }
            if (data[2] > mMcount) {
                mMcount = data[2]
            }
            if (seriesList!![1][i] > mMaxCards) {
                mMaxCards = seriesList!![1][i].toInt()
            }
        }
        mFirstElement = seriesList!![0][0]
        mLastElement = seriesList!![0][seriesList!![0].size - 1]
        mMaxElements = (maxHour - minHour).toInt()

        // some adjustments to not crash the chartbuilding with empty data
        if (mMaxElements == 0) {
            mMaxElements = 10
        }
        if (mMcount == 0.0) {
            mMcount = 10.0
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0.0
            mLastElement = 6.0
        }
        if (mMaxCards == 0) {
            mMaxCards = 10
        }
        return !list.isEmpty()
    }

    /**
     * Answer Buttons
     */
    fun calculateAnswerButtons(type: AxisType): Boolean {
        mHasColoredCumulative = false
        cumulative = null
        mTitle = R.string.stats_answer_buttons
        mBackwards = false
        mAxisTitles = intArrayOf(R.string.stats_answer_type, R.string.stats_answers)
        mValueLabels = intArrayOf(
            R.string.statistics_learn,
            R.string.statistics_young,
            R.string.statistics_mature
        )
        mColors = intArrayOf(R.attr.stats_learn, R.attr.stats_young, R.attr.stats_mature)
        mType = type
        val list = eases(type)

        // TODO adjust for AnswerButton, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty()) {
            list.add(0, doubleArrayOf(0.0, 1.0, 0.0))
        }
        seriesList = Array(4) { DoubleArray(list.size + 1) }
        for (i in list.indices) {
            val data = list[i]
            val currentType = data[0].toInt()
            var ease = data[1]
            val cnt = data[2]
            if (currentType == Consts.CARD_TYPE_LRN) {
                ease += 5.0
            } else if (currentType == 2) {
                ease += 10.0
            }
            seriesList!![0][i] = ease
            seriesList!![1 + currentType][i] = cnt
            if (cnt > mMaxCards) {
                mMaxCards = cnt.toInt()
            }
        }
        seriesList!![0][list.size] = 15.0
        mFirstElement = 0.5
        mLastElement = 14.5
        mMcount = 100.0
        mMaxElements = 15 // bars are positioned from 1 to 14
        if (mMaxCards == 0) {
            mMaxCards = 10
        }
        return !list.isEmpty()
    }

    private fun eases(type: AxisType): ArrayList<DoubleArray> {
        var lim = _getDeckFilter().replace("[\\[\\]]".toRegex(), "")
        val lims = Vector<String>()
        val days: Int
        if (lim.length > 0) {
            lims.add(lim)
        }
        days = if (type == AxisType.TYPE_MONTH) {
            30
        } else if (type == AxisType.TYPE_YEAR) {
            365
        } else {
            -1
        }
        if (days > 0) {
            lims.add("id > " + (col.sched.dayCutoff() - days * SECONDS_PER_DAY) * 1000)
        }

        // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
        lims.add("ease > 0")
        lim = "where " + lims[0]
        for (i in 1 until lims.size) {
            lim += " and " + lims[i]
        }
        val ease4repl: String
        ease4repl = if (col.schedVer() == 1) {
            "3"
        } else {
            "ease"
        }
        val list = ArrayList<DoubleArray>(3 * 4) // 3 types * 4 eases
        val query = "select (case " +
            "                when type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_REV + ") then 0 " +
            "        when lastIvl < 21 then 1 " +
            "        else 2 end) as thetype, " +
            "        (case when type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_REV + ") and ease = 4 then " + ease4repl + " else ease end), count() from revlog " + lim + " " +
            "        group by thetype, ease " +
            "        order by thetype, ease"
        Timber.d("AnswerButtons query: %s", query)
        col.db
            .query(query).use { cur ->
                while (cur.moveToNext()) {
                    list.add(doubleArrayOf(cur.getDouble(0), cur.getDouble(1), cur.getDouble(2)))
                }
            }
        return list
    }

    /**
     * Card Types
     */
    fun calculateCardTypes(type: AxisType?) {
        mTitle = R.string.title_activity_template_editor
        mBackwards = false
        mAxisTitles = intArrayOf(
            R.string.stats_answer_type,
            R.string.stats_answers,
            R.string.stats_cumulative_correct_percentage
        )
        mValueLabels = intArrayOf(
            R.string.statistics_mature,
            R.string.statistics_young_and_learn,
            R.string.statistics_unlearned,
            R.string.statistics_suspended,
            R.string.statistics_buried
        )
        mColors = intArrayOf(
            R.attr.stats_mature,
            R.attr.stats_young,
            R.attr.stats_unseen,
            R.attr.stats_suspended,
            R.attr.stats_buried
        )
        mType = type
        var pieData: DoubleArray
        val query =
            """select sum(case when queue=${Consts.QUEUE_TYPE_REV} and ivl >= 21 then 1 else 0 end), -- mtr
sum(case when queue in (${Consts.QUEUE_TYPE_LRN},${Consts.QUEUE_TYPE_DAY_LEARN_RELEARN}) or (queue=${Consts.QUEUE_TYPE_REV} and ivl < 21) then 1 else 0 end), -- yng/lrn
sum(case when queue=${Consts.QUEUE_TYPE_NEW} then 1 else 0 end), -- new
sum(case when queue=${Consts.QUEUE_TYPE_SUSPENDED} then 1 else 0 end), -- susp
sum(case when queue in (${Consts.QUEUE_TYPE_MANUALLY_BURIED},${Consts.QUEUE_TYPE_SIBLING_BURIED}) then 1 else 0 end) -- buried
from cards where did in ${_limit()}"""
        Timber.d("CardsTypes query: %s", query)
        col.db
            .query(query).use { cur ->
                cur.moveToFirst()
                pieData = doubleArrayOf(
                    cur.getDouble(0),
                    cur.getDouble(1),
                    cur.getDouble(2),
                    cur.getDouble(3),
                    cur.getDouble(4)
                )
            }

        // TODO adjust for CardsTypes, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
//        if (list.size() == 0 || list.get(0)[0] > 0) {
//            list.add(0, new double[] { 0, 0, 0 });
//        }
//        if (num == -1 && list.size() < 2) {
//            num = 31;
//        }
//        if (type != Utils.TYPE_LIFE && list.get(list.size() - 1)[0] < num) {
//            list.add(new double[] { num, 0, 0 });
//        } else if (type == Utils.TYPE_LIFE && list.size() < 2) {
//            list.add(new double[] { Math.max(12, list.get(list.size() - 1)[0] + 1), 0, 0 });
//        }
        seriesList = Array(1) { DoubleArray(5) }
        seriesList!![0] = pieData
        mFirstElement = 0.5
        mLastElement = 9.5
        mMcount = 100.0
        mMaxElements = 10 // bars are positioned from 1 to 14
        if (mMaxCards == 0) {
            mMaxCards = 10
        }
    }

    /**
     * Tools ***********************************************************************************************
     */
    private fun _limit(): String {
        return deckLimit(col, mDeckId)
    }

    private fun _getDeckFilter(): String {
        return if (mWholeCollection) {
            ""
        } else {
            "cid IN (SELECT id FROM cards WHERE did IN " + _limit() + ")"
        }
    }

    private fun _periodDays(type: AxisType? = mType): Int {
        return when (type) {
            AxisType.TYPE_MONTH -> 30
            AxisType.TYPE_YEAR -> 365
            AxisType.TYPE_LIFE -> -1
            else -> -1
        }
    }

    companion object {
        const val SECONDS_PER_DAY = 86400L
        const val ALL_DECKS_ID = 0L
        fun getDayOffset(col: Collection): Int {
            return when (col.schedVer()) {
                2 -> col.get_config("rollover", Preferences.DEFAULT_ROLLOVER_VALUE)!!
                // 1, or otherwise:
                else -> col.crtGregorianCalendar()[Calendar.HOUR_OF_DAY]
            }
        }

        /**
         * Note: NOT in libanki
         * Return a string of deck ids for the provided deck and its children, suitable for an SQL query
         * @param deckId the deck id to filter on, or ALL_DECKS_ID for all decks
         * @param col collection
         * @return
         */
        fun deckLimit(col: Collection, deckId: Long): String {
            return if (deckId == ALL_DECKS_ID) {
                // All decks
                val decks = col.decks.all()
                val ids = ArrayList<Long>(decks.size)
                for (d in decks) {
                    ids.add(d.getLong("id"))
                }
                Utils.ids2str(ids)
            } else {
                // The given deck id and its children
                val values: kotlin.collections.Collection<Long> = col.decks.children(deckId).values
                val ids = ArrayList<Long>(values.size)
                ids.add(deckId)
                ids.addAll(values)
                Utils.ids2str(ids)
            }
        }

        fun createCumulative(nonCumulative: Array<DoubleArray>): Array<DoubleArray> {
            val cumulativeValues = Array<DoubleArray?>(2) {
                DoubleArray(
                    nonCumulative[0].size
                )
            }
            cumulativeValues[0]!![0] = nonCumulative[0][0]
            cumulativeValues[1]!![0] = nonCumulative[1][0]
            for (i in 1 until nonCumulative[0].size) {
                cumulativeValues[0]!![i] = nonCumulative[0][i]
                cumulativeValues[1]!![i] = cumulativeValues[1]!![i - 1] + nonCumulative[1][i]
            }
            return cumulativeValues.requireNoNulls()
        }

        fun createCumulative(
            nonCumulative: Array<DoubleArray>,
            startAtIndex: Int
        ): Array<DoubleArray> {
            val cumulativeValues = Array<DoubleArray?>(2) {
                DoubleArray(
                    nonCumulative[0].size - startAtIndex
                )
            }
            cumulativeValues[0]!![0] = nonCumulative[0][startAtIndex]
            cumulativeValues[1]!![0] = nonCumulative[1][startAtIndex]
            for (i in startAtIndex + 1 until nonCumulative[0].size) {
                cumulativeValues[0]!![i - startAtIndex] = nonCumulative[0][i]
                cumulativeValues[1]!![i - startAtIndex] =
                    cumulativeValues[1]!![i - 1 - startAtIndex] + nonCumulative[1][i]
            }
            return cumulativeValues.requireNoNulls()
        }

        fun createCumulative(nonCumulative: DoubleArray): DoubleArray {
            val cumulativeValues = DoubleArray(nonCumulative.size)
            cumulativeValues[0] = nonCumulative[0]
            for (i in 1 until nonCumulative.size) {
                cumulativeValues[i] = cumulativeValues[i - 1] + nonCumulative[i]
            }
            return cumulativeValues
        }
    }

    init {
        mWholeCollection = did == ALL_DECKS_ID
        mDeckId = did
    }
}
