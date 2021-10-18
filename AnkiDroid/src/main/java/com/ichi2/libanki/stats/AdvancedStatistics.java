/****************************************************************************************
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

package com.ichi2.libanki.stats;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.stats.StatsMetaInfo;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.DeckManager;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.utils.Time;
import com.ichi2.utils.HashUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import timber.log.Timber;

import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;

/**
 * Display forecast statistics based on a simulation of future reviews.
 *
 * Sequence diagram (https://www.websequencediagrams.com/):
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
 */
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.ExcessiveClassLength","PMD.ExcessiveMethodLength",
                    "PMD.AvoidReassigningParameters","PMD.FieldDeclarationsShouldBeAtStartOfClass","PMD.SwitchStatementsShouldHaveDefault",
                    "PMD.NPathComplexity","PMD.OneDeclarationPerLine","PMD.SwitchStmtsShouldHaveDefault"})
public class AdvancedStatistics {

    private static final int TIME = 0;

    //For indexing arrays. We have *_PLUS_1 because we often add
    //the time dimension at index 0.
    private static final int CARD_TYPE_COUNT = 3;
    private static final int CARD_TYPE_NEW = 0;
    private static final int CARD_TYPE_YOUNG = 1;
    private static final int CARD_TYPE_MATURE = 2;

    private static final int CARD_TYPE_NEW_PLUS_1 = 1;
    private static final int CARD_TYPE_YOUNG_PLUS_1 = 2;
    private static final int CARD_TYPE_MATURE_PLUS_1 = 3;

    private static final int REVIEW_TYPE_COUNT = 4;
    private static final int REVIEW_TYPE_LEARN = 0;
    private static final int REVIEW_TYPE_YOUNG = 1;
    private static final int REVIEW_TYPE_MATURE = 2;
    private static final int REVIEW_TYPE_RELEARN = 3;

    private static final int REVIEW_TYPE_COUNT_PLUS_1 = 5;
    private static final int REVIEW_TYPE_LEARN_PLUS_1 = 1;
    private static final int REVIEW_TYPE_YOUNG_PLUS_1 = 2;
    private static final int REVIEW_TYPE_MATURE_PLUS_1 = 3;
    private static final int REVIEW_TYPE_RELEARN_PLUS_1 = 4;

    private static final int REVIEW_OUTCOME_REPEAT = 0;
    private static final int REVIEW_OUTCOME_HARD = 1;
    private static final int REVIEW_OUTCOME_GOOD = 2;
    private static final int REVIEW_OUTCOME_EASY = 3;

    private static final int REVIEW_OUTCOME_REPEAT_PLUS_1 = 1;
    private static final int REVIEW_OUTCOME_HARD_PLUS_1 = 2;
    private static final int REVIEW_OUTCOME_GOOD_PLUS_1 = 3;
    private static final int REVIEW_OUTCOME_EASY_PLUS_1 = 4;

    private final ArrayUtils mArrayUtils = new ArrayUtils();
    private final DeckFactory mDecks = new DeckFactory();
    private Settings mSettings;

    /**
     * Determine forecast statistics based on a computation or simulation of future reviews.
     * Returns all information required by stats.java to plot the 'forecast' chart based on these statistics.
     * The chart will display:
     * - The forecasted number of reviews per review type (relearn, mature, young, learn) as bars
     * - The forecasted number of cards in each state (new, young, mature) as lines
     * @param metaInfo Object which will be filled with all information required by stats.java to plot the 'forecast' chart and returned by this method.
     * @param type Type of 'forecast' chart for which to determine forecast statistics. Accepted values:
     *             Stats.TYPE_MONTH: Determine forecast statistics for next 30 days with 1-day chunks
     *             Stats.TYPE_YEAR:  Determine forecast statistics for next year with 7-day chunks
     *             Stats.TYPE_LIFE:  Determine forecast statistics for next 2 years with 30-day chunks
     * @param context Contains The collection which contains the decks to be simulated.
     *             Also used for access to the database and access to the creation time of the collection.
     *             The creation time of the collection is needed since due times of cards are relative to the creation time of the collection.
     *             So we could pass mCol here.
     * @param dids Deck id's
     * @return @see #metaInfo
     */
    public StatsMetaInfo calculateDueAsMetaInfo(StatsMetaInfo metaInfo, Stats.AxisType type, Context context, String dids) {

        if (!AnkiDroidApp.getSharedPrefs(context).getBoolean("advanced_statistics_enabled", false)) {
            return metaInfo;
        }
        //To indicate that we calculated the statistics so that Stats.java knows that it shouldn't display the standard Forecast chart.
        mSettings = new Settings(context);
        metaInfo.setStatsCalculated(true);

        Collection col = CollectionHelper.getInstance().getCol(context);

        int maxCards = 0;
        double lastElement = 0;
        int zeroIndex = 0;

        int[] valueLabels = {     R.string.statistics_relearn,
                                   R.string.statistics_mature,
                                   R.string.statistics_young,
                                   R.string.statistics_learn};
        int[] colors = {          R.attr.stats_relearn,
                                   R.attr.stats_mature,
                                   R.attr.stats_young,
                                   R.attr.stats_learn};

        int[] axisTitles = { type.ordinal(), R.string.stats_cards, R.string.stats_cumulative_cards };

        PlottableSimulationResult simuationResult = calculateDueAsPlottableSimulationResult(type, col, dids);

        ArrayList<int[]> dues = simuationResult.getNReviews();

        double[][] seriesList = new double[REVIEW_TYPE_COUNT_PLUS_1][dues.size()];

        for (int t = 0;t < dues.size(); t++) {
            int[] data = dues.get(t);
            int nReviews = data[REVIEW_TYPE_LEARN_PLUS_1] +
                           data[REVIEW_TYPE_YOUNG_PLUS_1] +
                           data[REVIEW_TYPE_MATURE_PLUS_1] +
                           data[REVIEW_TYPE_RELEARN_PLUS_1];

            if(nReviews > maxCards)
                maxCards = nReviews;                                                                     //Y-Axis: Max. value

            //In the bar-chart, the bars will be stacked on top of each other.
            //For the i^{th} bar counting from the bottom we therefore have to
            //provide the sum of the heights of the i^{th} bar and all bars below it.
            seriesList[TIME][t]                        = data[TIME];                                     //X-Axis: Day / Week / Month
            seriesList[REVIEW_TYPE_LEARN_PLUS_1][t]    = data[REVIEW_TYPE_LEARN_PLUS_1] +
                                                          data[REVIEW_TYPE_YOUNG_PLUS_1] +
                                                          data[REVIEW_TYPE_MATURE_PLUS_1] +
                                                          data[REVIEW_TYPE_RELEARN_PLUS_1];               //Y-Axis: # Cards
            seriesList[REVIEW_TYPE_YOUNG_PLUS_1][t]    = data[REVIEW_TYPE_LEARN_PLUS_1] +
                                                          data[REVIEW_TYPE_YOUNG_PLUS_1] +
                                                          data[REVIEW_TYPE_MATURE_PLUS_1];                //Y-Axis: # Mature cards
            seriesList[REVIEW_TYPE_MATURE_PLUS_1][t]   = data[REVIEW_TYPE_LEARN_PLUS_1] +
                                                          data[REVIEW_TYPE_YOUNG_PLUS_1];                 //Y-Axis: # Young
            seriesList[REVIEW_TYPE_RELEARN_PLUS_1][t]  = data[REVIEW_TYPE_LEARN_PLUS_1];                 //Y-Axis: # Learn

            if(data[TIME] > lastElement)
                lastElement = data[TIME];          //X-Axis: Max. value (only for TYPE_LIFE)
            if(data[TIME] == 0){
                zeroIndex = t;                     //Because we retrieve dues in the past and we should not cumulate them
            }
        }
        int maxElements = dues.size()-1;           //# X values
        switch (type) {
            case TYPE_MONTH:
                lastElement = 31;              //X-Axis: Max. value
                break;
            case TYPE_YEAR:
                lastElement = 52;              //X-Axis: Max. value
                break;
            default:
        }
        double firstElement = 0;                      //X-Axis: Min. value

        double[][] cumulative = simuationResult.getNInState();                                                          //Day starting at zeroIndex, Cumulative # cards

        double count = cumulative[CARD_TYPE_NEW_PLUS_1][cumulative[CARD_TYPE_NEW_PLUS_1].length-1] +             //Y-Axis: Max. cumulative value
                  cumulative[CARD_TYPE_YOUNG_PLUS_1][cumulative[CARD_TYPE_YOUNG_PLUS_1].length-1] +
                  cumulative[CARD_TYPE_MATURE_PLUS_1][cumulative[CARD_TYPE_MATURE_PLUS_1].length-1];

        //some adjustments to not crash the chartbuilding with empty data
        if(maxElements == 0){
            maxElements = 10;
        }
        if(count == 0){
            count = 10;
        }
        if(firstElement == lastElement){
            firstElement = 0;
            lastElement = 6;
        }
        if(maxCards == 0)
            maxCards = 10;

        metaInfo.setmDynamicAxis(true);
        metaInfo.setmHasColoredCumulative(true);
        metaInfo.setmType(type);
        metaInfo.setmTitle(R.string.stats_forecast);
        metaInfo.setmBackwards(true);
        metaInfo.setmValueLabels(valueLabels);
        metaInfo.setmColors(colors);
        metaInfo.setmAxisTitles(axisTitles);
        metaInfo.setmMaxCards(maxCards);
        metaInfo.setmMaxElements(maxElements);
        metaInfo.setmFirstElement(firstElement);
        metaInfo.setmLastElement(lastElement);
        metaInfo.setmZeroIndex(zeroIndex);
        metaInfo.setmCumulative(cumulative);
        metaInfo.setmMcount(count);

        metaInfo.setmSeriesList(seriesList);

        metaInfo.setDataAvailable(!dues.isEmpty());

        return metaInfo;
    }

    /**
     * Determine forecast statistics based on a computation or simulation of future reviews and returns the results of the simulation.
     * @param type @see #calculateDueOriginal(StatsMetaInfo, int, Context, String)
     * @param col @see #calculateDueOriginal(StatsMetaInfo, int, Context, String)
     * @param dids @see #calculateDueOriginal(StatsMetaInfo, int, Context, String)
     * @return An object containing the results of the simulation:
     *        - The forecasted number of reviews per review type (relearn, mature, young, learn)
     *        - The forecasted number of cards in each state (new, young, mature)
     */
    private PlottableSimulationResult calculateDueAsPlottableSimulationResult(Stats.AxisType type, Collection col, String dids) {
        int end = 0;
        int chunk = 0;
        switch (type) {
            case TYPE_MONTH:
                end = 31;
                chunk = 1;
                break;
            case TYPE_YEAR:
                end = 52;
                chunk = 7;
                break;
            case TYPE_LIFE:
                end = 24;
                chunk = 30;
                break;
        }


        EaseClassifier classifier = new EaseClassifier(col.getTime(), col.getDb());
        ReviewSimulator reviewSimulator = new ReviewSimulator(col.getDb(), classifier, end, chunk);
        TodayStats todayStats = new TodayStats(col, mSettings.getDayStartCutoff(col.getCrt()));

        long t0 = col.getTime().intTimeMS();
        SimulationResult simulationResult = reviewSimulator.simNreviews(mSettings.getToday((int)col.getCrt()), col.getDecks(), dids, todayStats);
        long t1 = col.getTime().intTimeMS();

        Timber.d("Simulation of all decks took: %d ms", t1 - t0);

        int[][] nReviews = mArrayUtils.transposeMatrix(simulationResult.getNReviews());
        int[][] nInState = mArrayUtils.transposeMatrix(simulationResult.getNInState());

        //Append row with zeros and transpose to make it the same dimension as nReviews
        //int[][] nInState = simulationResult.getNInState();
        //if(ArrayUtils.nCols(nInState) > 0)
        //    nInState = ArrayUtils.append(nInState, new int[ArrayUtils.nCols(nInState)], 1);

        ArrayList<int[]> dues = new ArrayList<>(nReviews.length + 2);
        // Forecasted number of reviews
        for(int i = 0; i<nReviews.length; i++) {
            dues.add(new int[] { i,                                         //Time
                                 nReviews[i][REVIEW_TYPE_LEARN],
                                 nReviews[i][REVIEW_TYPE_YOUNG],
                                 nReviews[i][REVIEW_TYPE_MATURE],
                                 nReviews[i][REVIEW_TYPE_RELEARN] });
        }

        // small adjustment for a proper chartbuilding
        if (dues.isEmpty() || dues.get(0)[0] > 0) {
            dues.add(0, new int[] { 0, 0, 0, 0, 0 });
        }
        if (type == Stats.AxisType.TYPE_LIFE && dues.size() < 2) {
            end = 31;
        }
        if (type != Stats.AxisType.TYPE_LIFE && dues.get(dues.size() - 1)[0] < end) {
            dues.add(new int[] { end, 0, 0, 0, 0 });
        } else if (type == Stats.AxisType.TYPE_LIFE && dues.size() < 2) {
            dues.add(new int[] { Math.max(12, dues.get(dues.size() - 1)[0] + 1), 0, 0, 0, 0 });
        }

        double[][] nInStateCum = new double[dues.size()][];

        for(int i = 0; i<dues.size(); i++) {

            if(i < nInState.length) {
                nInStateCum[i] = new double[] {
                        i,
                        0,                                                  //Y-Axis: Relearn = 0 (we can't say 'we know x relearn cards on day d')
                        //nInState[i][0] + nInState[i][1] + nInState[i][2], //Y-Axis: New + Young + Mature
                        //nInState[i][0] + nInState[i][1],                  //Y-Axis: New + Young
                        //nInState[i][0],                                   //Y-Axis: New
                        nInState[i][CARD_TYPE_MATURE],                      //Y-Axis: Mature
                        nInState[i][CARD_TYPE_YOUNG],                       //Y-Axis: Young
                        nInState[i][CARD_TYPE_NEW],                         //Y-Axis: New
                };
            }
            else {
                if(i==0)
                    nInStateCum[i] = new double[] {
                        i,
                        0,0,0,0
                    };
                else
                    nInStateCum[i] = nInStateCum[i-1];
            }
        }

        //Append columns to make it the same dimension as dues
        //if(dues.size() > nInState.length) {
        //    nInState = ArrayUtils.append(nInState, nInState[nInState.length-1], dues.size() - nInState.length);
        //}

        return new PlottableSimulationResult(dues, mArrayUtils.transposeMatrix(nInStateCum));
    }

    private static class Card {

        private int mIvl;
        private double mFactor;
        private int mLastReview;
        private int mDue;
        private int mCorrect;
        private long mId;

        @Override
        public @NonNull String toString() {
            return "Card [ivl=" + mIvl + ", factor=" + mFactor + ", due=" + mDue + ", correct=" + mCorrect + ", id="
                    + mId + "]";
        }

        public Card(long id, int ivl, int factor, int due, int correct, int lastReview) {
            super();
            this.mId = id;
            this.mIvl = ivl;
            this.mFactor = factor / 1000.0;
            this.mDue = due;
            this.mCorrect = correct;
            this.mLastReview = lastReview;
        }

        public void setAll(long id, int ivl, int factor, int due, int correct, int lastReview) {
            this.mId = id;
            this.mIvl = ivl;
            this.mFactor = factor / 1000.0;
            this.mDue = due;
            this.mCorrect = correct;
            this.mLastReview = lastReview;
        }

        public void setAll(Card card) {
            this.mId = card.mId;
            this.mIvl = card.mIvl;
            this.mFactor = card.mFactor;
            this.mDue = card.mDue;
            this.mCorrect = card.mCorrect;
            this.mLastReview = card.mLastReview;
        }

        public long getId() {
            return mId;
        }

        public int getIvl() {
            return mIvl;
        }

        public void setIvl(int ivl) {
            this.mIvl = ivl;
        }

        public double getFactor() {
            return mFactor;
        }

        public void setFactor(double factor) {
            this.mFactor = factor;
        }

        public int getDue() {
            return mDue;
        }

        public void setDue(int due) {
            this.mDue = due;
        }

        /**
         * Type of the card, based on the interval.
         * @return CARD_TYPE_NEW if interval = 0, CARD_TYPE_YOUNG if interval 1-20, CARD_TYPE_MATURE if interval >= 20
         */
        public int getType() {
            if(mIvl == 0) {
                return CARD_TYPE_NEW;
            } else if (mIvl >= 21) {
                return CARD_TYPE_MATURE;
            } else {
                return CARD_TYPE_YOUNG;
            }
        }

        public int getCorrect() {
            return mCorrect;
        }

        public void setCorrect(int correct) {
            this.mCorrect = correct;
        }

        public int getLastReview() {
            return mLastReview;
        }

        public void setLastReview(int lastReview) {
            this.mLastReview = lastReview;
        }
    }

    private class DeckFactory {

        public Deck createDeck(long did, DeckManager decks) {

            Timber.d("Trying to get deck settings for deck with id=%s", did);

            DeckConfig conf = decks.confForDid(did);

            int newPerDay = mSettings.getMaxNewPerDay();
            int revPerDay = mSettings.getMaxReviewsPerDay();
            int initialFactor = mSettings.getInitialFactor();

            if (conf.isStd()) {
                revPerDay = conf.getJSONObject("rev").getInt("perDay");
                newPerDay = conf.getJSONObject("new").getInt("perDay");
                initialFactor = conf.getJSONObject("new").getInt("initialFactor");

                Timber.d("rev.perDay=%d", revPerDay);
                Timber.d("new.perDay=%d", newPerDay);
                Timber.d("new.initialFactor=%d", initialFactor);
            } else {
                Timber.d("dyn=%d", conf.getInt("dyn"));
            }

            return new Deck(did, newPerDay, revPerDay, initialFactor);
        }
    }

    /**
     * Stores settings that are deck-specific.
     */
    private static class Deck {

        private final long mDid;

        private final int mNewPerDay;
        private final int mRevPerDay;
        private final int mInitialFactor;

        public Deck(long did, int newPerDay, int revPerDay, int initialFactor) {
            this.mDid = did;
            this.mNewPerDay = newPerDay;
            this.mRevPerDay = revPerDay;
            this.mInitialFactor = initialFactor;
        }

        public long getDid() {
            return mDid;
        }

        public int getNewPerDay() {
            return mNewPerDay;
        }

        public int getRevPerDay() {
            return mRevPerDay;
        }

        public int getInitialFactor() {
            return mInitialFactor;
        }

    }

    private static class CardIterator {

        private final Cursor mCur;

        private final int mToday;
        private final Deck mDeck;

        public CardIterator(DB db, int today, Deck deck) {

            this.mToday = today;
            this.mDeck = deck;

            long did = deck.getDid();

            String query = "SELECT id, due, ivl, factor, type, reps " +
                    "FROM cards " +
                    "WHERE did IN (" + did + ") " +
                    "AND queue != " + Consts.QUEUE_TYPE_SUSPENDED + " " +   // ignore suspended cards
                    "order by id;";
            Timber.d("Forecast query: %s", query);
            mCur = db.query(query);

        }

        public boolean moveToNext() {
            return mCur.moveToNext();
        }

        public void current(Card card) {
            card.setAll(mCur.getLong(0),                                             //Id
                    mCur.getInt(5) == 0 ? 0 : mCur.getInt(2),  		                //reps = 0 ? 0 : card interval
                    mCur.getInt(3) > 0 ? mCur.getInt(3) :  mDeck.getInitialFactor(),   //factor
                    Math.max(mCur.getInt(1) - mToday, 0),                             //due
                    1,                                                              //correct
                    -1                                                              //lastreview
                    );
        }

        public void close() {
            if (mCur != null && !mCur.isClosed())
                mCur.close();
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
    private static class EaseClassifier {

        private final Random mRandom;

        private final DB mDb;
        private double[][] mProbabilities;
        private double[][] mProbabilitiesCumulative;

        //# Prior that half of new cards are answered correctly
        private final int[] mPriorNew = {5, 0, 5, 0};		//half of new cards are answered correctly
        private final int[] mPriorYoung = {1, 0, 9, 0};	//90% of young cards get "good" response
        private final int[] mPriorMature = {1, 0, 9, 0};	//90% of mature cards get "good" response


        //TODO: should we determine these per deck or over decks?
        //Per deck means less data, but tuned to deck.
        //Over decks means more data, but not tuned to deck.
        private static final String queryBaseNew =
                "select "
                        +   "count() as N, "
                        +   "sum(case when ease=1 then 1 else 0 end) as repeat, "
                        +   "0 as hard, "	//Doesn't occur in query_new
                        +	  "sum(case when ease=2 then 1 else 0 end) as good, "
                        +	  "sum(case when ease=3 then 1 else 0 end) as easy "
                        + "from revlog ";

        private static final String queryBaseYoungMature =
                "select "
                        +   "count() as N, "
                        +   "sum(case when ease=1 then 1 else 0 end) as repeat, "
                        +   "sum(case when ease=2 then 1 else 0 end) as hard, "	//Doesn't occur in query_new
                        +	  "sum(case when ease=3 then 1 else 0 end) as good, "
                        +	  "sum(case when ease=4 then 1 else 0 end) as easy "
                        + "from revlog ";

        private static final String queryNew =
                queryBaseNew
                        + "where type=" + CARD_TYPE_NEW + ";";

        private static final String queryYoung =
                queryBaseYoungMature
                        + "where type=" + Consts.CARD_TYPE_LRN + " and lastIvl < 21;";

        private static final String queryMature =
                queryBaseYoungMature
                        + "where type=" + Consts.CARD_TYPE_LRN + " and lastIvl >= 21;";

        public EaseClassifier(Time time, DB db) {
            this.mDb = db;

            mSingleReviewOutcome = new ReviewOutcome(null, 0);

            long t0 = time.intTimeMS();
            calculateCumProbabilitiesForNewEasePerCurrentEase();
            long t1 = time.intTimeMS();

            Timber.d("Calculating probability distributions took: %d ms", t1 - t0);

            Timber.d("new\t\t%s", Arrays.toString(this.mProbabilities[0]));
            Timber.d("young\t\t%s", Arrays.toString(this.mProbabilities[1]));
            Timber.d("mature\t%s", Arrays.toString(this.mProbabilities[2]));

            Timber.d("Cumulative new\t\t%s", Arrays.toString(this.mProbabilitiesCumulative[0]));
            Timber.d("Cumulative young\t\t%s", Arrays.toString(this.mProbabilitiesCumulative[1]));
            Timber.d("Cumulative mature\t%s", Arrays.toString(this.mProbabilitiesCumulative[2]));

            mRandom = new Random();
        }

        private double[] cumsum(double[] p) {

            double[] q = new double[4];

            q[0] = p[0];
            q[1] = q[0] + p[1];
            q[2] = q[1] + p[2];
            q[3] = q[2] + p[3];

            return q;
        }

        private void calculateCumProbabilitiesForNewEasePerCurrentEase() {
            this.mProbabilities = new double[3][];
            this.mProbabilitiesCumulative = new double[3][];

            this.mProbabilities[CARD_TYPE_NEW] = calculateProbabilitiesForNewEaseForCurrentEase(queryNew, mPriorNew);
            this.mProbabilities[CARD_TYPE_YOUNG] = calculateProbabilitiesForNewEaseForCurrentEase(queryYoung, mPriorYoung);
            this.mProbabilities[CARD_TYPE_MATURE] = calculateProbabilitiesForNewEaseForCurrentEase(queryMature, mPriorMature);

            this.mProbabilitiesCumulative[CARD_TYPE_NEW] = cumsum(this.mProbabilities[CARD_TYPE_NEW]);
            this.mProbabilitiesCumulative[CARD_TYPE_YOUNG] = cumsum(this.mProbabilities[CARD_TYPE_YOUNG]);
            this.mProbabilitiesCumulative[CARD_TYPE_MATURE] = cumsum(this.mProbabilities[CARD_TYPE_MATURE]);
        }

        /**
         * Given a query which selects the frequency of each review outcome for the current type of the card,
         * and an array containing the prior frequency of each review outcome for the current type of the card,
         * it gives the probability of each possible review outcome (repeat, hard, good, easy).
         * @param queryNewEaseCountForCurrentEase Query which selects the frequency of each review outcome for the current type of the card.
         * @param prior Array containing the prior frequency of each review outcome for the current type of the card.
         * @return The probability of each possible review outcome (repeat, hard, good, easy).
         */
        private double[] calculateProbabilitiesForNewEaseForCurrentEase(String queryNewEaseCountForCurrentEase, int[] prior) {

            int[] freqs = new int[] {
                    prior[REVIEW_OUTCOME_REPEAT],
                    prior[REVIEW_OUTCOME_HARD],
                    prior[REVIEW_OUTCOME_GOOD],
                    prior[REVIEW_OUTCOME_EASY]
            };

            int n = prior[REVIEW_OUTCOME_REPEAT] + prior[REVIEW_OUTCOME_HARD] + prior[REVIEW_OUTCOME_GOOD] + prior[REVIEW_OUTCOME_EASY];

            try (Cursor cur = mDb.query(queryNewEaseCountForCurrentEase)) {
                cur.moveToNext();

                freqs[REVIEW_OUTCOME_REPEAT]    += cur.getInt(REVIEW_OUTCOME_REPEAT_PLUS_1);        //Repeat
                freqs[REVIEW_OUTCOME_HARD]      += cur.getInt(REVIEW_OUTCOME_HARD_PLUS_1);          //Hard
                freqs[REVIEW_OUTCOME_GOOD]      += cur.getInt(REVIEW_OUTCOME_GOOD_PLUS_1);          //Good
                freqs[REVIEW_OUTCOME_EASY]      += cur.getInt(REVIEW_OUTCOME_EASY_PLUS_1);          //Easy

                int nQuery = cur.getInt(0);         //N

                n += nQuery;

            }

            return new double[] {
                    freqs[REVIEW_OUTCOME_REPEAT] / (double) n,
                    freqs[REVIEW_OUTCOME_HARD]   / (double) n,
                    freqs[REVIEW_OUTCOME_GOOD]   / (double) n,
                    freqs[REVIEW_OUTCOME_EASY]   / (double) n
            };
        }

        private int draw(double[] p) {
            return searchsorted(p, mRandom.nextDouble());
        }

        private int searchsorted(double[] p, double random) {
            if(random <= p[0]) return 0;
            if(random <= p[1]) return 1;
            if(random <= p[2]) return 2;
            return 3;
        }

        private final ReviewOutcome mSingleReviewOutcome;
        public ReviewOutcome simSingleReview(Card c){

            @Consts.CARD_TYPE int type = c.getType();

            int outcome = draw(mProbabilitiesCumulative[type]);

            applyOutcomeToCard(c, outcome);

            mSingleReviewOutcome.setAll(c, 1);
            return mSingleReviewOutcome;
        }

        public ReviewOutcome simSingleReview(Card c, int outcome) {

            int c_type = c.getType();

            //For first review, re-use current card to prevent creating too many objects
            applyOutcomeToCard(c, outcome);
            mSingleReviewOutcome.setAll(c, mProbabilities[c_type][outcome]);

            return mSingleReviewOutcome;
        }

        private void applyOutcomeToCard(Card c, int outcome) {

            @Consts.CARD_TYPE int type = c.getType();
            int ivl = c.getIvl();
            double factor = c.getFactor();

            if(type == CARD_TYPE_NEW) {
                if (outcome <= 2)
                    ivl = 1;
                else
                    ivl = 4;
            }
            else {
                switch(outcome) {
                    case REVIEW_OUTCOME_REPEAT:
                        ivl = 1;
                        //factor = Math.max(1300, factor - 200);
                        break;
                    case REVIEW_OUTCOME_HARD:
                        ivl *= 1.2;
                        break;
                    case REVIEW_OUTCOME_GOOD:
                        ivl *= 1.2 * factor;
                        break;
                    case REVIEW_OUTCOME_EASY:
                    default:
                        ivl *= 1.2 * 2. * factor;
                        break;
                }
            }

            c.setIvl(ivl);
            c.setCorrect((outcome > 0) ? 1 : 0);
            //c.setTypetype);
            //c.setIvl(60);
            //c.setFactor(factor);
        }
    }

    public static class TodayStats {

        private final Map<Long, Integer> mNLearnedPerDeckId;

        public TodayStats(Collection col, long dayStartCutoff) {

            mNLearnedPerDeckId = HashUtil.HashMapInit(col.getDecks().count());
            SupportSQLiteDatabase db = col.getDb().getDatabase();

            String query = "select cards.did, "+
                    "sum(case when revlog.type = " + CARD_TYPE_NEW + " then 1 else 0 end)"+ /* learning */
                    " from revlog, cards where revlog.cid = cards.id and revlog.id > " + dayStartCutoff +
                    " group by cards.did";
            Timber.d("AdvancedStatistics.TodayStats query: %s", query);

            try (Cursor cur = db.query(query)) {

                while(cur.moveToNext()) {
                    mNLearnedPerDeckId.put(cur.getLong(0), cur.getInt(1));
                }
            }
        }

        public int getNLearned(long did) {
            if(mNLearnedPerDeckId.containsKey(did)) {
                return mNLearnedPerDeckId.get(did);
            }
            else {
                return 0;
            }
        }
    }

    public static class NewCardSimulator {

        private int mNAddedToday;
        private int mTAdd;

        public NewCardSimulator() {
            reset(0);
        }

        public int simulateNewCard(Deck deck) {
            mNAddedToday++;
            int tElapsed = mTAdd;	//differs from online
            if (mNAddedToday >= deck.getNewPerDay()) {
                mTAdd++;
                mNAddedToday = 0;
            }
            return tElapsed;
        }

        public void reset(int nAddedToday)
        {
            this.mNAddedToday = nAddedToday;
            this.mTAdd = 0;
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
    private class ReviewSimulator {

        private final DB mDb;
        private final EaseClassifier mClassifier;

        //TODO: also exists in Review
        private final int mNTimeBins;
        private final int mTimeBinLength;

        private final int mTMax;

        private final NewCardSimulator mNewCardSimulator = new NewCardSimulator();

        public ReviewSimulator(DB db, EaseClassifier classifier, int nTimeBins, int timeBinLength) {
            this.mDb = db;
            this.mClassifier = classifier;

            this.mNTimeBins = nTimeBins;
            this.mTimeBinLength = timeBinLength;

            this.mTMax = this.mNTimeBins * this.mTimeBinLength;
        }

        public SimulationResult simNreviews(int today, DeckManager decks, String didsStr, TodayStats todayStats) {

            SimulationResult simulationResultAggregated = new SimulationResult(mNTimeBins, mTimeBinLength, SimulationResult.DOUBLE_TO_INT_MODE_ROUND);

            long[] dids = mArrayUtils.stringToLongArray(didsStr);
            int nIterations = mSettings.getSimulateNIterations();
            double nIterationsInv = 1.0 / nIterations;

            for(long did : dids) {
                for(int iteration = 0; iteration < nIterations; iteration++) {
                    mNewCardSimulator.reset(todayStats.getNLearned(did));
                    simulationResultAggregated.add(simNreviews(today, mDecks.createDeck(did, decks)), nIterationsInv);
                }
            }

            return simulationResultAggregated;

        }

        private SimulationResult simNreviews(int today, Deck deck) {

            SimulationResult simulationResult;

            //we schedule a review if the number of reviews has not yet reached the maximum # reviews per day
            //If we compute the simulationresult, we keep track of the average number of reviews
            //Since it's the average, it can be a non-integer
            //Adding a review to a non-integer can make it exceed the maximum # reviews per day, but not by 1 or more
            //So if we take the floor when displaying it, we will display the maximum # reviews
            if(mSettings.getComputeNDays() > 0)
                simulationResult = new SimulationResult(mNTimeBins, mTimeBinLength, SimulationResult.DOUBLE_TO_INT_MODE_FLOOR);
            else
                simulationResult = new SimulationResult(mNTimeBins, mTimeBinLength, SimulationResult.DOUBLE_TO_INT_MODE_ROUND);

            //nSmooth=1

            //TODO:
            //Forecasted final state of deck
            //finalIvl = np.empty((nSmooth, nCards), dtype='f8')

            Timber.d("today: %d", today);

            Stack<Review> reviews = new Stack<>();
            ArrayList<Review> reviewList = new ArrayList<>();

            //By having simulateReview add future reviews depending on which simulation of this card this is (the nth) we can:
            //1. Do monte carlo simulation if we add nIterations future reviews if n = 1
            //   We don't do it this way. Instead we do this by having tis method [simNreviews] called nIterations times.
            //   The reason is that in that way we take into account the dependency between cards correctly, since we do
            //   for each iteration... for each card
            //   If we would do for each card... for each iteration... we would not take it into account correctly.
            //   We would not schedule new cards on a particular day if on average the new card limit would have been exceeded
            //   in simulations of previous cards.
            //2. Do a complete traversal of the future reviews tree if we add k future reviews for all n
            //   We accept the drawback as mentioned in (1).
            //3. Do any combination of these (controlled by computeNDays and computeMaxError)

            Card card = new Card(0, 0, 0, 0, 0, 0);
            CardIterator cardIterator = null;
            Review review = new Review(deck, simulationResult, mClassifier, reviews, reviewList);

            try {
                cardIterator = new CardIterator(mDb, today, deck);

                //int cardN = 0;

                while (cardIterator.moveToNext()) {

                    cardIterator.current(card);

                    review.newCard(card, mNewCardSimulator);

                    if (review.getT() < mTMax)
                        reviews.push(review);

                    //Timber.d("Card started: %d", cardN);

                    while (!reviews.isEmpty()) {
                        reviews.pop().simulateReview();
                    }

                    //Timber.d("Card done: %d", cardN++);

                }
            }
            finally {
                if(cardIterator != null)
                    cardIterator.close();
            }
            mArrayUtils.formatMatrix("nReviews", simulationResult.getNReviews(), "%04d ");
            mArrayUtils.formatMatrix("nInState", simulationResult.getNInState(), "%04d ");

            return simulationResult;
        }
    }

    /**
     * Stores global settings.
     */
    private static class Settings {

        private final int mComputeNDays;
        private final double mComputeMaxError;
        private final int mSimulateNIterations;
        private final Collection mCol;

        public Settings(Context context) {
            SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(context);
            mCol = CollectionHelper.getInstance().getCol(context);

            mComputeNDays = prefs.getInt("advanced_forecast_stats_compute_n_days", 0);
            int computePrecision = prefs.getInt("advanced_forecast_stats_compute_precision", 90);
            mComputeMaxError = (100-computePrecision)/100.0;

            mSimulateNIterations = prefs.getInt("advanced_forecast_stats_mc_n_iterations", 1);

            Timber.d("computeNDays: %s", mComputeNDays);
            Timber.d("computeMaxError: %s", mComputeMaxError);
            Timber.d("simulateNIterations: %s", mSimulateNIterations);
        }

        public int getComputeNDays() {
            return mComputeNDays;
        }

        public double getComputeMaxError() {
            return mComputeMaxError;
        }

        public int getSimulateNIterations() {
            return mSimulateNIterations;
        }

        /**
         * @return Maximum number of new cards per day which will be used if it cannot be read from Deck settings.
         */
        public int getMaxNewPerDay() {
            return 20;
        }

        /**
         * @return Maximum number of reviews per day which will be used if it cannot be read from Deck settings.
         */
        public int getMaxReviewsPerDay() {
            return 10000;
        }

        /**
         *
         * @return Factor which will be used if it cannot be read from Deck settings.
         */
        public int getInitialFactor() {
            return Consts.STARTING_FACTOR;
        }

        /**
         * Today.
         * @param collectionCreatedTime The difference, measured in seconds, between midnight, January 1, 1970 UTC and the time at which the collection was created.
         * @return Today in days counted from the time at which the collection was created
         */
        public int getToday(long collectionCreatedTime) {
            Timber.d("Collection creation timestamp: %d", collectionCreatedTime);

            long currentTime = mCol.getTime().intTime();
            Timber.d("Now: %d", currentTime);
            return (int) ((currentTime - collectionCreatedTime) / SECONDS_PER_DAY);
        }

        /**
         * Beginning of today.
         * @param collectionCreatedTime The difference, measured in seconds, between midnight, January 1, 1970 UTC and the time at which the collection was created.
         * @return The beginning of today in milliseconds counted from the time at which the collection was created
         */
        public long getDayStartCutoff (long collectionCreatedTime) {
            long today = getToday(collectionCreatedTime);
            return (collectionCreatedTime + (today * SECONDS_PER_DAY)) * 1000;
        }
    }

    private class ArrayUtils {
        public int[][] createIntMatrix(int m, int n) {
            int[][] matrix = new int[m][];
            for(int i=0; i<m; i++) {
                matrix[i] = new int[n];
                for(int j=0; j<n; j++)
                    matrix[i][j] = 0;
            }

            return matrix;
        }

        public int[][] toIntMatrix(double[][] doubleMatrix, int doubleToIntMode) {
            int m = doubleMatrix.length;
            if(m == 0)
                return new int[0][];
            int n = doubleMatrix[1].length;

            int[][] intMatrix = new int[m][];
            for(int i=0; i<m; i++) {
                intMatrix[i] = new int[n];
                for(int j=0; j<n; j++) {
                    if (doubleToIntMode == SimulationResult.DOUBLE_TO_INT_MODE_ROUND)
                        intMatrix[i][j] = (int) Math.round(doubleMatrix[i][j]);
                    else
                        intMatrix[i][j] = (int) doubleMatrix[i][j];
                }
            }

            return intMatrix;
        }

        public double[][] createDoubleMatrix(int m, int n) {
            double[][] matrix = new double[m][];
            for(int i=0; i<m; i++) {
                matrix[i] = new double[n];
                for(int j=0; j<n; j++)
                    matrix[i][j] = 0;
            }

            return matrix;
        }

        public<T> T[] append(T[] arr, T element, int n) {
            final int N0 = arr.length;
            final int N1 = N0 + n;
            arr = Arrays.copyOf(arr, N1);
            for(int N = N0; N < N1; N++)
                arr[N] = element;
            return arr;
        }

        public int nRows(int[][] matrix) {
            return matrix.length;
        }

        public int nCols(int[][] matrix) {
            if(matrix.length == 0)
                return 0;
            return matrix[0].length;
        }

        public long[] stringToLongArray(String s) {

            String[] split = s.substring(1, s.length() - 1).split(", ");

            long[] arr = new long[split.length];
            for(int i = 0; i<split.length; i++)
                arr[i] = Long.parseLong(split[i]);

            return arr;
        }

        public int[][] transposeMatrix(int[][] matrix) {
            if (matrix.length == 0)
                return matrix;

            int m = matrix.length;
            int n = matrix[0].length;

            int[][] transpose = new int[n][m];

            int c, d;
            for ( c = 0 ; c < m ; c++ )
            {
                for ( d = 0 ; d < n ; d++ )
                    transpose[d][c] = matrix[c][d];
            }

            return transpose;
        }


        public double[][] transposeMatrix(double[][] matrix) {
            if (matrix.length == 0)
                return matrix;

            int m = matrix.length;
            int n = matrix[0].length;

            double[][] transpose = new double[n][m];

            int c, d;
            for ( c = 0 ; c < m ; c++ )
            {
                for ( d = 0 ; d < n ; d++ )
                    transpose[d][c] = matrix[c][d];
            }

            return transpose;
        }

        public void formatMatrix(String matrixName, int[][] matrix, String format) {
            StringBuilder s = new StringBuilder();

            s.append(matrixName);
            s.append(":");
            s.append(System.getProperty("line.separator"));

            for (int[] aMatrix : matrix) {
                for (int i : aMatrix) {
                    s.append(String.format(format, i));
                }
                s.append(System.getProperty("line.separator"));
            }

            Timber.d(s.toString());
        }
    }

    /**
     * Statistics generated by simulations of Reviews.
     */
    private class SimulationResult {

        public static final int DOUBLE_TO_INT_MODE_FLOOR = 0;
        public static final int DOUBLE_TO_INT_MODE_ROUND = 1;

        private final int mDoubleToIntMode;

        private final int mNTimeBins;
        private final int mTimeBinLength;

        private final int mNDays;

        /**
         * Forecasted number of reviews per time bin (a time bin contains statistics for 1 or a multiple of days)
         * First dimension:
         * 0 = Learn
         * 1 = Young
         * 2 = Mature
         * 3 = Relearn
         * Second dimension: time
         */
        private final double[][] mNReviews;

        /**
         * Forecasted number of reviews per day.
         * @see #mNReviews
         */
        private final double[][] mNReviewsPerDay;

        /**
         * Forecasted number of cards per state
         * First dimension:
         * 0 = New
         * 1 = Young
         * 2 = Mature
         * Second dimension: time
         */
        private final double[][] mNInState;

        /**
         * Create an empty SimulationResult.
         * @param nTimeBins Number of time bins.
         * @param timeBinLength Length of 1 time bin in days.
         */
        public SimulationResult(int nTimeBins, int timeBinLength, int doubleToIntMode) {
            mNReviews = mArrayUtils.createDoubleMatrix(REVIEW_TYPE_COUNT, nTimeBins);
            mNReviewsPerDay = mArrayUtils.createDoubleMatrix(REVIEW_TYPE_COUNT, nTimeBins * timeBinLength);
            mNInState = mArrayUtils.createDoubleMatrix(CARD_TYPE_COUNT, nTimeBins);

            this.mNTimeBins = nTimeBins;
            this.mTimeBinLength = timeBinLength;
            this.mNDays = nTimeBins * timeBinLength;

            this.mDoubleToIntMode = doubleToIntMode;
        }

        public int getNDays() {
            return mNDays;
        }

        /**
         * Adds the statistics generated by another simulation to the current statistics.
         * Use to gather statistics over decks.
         * @param res2Add Statistics to be added to the current statistics.
         */
        public void add(SimulationResult res2Add, double prob) {

            int[][] nReviews = res2Add.getNReviews();
            int[][] nInState = res2Add.getNInState();

            for(int i = 0; i < nReviews.length; i++)
                for(int j = 0; j < nReviews[i].length; j++)
                    this.mNReviews[i][j] += nReviews[i][j] * prob;

            //This method is only used to aggregate over decks
            //We do not update nReviewsPerDay since it is not needed for the SimulationResult aggregated over decks.

            for(int i = 0; i < nInState.length; i++)
                for(int j = 0; j < nInState[i].length; j++)
                    this.mNInState[i][j] += nInState[i][j] * prob;
        }

        public int[][] getNReviews() {
            return mArrayUtils.toIntMatrix(mNReviews, mDoubleToIntMode);
        }

        public int[][] getNInState() {
            return mArrayUtils.toIntMatrix(mNInState, mDoubleToIntMode);
        }

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
        public int nReviewsDoneToday(int tElapsed) {
            return (int)(mNReviewsPerDay[REVIEW_TYPE_YOUNG][tElapsed] +
                         mNReviewsPerDay[REVIEW_TYPE_MATURE][tElapsed]);
        }

        /**
         * Increment the count 'number of reviews of card with type cardType' with one at day t.
         * @param cardType  Card type
         * @param t Day for which to increment
         */
        public void incrementNReviews(int cardType, int t, double prob) {
            mNReviews[cardType][t / mTimeBinLength]+= prob;
            mNReviewsPerDay[cardType][t]+= prob;
        }

        /**
         * Increment the count 'number of cards in the state of the given card' with one between tFrom and tTo.
         * @param card Card from which to read the state.
         * @param tFrom The first day for which to update the state.
         * @param tTo The day after the last day for which to update the state.
         */
        public void updateNInState(Card card, int tFrom, int tTo, double prob) {
            int cardType = card.getType();

            int t0 = tFrom / mTimeBinLength;
            int t1 = tTo / mTimeBinLength;

            for(int t = t0; t < t1; t++)
                if(t < mNTimeBins) {
                    mNInState[cardType][t]+= prob;
                } else {
                    return;
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
         * @see #updateNInState(Card, int, int, double)
         */
        public void updateNInState(Card prevCard, Card card, int tFrom, int tTo, double prob) {
            int lastReview = prevCard.getLastReview();

            int prevCardType = prevCard.getType();
            int cardType = card.getType();

            int t0 = tFrom / mTimeBinLength;
            int t1 = Math.min(lastReview, tTo) / mTimeBinLength;

            //Replace state set during last review
            for(int t = t0; t < t1; t++)
                if(t < mNTimeBins) {
                    mNInState[prevCardType][t]-= prob;
                } else {
                    break;
                }

            t1 = tTo / mTimeBinLength;

            //With state set during new review
            for(int t = t0; t < t1; t++)
                if(t < mNTimeBins) {
                    mNInState[cardType][t]+=prob;
                } else {
                    return;
                }

            //Alternative solution would be to keep this count for each day instead of keeping it for each bin and aggregate in the end
            //to a count for each bin.
            //That would also work because we do not simulate two reviews of one card at one and the same day.
        }
    }

    private static class PlottableSimulationResult {

        // Forecasted number of reviews
        // ArrayList: time
        // int[]:
        //   0 = Time
        //   1 = Learn
        //   2 = Young
        //   3 = Mature
        //   4 = Relearn
        private final ArrayList<int[]> mNReviews;

        // Forecasted number of cards per state
        // First dimension:
        //   0 = Time
        //   4 = New
        //   3 = Young
        //   2 = Mature
        //   1 = Zeros (we can't say 'we know x relearn cards on day d')
        // Second dimension: time
        private final double[][] mNInState;

        public PlottableSimulationResult(ArrayList<int[]> nReviews, double[][] nInState) {
            this.mNReviews = nReviews;
            this.mNInState = nInState;
        }

        public ArrayList<int[]> getNReviews() {
            return mNReviews;
        }

        public double[][] getNInState() {
            return mNInState;
        }
    }

    /**
     * A review has a particular outcome with a particular probability.
     * A review results in the state of the card (card interval) being changed.
     * A ReviewOutcome bundles the probability of the outcome and the card with changed state.
     */
    private static class ReviewOutcome {
        private Card mCard;
        private double mProb;

        public ReviewOutcome(Card card, double prob) {
            this.mCard = card;
            this.mProb = prob;
        }

        public void setAll(Card card, double prob) {
            this.mCard = card;
            this.mProb = prob;
        }

        public Card getCard() {
            return mCard;
        }

        public double getProb() {
            return mProb;
        }

        @Override
        public @NonNull String toString() {
            return "ReviewOutcome{" +
                    "card=" + mCard +
                    ", prob=" + mProb +
                    '}';
        }
    }

    /**
     * Bundles the information needed to simulate a review and the objects affected by the review.
     */
    private class Review {

        /**
         * Deck-specific setting stored separately to save a method call on the deck object)
         */
        private final int mMaxReviewsPerDay;

        /**
         * Number of reviews simulated for this card at time < tElapsed
         */
        private int mNPrevRevs;

        /**
         * The probability that the outcomes of the reviews simulated for this card at time < tElapsed are such that
         * this review [with this state of the card] will occur [at this time (tElapsed)].
         */
        private double mProb;

        /**
         * The time instant at which the review takes place.
         */
        private int mTElapsed;

        /**
         * The outcome of the review.
         * We still have to do the review if the outcome has already been specified
         * (to update statistics, deterime probability of specified outcome, and to schedule subsequent reviews)
         * Only relevant if we are computing (all possible review outcomes), not if simulating (only one possible outcome)
         */
        private int mOutcome;

        /**
         * Deck-specific settings
         */
        private final Deck mDeck;

        /**
         * State of the card before current review.
         * Needed to schedule current review but with different outcome and to update statistics.
         */
        private Card mCard = new Card(0, 0, 0, 0, 0, 0);
        private final Card mPrevCard = new Card(0, 0, 0, 0, 0, 0);

        /**
         * State of the card after current review.
         * Needed to schedule future review.
         */
        private Card mNewCard = new Card(0, 0, 0, 0, 0, 0);

        /**
         * Statistics
         */
        private final SimulationResult mSimulationResult;

        /**
         * Classifier which uses probability distribution from review log to predict outcome of review.
         */
        private final EaseClassifier mClassifier;

        /**
         * Reviews which are scheduled to be simulated.
         * For adding current review with other outcome and future review.
         */
        private final Stack<Review> mReviews;

        /**
         * Review objects to be re-used so that we don't have to create new Review objects all the time.
         * Be careful: it also contains Review objects which are still in use.
         * So the algorithm using this list has to make sure that it only re-uses Review objects which are not in use anymore.
         */
        private final List<Review> mReviewlist;

        /**
         * For creating future reviews which are to be scheduled as a result of the current review.
         * @see Review(Deck, SimulationResult, EaseClassifier, Stack<Review>)
         */
        private Review (Review prevReview, Card card, int nPrevRevs, int tElapsed, double prob) {
            this.mDeck = prevReview.mDeck;
            this.mCard.setAll(card);
            this.mSimulationResult = prevReview.mSimulationResult;
            this.mClassifier = prevReview.mClassifier;
            this.mReviews = prevReview.mReviews;
            this.mReviewlist = prevReview.mReviewlist;

            this.mNPrevRevs = nPrevRevs;
            this.mTElapsed = tElapsed;
            this.mProb = prob;

            this.mMaxReviewsPerDay = mDeck.getRevPerDay();
        }

        /**
         * For creating a review which is to be scheduled.
         * After this constructor, either @see newCard(Card, NewCardSimulator) or existingCard(Card, int, int, double) has to be called.
         * @param deck Information needed to simulate a review: deck settings.
         *             Will be affected by the review. After the review it will contain the card type etc. after the review.
         * @param simulationResult Will be affected by the review. After the review it will contain updated statistics.
         * @param classifier Information needed to simulate a review: transition probabilities to new card state for each possible current card state.
         * @param reviews Will be affected by the review. Scheduled future reviews of this card will be added.
         */
        public Review(Deck deck, SimulationResult simulationResult, EaseClassifier classifier, Stack<Review> reviews, List<Review> reviewList) {
            this.mDeck = deck;
            this.mSimulationResult = simulationResult;
            this.mClassifier = classifier;
            this.mReviews = reviews;
            this.mReviewlist = reviewList;

            this.mMaxReviewsPerDay = deck.getRevPerDay();
        }

        /**
         * Re-use the current review object to schedule a new card. A new card here means that it has not been reviewed yet.
         * @param card Information needed to simulate a review: card due date, type and factor.
         * @param newCardSimulator Information needed to simulate a review: The next day new cards will be added and the number of cards already added on that day.
         *                         Will be affected by the review. After the review of a new card, the number of cards added on that day will be updated.
         *                         Next day new cards will be added might be updated if new card limit has been reached.
         */
        public void newCard(Card card, NewCardSimulator newCardSimulator) {
            this.mCard = card;

            this.mNPrevRevs = 0;
            this.mProb = 1;
            this.mOutcome = 0;

            //# Rate-limit new cards by shifting starting time
            if (card.getType() == CARD_TYPE_NEW)
                mTElapsed = newCardSimulator.simulateNewCard(mDeck);
            else
                mTElapsed = card.getDue();

            // Set state of card between start and first review
            // New reviews happen with probability 1
            this.mSimulationResult.updateNInState(card, 0, mTElapsed, 1);
        }

        /**
         * Re-use the current review object to schedule an existing card. An existing card here means that it has been reviewed before (either by the user or by the simulation)
         * and hence the due date is known.
         */
        private void existingCard(Card card, int nPrevRevs, int tElapsed, double prob) {
            this.mCard.setAll(card);

            this.mNPrevRevs = nPrevRevs;
            this.mTElapsed = tElapsed;
            this.mProb = prob;
            this.mOutcome = 0;
        }

        /**
         * Simulates one review of the card. The review results in:
         * - The card (prevCard and newCard) being updated
         * - New card simulator (when to schedule next new card) being updated if the card was new
         * - The simulationResult being updated.
         * - New review(s) being scheduled.
         */
        public void simulateReview() {

            if(mCard.getType() == CARD_TYPE_NEW || mSimulationResult.nReviewsDoneToday(mTElapsed) < mMaxReviewsPerDay || mOutcome > 0) {
                // Update the forecasted number of reviews
                if(mOutcome == 0)
                    mSimulationResult.incrementNReviews(mCard.getType(), mTElapsed, mProb);

                // Simulate response
                mPrevCard.setAll(mCard);
                mNewCard.setAll(mCard);

                ReviewOutcome reviewOutcome;
                if(mTElapsed >= mSettings.getComputeNDays() || mProb < mSettings.getComputeMaxError())
                    reviewOutcome = mClassifier.simSingleReview(mNewCard);
                else
                    reviewOutcome = mClassifier.simSingleReview(mNewCard, mOutcome);

                //Timber.d("Simulation at t=" + tElapsed + ": outcome " + outcomeIdx + ": " + reviewOutcome.toString() );

                mNewCard = reviewOutcome.getCard();
                double outcomeProb = reviewOutcome.getProb();

                //writeLog(newCard, outcomeProb);

                mNewCard.setLastReview(mTElapsed);

                // If card failed, update "relearn" count
                if(mNewCard.getCorrect() == 0)
                    mSimulationResult.incrementNReviews(3, mTElapsed, mProb * outcomeProb);

                // Set state of card between current and next review
                mSimulationResult.updateNInState(mPrevCard, mNewCard, mTElapsed, mTElapsed + mNewCard.getIvl(), mProb * outcomeProb);

                // Schedule current review, but with other outcome
                if(outcomeProb < 1.0 && mOutcome < 3)
                    scheduleCurrentReview(mPrevCard);

                // Advance time to next review
                scheduleNextReview(mNewCard, mTElapsed + mNewCard.getIvl(), mProb * outcomeProb);
            }
            else {
                // Advance time to next review (max. #reviews reached for this day)
                mSimulationResult.updateNInState(mCard, mCard, mTElapsed, mTElapsed + 1, mProb);
                rescheduleCurrentReview(mTElapsed + 1);
            }
        }

        private void writeLog(Card newCard, double outcomeProb) {
            String tabs = "";
            for(int d = 0; d< mNPrevRevs; d++)
                tabs += "\t";
            Timber.d("%st=%d p=%f * %s", tabs, mTElapsed, mProb, outcomeProb);
            Timber.d("%s%s",tabs , mPrevCard);
            Timber.d("%s%s", tabs, newCard);
        }

        /**
         * Schedule the current review at another time (will re-use current Review).
         */
        private void rescheduleCurrentReview(int newTElapsed) {
            if (newTElapsed < mSimulationResult.getNDays()) {
                this.mTElapsed = newTElapsed;
                this.mReviews.push(this);
            }
        }

        /**
         * Schedule the current review at the current time, but with another outcome (will re-use current Review).
         * @param newCard
         */
        private void scheduleCurrentReview(Card newCard) {
            this.mCard.setAll(newCard);
            this.mOutcome++;
            this.mReviews.push(this);
        }

        /**
         * Schedule next review (will not re-use current Review).
         */
        private void scheduleNextReview(Card newCard, int newTElapsed, double newProb) {
            //Schedule next review(s) if they are within the time window of the simulation
            if (newTElapsed < mSimulationResult.getNDays()) {
                Review review;
                //Re-use existing instance of the review object (to limit memory usage and prevent time taken by garbage collector)
                //This is possible since reviews with nPrevRevs > nPrevRevs of the current review which were already scheduled have all already been processed before we do the current review.
                if(mReviewlist.size() > mNPrevRevs) {
                    review = mReviewlist.get(mNPrevRevs);
                    review.existingCard(newCard, mNPrevRevs + 1, newTElapsed, newProb);
                }
                else {
                    if(mReviewlist.size() == mNPrevRevs) {
                        review = new Review(this, newCard, mNPrevRevs + 1, newTElapsed, newProb);
                        mReviewlist.add(review);
                    }
                    else {
                        throw new IllegalStateException("State of previous reviews of this card should have been saved for determining possible future reviews other than the current one.");
                    }
                }

                this.mReviews.push(review);
            }
        }

        public int getT() {
            return mTElapsed;
        }
    }

}
