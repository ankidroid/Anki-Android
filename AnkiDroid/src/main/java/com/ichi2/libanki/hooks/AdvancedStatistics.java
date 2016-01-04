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

package com.ichi2.libanki.hooks;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.stats.StatsMetaInfo;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Stats;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Stack;

import timber.log.Timber;

/**
 * Display forecast statistics based on a simulation of future reviews.
 */
public class AdvancedStatistics extends Hook  {

    private final Settings Settings = new Settings();
    private final ArrayUtils ArrayUtils = new ArrayUtils();
    private final DeckFactory Decks = new DeckFactory();

    @Override
    public Object runFilter(Object arg, Object... args) {
        return calculateDueOriginal((StatsMetaInfo) arg, (int) args[0], (Context) args[1], (String) args[2]);
    }
    public static void install(Hooks h) {
        h.addHook("advancedStatistics", new AdvancedStatistics());
    }
    public static void uninstall(Hooks h) {
        h.remHook("advancedStatistics", new AdvancedStatistics());
    }

    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    //TODO: pass mCol?
    private StatsMetaInfo calculateDueOriginal(StatsMetaInfo metaInfo, int type, Context context, String dids) {

        metaInfo.setStatsCalculated(true);

        Collection mCol = CollectionHelper.getInstance().getCol(context);

        double[][] mSeriesList;

        int mType;
        int mTitle;
        int[] mValueLabels;
        int[] mColors;
        int[] mAxisTitles;
        int mMaxCards = 0;
        int mMaxElements;
        double mFirstElement;
        double mLastElement = 0;
        int mZeroIndex = 0;
        double[][] mCumulative;
        double mMcount;

        mType = type;
        mTitle = R.string.stats_forecast;

        //JPR
        //mValueLabels = new int[] { R.string.statistics_young, R.string.statistics_mature };
        //mColors = new int[] { R.color.stats_young, R.color.stats_mature };

        mValueLabels = new int[] { R.string.statistics_relearn,
                R.string.statistics_mature,
                R.string.statistics_young,
                R.string.statistics_learn};
        mColors = new int[] {      R.color.stats_relearn,
                R.color.stats_mature,
                R.color.stats_young,
                R.color.stats_learn};
        //JPR end

        mAxisTitles = new int[] { type, R.string.stats_cards, R.string.stats_cumulative_cards };

        //JPR (moved to own method + replace with new method)
        //ArrayList<int[]> dues = calculateDues(type);
        PlottableSimulationResult simuationResult = calculateDuesWithSimulation(type, mCol, dids);

        ArrayList<int[]> dues = simuationResult.getNReviews();

        //mSeriesList = new double[3][dues.size()];
        mSeriesList = new double[5][dues.size()];
        //JPR end

        for (int i = 0; i < dues.size(); i++) {
            int[] data = dues.get(i);

            //JPR
            //if(data[1] > mMaxCards)
            //    mMaxCards =data[1];                                           //Y-Axis: Max. value
            if(data[1] + data[2] + data[3] + data[4] > mMaxCards)
                mMaxCards = data[1] + data[2] + data[3] + data[4];

            //mSeriesList[0][i] = data[0];                                      //X-Axis: Day / Week / Month
            //mSeriesList[1][i] = data[1];                                      //Y-Axis: # Cards
            //mSeriesList[2][i] = data[2];                                      //Y-Axis: # Mature cards

            //   1 +
            //   0 = Learn
            //   1 = Young
            //   2 = Mature
            //   3 = Relearn
            mSeriesList[0][i] = data[0];                                        //X-Axis: Day / Week / Month
            mSeriesList[1][i] = data[1] + data[2] + data[3] + data[4];          //Y-Axis: # Cards
            mSeriesList[2][i] = data[1] + data[2] + data[3];                    //Y-Axis: # Mature cards
            mSeriesList[3][i] = data[1] + data[2];                              //Y-Axis: # Young
            mSeriesList[4][i] = data[1];                                        //Y-Axis: # Learn

            //JPR end

            if(data[0] > mLastElement)
                mLastElement = data[0];         //X-Axis: Max. value (only for TYPE_LIFE)
            if(data[0] == 0){
                mZeroIndex = i;                 //Because we retrieve dues in the past and we should not cumulate them
            }
        }
        mMaxElements = dues.size()-1;           //# X values
        switch (mType) {
            case Stats.TYPE_MONTH:
                mLastElement = 31;              //X-Axis: Max. value
                break;
            case Stats.TYPE_YEAR:
                mLastElement = 52;              //X-Axis: Max. value
                break;
            default:
        }
        mFirstElement = 0;                      //X-Axis: Min. value

        //mCumulative = Stats.createCumulative(new double[][]{mSeriesList[0], mSeriesList[1]}, mZeroIndex);   //Day starting at mZeroIndex, Cumulative # cards
        mCumulative = simuationResult.getNInState();

        //mMcount = mCumulative[1][mCumulative[1].length-1];                                                  //Y-Axis: Max. cumulative value
        mMcount = mCumulative[1][mCumulative[1].length-1] +                                                   //Y-Axis: Max. cumulative value
                  mCumulative[2][mCumulative[2].length-1] +
                  mCumulative[3][mCumulative[3].length-1] +
                  mCumulative[4][mCumulative[4].length-1];

        //some adjustments to not crash the chartbuilding with empty data
        if(mMaxElements == 0){
            mMaxElements = 10;
        }
        if(mMcount == 0){
            mMcount = 10;
        }
        if(mFirstElement == mLastElement){
            mFirstElement = 0;
            mLastElement = 6;
        }
        if(mMaxCards == 0)
            mMaxCards = 10;

        metaInfo.setmDynamicAxis(true);
        metaInfo.setmHasColoredCumulative(true);
        metaInfo.setmType(mType);
        metaInfo.setmTitle(mTitle);
        metaInfo.setmBackwards(false);
        metaInfo.setmValueLabels(mValueLabels);
        metaInfo.setmColors(mColors);
        metaInfo.setmAxisTitles(mAxisTitles);
        metaInfo.setmMaxCards(mMaxCards);
        metaInfo.setmMaxElements(mMaxElements);
        metaInfo.setmFirstElement(mFirstElement);
        metaInfo.setmLastElement(mLastElement);
        metaInfo.setmZeroIndex(mZeroIndex);
        metaInfo.setmCumulative(mCumulative);
        metaInfo.setmMcount(mMcount);

        metaInfo.setmSeriesList(mSeriesList);

        metaInfo.setDataAvailable(dues.size() > 0);

        return metaInfo;
    }

    private PlottableSimulationResult calculateDuesWithSimulation(int type, Collection mCol, String dids) {
        int end = 0;
        int chunk = 0;
        switch (type) {
            case Stats.TYPE_MONTH:
                end = 31;
                chunk = 1;
                break;
            case Stats.TYPE_YEAR:
                end = 52;
                chunk = 7;
                break;
            case Stats.TYPE_LIFE:
                end = 24;
                chunk = 30;
                break;
        }

        ArrayList<int[]> dues = new ArrayList<>();

        EaseClassifier classifier = new EaseClassifier(mCol.getDb().getDatabase());
        ReviewSimulator reviewSimulator = new ReviewSimulator(mCol.getDb().getDatabase(), classifier, end, chunk);

        SimulationResult simulationResult = reviewSimulator.simNreviews(Settings.getToday((int)mCol.getCrt()), mCol.getDecks(), dids);

        int[][] nReviews = ArrayUtils.transposeMatrix(simulationResult.getNReviews());
        int[][] nInState = ArrayUtils.transposeMatrix(simulationResult.getNInState());

        //Append row with zeros and transpose to make it the same dimension as nReviews
        //int[][] nInState = simulationResult.getNInState();
        //if(ArrayUtils.nCols(nInState) > 0)
        //    nInState = ArrayUtils.append(nInState, new int[ArrayUtils.nCols(nInState)], 1);

        // Forecasted number of reviews
        //   0 = Learn
        //   1 = Young
        //   2 = Mature
        //   3 = Relearn

        for(int i = 0; i<nReviews.length; i++) {
            dues.add(new int[] { i,  nReviews[i][0], nReviews[i][1], nReviews[i][2], nReviews[i][3] });
        }

        // small adjustment for a proper chartbuilding with achartengine
        if (dues.size() == 0 || dues.get(0)[0] > 0) {
            dues.add(0, new int[] { 0, 0, 0, 0, 0 });
        }
        if (type == Stats.TYPE_LIFE && dues.size() < 2) {
            end = 31;
        }
        if (type != Stats.TYPE_LIFE && dues.get(dues.size() - 1)[0] < end) {
            dues.add(new int[] { end, 0, 0, 0, 0 });
        } else if (type == Stats.TYPE_LIFE && dues.size() < 2) {
            dues.add(new int[] { Math.max(12, dues.get(dues.size() - 1)[0] + 1), 0, 0, 0, 0 });
        }

        double[][] nInStateCum = new double[dues.size()][];

        for(int i = 0; i<dues.size(); i++) {

            if(i < nInState.length) {
                nInStateCum[i] = new double[] {
                        i,
                        0,
                        //nInState[i][0] + nInState[i][1] + nInState[i][2], //Y-Axis: New + Young + Mature
                        //nInState[i][0] + nInState[i][1],                  //Y-Axis: New + Young
                        //nInState[i][0],                                   //Y-Axis: New
                        nInState[i][2],                                     //Y-Axis: New + Young + Mature
                        nInState[i][1],                                     //Y-Axis: New + Young
                        nInState[i][0],                                     //Y-Axis: New
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

        return new PlottableSimulationResult(dues, ArrayUtils.transposeMatrix(nInStateCum));
    }

    private class Card {
        private int ivl;
        private double factor;
        private int lastReview;
        private int due;
        private int correct;
        private final long id;

        @Override
        public String toString() {
            return "Card [ivl=" + ivl + ", factor=" + factor + ", due=" + due + ", correct=" + correct + ", id="
                    + id + "]";
        }

        public Card(long id, int ivl, int factor, int due, int correct, int lastReview) {
            super();
            this.id = id;
            this.ivl = ivl;
            this.factor = factor / 1000.0;
            this.due = due;
            this.correct = correct;
            this.lastReview = lastReview;
        }

        public Card clone() {
            return new Card(id, ivl, (int) (factor * 1000), due, correct, lastReview);
        }

        public long getId() {
            return id;
        }

        public int getIvl() {
            return ivl;
        }

        public void setIvl(int ivl) {
            this.ivl = ivl;
        }

        public double getFactor() {
            return factor;
        }

        public void setFactor(double factor) {
            this.factor = factor;
        }

        public int getDue() {
            return due;
        }

        public void setDue(int due) {
            this.due = due;
        }

        public int getType() {
            //# 0=new, 1=Young, 2=mature
            if(ivl == 0) {
                return 0;
            } else if (ivl >= 21) {
                return 2;
            } else {
                return 1;
            }
        }

        public int getCorrect() {
            return correct;
        }

        public void setCorrect(int correct) {
            this.correct = correct;
        }

        public int getLastReview() {
            return lastReview;
        }

        public void setLastReview(int lastReview) {
            this.lastReview = lastReview;
        }
    }

    private class DeckFactory {

        public Deck createDeck(long did, Decks decks) {

            Timber.d("Trying to get deck settings for deck with id=" + did);

            JSONObject conf = decks.confForDid(did);

            int newPerDay = Settings.getMaxNewPerDay();
            int revPerDay = Settings.getMaxReviewsPerDay();

            try {
                if (conf.getInt("dyn") == 0) {
                    revPerDay = conf.getJSONObject("rev").getInt("perDay");
                    newPerDay = conf.getJSONObject("new").getInt("perDay");

                    Timber.d("rev.perDay=" + revPerDay);
                    Timber.d("new.perDay=" + newPerDay);
                } else {
                    Timber.d("dyn=" + conf.getInt("dyn"));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            return new Deck(did, newPerDay, revPerDay);
        }
    }

    private class Deck {

        private long did;

        private int newPerDay;
        private int revPerDay;

        public Deck(long did, int newPerDay, int revPerDay) {
            this.did = did;
            this.newPerDay = newPerDay;
            this.revPerDay = revPerDay;
        }

        public long getDid() {
            return did;
        }

        public int getNewPerDay() {
            return newPerDay;
        }

        public int getRevPerDay() {
            return revPerDay;
        }

    }

    private class CardIterator {

        Cursor cur;

        private final int today;

        public CardIterator(SQLiteDatabase db, int today, Long did) {

            this.today = today;

            String query;
            query = "SELECT id, due, ivl, factor, type, reps " +
                    "FROM cards " +
                    "WHERE did IN (" + did + ") " +
                    "order by id;";
            Timber.d("Forecast query: %s", query);
            cur = db.rawQuery(query, null);

        }

        public boolean moveToNext() {
            return cur.moveToNext();
        }

        public Card current() {

            return new Card(cur.getLong(0),                                    //Id
                    cur.getInt(5) == 0 ? 0 : cur.getInt(2),  		           //reps = 0 ? 0 : card interval
                    cur.getInt(3) > 0 ? cur.getInt(3) :  2500,                 //factor
                    Math.max(cur.getInt(1) - today, 0),                        //due
                    1,                                                         //correct
                    -1                                                         //lastreview
                    );
        }

        public void close() {
            if (cur != null && !cur.isClosed())
                cur.close();
        }
    }

    private class EaseClassifier {

        private final Random random;

        private final SQLiteDatabase db;
        private final double[][] probabilitiesCumulative;

        //# Prior that half of new cards are answered correctly
        private final int[] priorNew = {5, 0, 5, 0};		//half of new cards are answered correctly
        private final int[] priorYoung = {1, 0, 9, 0};	//90% of young cards get "good" response
        private final int[] priorMature = {1, 0, 9, 0};	//90% of mature cards get "good" response

        private final String queryBaseNew =
                "select "
                        +   "count() as N, "
                        +   "sum(case when ease=1 then 1 else 0 end) as repeat, "
                        +   "0 as hard, "	//Doesn't occur in query_new
                        +	  "sum(case when ease=2 then 1 else 0 end) as good, "
                        +	  "sum(case when ease=3 then 1 else 0 end) as easy "
                        + "from revlog ";

        private final String queryBaseYoungMature =
                "select "
                        +   "count() as N, "
                        +   "sum(case when ease=1 then 1 else 0 end) as repeat, "
                        +   "sum(case when ease=2 then 1 else 0 end) as hard, "	//Doesn't occur in query_new
                        +	  "sum(case when ease=3 then 1 else 0 end) as good, "
                        +	  "sum(case when ease=4 then 1 else 0 end) as easy "
                        + "from revlog ";

        private final String queryNew =
                queryBaseNew
                        + "where type=0;";

        private final String queryYoung =
                queryBaseYoungMature
                        + "where type=1 and lastIvl < 21;";

        private final String queryMature =
                queryBaseYoungMature
                        + "where type=1 and lastIvl >= 21;";

        public EaseClassifier(SQLiteDatabase db) {
            this.db = db;
            this.probabilitiesCumulative = calculateCumProbabilitiesForNewEasePerCurrentEase();

            Timber.d("new\t\t" + Arrays.toString(this.probabilitiesCumulative[0]));
            Timber.d("young\t\t" + Arrays.toString(this.probabilitiesCumulative[1]));
            Timber.d("mature\t" + Arrays.toString(this.probabilitiesCumulative[2]));

            random = new Random();
        }

        private double[] cumsum(double[] p) {

            p[1] = p[0] + p[1];
            p[2] = p[1] + p[2];
            p[3] = p[2] + p[3];

            return p;
        }

        private double[][] calculateCumProbabilitiesForNewEasePerCurrentEase() {
            double[][] p = new double[3][];

            p[0] = cumsum(calculateProbabilitiesForNewEaseForCurrentEase(queryNew, priorNew));
            p[1] = cumsum(calculateProbabilitiesForNewEaseForCurrentEase(queryYoung, priorYoung));
            p[2] = cumsum(calculateProbabilitiesForNewEaseForCurrentEase(queryMature, priorMature));

            return p;
        }

        private double[] calculateProbabilitiesForNewEaseForCurrentEase(String queryNewEaseCountForCurrentEase, int[] prior) {

            Cursor cur = null;

            int[] freqs = new int[] {
                    prior[0],
                    prior[1],
                    prior[2],
                    prior[3]
            };

            int n = prior[0] + prior[1] + prior[2] + prior[3];

            try {
                cur = db.rawQuery(queryNewEaseCountForCurrentEase, null);
                cur.moveToNext();

                freqs[0] += cur.getInt(1);          //Repeat
                freqs[1] += cur.getInt(2);          //Hard
                freqs[2] += cur.getInt(3);          //Good
                freqs[3] += cur.getInt(4);          //Easy

                int nQuery = cur.getInt(0);        //N

                n += nQuery;

            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }

            return new double[] {
                    freqs[0] / (double) n,
                    freqs[1] / (double) n,
                    freqs[2] / (double) n,
                    freqs[3] / (double) n
            };
        }

        private int draw(double[] p) {
            return searchsorted(p, random.nextDouble());
        }

        private int searchsorted(double[] p, double random) {
            if(random <= p[0]) return 0;
            if(random <= p[1]) return 1;
            if(random <= p[2]) return 2;
            return 3;
        }

        public Card simSingleReview(Card c, boolean preserveCard) {
            if(preserveCard)
                c = c.clone();
            return simSingleReview(c);
        }

        public Card simSingleReview(Card c){

            int type = c.getType();

            int outcome = draw(probabilitiesCumulative[type]);

            applyOutcomeToCard(c, outcome);

            return c;
        }

        private void applyOutcomeToCard(Card c, int outcome) {

            int type = c.getType();
            int ivl = c.getIvl();
            double factor = c.getFactor();

            if(type == 0) {
                if (outcome <= 2)
                    ivl = 1;
                else
                    ivl = 4;
            }
            else {
                switch(outcome) {
                    case 0:
                        ivl = 1;
                        break;
                    case 1:
                        ivl *= 1.2;
                        break;
                    case 2:
                        ivl *= 1.2 * factor;
                        break;
                    case 3:
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

    public class NewCardSimulator {

        private int nAddedToday = 0;
        private int tAdd = 0;

        public int simulateNewCard(Deck deck) {
            nAddedToday++;
            int tElapsed = tAdd;	//differs from online
            if (nAddedToday >= deck.getNewPerDay()) {
                tAdd++;
                nAddedToday = 0;
            }
            return tElapsed;
        }
    }

    private class ReviewSimulator {

        private final SQLiteDatabase db;
        private final EaseClassifier classifier;

        //TODO: also exists in Review
        private final int nTimeBins;
        private final int timeBinLength;

        private final int tMax;

        private final NewCardSimulator newCardSimulator = new NewCardSimulator();

        public ReviewSimulator(SQLiteDatabase db, EaseClassifier classifier, int nTimeBins, int timeBinLength) {
            this.db = db;
            this.classifier = classifier;

            this.nTimeBins = nTimeBins;
            this.timeBinLength = timeBinLength;

            this.tMax = this.nTimeBins * this.timeBinLength;
        }

        public SimulationResult simNreviews(int today, Decks decks, String didsStr) {

            SimulationResult simulationResultAggregated = new SimulationResult(nTimeBins, timeBinLength);

            long[] dids = ArrayUtils.stringToLongArray(didsStr);

            for(long did : dids) {
                simulationResultAggregated.add(simNreviews(today, Decks.createDeck(did, decks)));
            }

            return simulationResultAggregated;

        }

        public SimulationResult simNreviews(int today, Deck deck) {

            SimulationResult simulationResult = new SimulationResult(nTimeBins, timeBinLength);

            //nSmooth=1

            //TODO:
            //Forecasted final state of deck
            //finalIvl = np.empty((nSmooth, nCards), dtype='f8')

            Timber.d("today: " + today);

            Stack<Review> reviews = new Stack<>();

            //TODO: by having simulateReview add future reviews depending on which simulation of this card this is (the nth) we can:
            //1. Do monte carlo simulation if we add k future reviews if n = 1
            //2. Do a complete traversal of the future reviews tree if we add k future reviews for all n
            //3. Do any combination of these

            CardIterator cardIterator = null;
            try {
                cardIterator = new CardIterator(db, today, deck.getDid());

                while (cardIterator.moveToNext()) {

                    Card card = cardIterator.current();

                    Review review = new Review(deck, card, simulationResult, newCardSimulator, classifier, reviews);

                    if (review.getT() < tMax)
                        reviews.push(review);

                    while (!reviews.isEmpty()) {
                        review = reviews.pop();
                        review.simulateReview();
                    }

                }
            }
            finally {
                if(cardIterator != null)
                    cardIterator.close();
            }
            ArrayUtils.formatMatrix("nReviews", simulationResult.getNReviews(), "%04d ");
            ArrayUtils.formatMatrix("nInState", simulationResult.getNInState(), "%04d ");

            return simulationResult;
        }
    }

    private class Settings {
        //TODO
        public int getDeckCreationTimeStamp() {
            return 1445619600;
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

        public long getNow() {
            //return 1451223980146L;
            return System.currentTimeMillis();
        }

        /**
         * Today.
         * @param collectionCreatedTime The difference, measured in seconds, between midnight, January 1, 1970 UTC and the time at which the collection was created.
         * @return Today in days counted from the time at which the collection was created
         */
        public int getToday(int collectionCreatedTime) {
            Timber.d("Collection creation timestamp: " + collectionCreatedTime);

            int currentTime = (int) (getNow() / 1000);
            Timber.d("Now: " + currentTime);
            return (currentTime - collectionCreatedTime) / getNSecsPerDay();
        }

        public int getNSecsPerDay() {
            return 86400;
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

            int transpose[][] = new int[n][m];

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

            double transpose[][] = new double[n][m];

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

            s.append(matrixName + ":");
            s.append(System.getProperty("line.separator"));

            for(int i=0; i<matrix.length; i++) {
                for(int j=0; j<matrix[i].length; j++) {
                    s.append(String.format(format, matrix[i][j]));
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

        private final int nTimeBins;
        private final int timeBinLength;

        private final int nDays;

        /**
         * Forecasted number of reviews per time bin (a time bin contains statistics for 1 or a multiple of days)
         * 0 = Learn
         * 1 = Young
         * 2 = Mature
         * 3 = Relearn
         */
        private final int[][] nReviews;

        /**
         * Forecasted number of reviews per day.
         * @see #nReviews
         */
        private final int[][] nReviewsPerDay;

        /**
         * Forecasted number of cards per state
         * 0 = New
         * 1 = Young
         * 2 = Mature
         */
        private final int[][] nInState;

        /**
         * Create an empty SimulationResult.
         * @param nTimeBins Number of time bins.
         * @param timeBinLength Length of 1 time bin in days.
         */
        public SimulationResult(int nTimeBins, int timeBinLength) {
            nReviews = ArrayUtils.createIntMatrix(4, nTimeBins);
            nReviewsPerDay = ArrayUtils.createIntMatrix(4, nTimeBins * timeBinLength);
            nInState = ArrayUtils.createIntMatrix(3, nTimeBins);

            this.nTimeBins = nTimeBins;
            this.timeBinLength = timeBinLength;
            this.nDays = nTimeBins * timeBinLength;
        }

        public int getnTimeBins() {
            return nTimeBins;
        }

        public int getTimeBinLength() {
            return timeBinLength;
        }

        public int getnDays() {
            return nDays;
        }

        /**
         * Adds the statistics generated by another simulation to the current statistics.
         * Use to gather statistics over decks.
         * @param res2Add Statistics to be added to the current statistics.
         */
        public void add(SimulationResult res2Add) {

            int[][] nReviews = res2Add.getNReviews();
            int[][] nInState = res2Add.getNInState();

            for(int i = 0; i < nReviews.length; i++)
                for(int j = 0; j < nReviews[i].length; j++)
                    this.nReviews[i][j] += nReviews[i][j];

            //This method is only used to aggregate over decks
            //We do not update nReviewsPerDay since it is not needed for the SimulationResult aggregated over decks.

            for(int i = 0; i < nInState.length; i++)
                for(int j = 0; j < nInState[i].length; j++)
                    this.nInState[i][j] += nInState[i][j];
        }

        public int[][] getNReviews() {
            return nReviews;
        }

        public int[][] getNInState() {
            return nInState;
        }

        /**
         * Request the number of reviews which have been simulated so far at a particular day
         * (to check if the 'maximum number of reviews per day' limit has been reached).
         * @param tElapsed Day for which the number of reviews is requested.
         * @return Number of reviews of young and mature cards simulated at time tElapsed.
         * This excludes new cards and relearns as they don't count towards the limit.
         */
        public int nReviewsDoneToday(int tElapsed) {
            return nReviewsPerDay[1][tElapsed] +
                    nReviewsPerDay[2][tElapsed];
        }

        /**
         * Increment the count 'number of reviews of card with type cardType' with one at day t.
         * @param cardType  Card type
         * @param t Day for which to increment
         */
        public void incrementNReviews(int cardType, int t) {
            nReviews[cardType][t / timeBinLength]++;
            nReviewsPerDay[cardType][t]++;
        }

        /**
         * Increment the count 'number of cards in the state of the given card' with one between tFrom and tTo.
         * @param card Card from which to read the state.
         * @param tFrom The first day for which to update the state.
         * @param tTo The day after the last day for which to update the state.
         */
        public void updateNInState(Card card, int tFrom, int tTo) {
            for(int t = tFrom / timeBinLength; t < tTo / timeBinLength; t++)
                if(t < nTimeBins) {
                    nInState[card.getType()][t]++;
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
         * @see #updateNInState(Card, int, int)
         */
        public void updateNInState(Card prevCard, Card card, int tFrom, int tTo) {
            int lastReview = prevCard.getLastReview();

            //Replace state set during last review
            for(int t = tFrom / timeBinLength; t < Math.min(lastReview, tTo) / timeBinLength; t++)
                if(t < nTimeBins) {
                    nInState[prevCard.getType()][t]--;
                }

            //With state set during new review
            for(int t = tFrom / timeBinLength; t < tTo / timeBinLength; t++)
                if(t < nTimeBins) {
                    nInState[card.getType()][t]++;
                }

            //Alternative solution would be to keep this count for each day instead of keeping it for each bin and aggregate in the end
            //to a count for each bin.
            //That would also work because we do not simulate two reviews of one card at one and the same day.
        }
    }

    private class PlottableSimulationResult {

        // Forecasted number of reviews
        //   0 = Time
        //   1 = Learn
        //   2 = Young
        //   3 = Mature
        //   4 = Relearn
        private final ArrayList<int[]> nReviews;

        // Forecasted number of cards per state
        //   0 = Time
        //   1 = New
        //   2 = Young
        //   3 = Mature
        //   4 = Zeros (we can't say 'we know x relearn cards on day d')
        private final double[][] nInState;

        public PlottableSimulationResult(ArrayList<int[]> nReviews, double[][] nInState) {
            this.nReviews = nReviews;
            this.nInState = nInState;
        }

        public ArrayList<int[]> getNReviews() {
            return nReviews;
        }

        public double[][] getNInState() {
            return nInState;
        }
    }

    /**
     * Bundles the information needed to simulate a review and the objects affected by the review.
     */
    private class Review {

        private final int maxReviewsPerDay;

        private int tElapsed;
        private Deck deck;
        private Card card;
        private final SimulationResult simulationResult;
        private final EaseClassifier classifier;
        private final Stack<Review> reviews;

        /**
         * For creating future reviews which are to be scheduled as a result of the current review.
         * @see Review(Deck, Card, SimulationResult, NewCardSimulator, EaseClassifier, Stack<Review>, int, int)
         */
        private Review (Deck deck, Card card, SimulationResult simulationResult, EaseClassifier classifier, Stack<Review> reviews, int tElapsed) {
            this.deck = deck;
            this.card = card;
            this.simulationResult = simulationResult;
            this.classifier = classifier;
            this.reviews = reviews;

            this.tElapsed = tElapsed;

            this.maxReviewsPerDay = deck.getRevPerDay();
        }

        /**
         * For creating a review which is to be scheduled.
         * @param deck Information needed to simulate a review: deck settings.
         * @param card Information needed to simulate a review: card due date, type and factor.
         *             Will be affected by the review. After the review it will contain the card type etc. after the review.
         * @param simulationResult Will be affected by the review. After the review it will contain updated statistics.
         * @param newCardSimulator Information needed to simulate a review: The next day new cards will be added and the number of cards already added on that day.
         *                         Will be affected by the review. After the review of a new card, the number of cards added on that day will be updated.
         *                         Next day new cards will be added might be updated if new card limit has been reached.
         * @param classifier Information needed to simulate a review: transition probabilities to new card state for each possible current card state.
         * @param reviews Will be affected by the review. Scheduled future reviews of this card will be added.
         */
        public Review (Deck deck, Card card, SimulationResult simulationResult, NewCardSimulator newCardSimulator, EaseClassifier classifier, Stack<Review> reviews) {
            this.deck = deck;
            this.card = card;
            this.simulationResult = simulationResult;
            this.classifier = classifier;
            this.reviews = reviews;

            this.maxReviewsPerDay = deck.getRevPerDay();

            //# Rate-limit new cards by shifting starting time
            if (card.getType() == 0)
                tElapsed = newCardSimulator.simulateNewCard(deck);
            else
                tElapsed = card.getDue();

            // Set state of card between start and first review
            this.simulationResult.updateNInState(card, 0, tElapsed);
        }

        /**
         * Simulates one review of the card. The review results in:
         * - The card being updated
         * - New card simulator (when to schedule next new card) being updated if the card was new
         * - The simulationResult being updated.
         * - New review(s) being scheduled.
         */
        public void simulateReview() {

            if(card.getType() == 0 || simulationResult.nReviewsDoneToday(tElapsed) < maxReviewsPerDay) {
                // Update the forecasted number of reviews
                simulationResult.incrementNReviews(card.getType(), tElapsed);

                // Simulate response
                Card prevCard = card.clone();
                card = classifier.simSingleReview(card, true);
                card.setLastReview(tElapsed);

                // If card failed, update "relearn" count
                if(card.getCorrect() == 0)
                    simulationResult.incrementNReviews(3, tElapsed);

                // Set state of card between current and next review
                simulationResult.updateNInState(prevCard, card, tElapsed, tElapsed + card.getIvl());

                // Advance time to next review
                tElapsed += card.getIvl();
            }
            else {
                // Advance time to next review (max. #reviews reached for this day)
                tElapsed += 1;
            }

            //Schedule next review(s) if they are within the time window of the simulation
            if (tElapsed < simulationResult.getnDays()) {
                Review review = new Review(deck, card, simulationResult, classifier, reviews, tElapsed);
                this.reviews.push(review);
            }
        }

        public int getT() {
            return tElapsed;
        }
    }

}
