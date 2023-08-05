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

import com.ichi2.anki.R
import com.ichi2.anki.stats.OverviewStatsBuilder.OverviewStats
import com.ichi2.anki.stats.OverviewStatsBuilder.OverviewStats.AnswerButtonsOverview
import com.ichi2.anki.utils.SECONDS_PER_DAY
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Utils
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*

class Stats(private val col: com.ichi2.libanki.Collection, did: Long) {
    enum class AxisType(val days: Int, val descriptionId: Int) {
        TYPE_MONTH(30, R.string.stats_period_month), TYPE_YEAR(
            365,
            R.string.stats_period_year
        ),
        TYPE_LIFE(-1, R.string.stats_period_lifetime);
    }

    private val mWholeCollection: Boolean
    private val mDeckId: Long
    private var mType: AxisType? = null
    var cumulative: Array<DoubleArray>? = null
        private set

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
                "and id > " + (col.sched.dayCutoff - SECONDS_PER_DAY) * 1000 + " " + lim
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
            "and lastIvl >= 21 and id > " + (col.sched.dayCutoff - SECONDS_PER_DAY) * 1000 + " " + lim
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
            "id " + operator + (col.sched.dayCutoff - timespan.days * SECONDS_PER_DAY) * 1000
        }
    }

    fun getNewCards(timespan: AxisType): Pair<Int, Double> {
        val chunk = getChunk(timespan)
        val num = getNum(timespan)
        val lims: MutableList<String?> = ArrayList(2)
        if (timespan != AxisType.TYPE_LIFE) {
            lims.add("id > " + (col.sched.dayCutoff - num * chunk * SECONDS_PER_DAY) * 1000)
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
        val cut = col.sched.dayCutoff
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
            Math.max(1, (1 + (col.sched.dayCutoff - t / 1000) / SECONDS_PER_DAY).toInt()).toLong()
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
                    " SELECT (cast((id/1000 - " + col.sched.dayCutoff + ") / " + SECONDS_PER_DAY + " AS INT)) AS day,  sum(time/1000.0/60.0) AS time_per_day" +
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
            lims.add("id > " + (col.sched.dayCutoff - days * SECONDS_PER_DAY) * 1000)
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
     * Tools ***********************************************************************************************
     */
    private fun _limit(): String {
        return deckLimit(mDeckId, col)
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
        const val ALL_DECKS_ID = 0L

        /**
         * Note: NOT in libanki
         * Return a string of deck ids for the provided deck and its children, suitable for an SQL query
         * @param deckId the deck id to filter on, or ALL_DECKS_ID for all decks
         * @param col collection
         * @return
         */
        fun deckLimit(deckId: Long, col: com.ichi2.libanki.Collection): String {
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
                val values: Collection<Long> = col.decks.children(deckId).values
                val ids = ArrayList<Long>(values.size)
                ids.add(deckId)
                ids.addAll(values)
                Utils.ids2str(ids)
            }
        }
    }

    init {
        mWholeCollection = did == ALL_DECKS_ID
        mDeckId = did
    }
}
