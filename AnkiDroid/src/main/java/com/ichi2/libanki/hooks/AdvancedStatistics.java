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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.ichi2.anki.R;
import com.ichi2.anki.stats.StatsMetaInfo;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Stack;

import timber.log.Timber;

/**
 * Display forecast statistics based on a simulation of future reviews.
 * Created by Jeffrey on 1-1-2016.
 */
public class AdvancedStatistics extends Hook  {

    private final Settings Settings = new Settings();
    private final ArrayUtils ArrayUtils = new ArrayUtils();
    private final Deck Deck = new Deck();

    @Override
    public Object runFilter(Object arg, Object... args) {
        return calculateDueOriginal((StatsMetaInfo) arg, (int) args[0], (Collection) args[1]);
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
    private StatsMetaInfo calculateDueOriginal(StatsMetaInfo metaInfo, int type, Collection mCol) {

        metaInfo.setStatsCalculated(true);

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
        ArrayList<int[]> dues = calculateDuesWithSimulation(type, mCol);

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

        mCumulative = Stats.createCumulative(new double[][]{mSeriesList[0], mSeriesList[1]}, mZeroIndex);   //Day starting at mZeroIndex, Cumulative # cards
        mMcount = mCumulative[1][mCumulative[1].length-1];                                                  //Y-Axis: Max. cumulative value
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
        metaInfo.setmHasColoredCumulative(false);
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

    private ArrayList<int[]> calculateDuesWithSimulation(int type, Collection mCol) {
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

        SimulationResult simulationResult = reviewSimulator.simNreviews();

        int[][] nReviews = ArrayUtils.transposeMatrix(simulationResult.getNReviews());
        //int[][] nInState = simulationResult.getNInState();

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

        return dues;
    }

    private class Card {
        private int ivl;
        private double factor;
        private int due;
        private int correct;
        private final long id;

        @Override
        public String toString() {
            return "Card [ivl=" + ivl + ", factor=" + factor + ", due=" + due + ", correct=" + correct + ", id="
                    + id + "]";
        }

        public Card(long id, int ivl, int factor, int due, int correct) {
            super();
            this.id = id;
            this.ivl = ivl;
            this.factor = factor / 1000.0;
            this.due = due;
            this.correct = correct;
        }

        public Card clone() {
            return new Card(id, ivl, (int) (factor * 1000), due, correct);
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
    }

    private class Deck {

        public List<Card> deckFromDB(SQLiteDatabase db, int today) {
            List<Card> deck = new ArrayList<>();

            Cursor cur = null;
            try {
                String query;
                query = "SELECT id, due, ivl, factor, type, reps " +
                        "FROM cards " +
                        "order by id;";
                Timber.d("Forecast query: %s", query);
                cur = db.rawQuery(query, null);
                while (cur.moveToNext()) {

                    Card card = new Card(cur.getLong(0),                                            //Id
                            cur.getInt(5) == 0 ? 0 : cur.getInt(2),  		            //reps = 0 ? 0 : card interval
                            cur.getInt(3) > 0 ? cur.getInt(3) :  2500,                 //factor
                            Math.max(cur.getInt(1) - today, 0),                        //due
                            1);                                                        //correct
                    deck.add(card);
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }

            return deck;
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

        private final int maxAddPerDay=20;

        private int nAddedToday = 0;
        private int tAdd = 0;

        public int simulateNewCard() {
            nAddedToday++;
            int tElapsed = tAdd;	//differs from online
            if (nAddedToday >= maxAddPerDay) {
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

        public SimulationResult simNreviews() {

            SimulationResult simulationResult = new SimulationResult(nTimeBins, timeBinLength);

            //nSmooth=1

            //TODO:
            //Forecasted final state of deck
            //finalIvl = np.empty((nSmooth, nCards), dtype='f8')

            int today = Settings.getToday();
            Timber.d("today: " + today);

            List<Card> deck = Deck.deckFromDB(db, today);

            Stack<Review> reviews = new Stack<>();

            //TODO: by having simulateReview add future reviews depending on which simulation of this card this is (the nth) we can:
            //1. Do monte carlo simulation if we add k future reviews if n = 1
            //2. Do a complete traversal of the future reviews tree if we add k future reviews for all n
            //3. Do any combination of these
            for (Card card : deck) {

                Review review = new Review(card, simulationResult, newCardSimulator, classifier, reviews, nTimeBins, timeBinLength);

                if (review.getT() < tMax)
                    reviews.push(review);

                while (!reviews.isEmpty()) {
                    review = reviews.pop();
                    review.simulateReview();
                }

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

        public int getMaxReviewsPerDay() {
            return 10000;
        }

        public long getNow() {
            return 1451223980146L;
            //return System.currentTimeMillis();
        }

        public int getToday() {
            int currentTime = (int) (getNow() / 1000);
            return (currentTime - getDeckCreationTimeStamp()) / getNSecsPerDay();
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

    private class SimulationResult {

        private final int nTimeBins;
        private final int timeBinLength;

        // Forecasted number of reviews
        //   0 = Learn
        //   1 = Young
        //   2 = Mature
        //   3 = Relearn
        private final int[][] nReviews;

        // Forecasted number of cards per state
        //   0 = New
        //   1 = Young
        //   2 = Mature
        private final int[][] nInState;

        public SimulationResult(int nTimeBins, int timeBinLength) {
            nReviews = ArrayUtils.createIntMatrix(4, nTimeBins);
            nInState = ArrayUtils.createIntMatrix(3, nTimeBins);

            this.nTimeBins = nTimeBins;
            this.timeBinLength = timeBinLength;
        }

        public int[][] getNReviews() {
            return nReviews;
        }

        public int[][] getNInState() {
            return nInState;
        }

        public int nReviewsDoneToday(int tElapsed) {
            //This excludes new cards and relearns
            return nReviews[1][tElapsed / timeBinLength] +
                    nReviews[2][tElapsed / timeBinLength];
        }

        public void incrementNInState(int cardType, int t) {
            nInState[cardType][t]++;
        }

        public void incrementNReviews(int cardType, int t) {
            nReviews[cardType][t]++;
        }

        public void updateNInState(Card card, int tFrom, int tTo) {
            for(int t = tFrom / timeBinLength; t < tTo / timeBinLength; t++)
                if(t < nTimeBins) {
                    nInState[card.getType()][t]++;
                }
        }
    }

    private class Review {

        private final int maxReviewsPerDay = Settings.getMaxReviewsPerDay();

        private final int nTimeBins;
        private final int timeBinLength;
        private final int tMax;

        private int tElapsed;
        private Card card;
        private final SimulationResult simulationResult;
        private final EaseClassifier classifier;
        private final Stack<Review> reviews;

        private Review (Card card, SimulationResult simulationResult, EaseClassifier classifier, Stack<Review> reviews, int tElapsed, int nTimeBins, int timeBinLength) {
            this.card = card;
            this.simulationResult = simulationResult;
            this.classifier = classifier;
            this.reviews = reviews;

            this.tElapsed = tElapsed;

            this.nTimeBins = nTimeBins;
            this.timeBinLength = timeBinLength;

            this.tMax = this.nTimeBins * this.timeBinLength;
        }

        public Review (Card card, SimulationResult simulationResult, NewCardSimulator newCardSimulator, EaseClassifier classifier, Stack<Review> reviews, int nTimeBins, int timeBinLength) {
            this.card = card;
            this.simulationResult = simulationResult;
            this.classifier = classifier;
            this.reviews = reviews;

            this.nTimeBins = nTimeBins;
            this.timeBinLength = timeBinLength;

            this.tMax = this.nTimeBins * this.timeBinLength;

            //# Rate-limit new cards by shifting starting time
            if (card.getType() == 0)
                tElapsed = newCardSimulator.simulateNewCard();
            else
                tElapsed = card.getDue();

            // Set state of card between start and first review
            this.simulationResult.updateNInState(card, 0, tElapsed);
        }

        public void simulateReview() {
            //Set state of card for current review
            simulationResult.incrementNInState(card.getType(), tElapsed / timeBinLength);

            if(card.getType() == 0 || simulationResult.nReviewsDoneToday(tElapsed) < maxReviewsPerDay * timeBinLength) {
                // Update the forecasted number of reviews
                simulationResult.incrementNReviews(card.getType(), tElapsed / timeBinLength);

                // Simulate response
                card = classifier.simSingleReview(card, true);

                // If card failed, update "relearn" count
                if(card.getCorrect() == 0)
                    simulationResult.incrementNReviews(3, tElapsed / timeBinLength);

                // Set state of card between current and next review
                simulationResult.updateNInState(card, tElapsed + 1, tElapsed + card.getIvl());

                // Advance time to next review
                tElapsed += card.getIvl();
            }
            else {
                // Advance time to next review (max. #reviews reached for this day)
                tElapsed += 1;
            }

            if (tElapsed < tMax) {
                Review review = new Review(card, simulationResult, classifier, reviews, tElapsed, nTimeBins, timeBinLength);
                this.reviews.push(review);
            }
        }

        public int getT() {
            return tElapsed;
        }
    }

}
