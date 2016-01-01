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

package com.ichi2.libanki;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

import timber.log.Timber;

/**
 * Deck statistics.
 */
public class Stats {

    public static final int TYPE_MONTH = 0;
    public static final int TYPE_YEAR = 1;
    public static final int TYPE_LIFE = 2;

    public static enum ChartType {FORECAST, REVIEW_COUNT, REVIEW_TIME,
        INTERVALS, HOURLY_BREAKDOWN, WEEKLY_BREAKDOWN, ANSWER_BUTTONS, CARDS_TYPES, OTHER};

    private static Stats sCurrentInstance;

    private Collection mCol;
    private boolean mWholeCollection;
    private boolean mDynamicAxis = false;
    private boolean mIsPieChart = false;
    private double[][] mSeriesList;

    private boolean mHasColoredCumulative = false;
    private int mType;
    private int mTitle;
    private boolean mBackwards;
    private int[] mValueLabels;
    private int[] mColors;
    private int[] mAxisTitles;
    private int mMaxCards = 0;
    private int mMaxElements = 0;
    private double mFirstElement = 0;
    private double mLastElement = 0;
    private int mZeroIndex = 0;
    private boolean mFoundLearnCards = false;
    private boolean mFoundCramCards = false;
    private boolean mFoundRelearnCards;
    private double[][] mCumulative = null;
    private String mAverage;
    private String mLongest;
    private double mPeak;
    private double mMcount;

    // JPR new
    private Settings Settings = new Settings();
    private ArrayUtils ArrayUtils = new ArrayUtils();
    private Deck Deck = new Deck();
    // JPR end

    public Stats(Collection col, boolean wholeCollection) {
        mCol = col;
        mWholeCollection = wholeCollection;
        sCurrentInstance = this;
    }


    public static Stats currentStats() {
        return sCurrentInstance;
    }


    public double[][] getSeriesList() {
        return mSeriesList;
    }
    public double[][] getCumulative() {
        return mCumulative;
    }


    public Object[] getMetaInfo() {
        String title;
        if (mWholeCollection) {
            title = AnkiDroidApp.getInstance().getResources().getString(R.string.card_browser_all_decks);
        } else {
            try {
                title = mCol.getDecks().current().getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return new Object[] {/*0*/ mType, /*1*/mTitle, /*2*/mBackwards, /*3*/mValueLabels, /*4*/mColors,
         /*5*/mAxisTitles, /*6*/title, /*7*/mMaxCards, /*8*/mMaxElements, /*9*/mFirstElement, /*10*/mLastElement,
                /*11*/mZeroIndex, /*12*/mFoundLearnCards, /*13*/mFoundCramCards, /*14*/mFoundRelearnCards, /*15*/mAverage,
                /*16*/mLongest, /*17*/mPeak, /*18*/mMcount, /*19*/mHasColoredCumulative, /*20*/mDynamicAxis};
    }


    /**
     * Todays statistics
     */
    public int[] calculateTodayStats(){
        String lim = _revlogLimit();
        if (lim.length() > 0)
            lim = " and " + lim;

        Cursor cur = null;
        String query = "select count(), sum(time)/1000, "+
                "sum(case when ease = 1 then 1 else 0 end), "+ /* failed */
                "sum(case when type = 0 then 1 else 0 end), "+ /* learning */
                "sum(case when type = 1 then 1 else 0 end), "+ /* review */
                "sum(case when type = 2 then 1 else 0 end), "+ /* relearn */
                "sum(case when type = 3 then 1 else 0 end) "+ /* filter */
                "from revlog where id > " + ((mCol.getSched().getDayCutoff()-86400)*1000) + " " +  lim;
        Timber.d("todays statistics query: %s", query);

        int cards, thetime, failed, lrn, rev, relrn, filt;
        try {
            cur = mCol.getDb()
                    .getDatabase()
                    .rawQuery(query, null);

            cur.moveToFirst();
            cards = cur.getInt(0);
            thetime = cur.getInt(1);
            failed = cur.getInt(2);
            lrn = cur.getInt(3);
            rev = cur.getInt(4);
            relrn = cur.getInt(5);
            filt = cur.getInt(6);



        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        query = "select count(), sum(case when ease = 1 then 0 else 1 end) from revlog " +
        "where lastIvl >= 21 and id > " + ((mCol.getSched().getDayCutoff()-86400)*1000) + " " +  lim;
        Timber.d("todays statistics query 2: %s", query);

        int mcnt, msum;
        try {
            cur = mCol.getDb()
                    .getDatabase()
                    .rawQuery(query, null);

            cur.moveToFirst();
            mcnt = cur.getInt(0);
            msum = cur.getInt(1);
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        return new int[]{cards, thetime, failed, lrn, rev, relrn, filt, mcnt, msum};
    }
    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    public boolean calculateDue(int type) {
        mHasColoredCumulative = false;
        mType = type;
        mDynamicAxis = true;
        mBackwards = false;
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
        ArrayList<int[]> dues = calculateDuesWithSimulation(type);

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
            case TYPE_MONTH:
                mLastElement = 31;              //X-Axis: Max. value
                break;
            case TYPE_YEAR:
                mLastElement = 52;              //X-Axis: Max. value
                break;
            default:
        }
        mFirstElement = 0;                      //X-Axis: Min. value

        mHasColoredCumulative = false;
        mCumulative = Stats.createCumulative(new double[][]{mSeriesList[0], mSeriesList[1]}, mZeroIndex);   //Day starting at mZeroIndex, Cumulative # cards
        mMcount = mCumulative[1][mCumulative[1].length-1];                                                  //Y-Axis: Max. cumulative value
        //some adjustments to not crash the chartbuilding with emtpy data
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
        return dues.size() > 0;
    }

    //30-12-2015 JPR new
    private ArrayList<int[]> calculateDues(int type) {
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
                end = -1;
                chunk = 30;
                break;
        }

        String lim = "";// AND due - " + mCol.getSched().getToday() + " >= " + start; // leave this out in order to show
        // card too which were due the days before
        if (end != -1) {
            lim += " AND day <= " + end;
        }

        ArrayList<int[]> dues = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            String query;
            query = "SELECT (due - " + mCol.getSched().getToday() + ")/" + chunk
                    + " AS day, " // day
                    + "count(), " // all cards
                    + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                    + "FROM cards WHERE did IN " + _limit() + " AND queue IN (2,3)" + lim
                    + " GROUP BY day ORDER BY day";
            Timber.d("Forecast query: %s", query);
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(query, null);
            while (cur.moveToNext()) {
                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // small adjustment for a proper chartbuilding with achartengine
        if (dues.size() == 0 || dues.get(0)[0] > 0) {
            dues.add(0, new int[] { 0, 0, 0 });
        }
        if (end == -1 && dues.size() < 2) {
            end = 31;
        }
        if (type != TYPE_LIFE && dues.get(dues.size() - 1)[0] < end) {
            dues.add(new int[] { end, 0, 0 });
        } else if (type == TYPE_LIFE && dues.size() < 2) {
            dues.add(new int[] { Math.max(12, dues.get(dues.size() - 1)[0] + 1), 0, 0 });
        }

        return dues;
    }

    private ArrayList<int[]> calculateDuesWithSimulation(int type) {
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
                end = -1;
                chunk = 30;
                break;
        }

        ArrayList<int[]> dues = new ArrayList<int[]>();

        EaseClassifier classifier = new EaseClassifier(mCol.getDb().getDatabase());
        ReviewSimulator reviewSimulator = new ReviewSimulator(mCol.getDb().getDatabase(), classifier);

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
        if (end == -1 && dues.size() < 2) {
            end = 31;
        }
        if (type != TYPE_LIFE && dues.get(dues.size() - 1)[0] < end) {
            dues.add(new int[] { end, 0, 0, 0, 0 });
        } else if (type == TYPE_LIFE && dues.size() < 2) {
            dues.add(new int[] { Math.max(12, dues.get(dues.size() - 1)[0] + 1), 0, 0, 0, 0 });
        }

        return dues;
    }

    private class Card {
        private int ivl;
        private double factor;
        private int due;
        private int correct;
        private long id;

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
            List<Card> deck = new ArrayList<Card>();

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

        private Random random;

        private SQLiteDatabase db;
        private double[][] probabilitiesCumulative;

        //# Prior that half of new cards are answered correctly
        private int[] priorNew = {5, 0, 5, 0};		//half of new cards are answered correctly
        private int[] priorYoung = {1, 0, 9, 0};	//90% of young cards get "good" response
        private int[] priorMature = {1, 0, 9, 0};	//90% of mature cards get "good" response

        private String queryBaseNew =
                "select "
                        +   "count() as N, "
                        +   "sum(case when ease=1 then 1 else 0 end) as repeat, "
                        +   "0 as hard, "	//Doesn't occur in query_new
                        +	  "sum(case when ease=2 then 1 else 0 end) as good, "
                        +	  "sum(case when ease=3 then 1 else 0 end) as easy "
                        + "from revlog ";

        private String queryBaseYoungMature =
                "select "
                        +   "count() as N, "
                        +   "sum(case when ease=1 then 1 else 0 end) as repeat, "
                        +   "sum(case when ease=2 then 1 else 0 end) as hard, "	//Doesn't occur in query_new
                        +	  "sum(case when ease=3 then 1 else 0 end) as good, "
                        +	  "sum(case when ease=4 then 1 else 0 end) as easy "
                        + "from revlog ";

        private String queryNew =
                queryBaseNew
                        + "where type=0;";

        private String queryYoung =
                queryBaseYoungMature
                        + "where type=1 and lastIvl < 21;";

        private String queryMature =
                queryBaseYoungMature
                        + "where type=1 and lastIvl >= 21;";

        public EaseClassifier(SQLiteDatabase db) {
            this.db = db;
            this.probabilitiesCumulative = calculateCumProbabilitiesForNewEasePerCurrentEase();

            System.out.println("new\t" + Arrays.toString(this.probabilitiesCumulative[0]));
            System.out.println("young\t" + Arrays.toString(this.probabilitiesCumulative[1]));
            System.out.println("mature\t" + Arrays.toString(this.probabilitiesCumulative[2]));

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

            int nPrior = prior[0] + prior[1] + prior[2] + prior[3];

            int n = nPrior;

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

            double[] probs = new double[] {
                    freqs[0] / (double) n,
                    freqs[1] / (double) n,
                    freqs[2] / (double) n,
                    freqs[3] / (double) n
            };

            return probs;
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

        private int maxAddPerDay=20;

        private int nAddedToday = 0;
        private int tAdd = 0;

        public int simulateNewCard(Card card) {
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

        private SQLiteDatabase db;
        private EaseClassifier classifier;

        //TODO: also exists in Review
        private int nTimeBins = Settings.getNTimeBins();
        private int timeBinLength = Settings.getTimeBinLength();

        private int tMax = nTimeBins * timeBinLength;

        private NewCardSimulator newCardSimulator = new NewCardSimulator();

        public ReviewSimulator(SQLiteDatabase db, EaseClassifier classifier) {
            this.db = db;
            this.classifier = classifier;
        }

        public SimulationResult simNreviews() {

            SimulationResult simulationResult = new SimulationResult(nTimeBins);

            //nSmooth=1

            //TODO:
            //Forecasted final state of deck
            //finalIvl = np.empty((nSmooth, nCards), dtype='f8')

            int currentTime = (int) (System.currentTimeMillis() / 1000);

            int today = (int) ((currentTime - Settings.getDeckCreationTimeStamp()) / Settings.getNSecsPerDay());
            int mDayCutoff = Settings.getDeckCreationTimeStamp() + ((today + 1) * Settings.getNSecsPerDay());

            System.out.println("today: " + today);
            System.out.println("todayCutoff: " + mDayCutoff);

            List<Card> deck = Deck.deckFromDB(db, today);

            Stack<Review> reviews = new Stack<Review>();

            //TODO: by having simulateReview add future reviews depending on which simulation of this card this is (the nth) we can:
            //1. Do monte carlo simulation if we add k future reviews if n = 1
            //2. Do a complete traversal of the future reviews tree if we add k future reviews for all n
            //3. Do any combination of these
            for (Card card : deck) {

                Review review = new Review(card, simulationResult, newCardSimulator, classifier, reviews);

                if (review.getT() < tMax)
                    reviews.push(review);

                while (!reviews.isEmpty()) {
                    review = reviews.pop();
                    review.simulateReview();
                }

            }

            ArrayUtils.formatMatrix(simulationResult.getNReviews(), "%02d ");
            ArrayUtils.formatMatrix(simulationResult.getNInState(), "%02d ");

            return simulationResult;
        }
    }

    private class Settings {
        //TODO
        public int getDeckCreationTimeStamp() {
            return 1445619600;
        }

        public int getNTimeBins() {
            return 30;
        }

        public int getTimeBinLength() {
            return 1;
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
            int today = (int) ((currentTime - getDeckCreationTimeStamp()) / getNSecsPerDay());
            return today;
        }

        public int getNSecsPerDay() {
            return 86400;
        }

        public int getTodayCutoffSecsFromDeckCreation() {
            int mDayCutoff = getDeckCreationTimeStamp() + ((getToday() + 1) * getNSecsPerDay());
            return mDayCutoff;
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

        public void formatMatrix(int[][] matrix, String format) {
            StringBuilder s = new StringBuilder();

            for(int i=0; i<matrix.length; i++) {
                for(int j=0; j<matrix[i].length; j++) {
                    s.append(String.format(format, matrix[i][j]));
                    s.append(System.getProperty("line.separator"));
                }
            }

            Timber.d(s.toString());
        }
    }

    private class SimulationResult {

        private int timeBinLength = Settings.getTimeBinLength();
        private int tMax;

        // Forecasted number of reviews
        //   0 = Learn
        //   1 = Young
        //   2 = Mature
        //   3 = Relearn
        private int[][] nReviews;

        // Forecasted number of cards per state
        //   0 = New
        //   1 = Young
        //   2 = Mature
        private int[][] nInState;

        public SimulationResult(int nTimeBins) {
            nReviews = ArrayUtils.createIntMatrix(4, nTimeBins);
            nInState = ArrayUtils.createIntMatrix(3, nTimeBins);

            tMax = nTimeBins * timeBinLength;
        }

        public SimulationResult(int[][] nReviews, int[][] nInState) {
            this.nReviews = nReviews;
            this.nInState = nInState;
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
                if(t < tMax) {
                    nInState[card.getType()][t]++;
                }
        }
    }

    private class Review {

        private int maxReviewsPerDay = Settings.getMaxReviewsPerDay();

        private int nTimeBins = Settings.getNTimeBins();
        private int timeBinLength = Settings.getTimeBinLength();

        private int tMax = nTimeBins * timeBinLength;

        private int tElapsed;
        private Card card;
        private SimulationResult simulationResult;
        private EaseClassifier classifier;
        private Stack<Review> reviews;

        private Review (Card card, SimulationResult simulationResult, EaseClassifier classifier, Stack<Review> reviews, int tElapsed) {
            this.card = card;
            this.simulationResult = simulationResult;
            this.classifier = classifier;
            this.reviews = reviews;

            this.tElapsed = tElapsed;
        }

        public Review (Card card, SimulationResult simulationResult, NewCardSimulator newCardSimulator, EaseClassifier classifier, Stack<Review> reviews) {
            this.card = card;
            this.simulationResult = simulationResult;
            this.classifier = classifier;
            this.reviews = reviews;

            //# Rate-limit new cards by shifting starting time
            if (card.getType() == 0)
                tElapsed = newCardSimulator.simulateNewCard(card);
            else
                tElapsed = card.getDue();

            // Set state of card between start and first review
            this.simulationResult.updateNInState(card, 0, tElapsed);
        }

        public void simulateReview() {
            //Set state of card for current review
            simulationResult.incrementNInState(card.getType(), tElapsed / timeBinLength);

            if(card.getType() == 0 || simulationResult.nReviewsDoneToday(tElapsed) < maxReviewsPerDay) {
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
                Review review = new Review(card, simulationResult, classifier, reviews, tElapsed);
                this.reviews.push(review);
            }
        }

        public int getT() {
            return tElapsed;
        }
    }

    //30-12-2015 JPR new end

    /* only needed for studyoptions small chart */
    public static double[][] getSmallDueStats(Collection col) {
        ArrayList<int[]> dues = new ArrayList<int[]>();
        Cursor cur = null;
        try {
            cur = col
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            "SELECT (due - " + col.getSched().getToday()
                                    + ") AS day, " // day
                                    + "count(), " // all cards
                                    + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                                    + "FROM cards WHERE did IN " + col.getSched()._deckLimit()
                                    + " AND queue IN (2,3) AND day <= 7 GROUP BY day ORDER BY day", null);
            while (cur.moveToNext()) {
                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // small adjustment for a proper chartbuilding with achartengine
        if (dues.size() == 0 || dues.get(0)[0] > 0) {
            dues.add(0, new int[] { 0, 0, 0 });
        }
        if (dues.get(dues.size() - 1)[0] < 7) {
            dues.add(new int[] { 7, 0, 0 });
        }
        double[][] serieslist = new double[3][dues.size()];
        for (int i = 0; i < dues.size(); i++) {
            int[] data = dues.get(i);
            serieslist[0][i] = data[0];
            serieslist[1][i] = data[1];
            serieslist[2][i] = data[2];
        }
        return serieslist;
    }

    public boolean calculateDone(int type, boolean reps) {
        mHasColoredCumulative = true;
        mDynamicAxis = true;
        mType = type;
        mBackwards = true;
        if (reps) {
            mTitle = R.string.stats_review_count;
            mAxisTitles = new int[] { type, R.string.stats_answers, R.string.stats_cumulative_answers };
        } else {
            mTitle = R.string.stats_review_time;
        }
        mValueLabels = new int[] { R.string.statistics_learn, R.string.statistics_relearn, R.string.statistics_young,
                R.string.statistics_mature, R.string.statistics_cram };
        mColors = new int[] { R.color.stats_learn, R.color.stats_relearn, R.color.stats_young, R.color.stats_mature,
                R.color.stats_cram };
        int num = 0;
        int chunk = 0;
        switch (type) {
            case TYPE_MONTH:
                num = 31;
                chunk = 1;
                break;
            case TYPE_YEAR:
                num = 52;
                chunk = 7;
                break;
            case TYPE_LIFE:
                num = -1;
                chunk = 30;
                break;
        }
        ArrayList<String> lims = new ArrayList<String>();
        if (num != -1) {
            lims.add("id > " + ((mCol.getSched().getDayCutoff() - ((num + 1) * chunk * 86400)) * 1000));
        }
        String lim = _revlogLimit().replaceAll("[\\[\\]]", "");
        if (lim.length() > 0) {
            lims.add(lim);
        }
        if (lims.size() > 0) {
            lim = "WHERE ";
            while (lims.size() > 1) {
                lim += lims.remove(0) + " AND ";
            }
            lim += lims.remove(0);
        } else {
            lim = "";
        }
        String ti;
        String tf;
        if (!reps) {
            ti = "time/1000";
            if (mType == TYPE_MONTH) {
                tf = "/60.0"; // minutes
                mAxisTitles = new int[] { type, R.string.stats_minutes, R.string.stats_cumulative_time_minutes };
            } else {
                tf = "/3600.0"; // hours
                mAxisTitles = new int[] { type, R.string.stats_hours, R.string.stats_cumulative_time_hours };
            }
        } else {
            ti = "1";
            tf = "";
        }
        ArrayList<double[]> list = new ArrayList<double[]>();
        Cursor cur = null;
        String query = "SELECT (cast((id/1000 - " + mCol.getSched().getDayCutoff() + ") / 86400.0 AS INT))/"
                + chunk + " AS day, " + "sum(CASE WHEN type = 0 THEN " + ti + " ELSE 0 END)"
                + tf
                + ", " // lrn
                + "sum(CASE WHEN type = 1 AND lastIvl < 21 THEN " + ti + " ELSE 0 END)" + tf
                + ", " // yng
                + "sum(CASE WHEN type = 1 AND lastIvl >= 21 THEN " + ti + " ELSE 0 END)" + tf
                + ", " // mtr
                + "sum(CASE WHEN type = 2 THEN " + ti + " ELSE 0 END)" + tf + ", " // lapse
                + "sum(CASE WHEN type = 3 THEN " + ti + " ELSE 0 END)" + tf // cram
                + " FROM revlog " + lim + " GROUP BY day ORDER BY day";

        Timber.d("ReviewCount query: %s", query);

        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            query, null);
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(1), cur.getDouble(4), cur.getDouble(2),
                        cur.getDouble(3), cur.getDouble(5) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        // small adjustment for a proper chartbuilding with achartengine
        if (type != TYPE_LIFE && (list.size() == 0 || list.get(0)[0] > -num)) {
            list.add(0, new double[] { -num, 0, 0, 0, 0, 0 });
        } else if (type == TYPE_LIFE && list.size() == 0) {
            list.add(0, new double[] { -12, 0, 0, 0, 0, 0 });
        }
        if (list.get(list.size() - 1)[0] < 0) {
            list.add(new double[] { 0, 0, 0, 0, 0, 0 });
        }

        mSeriesList = new double[6][list.size()];
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            mSeriesList[0][i] = data[0]; // day
            mSeriesList[1][i] = data[1] + data[2] + data[3] + data[4] + data[5]; // lrn
            mSeriesList[2][i] = data[2] + data[3] + data[4] + data[5]; // relearn
            mSeriesList[3][i] = data[3] + data[4] + data[5]; // young
            mSeriesList[4][i] = data[4] + data[5]; // mature
            mSeriesList[5][i] = data[5]; // cram
            if(mSeriesList[1][i] > mMaxCards)
                mMaxCards = (int) Math.round(data[1] + data[2] + data[3] + data[4] + data[5]);

            if(data[5] >= 0.999)
                mFoundCramCards = true;

            if(data[1] >= 0.999)
                mFoundLearnCards = true;

            if(data[2] >= 0.999)
                mFoundRelearnCards = true;
            if(data[0] > mLastElement)
                mLastElement = data[0];
            if(data[0] < mFirstElement)
                mFirstElement = data[0];
            if(data[0] == 0){
                mZeroIndex = i;
            }
        }
        mMaxElements = list.size()-1;

        mCumulative = new double[6][];
        mCumulative[0] = mSeriesList[0];
        for(int i= 1; i<mSeriesList.length; i++){
            mCumulative[i] = createCumulative(mSeriesList[i]);
            if(i>1){
                for(int j = 0; j< mCumulative[i-1].length; j++){
                    mCumulative[i-1][j] -= mCumulative[i][j];
                }
            }
        }

        switch (mType) {
            case TYPE_MONTH:
                mFirstElement = -31;
                break;
            case TYPE_YEAR:
                mFirstElement = -52;
                break;
            default:
        }


        mMcount = 0;
        // we could assume the last element to be the largest,
        // but on some collections that may not be true due some negative values
        //so we search for the largest element:
        for(int i = 1; i < mCumulative.length; i++){
            for(int j = 0; j< mCumulative[i].length; j++){
                if(mMcount < mCumulative[i][j])
                    mMcount = mCumulative[i][j];
            }
        }

        //some adjustments to not crash the chartbuilding with emtpy data

        if(mMaxCards == 0)
            mMaxCards = 10;

        if(mMaxElements == 0){
            mMaxElements = 10;
        }
        if(mMcount == 0){
            mMcount = 10;
        }
        if(mFirstElement == mLastElement){
            mFirstElement = -10;
            mLastElement = 0;
        }
        return list.size() > 0;
    }


    /**
     * Intervals ***********************************************************************************************
     */

    public boolean calculateIntervals(Context context, int type) {
        mDynamicAxis = true;
        mType = type;
        double all = 0, avg = 0, max_ = 0;
        mBackwards = false;

        mTitle = R.string.stats_review_intervals;
        mAxisTitles = new int[] { type, R.string.stats_cards, R.string.stats_percentage };

        mValueLabels = new int[] { R.string.stats_cards_intervals};
        mColors = new int[] { R.color.stats_interval};
        int num = 0;
        String lim = "";
        int chunk = 0;
        switch (type) {
            case TYPE_MONTH:
                num = 31;
                chunk = 1;
                lim = " and grp <= 30";
                break;
            case TYPE_YEAR:
                num = 52;
                chunk = 7;
                lim = " and grp <= 52";
                break;
            case TYPE_LIFE:
                num = -1;
                chunk = 30;
                lim = "";
                break;
        }

        ArrayList<double[]> list = new ArrayList<double[]>();
        Cursor cur = null;
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            "select ivl / " + chunk + " as grp, count() from cards " +
                                    "where did in "+ _limit() +" and queue = 2 " + lim + " " +
                                    "group by grp " +
                                    "order by grp", null);
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(1) });
            }
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            "select count(), avg(ivl), max(ivl) from cards where did in " +_limit() +
                                    " and queue = 2", null);
            cur.moveToFirst();
            all = cur.getDouble(0);
            avg = cur.getDouble(1);
            max_ = cur.getDouble(2);

        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        // small adjustment for a proper chartbuilding with achartengine
        if (list.size() == 0 || list.get(0)[0] > 0) {
            list.add(0, new double[] { 0, 0, 0 });
        }
        if (num == -1 && list.size() < 2) {
            num = 31;
        }
        if (type != TYPE_LIFE && list.get(list.size() - 1)[0] < num) {
            list.add(new double[] { num, 0 });
        } else if (type == TYPE_LIFE && list.size() < 2) {
            list.add(new double[] { Math.max(12, list.get(list.size() - 1)[0] + 1), 0 });
        }

        mLastElement=0;
        mSeriesList = new double[2][list.size()];
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            mSeriesList[0][i] = data[0]; // grp
            mSeriesList[1][i] = data[1]; // cnt
            if(mSeriesList[1][i] > mMaxCards)
                mMaxCards = (int) Math.round(data[1]);
            if(data[0]>mLastElement)
                mLastElement = data[0];

        }
        mCumulative = createCumulative(mSeriesList);
        for(int i = 0; i<list.size(); i++){
            mCumulative[1][i] /= all/100;
        }
        mMcount = 100;

        switch (mType) {
            case TYPE_MONTH:
                mLastElement = 31;
                break;
            case TYPE_YEAR:
                mLastElement = 52;
                break;
            default:
        }
        mFirstElement = 0;

        mMaxElements = list.size()-1;
        mAverage = Utils.timeSpan(context, (int)Math.round(avg*86400));
        mLongest = Utils.timeSpan(context, (int)Math.round(max_*86400));

        //some adjustments to not crash the chartbuilding with emtpy data
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
        return list.size() > 0;
    }

    /**
     * Hourly Breakdown
     */
    public boolean calculateBreakdown(int type) {
        mTitle = R.string.stats_breakdown;
        mAxisTitles = new int[] { R.string.stats_time_of_day, R.string.stats_percentage_correct, R.string.stats_reviews };

        mValueLabels = new int[] { R.string.stats_percentage_correct, R.string.stats_answers};
        mColors = new int[] { R.color.stats_counts, R.color.stats_hours};

        mType = type;
        String lim = _revlogLimit().replaceAll("[\\[\\]]", "");

        if (lim.length() > 0) {
            lim = " and " + lim;
        }

        Calendar sd = GregorianCalendar.getInstance();
        sd.setTimeInMillis(mCol.getCrt() * 1000);

        int pd = _periodDays();
        if(pd > 0){
            lim += " and id > "+ ((mCol.getSched().getDayCutoff()-(86400*pd))*1000);
        }
        long cutoff = mCol.getSched().getDayCutoff();
        long cut = cutoff  - sd.get(Calendar.HOUR_OF_DAY)*3600;

        ArrayList<double[]> list = new ArrayList<double[]>();
        Cursor cur = null;
        String query = "select " +
                "23 - ((cast((" + cut + " - id/1000) / 3600.0 as int)) % 24) as hour, " +
                "sum(case when ease = 1 then 0 else 1 end) / " +
                "cast(count() as float) * 100, " +
                "count() " +
                "from revlog where type in (0,1,2) " + lim +" " +
                "group by hour having count() > 30 order by hour";
        Timber.d(sd.get(Calendar.HOUR_OF_DAY) + " : " +cutoff + " breakdown query: %s", query);
        try {
            cur = mCol.getDb()
                    .getDatabase()
                    .rawQuery(query, null);
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(1), cur.getDouble(2) });
            }


        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        //TODO adjust for breakdown, for now only copied from intervals
        //small adjustment for a proper chartbuilding with achartengine
        if (list.size() == 0) {
            list.add(0, new double[] { 0, 0, 0 });
        }


        for(int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int intHour = (int)data[0];
            int hour = (intHour - 4) % 24;
            if (hour < 0)
                hour += 24;
            data[0] = hour;
            list.set(i, data);
        }
        Collections.sort(list, new Comparator<double[]>() {
            @Override
            public int compare(double[] s1, double[] s2) {
                if (s1[0] < s2[0]) return -1;
                if (s1[0] > s2[0]) return 1;
                return 0;
            }
        });

        mSeriesList = new double[4][list.size()];
        mPeak = 0.0;
        mMcount = 0.0;
        double minHour = Double.MAX_VALUE;
        double maxHour = 0;
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int hour = (int)data[0];

            //double hour = data[0];
            if(hour < minHour)
                minHour = hour;

            if(hour > maxHour)
                maxHour = hour;

            double pct = data[1];
            if (pct > mPeak)
                mPeak = pct;

            mSeriesList[0][i] = hour;
            mSeriesList[1][i] = pct;
            mSeriesList[2][i] = data[2];
            if(i==0){
                mSeriesList[3][i] = pct;
            } else {
                double prev = mSeriesList[3][i-1];
                double diff = pct-prev;
                diff /= 3.0;
                diff = Math.round(diff*10.0)/10.0;

                mSeriesList[3][i] = prev+diff;
            }

            if (data[2] > mMcount)
                mMcount = data[2];
            if(mSeriesList[1][i] > mMaxCards)
                mMaxCards = (int) mSeriesList[1][i];
        }

        mFirstElement = mSeriesList[0][0];
        mLastElement = mSeriesList[0][mSeriesList[0].length-1];
        mMaxElements = (int)(maxHour -minHour);

        //some adjustments to not crash the chartbuilding with emtpy data
        if(mMaxElements == 0){
            mMaxElements = 10;
        }
        if(mMcount == 0){
            mMcount = 10;
        }
        if(mFirstElement == mLastElement){
            mFirstElement = 0;
            mLastElement = 23;
        }
        if(mMaxCards == 0)
            mMaxCards = 10;
        return list.size() > 0;
    }

    /**
     * Weekly Breakdown
     */

    public boolean calculateWeeklyBreakdown(int type) {
        mTitle = R.string.stats_weekly_breakdown;
        mAxisTitles = new int[] { R.string.stats_day_of_week, R.string.stats_percentage_correct, R.string.stats_reviews };

        mValueLabels = new int[] { R.string.stats_percentage_correct, R.string.stats_answers};
        mColors = new int[] { R.color.stats_counts, R.color.stats_hours};

        mType = type;
        String lim = _revlogLimit().replaceAll("[\\[\\]]", "");

        if (lim.length() > 0) {
            lim = " and " + lim;
        }

        Calendar sd = GregorianCalendar.getInstance();
        sd.setTimeInMillis(mCol.getSched().getDayCutoff() * 1000);


        int pd = _periodDays();
        if(pd > 0){
            lim += " and id > "+ ((mCol.getSched().getDayCutoff()-(86400*pd))*1000);
        }

        long cutoff = mCol.getSched().getDayCutoff();
        long cut = cutoff  - sd.get(Calendar.HOUR_OF_DAY)*3600;



        ArrayList<double[]> list = new ArrayList<double[]>();
        Cursor cur = null;
        String query = "SELECT strftime('%w',datetime( cast(id/ 1000  -" + sd.get(Calendar.HOUR_OF_DAY)*3600 +
                " as int), 'unixepoch')) as wd, " +
                "sum(case when ease = 1 then 0 else 1 end) / " +
                "cast(count() as float) * 100, " +
                "count() " +
                "from revlog " +
                "where type in (0,1,2) " + lim +" " +
                "group by wd " +
                "order by wd";
        Timber.d(sd.get(Calendar.HOUR_OF_DAY) + " : " +cutoff + " weekly breakdown query: %s", query);
        try {
            cur = mCol.getDb()
                    .getDatabase()
                    .rawQuery(query, null);
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(1), cur.getDouble(2) });
            }


        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        //TODO adjust for breakdown, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
        if (list.size() == 0 ) {
            list.add(0, new double[] { 0, 0, 0 });
        }



        mSeriesList = new double[4][list.size()];
        mPeak = 0.0;
        mMcount = 0.0;
        double minHour = Double.MAX_VALUE;
        double maxHour = 0;
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int hour = (int)data[0];

            //double hour = data[0];
            if(hour < minHour)
                minHour = hour;

            if(hour > maxHour)
                maxHour = hour;

            double pct = data[1];
            if (pct > mPeak)
                mPeak = pct;

            mSeriesList[0][i] = hour;
            mSeriesList[1][i] = pct;
            mSeriesList[2][i] = data[2];
            if(i==0){
                mSeriesList[3][i] = pct;
            } else {
                double prev = mSeriesList[3][i-1];
                double diff = pct-prev;
                diff /= 3.0;
                diff = Math.round(diff*10.0)/10.0;

                mSeriesList[3][i] = prev+diff;
            }

            if (data[2] > mMcount)
                mMcount = data[2];
            if(mSeriesList[1][i] > mMaxCards)
                mMaxCards = (int) mSeriesList[1][i];
        }
        mFirstElement = mSeriesList[0][0];
        mLastElement = mSeriesList[0][mSeriesList[0].length-1];
        mMaxElements = (int)(maxHour -minHour);

        //some adjustments to not crash the chartbuilding with emtpy data
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

        return list.size() > 0;
    }

    /**
     * Answer Buttons
     */

    public boolean calculateAnswerButtons(int type) {
        mHasColoredCumulative = true;
        mTitle = R.string.stats_answer_buttons;
        mAxisTitles = new int[] { R.string.stats_answer_type, R.string.stats_answers, R.string.stats_cumulative_correct_percentage };

        mValueLabels = new int[] { R.string.statistics_learn, R.string.statistics_young, R.string.statistics_mature};
        mColors = new int[] { R.color.stats_learn, R.color.stats_young, R.color.stats_mature};

        mType = type;
        String lim = _revlogLimit().replaceAll("[\\[\\]]", "");

        Vector<String> lims = new Vector<String>();
        int days = 0;

        if (lim.length() > 0)
            lims.add(lim);

        if (type == TYPE_MONTH)
            days = 30;
        else if (type == TYPE_YEAR)
            days = 365;
        else
            days = -1;

        if (days > 0)
            lims.add("id > " + ((mCol.getSched().getDayCutoff()-(days*86400))*1000));
        if (lims.size() > 0) {
            lim = "where " + lims.get(0);
            for(int i=1; i<lims.size(); i++)
                lim+= " and " + lims.get(i);
        }
        else
            lim = "";

        ArrayList<double[]> list = new ArrayList<double[]>();
        Cursor cur = null;
        String query = "select (case " +
                "                when type in (0,2) then 0 " +
                "        when lastIvl < 21 then 1 " +
                "        else 2 end) as thetype, " +
                "        (case when type in (0,2) and ease = 4 then 3 else ease end), count() from revlog " + lim + " "+
                "        group by thetype, ease " +
                "        order by thetype, ease";
        Timber.d("AnswerButtons query: %s", query);

        try {
            cur = mCol.getDb()
                    .getDatabase()
                    .rawQuery(query, null);
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(1), cur.getDouble(2) });
            }


        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        //TODO adjust for AnswerButton, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
        if (list.size() == 0) {
            list.add(0, new double[] { 0, 1, 0 });
        }



        double[] totals= new double[3];
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int currentType = (int)data[0];
            double ease = data[1];
            double cnt = data[2];

            totals[currentType] += cnt;
        }
        int badNew = 0;
        int badYoung = 0;
        int badMature = 0;


        mSeriesList = new double[4][list.size()+1];

        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int currentType = (int)data[0];
            double ease = data[1];
            double cnt = data[2];

            if (currentType == 1)
                ease += 5;
            else if(currentType == 2)
                ease += 10;

            if((int)ease == 1){
                badNew = i;
            }

            if((int)ease == 6){
                badYoung = i;
            }
            if((int)ease == 11){
                badMature = i;
            }
            mSeriesList[0][i] = ease;
            mSeriesList[1+currentType][i] = cnt;
            if(cnt > mMaxCards)
                mMaxCards = (int) cnt;
        }
        mSeriesList[0][list.size()] = 15;

        mCumulative = new double[4][];
        mCumulative[0] = mSeriesList[0];
        mCumulative[1] = createCumulativeInPercent(mSeriesList[1], totals[0], badNew);
        mCumulative[2] = createCumulativeInPercent(mSeriesList[2], totals[1], badYoung);
        mCumulative[3] = createCumulativeInPercent(mSeriesList[3], totals[2], badMature);

        mFirstElement = 0.5;
        mLastElement = 14.5;
        mMcount = 100;
        mMaxElements = 15;      //bars are positioned from 1 to 14
        if(mMaxCards == 0)
            mMaxCards = 10;

        return list.size() > 0;
    }

    /**
     * Cards Types
     */
    public boolean calculateCardsTypes(int type) {
        mTitle = R.string.stats_cards_types;
        mIsPieChart = true;
        mAxisTitles = new int[] { R.string.stats_answer_type, R.string.stats_answers, R.string.stats_cumulative_correct_percentage };

        mValueLabels = new int[] {R.string.statistics_mature, R.string.statistics_young_and_learn, R.string.statistics_unlearned, R.string.statistics_suspended};
        mColors = new int[] { R.color.stats_mature, R.color.stats_young, R.color.stats_unseen, R.color.stats_suspended };



        mType = type;


        ArrayList<double[]> list = new ArrayList<double[]>();
        double[] pieData;
        Cursor cur = null;
        String query = "select " +
                "sum(case when queue=2 and ivl >= 21 then 1 else 0 end), -- mtr\n" +
                "sum(case when queue in (1,3) or (queue=2 and ivl < 21) then 1 else 0 end), -- yng/lrn\n" +
                "sum(case when queue=0 then 1 else 0 end), -- new\n" +
                "sum(case when queue<0 then 1 else 0 end) -- susp\n" +
                "from cards where did in " + _limit();
        Timber.d("CardsTypes query: %s", query);

        try {
            cur = mCol.getDb()
                    .getDatabase()
                    .rawQuery(query, null);

            cur.moveToFirst();
            pieData = new double[]{ cur.getDouble(0), cur.getDouble(1), cur.getDouble(2), cur.getDouble(3) };


        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        //TODO adjust for CardsTypes, for now only copied from intervals
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

        mSeriesList = new double[1][4];
        mSeriesList[0]= pieData;

        mFirstElement = 0.5;
        mLastElement = 9.5;
        mMcount = 100;
        mMaxElements = 10;      //bars are positioned from 1 to 14
        if(mMaxCards == 0)
            mMaxCards = 10;
        return list.size() > 0;
    }

    /**
     * Tools ***********************************************************************************************
     */

    private String _limit() {
        if (mWholeCollection) {
            ArrayList<Long> ids = new ArrayList<Long>();
            for (JSONObject d : mCol.getDecks().all()) {
                try {
                    ids.add(d.getLong("id"));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            return Utils.ids2str(Utils.arrayList2array(ids));
        } else {
            return mCol.getSched()._deckLimit();
        }
    }


    private String _revlogLimit() {
        if (mWholeCollection) {
            return "";
        } else {
            return "cid IN (SELECT id FROM cards WHERE did IN " + Utils.ids2str(mCol.getDecks().active()) + ")";
        }
    }

    public static double[][] createCumulative(double [][] nonCumulative){
        double[][] cumulativeValues = new double[2][nonCumulative[0].length];
        cumulativeValues[0][0] = nonCumulative[0][0];
        cumulativeValues[1][0] = nonCumulative[1][0];
        for(int i = 1; i<nonCumulative[0].length; i++){
            cumulativeValues[0][i] = nonCumulative[0][i];
            cumulativeValues[1][i] = cumulativeValues[1][i-1] + nonCumulative[1][i];

        }

        return cumulativeValues;
    }

    public static double[][] createCumulative(double [][] nonCumulative, int startAtIndex){
        double[][] cumulativeValues = new double[2][nonCumulative[0].length - startAtIndex];
        cumulativeValues[0][0] = nonCumulative[0][startAtIndex];
        cumulativeValues[1][0] = nonCumulative[1][startAtIndex];
        for(int i = startAtIndex+1; i<nonCumulative[0].length; i++){
            cumulativeValues[0][i- startAtIndex] = nonCumulative[0][i];
            cumulativeValues[1][i- startAtIndex] = cumulativeValues[1][i-1- startAtIndex] + nonCumulative[1][i];

        }

        return cumulativeValues;
    }

    public static double[] createCumulative(double [] nonCumulative){
        double[] cumulativeValues = new double[nonCumulative.length];
        cumulativeValues[0] = nonCumulative[0];
        for(int i = 1; i<nonCumulative.length; i++){
            cumulativeValues[i] = cumulativeValues[i-1] + nonCumulative[i];
        }
        return cumulativeValues;
    }
    public static double[] createCumulativeInPercent(double [] nonCumulative, double total){
        return createCumulativeInPercent(nonCumulative, total, -1);
    }

    //use -1 on ignoreIndex if you do not want to exclude anything
    public static double[] createCumulativeInPercent(double [] nonCumulative, double total, int ignoreIndex){
        double[] cumulativeValues = new double[nonCumulative.length];
        if(total < 1)
            cumulativeValues[0] = 0;
        else if (0 != ignoreIndex)
            cumulativeValues[0] = nonCumulative[0] / total * 100.0;

        for(int i = 1; i<nonCumulative.length; i++){
            if(total < 1){
                cumulativeValues[i] = 0;
            } else if (i != ignoreIndex)
                cumulativeValues[i] = cumulativeValues[i-1] + nonCumulative[i] / total * 100.0;
            else
                cumulativeValues[i] = cumulativeValues[i-1];
        }
        return cumulativeValues;
    }

    private int _periodDays() {
        switch (mType){
            case TYPE_MONTH:
                return 30;
            case TYPE_YEAR:
                return 365;
            default:
            case TYPE_LIFE:
                return -1;
        }
    }
}
