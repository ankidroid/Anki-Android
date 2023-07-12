/****************************************************************************************
 * Copyright (c) 2016 Jeffrey van Prehn <jvanprehn@gmail.com>                           *
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
import android.database.Cursor
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.stats.StatsMetaInfo
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Consts.CARD_TYPE
import com.ichi2.libanki.DB
import com.ichi2.libanki.DeckManager
import com.ichi2.libanki.stats.Stats.AxisType
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.HashUtil.HashMapInit
import timber.log.Timber
import java.util.*

/**
 * Display forecast statistics based on a simulation of future reviews.
 *
 * Sequence diagram (https://www.websequencediagrams.com/):
 * ```
 * Stats->+AdvancedStatistics: runFilter
 * AdvancedStatistics->+ReviewSimulator: simNreviews
 * loop dids
 *   loop nIterations
 *       loop cards
 *           ReviewSimulator->+Review: newCard
 *           Review->+NewCardSimulator: simulateNewCard
 *           NewCardSimulator->-Review: tElapsed:int
 *           Review-->-ReviewSimulator: SimulationResult, Review
 *
 *           loop reviews
 *               ReviewSimulator->+Review: simulateReview
 *               Review->+EaseClassifier: simSingleReview
 *               EaseClassifier->+Card:getType
 *               Card-->-EaseClassifier:cardType:int
 *               EaseClassifier-->-Review: ReviewOutcome
 *               Review-->-ReviewSimulator: SimulationResult, Review[]
 *           end
 *        end
 *   end
 * end
 * ReviewSimulator-->-AdvancedStatistics: SimulationResult
 * AdvancedStatistics-->-Stats: StatsMetaInfo
 *
 * %2F%2F Class diagram (http://yuml.me/diagram/scruffy/class/draw; http://yuml.me/edit/e0ad47bf):
 * [AdvancedStatistics]
 * [ReviewSimulator]
 * [StatsMetaInfo|mTitle:int;mType:int;mAxisTitles:int［］;mValueLabels:int［］;mColors:int［］;]
 * [Settings|computeNDays:int;computeMaxError:double;simulateNIterations:int]
 * [Deck|-did:long;newPerDay:int;revPerDay:int]
 * [Card|-id:long;ivl:int;factor:double;lastReview:int;due:int;correct:int|setAll();getType()]
 * [Review|prob:double;tElapsed:int]
 * [SimulationResult|nReviews［CARD_TYPE］［t］;nInState［CARD_TYPE］［t］]
 * [ReviewOutcome|prob:double]
 * [ReviewSimulator]uses -.->[CardIterator]
 * [ReviewSimulator]uses -.->[DeckFactory]
 * [ReviewSimulator]creates -.->[SimulationResult]
 * [ReviewSimulator]creates -.->[Review]
 * [Card]belongs to-.->[Deck]
 * [Review]updates -.->[SimulationResult]
 * [Review]]++-1>[Card]
 * [Review]creates -.->[Review]
 * [AdvancedStatistics]uses -.->[ReviewSimulator]
 * [Review]uses -.->[NewCardSimulator|nAddedToday:int;tAdd:int]
 * [Review]uses -.->[EaseClassifier|probabilities:double［CARD_TYPE］［REVIEW_OUTCOME］]
 * [EaseClassifier]creates -.->[ReviewOutcome]
 * [ReviewOutcome]++-1>[Card]
 * [AdvancedStatistics]creates -.-> [StatsMetaInfo]
 * ```
 */
class AdvancedStatistics {
    private val mArrayUtils: ArrayUtils = ArrayUtils()
    private val mDecks = DeckFactory()
    private var mSettings: Settings? = null

    /**
     * Determine forecast statistics based on a computation or simulation of future reviews.
     * Returns all information required by stats.java to plot the 'forecast' chart based on these statistics.
     * The chart will display:
     * - The forecasted number of reviews per review type (relearn, mature, young, learn) as bars
     * - The forecasted number of cards in each state (new, young, mature) as lines
     * @param metaInfo Object which will be filled with all information required by stats.java to plot the 'forecast' chart and returned by this method.
     * @param type Type of 'forecast' chart for which to determine forecast statistics. Accepted values:
     * Stats.TYPE_MONTH: Determine forecast statistics for next 30 days with 1-day chunks
     * Stats.TYPE_YEAR:  Determine forecast statistics for next year with 7-day chunks
     * Stats.TYPE_LIFE:  Determine forecast statistics for next 2 years with 30-day chunks
     * @param context Contains The collection which contains the decks to be simulated.
     * Also used for access to the database and access to the creation time of the collection.
     * The creation time of the collection is needed since due times of cards are relative to the creation time of the collection.
     * So we could pass mCol here.
     * @param dids Deck id's
     * @return @see #metaInfo
     */
    fun calculateDueAsMetaInfo(
        metaInfo: StatsMetaInfo,
        type: AxisType,
        context: Context,
        dids: String
    ): StatsMetaInfo {
        if (!context.sharedPrefs().getBoolean("advanced_statistics_enabled", false)) {
            return metaInfo
        }
        // To indicate that we calculated the statistics so that Stats.java knows that it shouldn't display the standard Forecast chart.
        mSettings = Settings(context)
        metaInfo.isStatsCalculated = true
        val col = CollectionHelper.instance.getCol(context)!!
        var maxCards = 0
        var lastElement = 0.0
        var zeroIndex = 0
        val valueLabels = intArrayOf(
            R.string.statistics_relearn,
            R.string.statistics_mature,
            R.string.statistics_young,
            R.string.statistics_learn
        )
        val colors = intArrayOf(
            R.attr.stats_relearn,
            R.attr.stats_mature,
            R.attr.stats_young,
            R.attr.stats_learn
        )
        val axisTitles =
            intArrayOf(type.ordinal, R.string.stats_cards, R.string.stats_cumulative_cards)
        val simuationResult = calculateDueAsPlottableSimulationResult(type, col, dids)
        val dues = simuationResult.nReviews
        val seriesList = Array(REVIEW_TYPE_COUNT_PLUS_1) { DoubleArray(dues.size) }
        for (t in dues.indices) {
            val data = dues[t]
            val nReviews = data[REVIEW_TYPE_LEARN_PLUS_1] +
                data[REVIEW_TYPE_YOUNG_PLUS_1] +
                data[REVIEW_TYPE_MATURE_PLUS_1] +
                data[REVIEW_TYPE_RELEARN_PLUS_1]
            if (nReviews > maxCards) maxCards = nReviews // Y-Axis: Max. value

            // In the bar-chart, the bars will be stacked on top of each other.
            // For the i^{th} bar counting from the bottom we therefore have to
            // provide the sum of the heights of the i^{th} bar and all bars below it.
            seriesList[TIME][t] = data[TIME]
                .toDouble() // X-Axis: Day / Week / Month
            seriesList[REVIEW_TYPE_LEARN_PLUS_1][t] = (
                data[REVIEW_TYPE_LEARN_PLUS_1] +
                    data[REVIEW_TYPE_YOUNG_PLUS_1] +
                    data[REVIEW_TYPE_MATURE_PLUS_1] +
                    data[REVIEW_TYPE_RELEARN_PLUS_1]
                ).toDouble() // Y-Axis: # Cards
            seriesList[REVIEW_TYPE_YOUNG_PLUS_1][t] = (
                data[REVIEW_TYPE_LEARN_PLUS_1] +
                    data[REVIEW_TYPE_YOUNG_PLUS_1] +
                    data[REVIEW_TYPE_MATURE_PLUS_1]
                ).toDouble() // Y-Axis: # Mature cards
            seriesList[REVIEW_TYPE_MATURE_PLUS_1][t] = (
                data[REVIEW_TYPE_LEARN_PLUS_1] +
                    data[REVIEW_TYPE_YOUNG_PLUS_1]
                ).toDouble() // Y-Axis: # Young
            seriesList[REVIEW_TYPE_RELEARN_PLUS_1][t] = data[REVIEW_TYPE_LEARN_PLUS_1]
                .toDouble() // Y-Axis: # Learn
            if (data[TIME] > lastElement) {
                lastElement = data[TIME]
                    .toDouble() // X-Axis: Max. value (only for TYPE_LIFE)
            }
            if (data[TIME] == 0) {
                zeroIndex = t // Because we retrieve dues in the past and we should not cumulate them
            }
        }
        var maxElements = dues.size - 1 // # X values
        when (type) {
            AxisType.TYPE_MONTH -> lastElement = 31.0 // X-Axis: Max. value
            AxisType.TYPE_YEAR -> lastElement = 52.0 // X-Axis: Max. value
            else -> {}
        }
        var firstElement = 0.0 // X-Axis: Min. value
        val cumulative = simuationResult.nInState // Day starting at zeroIndex, Cumulative # cards
        var count =
            cumulative[CARD_TYPE_NEW_PLUS_1][cumulative[CARD_TYPE_NEW_PLUS_1].size - 1] + // Y-Axis: Max. cumulative value
                cumulative[CARD_TYPE_YOUNG_PLUS_1][cumulative[CARD_TYPE_YOUNG_PLUS_1].size - 1] +
                cumulative[CARD_TYPE_MATURE_PLUS_1][cumulative[CARD_TYPE_MATURE_PLUS_1].size - 1]

        // some adjustments to not crash the chartbuilding with empty data
        if (maxElements == 0) {
            maxElements = 10
        }
        if (count == 0.0) {
            count = 10.0
        }
        if (firstElement == lastElement) {
            firstElement = 0.0
            lastElement = 6.0
        }
        if (maxCards == 0) maxCards = 10
        metaInfo.dynamicAxis = true
        metaInfo.hasColoredCumulative = true
        metaInfo.type = type
        metaInfo.title = R.string.stats_forecast
        metaInfo.backwards = true
        metaInfo.valueLabels = valueLabels
        metaInfo.colors = colors
        metaInfo.axisTitles = axisTitles
        metaInfo.maxCards = maxCards
        metaInfo.maxElements = maxElements
        metaInfo.firstElement = firstElement
        metaInfo.lastElement = lastElement
        metaInfo.zeroIndex = zeroIndex
        metaInfo.cumulative = cumulative
        metaInfo.mcount = count
        metaInfo.seriesList = seriesList
        metaInfo.isDataAvailable = !dues.isEmpty()
        return metaInfo
    }

    /**
     * Determine forecast statistics based on a computation or simulation of future reviews and returns the results of the simulation.
     * @param type @see #calculateDueOriginal(StatsMetaInfo, int, Context, String)
     * @param col @see #calculateDueOriginal(StatsMetaInfo, int, Context, String)
     * @param dids @see #calculateDueOriginal(StatsMetaInfo, int, Context, String)
     * @return An object containing the results of the simulation:
     * - The forecasted number of reviews per review type (relearn, mature, young, learn)
     * - The forecasted number of cards in each state (new, young, mature)
     */
    private fun calculateDueAsPlottableSimulationResult(
        type: AxisType,
        col: Collection,
        dids: String
    ): PlottableSimulationResult {
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
                end = 24
                chunk = 30
            }
        }
        val classifier = EaseClassifier(time, col.db)
        val reviewSimulator = ReviewSimulator(col.db, classifier, end, chunk)
        val todayStats = TodayStats(col, mSettings!!.getDayStartCutoff(col.crt))
        val t0 = time.intTimeMS()
        val simulationResult = reviewSimulator.simNreviews(
            mSettings!!.getToday(col.crt.toInt().toLong()),
            col.decks,
            dids,
            todayStats
        )
        val t1 = time.intTimeMS()
        Timber.d("Simulation of all decks took: %d ms", t1 - t0)
        val nReviews = mArrayUtils.transposeMatrix(simulationResult.nReviews)
        val nInState = mArrayUtils.transposeMatrix(simulationResult.nInState)

        // Append row with zeros and transpose to make it the same dimension as nReviews
        // int[][] nInState = simulationResult.getNInState();
        // if(ArrayUtils.nCols(nInState) > 0)
        //    nInState = ArrayUtils.append(nInState, new int[ArrayUtils.nCols(nInState)], 1);
        val dues = ArrayList<IntArray>(nReviews.size + 2)
        // Forecasted number of reviews
        for (i in nReviews.indices) {
            dues.add(
                intArrayOf(
                    i, // Time
                    nReviews[i][REVIEW_TYPE_LEARN],
                    nReviews[i][REVIEW_TYPE_YOUNG],
                    nReviews[i][REVIEW_TYPE_MATURE],
                    nReviews[i][REVIEW_TYPE_RELEARN]
                )
            )
        }

        // small adjustment for a proper chartbuilding
        if (dues.isEmpty() || dues[0][0] > 0) {
            dues.add(0, intArrayOf(0, 0, 0, 0, 0))
        }
        if (type === AxisType.TYPE_LIFE && dues.size < 2) {
            end = 31
        }
        if (type !== AxisType.TYPE_LIFE && dues[dues.size - 1][0] < end) {
            dues.add(intArrayOf(end, 0, 0, 0, 0))
        } else if (type === AxisType.TYPE_LIFE && dues.size < 2) {
            dues.add(intArrayOf(Math.max(12, dues[dues.size - 1][0] + 1), 0, 0, 0, 0))
        }
        val nInStateCum = arrayOfNulls<DoubleArray>(dues.size)
        for (i in dues.indices) {
            if (i < nInState.size) {
                nInStateCum[i] = doubleArrayOf(
                    i.toDouble(),
                    0.0, // Y-Axis: Relearn = 0 (we can't say 'we know x relearn cards on day d')
                    // nInState[i][0] + nInState[i][1] + nInState[i][2], //Y-Axis: New + Young + Mature
                    // nInState[i][0] + nInState[i][1],                  //Y-Axis: New + Young
                    // nInState[i][0],                                   //Y-Axis: New
                    nInState[i][CARD_TYPE_MATURE].toDouble(), // Y-Axis: Mature
                    nInState[i][CARD_TYPE_YOUNG].toDouble(), // Y-Axis: Young
                    nInState[i][CARD_TYPE_NEW].toDouble()
                )
            } else {
                if (i == 0) {
                    nInStateCum[i] = doubleArrayOf(
                        i.toDouble(),
                        0.0,
                        0.0,
                        0.0,
                        0.0
                    )
                } else {
                    nInStateCum[i] = nInStateCum[i - 1]
                }
            }
        }

        // Append columns to make it the same dimension as dues
        // if(dues.size() > nInState.length) {
        //    nInState = ArrayUtils.append(nInState, nInState[nInState.length-1], dues.size() - nInState.length);
        // }
        return PlottableSimulationResult(dues, mArrayUtils.transposeMatrix(nInStateCum.requireNoNulls()))
    }

    private class Card(
        var id: Long,
        var ivl: Int,
        factor: Int,
        due: Int,
        correct: Int,
        lastReview: Int
    ) {
        var factor: Double
        var lastReview: Int
        var due: Int
        var correct: Int
        override fun toString(): String {
            return (
                "Card [ivl=" + ivl + ", factor=" + factor + ", due=" + due + ", correct=" + correct + ", id=" +
                    id + "]"
                )
        }

        fun setAll(id: Long, ivl: Int, factor: Int, due: Int, correct: Int, lastReview: Int) {
            this.id = id
            this.ivl = ivl
            this.factor = factor / 1000.0
            this.due = due
            this.correct = correct
            this.lastReview = lastReview
        }

        fun setAll(card: Card?) {
            id = card!!.id
            ivl = card.ivl
            factor = card.factor
            due = card.due
            correct = card.correct
            lastReview = card.lastReview
        }

        /**
         * Type of the card, based on the interval.
         * @return CARD_TYPE_NEW if interval = 0, CARD_TYPE_YOUNG if interval 1-20, CARD_TYPE_MATURE if interval >= 20
         */
        val type: Int
            get() = if (ivl == 0) {
                CARD_TYPE_NEW
            } else if (ivl >= 21) {
                CARD_TYPE_MATURE
            } else {
                CARD_TYPE_YOUNG
            }

        init {
            this.factor = factor / 1000.0
            this.due = due
            this.correct = correct
            this.lastReview = lastReview
        }
    }

    private inner class DeckFactory {
        fun createDeck(did: Long, decks: DeckManager): Deck {
            Timber.d("Trying to get deck settings for deck with id=%s", did)
            val conf = decks.confForDid(did)
            var newPerDay = mSettings!!.maxNewPerDay
            var revPerDay = mSettings!!.maxReviewsPerDay
            var initialFactor: Int = Settings.initialFactor
            if (conf.isStd) {
                revPerDay = conf.getJSONObject("rev").getInt("perDay")
                newPerDay = conf.getJSONObject("new").getInt("perDay")
                initialFactor = conf.getJSONObject("new").getInt("initialFactor")
                Timber.d("rev.perDay=%d", revPerDay)
                Timber.d("new.perDay=%d", newPerDay)
                Timber.d("new.initialFactor=%d", initialFactor)
            } else {
                Timber.d("dyn=%d", conf.getInt("dyn"))
            }
            return Deck(did, newPerDay, revPerDay, initialFactor)
        }
    }

    /**
     * Stores settings that are deck-specific.
     */
    class Deck(val did: Long, val newPerDay: Int, val revPerDay: Int, val initialFactor: Int)
    private class CardIterator(db: DB, private val today: Int, private val deck: Deck) {
        private val mCur: Cursor?
        fun moveToNext(): Boolean {
            return mCur!!.moveToNext()
        }

        fun current(card: Card) {
            card.setAll(
                mCur!!.getLong(0), // Id
                if (mCur.getInt(5) == 0) 0 else mCur.getInt(2), // reps = 0 ? 0 : card interval
                if (mCur.getInt(3) > 0) mCur.getInt(3) else deck.initialFactor, // factor
                Math.max(mCur.getInt(1) - today, 0), // due
                1, // correct
                -1 // lastreview
            )
        }

        fun close() {
            if (mCur != null && !mCur.isClosed) mCur.close()
        }

        init {
            val did = deck.did
            val query = "SELECT id, due, ivl, factor, type, reps " +
                "FROM cards " +
                "WHERE did IN (" + did + ") " +
                "AND queue != " + Consts.QUEUE_TYPE_SUSPENDED + " " + // ignore suspended cards
                "order by id;"
            Timber.d("Forecast query: %s", query)
            mCur = db.query(query)
        }
    }

    /**
     * Based on the current type of the card (@see Card#getType()), determines the interval of the card after review and the probability of the card having that interval after review.
     * This is done using a discrete probability distribution, which is built on construction.
     * For each possible current type of the card, it gives the probability of each possible review outcome (repeat, hard, good, easy).
     * The review outcome determines the next interval of the card.
     *
     * If the review outcome is specified by the caller, the next interval of the card will be determined based on the review outcome
     * and the probability will be fetched from the probability distribution.
     * If the review outcome is not specified by the caller, the review outcome will be sampled randomly from the probability distribution
     * and the probability will be 1.
     */
    private class EaseClassifier(time: Time, private val db: DB) {
        private val mRandom: Random
        private lateinit var mProbabilities: Array<DoubleArray?>
        private lateinit var mProbabilitiesCumulative: Array<DoubleArray?>

        // # Prior that half of new cards are answered correctly
        private val mPriorNew = intArrayOf(5, 0, 5, 0) // half of new cards are answered correctly
        private val mPriorYoung = intArrayOf(1, 0, 9, 0) // 90% of young cards get "good" response
        private val mPriorMature = intArrayOf(1, 0, 9, 0) // 90% of mature cards get "good" response
        private fun cumsum(p: DoubleArray?): DoubleArray {
            val q = DoubleArray(4)
            q[0] = p!![0]
            q[1] = q[0] + p[1]
            q[2] = q[1] + p[2]
            q[3] = q[2] + p[3]
            return q
        }

        private fun calculateCumProbabilitiesForNewEasePerCurrentEase() {
            mProbabilities = arrayOfNulls(3)
            mProbabilitiesCumulative = arrayOfNulls(3)
            mProbabilities[CARD_TYPE_NEW] =
                calculateProbabilitiesForNewEaseForCurrentEase(queryNew, mPriorNew)
            mProbabilities[CARD_TYPE_YOUNG] = calculateProbabilitiesForNewEaseForCurrentEase(
                queryYoung,
                mPriorYoung
            )
            mProbabilities[CARD_TYPE_MATURE] = calculateProbabilitiesForNewEaseForCurrentEase(
                queryMature,
                mPriorMature
            )
            mProbabilitiesCumulative[CARD_TYPE_NEW] = cumsum(
                mProbabilities[CARD_TYPE_NEW]
            )
            mProbabilitiesCumulative[CARD_TYPE_YOUNG] = cumsum(
                mProbabilities[CARD_TYPE_YOUNG]
            )
            mProbabilitiesCumulative[CARD_TYPE_MATURE] = cumsum(
                mProbabilities[CARD_TYPE_MATURE]
            )
        }

        /**
         * Given a query which selects the frequency of each review outcome for the current type of the card,
         * and an array containing the prior frequency of each review outcome for the current type of the card,
         * it gives the probability of each possible review outcome (repeat, hard, good, easy).
         * @param queryNewEaseCountForCurrentEase Query which selects the frequency of each review outcome for the current type of the card.
         * @param prior Array containing the prior frequency of each review outcome for the current type of the card.
         * @return The probability of each possible review outcome (repeat, hard, good, easy).
         */
        private fun calculateProbabilitiesForNewEaseForCurrentEase(
            queryNewEaseCountForCurrentEase: String,
            prior: IntArray
        ): DoubleArray {
            val freqs = intArrayOf(
                prior[REVIEW_OUTCOME_REPEAT],
                prior[REVIEW_OUTCOME_HARD],
                prior[REVIEW_OUTCOME_GOOD],
                prior[REVIEW_OUTCOME_EASY]
            )
            var n =
                prior[REVIEW_OUTCOME_REPEAT] + prior[REVIEW_OUTCOME_HARD] + prior[REVIEW_OUTCOME_GOOD] + prior[REVIEW_OUTCOME_EASY]
            db.query(queryNewEaseCountForCurrentEase).use { cur ->
                cur.moveToNext()
                freqs[REVIEW_OUTCOME_REPEAT] += cur.getInt(REVIEW_OUTCOME_REPEAT_PLUS_1) // Repeat
                freqs[REVIEW_OUTCOME_HARD] += cur.getInt(REVIEW_OUTCOME_HARD_PLUS_1) // Hard
                freqs[REVIEW_OUTCOME_GOOD] += cur.getInt(REVIEW_OUTCOME_GOOD_PLUS_1) // Good
                freqs[REVIEW_OUTCOME_EASY] += cur.getInt(REVIEW_OUTCOME_EASY_PLUS_1) // Easy
                val nQuery = cur.getInt(0) // N
                n += nQuery
            }
            return doubleArrayOf(
                freqs[REVIEW_OUTCOME_REPEAT] / n.toDouble(),
                freqs[REVIEW_OUTCOME_HARD] / n.toDouble(),
                freqs[REVIEW_OUTCOME_GOOD] / n.toDouble(),
                freqs[REVIEW_OUTCOME_EASY] / n.toDouble()
            )
        }

        private fun draw(p: DoubleArray?): Int {
            return searchsorted(p, mRandom.nextDouble())
        }

        private fun searchsorted(p: DoubleArray?, random: Double): Int {
            if (random <= p!![0]) return 0
            if (random <= p[1]) return 1
            return if (random <= p[2]) 2 else 3
        }

        private val mSingleReviewOutcome: ReviewOutcome
        fun simSingleReview(c: Card?): ReviewOutcome {
            @CARD_TYPE val type = c!!.type
            val outcome = draw(mProbabilitiesCumulative[type])
            applyOutcomeToCard(c, outcome)
            mSingleReviewOutcome.setAll(c, 1.0)
            return mSingleReviewOutcome
        }

        fun simSingleReview(c: Card?, outcome: Int): ReviewOutcome {
            val c_type = c!!.type

            // For first review, re-use current card to prevent creating too many objects
            applyOutcomeToCard(c, outcome)
            mSingleReviewOutcome.setAll(c, mProbabilities[c_type]!![outcome])
            return mSingleReviewOutcome
        }

        private fun applyOutcomeToCard(c: Card?, outcome: Int) {
            @CARD_TYPE val type = c!!.type
            var ivl = c.ivl
            val factor = c.factor
            if (type == CARD_TYPE_NEW) {
                ivl = if (outcome <= 2) 1 else 4
            } else {
                when (outcome) {
                    REVIEW_OUTCOME_REPEAT -> ivl = 1
                    REVIEW_OUTCOME_HARD -> ivl *= 1.2.toInt()
                    REVIEW_OUTCOME_GOOD -> ivl *= (1.2 * factor).toInt()
                    REVIEW_OUTCOME_EASY -> ivl *= (1.2 * 2.0 * factor).toInt()
                    else -> ivl *= (1.2 * 2.0 * factor).toInt()
                }
            }
            c.ivl = ivl
            c.correct = if (outcome > 0) 1 else 0
            // c.setTypetype);
            // c.setIvl(60);
            // c.setFactor(factor);
        }

        companion object {
            // TODO: should we determine these per deck or over decks?
            // Per deck means less data, but tuned to deck.
            // Over decks means more data, but not tuned to deck.
            private const val queryBaseNew = (
                "select " +
                    "count() as N, " +
                    "sum(case when ease=1 then 1 else 0 end) as repeat, " +
                    "0 as hard, " + // Doesn't occur in query_new
                    "sum(case when ease=2 then 1 else 0 end) as good, " +
                    "sum(case when ease=3 then 1 else 0 end) as easy " +
                    "from revlog "
                )
            private const val queryBaseYoungMature = (
                "select " +
                    "count() as N, " +
                    "sum(case when ease=1 then 1 else 0 end) as repeat, " +
                    "sum(case when ease=2 then 1 else 0 end) as hard, " + // Doesn't occur in query_new
                    "sum(case when ease=3 then 1 else 0 end) as good, " +
                    "sum(case when ease=4 then 1 else 0 end) as easy " +
                    "from revlog "
                )
            private const val queryNew = (
                queryBaseNew +
                    "where type=" + CARD_TYPE_NEW + ";"
                )
            private const val queryYoung = (
                queryBaseYoungMature +
                    "where type=" + Consts.CARD_TYPE_LRN + " and lastIvl < 21;"
                )
            private const val queryMature = (
                queryBaseYoungMature +
                    "where type=" + Consts.CARD_TYPE_LRN + " and lastIvl >= 21;"
                )
        }

        init {
            mSingleReviewOutcome = ReviewOutcome(null, 0.0)
            val t0 = time.intTimeMS()
            calculateCumProbabilitiesForNewEasePerCurrentEase()
            val t1 = time.intTimeMS()
            Timber.d("Calculating probability distributions took: %d ms", t1 - t0)
            Timber.d("new\t\t%s", Arrays.toString(mProbabilities[0]))
            Timber.d("young\t\t%s", Arrays.toString(mProbabilities[1]))
            Timber.d("mature\t%s", Arrays.toString(mProbabilities[2]))
            Timber.d("Cumulative new\t\t%s", Arrays.toString(mProbabilitiesCumulative[0]))
            Timber.d("Cumulative young\t\t%s", Arrays.toString(mProbabilitiesCumulative[1]))
            Timber.d("Cumulative mature\t%s", Arrays.toString(mProbabilitiesCumulative[2]))
            mRandom = Random()
        }
    }

    class TodayStats(col: Collection, dayStartCutoff: Long) {
        private val mNLearnedPerDeckId: MutableMap<Long, Int>
        fun getNLearned(did: Long): Int {
            return if (mNLearnedPerDeckId.containsKey(did)) {
                mNLearnedPerDeckId[did]!!
            } else {
                0
            }
        }

        init {
            mNLearnedPerDeckId = HashMapInit(col.decks.count())
            val db = col.db.database
            val query = "select cards.did, " +
                "sum(case when revlog.type = " + CARD_TYPE_NEW + " then 1 else 0 end)" + /* learning */
                " from revlog, cards where revlog.cid = cards.id and revlog.id > " + dayStartCutoff +
                " group by cards.did"
            Timber.d("AdvancedStatistics.TodayStats query: %s", query)
            db.query(query).use { cur ->
                while (cur.moveToNext()) {
                    mNLearnedPerDeckId[cur.getLong(0)] = cur.getInt(1)
                }
            }
        }
    }

    class NewCardSimulator {
        private var mNAddedToday = 0
        private var mTAdd = 0
        fun simulateNewCard(deck: Deck): Int {
            mNAddedToday++
            val tElapsed = mTAdd // differs from online
            if (mNAddedToday >= deck.newPerDay) {
                mTAdd++
                mNAddedToday = 0
            }
            return tElapsed
        }

        fun reset(nAddedToday: Int) {
            mNAddedToday = nAddedToday
            mTAdd = 0
        }

        init {
            reset(0)
        }
    }

    /**
     * Simulates future card reviews, keeping track of statistics and returns those as SimulationResult.
     *
     * A simulation is run for each of the specified decks using the settings (max # cards per day, max # reviews per day, initial factor for new cards) for that deck.
     * Within each deck the simulation consists of one or more simulations of each card within that deck.
     * A simulation of a single card means simulating future card reviews starting from now until the end of the simulation window as specified by nTimeBins and timeBinLength.
     *
     * A review of a single card is run by the specified classifier.
     */
    private inner class ReviewSimulator(
        private val db: DB,
        private val classifier: EaseClassifier, // TODO: also exists in Review
        private val nTimeBins: Int,
        private val timeBinLength: Int
    ) {
        private val mTMax: Int
        private val mNewCardSimulator = NewCardSimulator()
        fun simNreviews(
            today: Int,
            decks: DeckManager,
            didsStr: String,
            todayStats: TodayStats
        ): SimulationResult {
            val simulationResultAggregated = SimulationResult(
                nTimeBins,
                timeBinLength,
                DOUBLE_TO_INT_MODE_ROUND
            )
            val dids = mArrayUtils.stringToLongArray(didsStr)
            val nIterations = mSettings!!.simulateNIterations
            val nIterationsInv = 1.0 / nIterations
            for (did in dids) {
                for (iteration in 0 until nIterations) {
                    mNewCardSimulator.reset(todayStats.getNLearned(did))
                    simulationResultAggregated.add(
                        simNreviews(
                            today,
                            mDecks.createDeck(did, decks)
                        ),
                        nIterationsInv
                    )
                }
            }
            return simulationResultAggregated
        }

        private fun simNreviews(today: Int, deck: Deck): SimulationResult {
            val simulationResult: SimulationResult

            // we schedule a review if the number of reviews has not yet reached the maximum # reviews per day
            // If we compute the simulationresult, we keep track of the average number of reviews
            // Since it's the average, it can be a non-integer
            // Adding a review to a non-integer can make it exceed the maximum # reviews per day, but not by 1 or more
            // So if we take the floor when displaying it, we will display the maximum # reviews
            simulationResult = if (mSettings!!.computeNDays > 0) {
                SimulationResult(
                    nTimeBins,
                    timeBinLength,
                    DOUBLE_TO_INT_MODE_FLOOR
                )
            } else {
                SimulationResult(
                    nTimeBins,
                    timeBinLength,
                    DOUBLE_TO_INT_MODE_ROUND
                )
            }

            // nSmooth=1

            // TODO:
            // Forecasted final state of deck
            // finalIvl = np.empty((nSmooth, nCards), dtype='f8')
            Timber.d("today: %d", today)
            val reviews = Stack<Review>()
            val reviewList = ArrayList<Review>()

            // By having simulateReview add future reviews depending on which simulation of this card this is (the nth) we can:
            // 1. Do monte carlo simulation if we add nIterations future reviews if n = 1
            //   We don't do it this way. Instead we do this by having tis method [simNreviews] called nIterations times.
            //   The reason is that in that way we take into account the dependency between cards correctly, since we do
            //   for each iteration... for each card
            //   If we would do for each card... for each iteration... we would not take it into account correctly.
            //   We would not schedule new cards on a particular day if on average the new card limit would have been exceeded
            //   in simulations of previous cards.
            // 2. Do a complete traversal of the future reviews tree if we add k future reviews for all n
            //   We accept the drawback as mentioned in (1).
            // 3. Do any combination of these (controlled by computeNDays and computeMaxError)
            val card = Card(0, 0, 0, 0, 0, 0)
            var cardIterator: CardIterator? = null
            val review: Review = Review(deck, simulationResult, classifier, reviews, reviewList)
            try {
                cardIterator = CardIterator(db, today, deck)

                // int cardN = 0;
                while (cardIterator.moveToNext()) {
                    cardIterator.current(card)
                    review.newCard(card, mNewCardSimulator)
                    if (review.t < mTMax) reviews.push(review)

                    // Timber.d("Card started: %d", cardN);
                    while (!reviews.isEmpty()) {
                        reviews.pop().simulateReview()
                    }

                    // Timber.d("Card done: %d", cardN++);
                }
            } finally {
                cardIterator?.close()
            }
            mArrayUtils.formatMatrix("nReviews", simulationResult.nReviews, "%04d ")
            mArrayUtils.formatMatrix("nInState", simulationResult.nInState, "%04d ")
            return simulationResult
        }

        init {
            mTMax = nTimeBins * timeBinLength
        }
    }

    /**
     * Stores global settings.
     */
    private class Settings(context: Context) {
        val computeNDays: Int
        val computeMaxError: Double
        val simulateNIterations: Int
        private val mCol: Collection

        /**
         * @return Maximum number of new cards per day which will be used if it cannot be read from Deck settings.
         */
        val maxNewPerDay: Int
            get() = 20

        /**
         * @return Maximum number of reviews per day which will be used if it cannot be read from Deck settings.
         */
        val maxReviewsPerDay: Int
            get() = 10000

        /**
         * Today.
         * @param collectionCreatedTime The difference, measured in seconds, between midnight, January 1, 1970 UTC and the time at which the collection was created.
         * @return Today in days counted from the time at which the collection was created
         */
        fun getToday(collectionCreatedTime: Long): Int {
            Timber.d("Collection creation timestamp: %d", collectionCreatedTime)
            val currentTime = time.intTime()
            Timber.d("Now: %d", currentTime)
            return ((currentTime - collectionCreatedTime) / Stats.SECONDS_PER_DAY).toInt()
        }

        /**
         * Beginning of today.
         * @param collectionCreatedTime The difference, measured in seconds, between midnight, January 1, 1970 UTC and the time at which the collection was created.
         * @return The beginning of today in milliseconds counted from the time at which the collection was created
         */
        fun getDayStartCutoff(collectionCreatedTime: Long): Long {
            val today = getToday(collectionCreatedTime).toLong()
            return (collectionCreatedTime + today * Stats.SECONDS_PER_DAY) * 1000
        }

        init {
            val prefs = context.sharedPrefs()
            mCol = CollectionHelper.instance.getCol(context)!!
            computeNDays = prefs.getInt("advanced_forecast_stats_compute_n_days", 0)
            val computePrecision = prefs.getInt("advanced_forecast_stats_compute_precision", 90)
            computeMaxError = (100 - computePrecision) / 100.0
            simulateNIterations = prefs.getInt("advanced_forecast_stats_mc_n_iterations", 1)
            Timber.d("computeNDays: %s", computeNDays)
            Timber.d("computeMaxError: %s", computeMaxError)
            Timber.d("simulateNIterations: %s", simulateNIterations)
        }

        companion object {
            /**
             *
             * @return Factor which will be used if it cannot be read from Deck settings.
             */
            const val initialFactor = Consts.STARTING_FACTOR
        }
    }

    private inner class ArrayUtils {
        fun createIntMatrix(m: Int, n: Int): Array<IntArray?> {
            val matrix = arrayOfNulls<IntArray>(m)
            for (i in 0 until m) {
                matrix[i] = IntArray(n)
                for (j in 0 until n) matrix[i]!![j] = 0
            }
            return matrix
        }

        fun toIntMatrix(doubleMatrix: Array<DoubleArray>, doubleToIntMode: Int): Array<IntArray> {
            val m = doubleMatrix.size
            if (m == 0) return emptyArray()
            val n: Int = doubleMatrix[1].size
            val intMatrix = arrayOfNulls<IntArray>(m)
            for (i in 0 until m) {
                intMatrix[i] = IntArray(n)
                for (j in 0 until n) {
                    if (doubleToIntMode == DOUBLE_TO_INT_MODE_ROUND) {
                        intMatrix[i]!![j] =
                            Math.round(
                                doubleMatrix[i][j]
                            ).toInt()
                    } else {
                        intMatrix[i]!![j] = doubleMatrix[i][j].toInt()
                    }
                }
            }
            return intMatrix.requireNoNulls()
        }

        fun createDoubleMatrix(m: Int, n: Int): Array<DoubleArray> {
            val matrix = arrayOfNulls<DoubleArray>(m)
            for (i in 0 until m) {
                matrix[i] = DoubleArray(n)
                for (j in 0 until n) matrix[i]!![j] = 0.0
            }
            return matrix.requireNoNulls()
        }

        fun <T> append(arr: Array<T>, element: T, n: Int): Array<T> {
            @Suppress("NAME_SHADOWING")
            var arr = arr
            val N0 = arr.size
            val N1 = N0 + n
            arr = Arrays.copyOf(arr, N1)
            for (N in N0 until N1) arr[N] = element
            return arr
        }

        fun nRows(matrix: Array<IntArray?>): Int {
            return matrix.size
        }

        fun nCols(matrix: Array<IntArray>): Int {
            return if (matrix.size == 0) 0 else matrix[0].size
        }

        fun stringToLongArray(s: String): LongArray {
            val split = s.substring(1, s.length - 1).split(", ".toRegex()).toTypedArray()
            val arr = LongArray(split.size)
            for (i in split.indices) arr[i] = split[i].toLong()
            return arr
        }

        fun transposeMatrix(matrix: Array<IntArray>): Array<IntArray> {
            if (matrix.size == 0) return matrix
            val m = matrix.size
            val n: Int = matrix[0].size
            val transpose = Array<IntArray?>(n) { IntArray(m) }
            var c: Int
            var d: Int
            c = 0
            while (c < m) {
                d = 0
                while (d < n) {
                    transpose[d]!![c] = matrix[c][d]
                    d++
                }
                c++
            }
            return transpose.requireNoNulls()
        }

        fun transposeMatrix(matrix: Array<DoubleArray>): Array<DoubleArray> {
            if (matrix.size == 0) return matrix
            val m = matrix.size
            val n: Int = matrix[0].size
            val transpose = Array(n) { DoubleArray(m) }
            var c: Int
            var d: Int
            c = 0
            while (c < m) {
                d = 0
                while (d < n) {
                    transpose[d][c] = matrix[c][d]
                    d++
                }
                c++
            }
            return transpose
        }

        fun formatMatrix(matrixName: String?, matrix: Array<IntArray>, format: String?) {
            val s = StringBuilder()
            s.append(matrixName)
            s.append(":")
            s.append(System.getProperty("line.separator"))
            for (aMatrix in matrix) {
                for (i in aMatrix) {
                    s.append(String.format(format!!, i))
                }
                s.append(System.getProperty("line.separator"))
            }
            Timber.d(s.toString())
        }
    }

    /**
     * Statistics generated by simulations of Reviews.
     */
    private inner class SimulationResult(nTimeBins: Int, timeBinLength: Int, doubleToIntMode: Int) {
        private val mDoubleToIntMode: Int
        private val mNTimeBins: Int
        private val mTimeBinLength: Int
        val nDays: Int

        /**
         * Forecasted number of reviews per time bin (a time bin contains statistics for 1 or a multiple of days)
         * First dimension:
         * 0 = Learn
         * 1 = Young
         * 2 = Mature
         * 3 = Relearn
         * Second dimension: time
         */
        private val mNReviews: Array<DoubleArray>

        /**
         * Forecasted number of reviews per day.
         * @see .mNReviews
         */
        private val mNReviewsPerDay: Array<DoubleArray>

        /**
         * Forecasted number of cards per state
         * First dimension:
         * 0 = New
         * 1 = Young
         * 2 = Mature
         * Second dimension: time
         */
        private val mNInState: Array<DoubleArray>

        /**
         * Adds the statistics generated by another simulation to the current statistics.
         * Use to gather statistics over decks.
         * @param res2Add Statistics to be added to the current statistics.
         */
        fun add(res2Add: SimulationResult, prob: Double) {
            val nReviews = res2Add.nReviews
            val nInState = res2Add.nInState
            for (i in nReviews.indices) for (j in 0 until nReviews[i].size) mNReviews[i][j] += nReviews[i][j] * prob

            // This method is only used to aggregate over decks
            // We do not update nReviewsPerDay since it is not needed for the SimulationResult aggregated over decks.
            for (i in nInState.indices) for (j in 0 until nInState[i].size) mNInState[i][j] += nInState[i][j] * prob
        }

        val nReviews: Array<IntArray>
            get() = mArrayUtils.toIntMatrix(mNReviews, mDoubleToIntMode)
        val nInState: Array<IntArray>
            get() = mArrayUtils.toIntMatrix(mNInState, mDoubleToIntMode)

        /**
         * Request the number of reviews which have been simulated so far at a particular day
         * (to check if the 'maximum number of reviews per day' limit has been reached).
         * If we are doing more than one simulation this means the average number of reviews
         * simulated so far at the requested day (over simulations).
         * More correct would be simulating all (or several) possible futures and returning here the number of
         * reviews done in the future currently being simulated.
         *
         * But that would change the entire structure of the simulation (which is now in a for each card loop).
         * @param tElapsed Day for which the number of reviews is requested.
         * @return Number of reviews of young and mature cards simulated at time tElapsed.
         * This excludes new cards and relearns as they don't count towards the limit.
         */
        fun nReviewsDoneToday(tElapsed: Int): Int {
            return (
                mNReviewsPerDay[REVIEW_TYPE_YOUNG][tElapsed] +
                    mNReviewsPerDay[REVIEW_TYPE_MATURE][tElapsed]
                ).toInt()
        }

        /**
         * Increment the count 'number of reviews of card with type cardType' with one at day t.
         * @param cardType  Card type
         * @param t Day for which to increment
         */
        fun incrementNReviews(cardType: Int, t: Int, prob: Double) {
            mNReviews[cardType][t / mTimeBinLength] += prob
            mNReviewsPerDay[cardType][t] += prob
        }

        /**
         * Increment the count 'number of cards in the state of the given card' with one between tFrom and tTo.
         * @param card Card from which to read the state.
         * @param tFrom The first day for which to update the state.
         * @param tTo The day after the last day for which to update the state.
         */
        fun updateNInState(card: Card, tFrom: Int, tTo: Int, prob: Double) {
            val cardType = card.type
            val t0 = tFrom / mTimeBinLength
            val t1 = tTo / mTimeBinLength
            for (t in t0 until t1) if (t < mNTimeBins) {
                mNInState[cardType][t] += prob
            } else {
                return
            }
        }

        /**
         * Increment the count 'number of cards in the state of the given card' with one between tFrom and tTo and
         * replace state set during last review (contained in prevCard) with state set during new review (contained in card).
         *
         * This is necessary because we want to display the state at the end of each time bin.
         * So if two reviews occurred in one time bin, that time bin should display the
         * last review which occurred in it.
         *
         * @see .updateNInState
         */
        fun updateNInState(prevCard: Card, card: Card?, tFrom: Int, tTo: Int, prob: Double) {
            val lastReview = prevCard.lastReview
            val prevCardType = prevCard.type
            val cardType = card!!.type
            val t0 = tFrom / mTimeBinLength
            var t1 = Math.min(lastReview, tTo) / mTimeBinLength

            // Replace state set during last review
            for (t in t0 until t1) if (t < mNTimeBins) {
                mNInState[prevCardType][t] -= prob
            } else {
                break
            }
            t1 = tTo / mTimeBinLength

            // With state set during new review
            for (t in t0 until t1) if (t < mNTimeBins) {
                mNInState[cardType][t] += prob
            } else {
                return
            }

            // Alternative solution would be to keep this count for each day instead of keeping it for each bin and aggregate in the end
            // to a count for each bin.
            // That would also work because we do not simulate two reviews of one card at one and the same day.
        }

        /**
         * Create an empty SimulationResult.
         * @param nTimeBins Number of time bins.
         * @param timeBinLength Length of 1 time bin in days.
         */
        init {
            mNReviews = mArrayUtils.createDoubleMatrix(REVIEW_TYPE_COUNT, nTimeBins)
            mNReviewsPerDay =
                mArrayUtils.createDoubleMatrix(REVIEW_TYPE_COUNT, nTimeBins * timeBinLength)
            mNInState = mArrayUtils.createDoubleMatrix(CARD_TYPE_COUNT, nTimeBins)
            mNTimeBins = nTimeBins
            mTimeBinLength = timeBinLength
            nDays = nTimeBins * timeBinLength
            mDoubleToIntMode = doubleToIntMode
        }
    }

    private class PlottableSimulationResult( // Forecasted number of reviews
        // ArrayList: time
        // int[]:
        //   0 = Time
        //   1 = Learn
        //   2 = Young
        //   3 = Mature
        //   4 = Relearn
        val nReviews: ArrayList<IntArray>, // Forecasted number of cards per state
        // First dimension:
        //   0 = Time
        //   4 = New
        //   3 = Young
        //   2 = Mature
        //   1 = Zeros (we can't say 'we know x relearn cards on day d')
        // Second dimension: time
        val nInState: Array<DoubleArray>
    )

    /**
     * A review has a particular outcome with a particular probability.
     * A review results in the state of the card (card interval) being changed.
     * A ReviewOutcome bundles the probability of the outcome and the card with changed state.
     */
    private class ReviewOutcome(var card: Card?, var prob: Double) {
        fun setAll(card: Card?, prob: Double) {
            this.card = card
            this.prob = prob
        }

        override fun toString(): String {
            return "ReviewOutcome{" +
                "card=" + card +
                ", prob=" + prob +
                '}'
        }
    }

    /**
     * Bundles the information needed to simulate a review and the objects affected by the review.
     */
    private inner class Review {
        /**
         * Deck-specific setting stored separately to save a method call on the deck object)
         */
        private val mMaxReviewsPerDay: Int

        /**
         * Number of reviews simulated for this card at time < tElapsed
         */
        private var mNPrevRevs = 0

        /**
         * The probability that the outcomes of the reviews simulated for this card at time < tElapsed are such that
         * this review [with this state of the card] will occur [at this time (tElapsed)].
         */
        private var mProb = 0.0

        /**
         * The time instant at which the review takes place.
         */
        var t = 0
            private set

        /**
         * The outcome of the review.
         * We still have to do the review if the outcome has already been specified
         * (to update statistics, deterime probability of specified outcome, and to schedule subsequent reviews)
         * Only relevant if we are computing (all possible review outcomes), not if simulating (only one possible outcome)
         */
        private var mOutcome = 0

        /**
         * Deck-specific settings
         */
        private val mDeck: Deck

        /**
         * State of the card before current review.
         * Needed to schedule current review but with different outcome and to update statistics.
         */
        private var mCard = Card(0, 0, 0, 0, 0, 0)
        private val mPrevCard = Card(0, 0, 0, 0, 0, 0)

        /**
         * State of the card after current review.
         * Needed to schedule future review.
         */
        private var mNewCard: Card? = Card(0, 0, 0, 0, 0, 0)

        /**
         * Statistics
         */
        private val mSimulationResult: SimulationResult

        /**
         * Classifier which uses probability distribution from review log to predict outcome of review.
         */
        private val mClassifier: EaseClassifier

        /**
         * Reviews which are scheduled to be simulated.
         * For adding current review with other outcome and future review.
         */
        private val mReviews: Stack<Review>

        /**
         * Review objects to be re-used so that we don't have to create new Review objects all the time.
         * Be careful: it also contains Review objects which are still in use.
         * So the algorithm using this list has to make sure that it only re-uses Review objects which are not in use anymore.
         */
        private val mReviewlist: MutableList<Review>

        /**
         * For creating future reviews which are to be scheduled as a result of the current review.
         * @see Review
         */
        private constructor(
            prevReview: Review,
            card: Card,
            nPrevRevs: Int,
            tElapsed: Int,
            prob: Double
        ) {
            mDeck = prevReview.mDeck
            mCard.setAll(card)
            mSimulationResult = prevReview.mSimulationResult
            mClassifier = prevReview.mClassifier
            mReviews = prevReview.mReviews
            mReviewlist = prevReview.mReviewlist
            mNPrevRevs = nPrevRevs
            t = tElapsed
            mProb = prob
            mMaxReviewsPerDay = mDeck.revPerDay
        }

        /**
         * For creating a review which is to be scheduled.
         * After this constructor, either @see newCard(Card, NewCardSimulator) or existingCard(Card, int, int, double) has to be called.
         * @param deck Information needed to simulate a review: deck settings.
         * Will be affected by the review. After the review it will contain the card type etc. after the review.
         * @param simulationResult Will be affected by the review. After the review it will contain updated statistics.
         * @param classifier Information needed to simulate a review: transition probabilities to new card state for each possible current card state.
         * @param reviews Will be affected by the review. Scheduled future reviews of this card will be added.
         */
        constructor(
            deck: Deck,
            simulationResult: SimulationResult,
            classifier: EaseClassifier,
            reviews: Stack<Review>,
            reviewList: MutableList<Review>
        ) {
            mDeck = deck
            mSimulationResult = simulationResult
            mClassifier = classifier
            mReviews = reviews
            mReviewlist = reviewList
            mMaxReviewsPerDay = deck.revPerDay
        }

        /**
         * Re-use the current review object to schedule a new card. A new card here means that it has not been reviewed yet.
         * @param card Information needed to simulate a review: card due date, type and factor.
         * @param newCardSimulator Information needed to simulate a review: The next day new cards will be added and the number of cards already added on that day.
         * Will be affected by the review. After the review of a new card, the number of cards added on that day will be updated.
         * Next day new cards will be added might be updated if new card limit has been reached.
         */
        fun newCard(card: Card, newCardSimulator: NewCardSimulator) {
            mCard = card
            mNPrevRevs = 0
            mProb = 1.0
            mOutcome = 0

            // # Rate-limit new cards by shifting starting time
            if (card.type == CARD_TYPE_NEW) {
                t = newCardSimulator.simulateNewCard(mDeck)
            } else {
                t =
                    card.due
            }

            // Set state of card between start and first review
            // New reviews happen with probability 1
            mSimulationResult.updateNInState(card, 0, t, 1.0)
        }

        /**
         * Re-use the current review object to schedule an existing card. An existing card here means that it has been reviewed before (either by the user or by the simulation)
         * and hence the due date is known.
         */
        private fun existingCard(card: Card?, nPrevRevs: Int, tElapsed: Int, prob: Double) {
            mCard.setAll(card)
            mNPrevRevs = nPrevRevs
            t = tElapsed
            mProb = prob
            mOutcome = 0
        }

        /**
         * Simulates one review of the card. The review results in:
         * - The card (prevCard and newCard) being updated
         * - New card simulator (when to schedule next new card) being updated if the card was new
         * - The simulationResult being updated.
         * - New review(s) being scheduled.
         */
        fun simulateReview() {
            if (mCard.type == CARD_TYPE_NEW || mSimulationResult.nReviewsDoneToday(
                    t
                ) < mMaxReviewsPerDay || mOutcome > 0
            ) {
                // Update the forecasted number of reviews
                if (mOutcome == 0) mSimulationResult.incrementNReviews(mCard.type, t, mProb)

                // Simulate response
                mPrevCard.setAll(mCard)
                mNewCard!!.setAll(mCard)
                val reviewOutcome: ReviewOutcome
                reviewOutcome =
                    if (t >= mSettings!!.computeNDays || mProb < mSettings!!.computeMaxError) {
                        mClassifier.simSingleReview(
                            mNewCard
                        )
                    } else {
                        mClassifier.simSingleReview(mNewCard, mOutcome)
                    }

                // Timber.d("Simulation at t=" + tElapsed + ": outcome " + outcomeIdx + ": " + reviewOutcome.toString() );
                mNewCard = reviewOutcome.card
                val outcomeProb = reviewOutcome.prob

                // writeLog(newCard, outcomeProb);
                mNewCard!!.lastReview = t

                // If card failed, update "relearn" count
                if (mNewCard!!.correct == 0) {
                    mSimulationResult.incrementNReviews(
                        3,
                        t,
                        mProb * outcomeProb
                    )
                }

                // Set state of card between current and next review
                mSimulationResult.updateNInState(
                    mPrevCard,
                    mNewCard,
                    t,
                    t + mNewCard!!.ivl,
                    mProb * outcomeProb
                )

                // Schedule current review, but with other outcome
                if (outcomeProb < 1.0 && mOutcome < 3) scheduleCurrentReview(mPrevCard)

                // Advance time to next review
                scheduleNextReview(mNewCard!!, t + mNewCard!!.ivl, mProb * outcomeProb)
            } else {
                // Advance time to next review (max. #reviews reached for this day)
                mSimulationResult.updateNInState(mCard, mCard, t, t + 1, mProb)
                rescheduleCurrentReview(t + 1)
            }
        }

        private fun writeLog(newCard: Card, outcomeProb: Double) {
            var tabs = ""
            for (d in 0 until mNPrevRevs) tabs += "\t"
            Timber.d("%st=%d p=%f * %s", tabs, t, mProb, outcomeProb)
            Timber.d("%s%s", tabs, mPrevCard)
            Timber.d("%s%s", tabs, newCard)
        }

        /**
         * Schedule the current review at another time (will re-use current Review).
         */
        private fun rescheduleCurrentReview(newTElapsed: Int) {
            if (newTElapsed < mSimulationResult.nDays) {
                t = newTElapsed
                mReviews.push(this)
            }
        }

        /**
         * Schedule the current review at the current time, but with another outcome (will re-use current Review).
         * @param newCard
         */
        private fun scheduleCurrentReview(newCard: Card) {
            mCard.setAll(newCard)
            mOutcome++
            mReviews.push(this)
        }

        /**
         * Schedule next review (will not re-use current Review).
         */
        private fun scheduleNextReview(newCard: Card, newTElapsed: Int, newProb: Double) {
            // Schedule next review(s) if they are within the time window of the simulation
            if (newTElapsed < mSimulationResult.nDays) {
                val review: Review
                // Re-use existing instance of the review object (to limit memory usage and prevent time taken by garbage collector)
                // This is possible since reviews with nPrevRevs > nPrevRevs of the current review which were already scheduled have all already been processed before we do the current review.
                if (mReviewlist.size > mNPrevRevs) {
                    review = mReviewlist[mNPrevRevs]
                    review.existingCard(newCard, mNPrevRevs + 1, newTElapsed, newProb)
                } else {
                    if (mReviewlist.size == mNPrevRevs) {
                        review = Review(this, newCard, mNPrevRevs + 1, newTElapsed, newProb)
                        mReviewlist.add(review)
                    } else {
                        throw IllegalStateException("State of previous reviews of this card should have been saved for determining possible future reviews other than the current one.")
                    }
                }
                mReviews.push(review)
            }
        }
    }

    companion object {
        private const val TIME = 0

        // For indexing arrays. We have *_PLUS_1 because we often add
        // the time dimension at index 0.
        private const val CARD_TYPE_COUNT = 3
        private const val CARD_TYPE_NEW = 0
        private const val CARD_TYPE_YOUNG = 1
        private const val CARD_TYPE_MATURE = 2
        private const val CARD_TYPE_NEW_PLUS_1 = 1
        private const val CARD_TYPE_YOUNG_PLUS_1 = 2
        private const val CARD_TYPE_MATURE_PLUS_1 = 3
        private const val REVIEW_TYPE_COUNT = 4
        private const val REVIEW_TYPE_LEARN = 0
        private const val REVIEW_TYPE_YOUNG = 1
        private const val REVIEW_TYPE_MATURE = 2
        private const val REVIEW_TYPE_RELEARN = 3
        private const val REVIEW_TYPE_COUNT_PLUS_1 = 5
        private const val REVIEW_TYPE_LEARN_PLUS_1 = 1
        private const val REVIEW_TYPE_YOUNG_PLUS_1 = 2
        private const val REVIEW_TYPE_MATURE_PLUS_1 = 3
        private const val REVIEW_TYPE_RELEARN_PLUS_1 = 4
        private const val REVIEW_OUTCOME_REPEAT = 0
        private const val REVIEW_OUTCOME_HARD = 1
        private const val REVIEW_OUTCOME_GOOD = 2
        private const val REVIEW_OUTCOME_EASY = 3
        private const val REVIEW_OUTCOME_REPEAT_PLUS_1 = 1
        private const val REVIEW_OUTCOME_HARD_PLUS_1 = 2
        private const val REVIEW_OUTCOME_GOOD_PLUS_1 = 3
        private const val REVIEW_OUTCOME_EASY_PLUS_1 = 4

        private const val DOUBLE_TO_INT_MODE_FLOOR = 0
        private const val DOUBLE_TO_INT_MODE_ROUND = 1
    }
}
