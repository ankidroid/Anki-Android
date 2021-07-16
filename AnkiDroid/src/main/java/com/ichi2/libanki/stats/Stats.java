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

package com.ichi2.libanki.stats;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Preferences;
import com.ichi2.anki.R;
import com.ichi2.anki.stats.OverviewStatsBuilder;
import com.ichi2.anki.stats.OverviewStatsBuilder.OverviewStats.AnswerButtonsOverview;
import com.ichi2.anki.stats.StatsMetaInfo;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.utils.Time;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import timber.log.Timber;


@SuppressWarnings({"PMD.ExcessiveClassLength","PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.ExcessiveMethodLength","PMD.OneDeclarationPerLine",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.EmptyIfStmt"})
public class Stats {

    public enum AxisType {
        TYPE_MONTH(30, R.string.stats_period_month),
        TYPE_YEAR(365, R.string.stats_period_year),
        TYPE_LIFE(-1, R.string.stats_period_lifetime);

        public final int days;
        public final int descriptionId;
        AxisType(int dayss, int descriptionId) {
            this.days = dayss;
            this.descriptionId = descriptionId;
        }
    }

    public enum ChartType {FORECAST, REVIEW_COUNT, REVIEW_TIME,
        INTERVALS, HOURLY_BREAKDOWN, WEEKLY_BREAKDOWN, ANSWER_BUTTONS, CARDS_TYPES, OTHER}

    private final Collection mCol;
    private final boolean mWholeCollection;
    private final long mDeckId;
    private boolean mDynamicAxis = false;
    private double[][] mSeriesList;

    private boolean mHasColoredCumulative = false;
    private AxisType mType;
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

    public static final long SECONDS_PER_DAY = 86400L;
    public static final long ALL_DECKS_ID = 0L;

    public Stats(Collection col, long did) {
        mCol = col;
        mWholeCollection = (did == ALL_DECKS_ID);
        mDeckId = did;
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
            title = mCol.getDecks().get(mDeckId).getString("name");
        }
        return new Object[] {/*0*/ mType, /*1*/mTitle, /*2*/mBackwards, /*3*/mValueLabels, /*4*/mColors,
         /*5*/mAxisTitles, /*6*/title, /*7*/mMaxCards, /*8*/mMaxElements, /*9*/mFirstElement, /*10*/mLastElement,
                /*11*/mZeroIndex, /*12*/mFoundLearnCards, /*13*/mFoundCramCards, /*14*/mFoundRelearnCards, /*15*/mAverage,
                /*16*/mLongest, /*17*/mPeak, /*18*/mMcount, /*19*/mHasColoredCumulative, /*20*/mDynamicAxis};
    }


    /**
     * Today's statistics
     */
    public int[] calculateTodayStats(){
        String lim = _getDeckFilter();
        if (lim.length() > 0) {
            lim = " and " + lim;
        }

        String query = "select sum(case when ease > 0 then 1 else 0 end), "+ /* cards, excludes rescheduled cards https://github.com/ankidroid/Anki-Android/issues/8592 */
                "sum(time)/1000, "+ /*time*/
                "sum(case when ease = 1 then 1 else 0 end), "+ /* failed */
                "sum(case when type = " + Consts.CARD_TYPE_NEW + " then 1 else 0 end), "+ /* learning */
                "sum(case when type = " + Consts.CARD_TYPE_LRN + " then 1 else 0 end), "+ /* review */
                "sum(case when type = " + Consts.CARD_TYPE_REV + " then 1 else 0 end), "+ /* relearn */
                "sum(case when type = " + Consts.CARD_TYPE_RELEARNING + " then 1 else 0 end) "+ /* filter */
                "from revlog "+
                "where ease > 0 "+  // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
                "and id > " + ((mCol.getSched().getDayCutoff() - SECONDS_PER_DAY) * 1000) + " " +  lim;
        Timber.d("todays statistics query: %s", query);

        int cards, thetime, failed, lrn, rev, relrn, filt;
        try (Cursor cur = mCol.getDb()
                    .query(query)) {

            cur.moveToFirst();
            cards = cur.getInt(0);
            thetime = cur.getInt(1);
            failed = cur.getInt(2);
            lrn = cur.getInt(3);
            rev = cur.getInt(4);
            relrn = cur.getInt(5);
            filt = cur.getInt(6);



        }
        query = "select sum(case when ease > 0 then 1 else 0 end), "+ /* cards, excludes rescheduled cards https://github.com/ankidroid/Anki-Android/issues/8592 */
                "sum(case when ease = 1 then 0 else 1 end) from revlog " +
                "where ease > 0 "+ // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
                "and lastIvl >= 21 and id > " + ((mCol.getSched().getDayCutoff() - SECONDS_PER_DAY) * 1000) + " " +  lim;
        Timber.d("todays statistics query 2: %s", query);

        int mcnt, msum;
        try (Cursor cur = mCol.getDb()
                    .query(query)) {

            cur.moveToFirst();
            mcnt = cur.getInt(0);
            msum = cur.getInt(1);
        }

        return new int[]{cards, thetime, failed, lrn, rev, relrn, filt, mcnt, msum};
    }

    private String getRevlogTimeFilter(AxisType timespan, boolean inverse) {
        if (timespan == AxisType.TYPE_LIFE) {
            return "";
        }
        else {
            String operator;
            if (inverse) {
                operator = "<= ";
            } else {
                operator = "> ";
            }
            return "id "+ operator + ((mCol.getSched().getDayCutoff() - (timespan.days * SECONDS_PER_DAY)) * 1000);
        }
    }

    public Pair<Integer, Double> getNewCards(AxisType timespan) {
        int chunk = getChunk(timespan);
        int num = getNum(timespan);
        List<String> lims = new ArrayList<>(2);
        if (timespan != AxisType.TYPE_LIFE) {
            lims.add("id > " + (mCol.getSched().getDayCutoff() - (num * chunk * SECONDS_PER_DAY)) * 1000);
        }
        lims.add("did in " + _limit());
        String lim;
        if (!lims.isEmpty()) {
            lim = "where " + TextUtils.join(" and ", lims);
        } else {
            lim = "";
        }
        // PORTING: tf appears unused, but was passed into the SQL query
        double tf;
        if (timespan == AxisType.TYPE_MONTH) {
            tf = 60.0; //minutes
        } else {
            tf = 3600.0; //hours
        }


        long cut = mCol.getSched().getDayCutoff();
        int cardCount= mCol.getDb().queryScalar("select count(id) from cards " + lim);

        long periodDays = _periodDays(timespan); // 30|365|-1
        if (periodDays == -1) {
            periodDays = _deckAge(DeckAgeType.ADD);
        }
        // Porting - being safe to avoid DIV0
        if (periodDays == 0) {
            Timber.w("periodDays should not be 0");
            periodDays = 1;
        }

        return new Pair<>(cardCount, (double)cardCount / (double) periodDays);
    }

    private enum DeckAgeType { ADD, REVIEW }


    private long _deckAge(DeckAgeType by) {
        String lim = _revlogLimit();
        if (!TextUtils.isEmpty(lim)) {
            lim += " where " + lim;
        }
        double t = 0;
        if (by == DeckAgeType.REVIEW) {
            t = mCol.getDb().queryLongScalar("select id from revlog " + lim + " order by id limit 1");
        } else if (by == DeckAgeType.ADD) {
            lim = "where did in " + Utils.ids2str(mCol.getDecks().active());
            t = mCol.getDb().queryLongScalar("select id from cards " + lim + " order by id limit 1");
        }

        long period;
        if (t == 0) {
            period = 1;
        } else {
            period = Math.max(1, (int)(1+((mCol.getSched().getDayCutoff() - (t/1000)) / SECONDS_PER_DAY)));
        }
        return period;
    }


    private String _revlogLimit() {
        if (this.mWholeCollection) {
            return "";
        } else {
            return "cid in (select id from cards where did in " + Utils.ids2str(mCol.getDecks().active()) + ")";
        }
    }


    private String getRevlogFilter(AxisType timespan,boolean inverseTimeSpan){
        ArrayList<String> lims = new ArrayList<>(2);
        String dayFilter = getRevlogTimeFilter(timespan, inverseTimeSpan);
        if (!TextUtils.isEmpty(dayFilter)) {
            lims.add(dayFilter);
        }
        String lim = _getDeckFilter().replaceAll("[\\[\\]]", "");
        if (lim.length() > 0){
            lims.add(lim);
        }

        // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
        lims.add("ease > 0");

        lim = "WHERE ";
        lim += TextUtils.join(" AND ", lims.toArray());

        return lim;
    }

    public void calculateOverviewStatistics(AxisType timespan, OverviewStatsBuilder.OverviewStats oStats) {
        oStats.allDays = timespan.days;
        String lim = getRevlogFilter(timespan,false);
        try (Cursor cur = mCol.getDb().query(
                    "SELECT COUNT(*) as num_reviews, sum(case when type = " + Consts.CARD_TYPE_NEW + " then 1 else 0 end) as new_cards FROM revlog " + lim)) {
            while (cur.moveToNext()) {
                oStats.totalReviews = cur.getInt(0);
            }
        }

        String cntquery = "SELECT  COUNT(*) numDays, MIN(day) firstDay, SUM(time_per_day) sum_time  from (" +
                " SELECT (cast((id/1000 - " + mCol.getSched().getDayCutoff() + ") / "+SECONDS_PER_DAY+" AS INT)) AS day,  sum(time/1000.0/60.0) AS time_per_day"
                + " FROM revlog " + lim + " GROUP BY day ORDER BY day)";
        Timber.d("Count cntquery: %s", cntquery);
        try (Cursor cur = mCol.getDb().query(cntquery)) {
            while (cur.moveToNext()) {
                oStats.daysStudied = cur.getInt(0);
                oStats.totalTime = cur.getDouble(2);
                if (timespan == AxisType.TYPE_LIFE) {
                    oStats.allDays = Math.abs(cur.getInt(1)) + 1; // +1 for today
                }
            }
        }

        try (Cursor cur = mCol.getDb().query(
                    "select avg(ivl), max(ivl) from cards where did in " +_limit() + " and queue = " + Consts.QUEUE_TYPE_REV + "")) {
            cur.moveToFirst();
            oStats.averageInterval = cur.getDouble(0);
            oStats.longestInterval = cur.getDouble(1);
        }
        oStats.reviewsPerDayOnAll = (double) oStats.totalReviews / oStats.allDays;
        oStats.reviewsPerDayOnStudyDays = oStats.daysStudied == 0 ? 0 : (double) oStats.totalReviews / oStats.daysStudied;

        oStats.timePerDayOnAll = oStats.totalTime / oStats.allDays;
        oStats.timePerDayOnStudyDays = oStats.daysStudied == 0 ? 0 : oStats.totalTime / oStats.daysStudied;

        Pair<Integer, Double> newCardStats = getNewCards(timespan);
        oStats.totalNewCards = newCardStats.first;
        oStats.newCardsPerDay = newCardStats.second;

        ArrayList<double[]> list = eases(timespan);
        oStats.newCardsOverview = toOverview(0, list);
        oStats.youngCardsOverview = toOverview(1, list);
        oStats.matureCardsOverview = toOverview(2, list);

        String totalCountQuery = "select count(id), count(distinct nid) from cards where did in " + this._limit();
        try (Cursor cur = mCol.getDb().query(totalCountQuery)) {
            if (cur.moveToFirst()) {
                oStats.totalCards = cur.getLong(0);
                oStats.totalNotes = cur.getLong(1);
            }
        }

        String factorQuery = "select\n" +
                "min(factor) / 10.0,\n" +
                "avg(factor) / 10.0,\n" +
                "max(factor) / 10.0\n" +
                "from cards where did in " + _limit() + " and queue = " + Consts.QUEUE_TYPE_REV;
        try (Cursor cur = mCol.getDb().query(factorQuery)) {
            if (cur.moveToFirst()) {
                oStats.lowestEase = cur.getLong(0);
                oStats.averageEase = cur.getLong(1);
                oStats.highestEase = cur.getLong(2);
            }
        }
    }


    private AnswerButtonsOverview toOverview(int type, ArrayList<double[]> list) {
        AnswerButtonsOverview answerButtonsOverview = new AnswerButtonsOverview();

        final int INDEX_TYPE = 0; //0:learn; 1:young; 2:mature
        final int INDEX_EASE = 1; //1...4 - AGAIN - EASY
        final int INDEX_COUNT = 2;

        final double EASE_AGAIN = 1d;

        for (double[] elements : list) {
            //if we're not of the type we're looking for, continue
            if (elements[INDEX_TYPE] != type) {
                continue;
            }

            double answersCountForTypeAndEase = elements[INDEX_COUNT];
            boolean isAgain = elements[INDEX_EASE] == EASE_AGAIN;

            answerButtonsOverview.total += answersCountForTypeAndEase;
            answerButtonsOverview.correct += isAgain ? 0 : answersCountForTypeAndEase;
        }

        return answerButtonsOverview;
    }


    public boolean calculateDue(Context context, AxisType type) {
        // Not in libanki
        StatsMetaInfo metaInfo = new StatsMetaInfo();
        metaInfo = new AdvancedStatistics().calculateDueAsMetaInfo(metaInfo, type, context, _limit());
        if (metaInfo.isStatsCalculated()) {
            mDynamicAxis = metaInfo.ismDynamicAxis();
            mHasColoredCumulative = metaInfo.ismHasColoredCumulative();
            mType = metaInfo.getmType();
            mTitle = metaInfo.getmTitle();
            mBackwards = metaInfo.ismBackwards();
            mValueLabels = metaInfo.getmValueLabels();
            mColors = metaInfo.getmColors();
            mAxisTitles = metaInfo.getmAxisTitles();
            mMaxCards = metaInfo.getmMaxCards();
            mMaxElements = metaInfo.getmMaxElements();
            mFirstElement = metaInfo.getmFirstElement();
            mLastElement = metaInfo.getmLastElement();
            mZeroIndex = metaInfo.getmZeroIndex();
            mCumulative = metaInfo.getmCumulative();
            mMcount = metaInfo.getmMcount();
            mSeriesList = metaInfo.getmSeriesList();
            return metaInfo.isDataAvailable();
        } else {
            return calculateDue(type);
        }
    }

    /**
     * Due and cumulative due
     * ***********************************************************************************************
     */
    private boolean calculateDue(AxisType type) {
        mHasColoredCumulative = false;
        mType = type;
        mDynamicAxis = true;
        mBackwards = true;
        mTitle = R.string.stats_forecast;
        mValueLabels = new int[] { R.string.statistics_young, R.string.statistics_mature };
        mColors = new int[] { R.attr.stats_young, R.attr.stats_mature };
        mAxisTitles = new int[] { type.ordinal(), R.string.stats_cards, R.string.stats_cumulative_cards };
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

        ArrayList<int[]> dues = new ArrayList<>();
        String query = "SELECT (due - " + mCol.getSched().getToday() + ")/" + chunk
                + " AS day, " // day
                + "count(), " // all cards
                + "sum(CASE WHEN ivl >= 21 THEN 1 ELSE 0 END) " // mature cards
                + "FROM cards WHERE did IN " + _limit() + " AND queue IN (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")" + lim
                + " GROUP BY day ORDER BY day";
        Timber.d("Forecast query: %s", query);
        try (Cursor cur = mCol
                    .getDb().query(query)) {
            while (cur.moveToNext()) {
                dues.add(new int[] { cur.getInt(0), cur.getInt(1), cur.getInt(2) });
            }
        }
        // small adjustment for a proper chartbuilding with achartengine
        if (dues.isEmpty() || dues.get(0)[0] > 0) {
            dues.add(0, new int[] { 0, 0, 0 });
        }
        if (end == -1 && dues.size() < 2) {
            end = 31;
        }
        if (type != AxisType.TYPE_LIFE && dues.get(dues.size() - 1)[0] < end) {
            dues.add(new int[] { end, 0, 0 });
        } else if (type == AxisType.TYPE_LIFE && dues.size() < 2) {
            dues.add(new int[] { Math.max(12, dues.get(dues.size() - 1)[0] + 1), 0, 0 });
        }

        mSeriesList = new double[3][dues.size()];
        for (int i = 0; i < dues.size(); i++) {
            int[] data = dues.get(i);

            if (data[1] > mMaxCards) {
                mMaxCards = data[1];
            }

            mSeriesList[0][i] = data[0];
            mSeriesList[1][i] = data[1];
            mSeriesList[2][i] = data[2];
            if (data[0] > mLastElement) {
                mLastElement = data[0];
            }
            if (data[0] == 0) {
                mZeroIndex = i;
            }
        }
        mMaxElements = dues.size()-1;
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
        mHasColoredCumulative = false;
        mCumulative = Stats.createCumulative(new double[][]{mSeriesList[0], mSeriesList[1]}, mZeroIndex);
        mMcount = mCumulative[1][mCumulative[1].length-1];
        //some adjustments to not crash the chartbuilding with emtpy data
        if (mMaxElements == 0) {
            mMaxElements = 10;
        }
        if (mMcount == 0) {
            mMcount = 10;
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0;
            mLastElement = 6;
        }
        if (mMaxCards == 0) {
            mMaxCards = 10;
        }
        return !dues.isEmpty();
    }


    public boolean calculateReviewCount(AxisType type) {
        return calculateDone(type, ChartType.REVIEW_COUNT);
    }

    public boolean calculateReviewTime(AxisType type) {
        return calculateDone(type, ChartType.REVIEW_TIME);
    }


    /**
     * Calculation of Review count or Review time
     * @param type Type
     * @param charType CharType.REVIEW_COUNT or Chartype.REVIEW_TIME
     */
    private boolean calculateDone(AxisType type,  ChartType charType) {
        mHasColoredCumulative = true;
        mDynamicAxis = true;
        mType = type;
        mBackwards = true;
        if (charType == ChartType.REVIEW_COUNT) {
            mTitle = R.string.stats_review_count;
            mAxisTitles = new int[] { type.ordinal(), R.string.stats_answers, R.string.stats_cumulative_answers };
        } else if(charType == ChartType.REVIEW_TIME) {
            mTitle = R.string.stats_review_time;
        }
        mValueLabels = new int[] { R.string.statistics_cram, R.string.statistics_learn, R.string.statistics_relearn, R.string.statistics_young,
                R.string.statistics_mature };
        mColors = new int[] { R.attr.stats_cram, R.attr.stats_learn, R.attr.stats_relearn, R.attr.stats_young,
                R.attr.stats_mature };
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
        ArrayList<String> lims = new ArrayList<>(2);
        if (num != -1) {
            lims.add("id > " + ((mCol.getSched().getDayCutoff() - ((num + 1) * chunk * SECONDS_PER_DAY)) * 1000));
        }
        String lim = _getDeckFilter().replaceAll("[\\[\\]]", "");
        if (lim.length() > 0) {
            lims.add(lim);
        }
        if (!lims.isEmpty()) {
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
        if (charType == ChartType.REVIEW_TIME) {
            ti = "time/1000.0";
            if (mType == AxisType.TYPE_MONTH) {
                tf = "/60.0"; // minutes
                mAxisTitles = new int[] { type.ordinal(), R.string.stats_minutes, R.string.stats_cumulative_time_minutes };
            } else {
                tf = "/3600.0"; // hours
                mAxisTitles = new int[] { type.ordinal(), R.string.stats_hours, R.string.stats_cumulative_time_hours };
            }
        } else {
            ti = "1";
            tf = "";
        }
        ArrayList<double[]> list = new ArrayList<>();
        String query = "SELECT (cast((id/1000 - " + mCol.getSched().getDayCutoff() + ") / "+SECONDS_PER_DAY+" AS INT))/"
                + chunk + " AS day, " + "sum(CASE WHEN type = " + Consts.CARD_TYPE_NEW + " THEN " + ti + " ELSE 0 END)"
                + tf
                + ", " // lrn
                + "sum(CASE WHEN type = " + Consts.CARD_TYPE_LRN + " AND lastIvl < 21 THEN " + ti + " ELSE 0 END)" + tf
                + ", " // yng
                + "sum(CASE WHEN type = " + Consts.CARD_TYPE_LRN + " AND lastIvl >= 21 THEN " + ti + " ELSE 0 END)" + tf
                + ", " // mtr
                + "sum(CASE WHEN type = 2 THEN " + ti + " ELSE 0 END)" + tf + ", " // lapse
                + "sum(CASE WHEN type = " + Consts.CARD_TYPE_RELEARNING + " THEN " + ti + " ELSE 0 END)" + tf // cram
                + " FROM revlog " + lim + " GROUP BY day ORDER BY day";

        Timber.d("ReviewCount query: %s", query);

        try (Cursor cur = mCol
                    .getDb()
                    .query(query)) {
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(5), cur.getDouble(1), cur.getDouble(4),
                        cur.getDouble(2), cur.getDouble(3)});
            }
        }


        // small adjustment for a proper chartbuilding with achartengine
        if (type != AxisType.TYPE_LIFE && (list.isEmpty() || list.get(0)[0] > -num)) {
            list.add(0, new double[] { -num, 0, 0, 0, 0, 0 });
        } else if (type == AxisType.TYPE_LIFE && list.isEmpty()) {
            list.add(0, new double[] { -12, 0, 0, 0, 0, 0 });
        }
        if (list.get(list.size() - 1)[0] < 0) {
            list.add(new double[] { 0, 0, 0, 0, 0, 0 });
        }

        mSeriesList = new double[6][list.size()];
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            mSeriesList[0][i] = data[0]; // day
            mSeriesList[1][i] = data[1] + data[2] + data[3] + data[4] + data[5]; // cram
            mSeriesList[2][i] = data[2] + data[3] + data[4] + data[5]; // learn
            mSeriesList[3][i] = data[3] + data[4] + data[5]; // relearn
            mSeriesList[4][i] = data[4] + data[5]; // young
            mSeriesList[5][i] = data[5]; // mature

            if (mSeriesList[1][i] > mMaxCards) {
                mMaxCards = (int) Math.round(data[1] + data[2] + data[3] + data[4] + data[5]);
            }
            if (data[5] >= 0.999) {
                mFoundCramCards = true;
            }
            if (data[1] >= 0.999) {
                mFoundLearnCards = true;
            }
            if (data[2] >= 0.999) {
                mFoundRelearnCards = true;
            }
            if (data[0] > mLastElement) {
                mLastElement = data[0];
            }
            if (data[0] < mFirstElement) {
                mFirstElement = data[0];
            }
            if (data[0] == 0) {
                mZeroIndex = i;
            }
        }
        mMaxElements = list.size()-1;
        mCumulative = new double[6][];
        mCumulative[0] = mSeriesList[0];
        for (int i = 1; i < mSeriesList.length; i++) {
            mCumulative[i] = createCumulative(mSeriesList[i]);
            if (i > 1) {
                for (int j = 0; j < mCumulative[i - 1].length; j++) {
                    mCumulative[i - 1][j] -= mCumulative[i][j];
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
        for (int i = 1; i < mCumulative.length; i++) {
            for (int j = 0; j < mCumulative[i].length; j++) {
                if (mMcount < mCumulative[i][j])
                    mMcount = mCumulative[i][j];
            }
        }

        //some adjustments to not crash the chartbuilding with emtpy data
        if (mMaxCards == 0) {
            mMaxCards = 10;
        }
        if (mMaxElements == 0) {
            mMaxElements = 10;
        }
        if (mMcount == 0) {
            mMcount = 10;
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = -10;
            mLastElement = 0;
        }
        return !list.isEmpty();
    }


    private int getChunk(AxisType axisType) {
        switch (axisType) {
            case TYPE_MONTH: return 1;
            case TYPE_YEAR: return 7;
            case TYPE_LIFE: return 30;
            default: throw new IllegalStateException(String.format("Invalid axisType: %s", axisType));
        }
    }

    private int getNum(AxisType axisType) {
        switch (axisType) {
            case TYPE_MONTH: return 31;
            case TYPE_YEAR: return 52;
            case TYPE_LIFE: return -1; // Note: can also be 'None'
            default: throw new IllegalStateException(String.format("Invalid axisType: %s", axisType));
        }
    }

    /**
     * Intervals ***********************************************************************************************
     */

    public boolean calculateIntervals(Context context, AxisType type) {
        mDynamicAxis = true;
        mType = type;
        double all = 0, avg = 0, max_ = 0;
        mBackwards = false;
        mTitle = R.string.stats_review_intervals;
        mAxisTitles = new int[] { type.ordinal(), R.string.stats_cards, R.string.stats_percentage };
        mValueLabels = new int[] { R.string.stats_cards_intervals};
        mColors = new int[] { R.attr.stats_interval};
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

        ArrayList<double[]> list = new ArrayList<>(52); // Max of `num`, given that we probably won't have card with more than 52 year interval
        try (Cursor cur = mCol
                    .getDb()
                    .query(
                            "select ivl / " + chunk + " as grp, count() from cards " +
                                    "where did in "+ _limit() +" and queue = " + Consts.QUEUE_TYPE_REV + " " + lim + " " +
                                    "group by grp " +
                                    "order by grp")) {
            while (cur.moveToNext()) {
                list.add(new double[] {cur.getDouble(0), cur.getDouble(1)});
            }
        }
        try (Cursor cur = mCol
                    .getDb()
                    .query(
                            "select count(), avg(ivl), max(ivl) from cards where did in " +_limit() +
                                    " and queue = " + Consts.QUEUE_TYPE_REV + "")) {
            cur.moveToFirst();
            all = cur.getDouble(0);
            avg = cur.getDouble(1);
            max_ = cur.getDouble(2);
        }

        // small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty() || list.get(0)[0] > 0) {
            list.add(0, new double[] { 0, 0, 0 });
        }
        if (num == -1 && list.size() < 2) {
            num = 31;
        }
        if (type != AxisType.TYPE_LIFE && list.get(list.size() - 1)[0] < num) {
            list.add(new double[] { num, 0 });
        } else if (type == AxisType.TYPE_LIFE && list.size() < 2) {
            list.add(new double[] { Math.max(12, list.get(list.size() - 1)[0] + 1), 0 });
        }

        mLastElement = 0;
        mSeriesList = new double[2][list.size()];
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            mSeriesList[0][i] = data[0]; // grp
            mSeriesList[1][i] = data[1]; // cnt
            if (mSeriesList[1][i] > mMaxCards)
                mMaxCards = (int) Math.round(data[1]);
            if (data[0] > mLastElement)
                mLastElement = data[0];

        }
        mCumulative = createCumulative(mSeriesList);
        for (int i = 0; i < list.size(); i++) {
            mCumulative[1][i] /= all / 100;
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
        mMaxElements = list.size() - 1;
        mAverage = Utils.timeSpan(context, Math.round(avg * SECONDS_PER_DAY));
        mLongest = Utils.timeSpan(context, Math.round(max_ * SECONDS_PER_DAY));

        //some adjustments to not crash the chartbuilding with emtpy data
        if (mMaxElements == 0) {
            mMaxElements = 10;
        }
        if (mMcount == 0) {
            mMcount = 10;
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0;
            mLastElement = 6;
        }
        if (mMaxCards == 0) {
            mMaxCards = 10;
        }
        return !list.isEmpty();
    }

    /**
     * Hourly Breakdown
     */
    public boolean calculateBreakdown(AxisType type) {
        mTitle = R.string.stats_breakdown;
        mBackwards = false;
        mAxisTitles = new int[] { R.string.stats_time_of_day, R.string.stats_percentage_correct, R.string.stats_reviews };
        mValueLabels = new int[] { R.string.stats_percentage_correct, R.string.stats_answers};
        mColors = new int[] { R.attr.stats_counts, R.attr.stats_hours};
        mType = type;
        String lim = _getDeckFilter().replaceAll("[\\[\\]]", "");

        if (lim.length() > 0) {
            lim = " and " + lim;
        }
        int rolloverHour = Preferences.getDayOffset(mCol);
        int pd = _periodDays();
        if (pd > 0) {
            lim += " and id > " + ((mCol.getSched().getDayCutoff() -  (SECONDS_PER_DAY * pd)) * 1000);
        }
        long cutoff = mCol.getSched().getDayCutoff();
        long cut = cutoff - rolloverHour * 3600;

        ArrayList<double[]> list = new ArrayList<>(24); // number of hours
        for (int i = 0; i < 24; i++) {
            list.add(new double[] { i, 0, 0 });
        }
        String query = "select " +
                "23 - ((cast((" + cut + " - id/1000) / 3600.0 as int)) % 24) as hour, " +
                "sum(case when ease = 1 then 0 else 1 end) / " +
                "cast(count() as float) * 100, " +
                "count() " +
                "from revlog where type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_LRN + "," + Consts.CARD_TYPE_REV + ") " + lim +" " +
                "group by hour having count() > 30 order by hour";
        Timber.d("%d : %d breakdown query: %s", rolloverHour, cutoff, query);
        try (Cursor cur = mCol.getDb()
                    .query(query)) {
            while (cur.moveToNext()) {
                double[] hourData = new double[] { cur.getDouble(0), cur.getDouble(1), cur.getDouble(2) };
                list.set(((((int)hourData[0] % 24) + 24) % 24), hourData); // Force the data to be positive int in 0-23 range
            }
        }

        //TODO adjust for breakdown, for now only copied from intervals
        //small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty()) {
            list.add(0, new double[] { 0, 0, 0 });
        }

        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int intHour = (int) data[0];
            int hour = (intHour - 4) % 24;
            if (hour < 0) {
                hour += 24;
            }
            data[0] = hour;
            list.set(i, data);
        }
        Collections.sort(list, (s1, s2) -> Double.compare(s1[0], s2[0]));

        mSeriesList = new double[4][list.size()];
        mPeak = 0.0;
        mMcount = 0.0;
        double minHour = Double.MAX_VALUE;
        double maxHour = 0;
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int hour = (int)data[0];

            //double hour = data[0];
            if (hour < minHour) {
                minHour = hour;
            }
            if (hour > maxHour) {
                maxHour = hour;
            }
            double pct = data[1];
            if (pct > mPeak) {
                mPeak = pct;
            }
            mSeriesList[0][i] = hour;
            mSeriesList[1][i] = pct;
            mSeriesList[2][i] = data[2];
            if (i == 0) {
                mSeriesList[3][i] = pct;
            } else {
                double prev = mSeriesList[3][i - 1];
                double diff = pct - prev;
                diff /= 3.0;
                diff = Math.round(diff * 10.0) / 10.0;
                mSeriesList[3][i] = prev + diff;
            }
            if (data[2] > mMcount) {
                mMcount = data[2];
            }
            if (mSeriesList[1][i] > mMaxCards) {
                mMaxCards = (int) mSeriesList[1][i];
            }
        }

        mFirstElement = mSeriesList[0][0];
        mLastElement = mSeriesList[0][mSeriesList[0].length - 1];
        mMaxElements = (int) (maxHour - minHour);

        //some adjustments to not crash the chartbuilding with emtpy data
        if (mMaxElements == 0) {
            mMaxElements = 10;
        }
        if (mMcount == 0) {
            mMcount = 10;
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0;
            mLastElement = 23;
        }
        if (mMaxCards == 0) {
            mMaxCards = 10;
        }
        return !list.isEmpty();
    }

    /**
     * Weekly Breakdown
     */
    public boolean calculateWeeklyBreakdown(AxisType type) {
        mTitle = R.string.stats_weekly_breakdown;
        mBackwards = false;
        mAxisTitles = new int[] { R.string.stats_day_of_week, R.string.stats_percentage_correct, R.string.stats_reviews };
        mValueLabels = new int[] { R.string.stats_percentage_correct, R.string.stats_answers};
        mColors = new int[] { R.attr.stats_counts, R.attr.stats_hours};
        mType = type;
        String lim = _getDeckFilter().replaceAll("[\\[\\]]", "");

        if (lim.length() > 0) {
            lim = " and " + lim;
        }

        Calendar sd = Time.gregorianCalendar(mCol.getSched().getDayCutoff() * 1000);

        int pd = _periodDays();
        if (pd > 0) {
            pd = Math.round( pd / 7 ) * 7;
            lim += " and id > " + ((mCol.getSched().getDayCutoff() - (SECONDS_PER_DAY * pd)) * 1000);
        }

        long cutoff = mCol.getSched().getDayCutoff();
        ArrayList<double[]> list = new ArrayList<>(7); // one by day of the week
        String query = "SELECT strftime('%w',datetime( cast(id/ 1000  -" + sd.get(Calendar.HOUR_OF_DAY) * 3600 +
                " as int), 'unixepoch')) as wd, " +
                "sum(case when ease = 1 then 0 else 1 end) / " +
                "cast(count() as float) * 100, " +
                "count() " +
                "from revlog " +
                "where type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_LRN + "," + Consts.CARD_TYPE_REV + ") " + lim +" " +
                "group by wd " +
                "order by wd";
        Timber.d(sd.get(Calendar.HOUR_OF_DAY) + " : " +cutoff + " weekly breakdown query: %s", query);
        try (Cursor cur = mCol.getDb()
                    .query(query)) {
            while (cur.moveToNext()) {
                list.add(new double[] { cur.getDouble(0), cur.getDouble(1), cur.getDouble(2) });
            }
        }

        //TODO adjust for breakdown, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty() ) {
            list.add(0, new double[] { 0, 0, 0 });
        }

        mSeriesList = new double[4][list.size()];
        mPeak = 0.0;
        mMcount = 0.0;
        double minHour = Double.MAX_VALUE;
        double maxHour = 0;
        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int hour = (int) data[0];

            //double hour = data[0];
            if (hour < minHour) {
                minHour = hour;
            }
            if (hour > maxHour) {
                maxHour = hour;
            }
            double pct = data[1];
            if (pct > mPeak) {
                mPeak = pct;
            }

            mSeriesList[0][i] = hour;
            mSeriesList[1][i] = pct;
            mSeriesList[2][i] = data[2];
            if (i == 0) {
                mSeriesList[3][i] = pct;
            } else {
                double prev = mSeriesList[3][i - 1];
                double diff = pct - prev;
                diff /= 3.0;
                diff = Math.round(diff * 10.0) / 10.0;
                mSeriesList[3][i] = prev + diff;
            }
            if (data[2] > mMcount) {
                mMcount = data[2];
            }
            if (mSeriesList[1][i] > mMaxCards) {
                mMaxCards = (int) mSeriesList[1][i];
            }
        }
        mFirstElement = mSeriesList[0][0];
        mLastElement = mSeriesList[0][mSeriesList[0].length - 1];
        mMaxElements = (int) (maxHour - minHour);

        //some adjustments to not crash the chartbuilding with emtpy data
        if (mMaxElements == 0) {
            mMaxElements = 10;
        }
        if (mMcount == 0) {
            mMcount = 10;
        }
        if (mFirstElement == mLastElement) {
            mFirstElement = 0;
            mLastElement = 6;
        }
        if (mMaxCards == 0) {
            mMaxCards = 10;
        }
        return !list.isEmpty();
    }


    /**
     * Answer Buttons
     */
    public boolean calculateAnswerButtons(AxisType type) {
        mHasColoredCumulative = false;
        mCumulative = null;
        mTitle = R.string.stats_answer_buttons;
        mBackwards = false;
        mAxisTitles = new int[] { R.string.stats_answer_type, R.string.stats_answers };
        mValueLabels = new int[] { R.string.statistics_learn, R.string.statistics_young, R.string.statistics_mature};
        mColors = new int[] { R.attr.stats_learn, R.attr.stats_young, R.attr.stats_mature};
        mType = type;
        ArrayList<double[]> list = eases(type);

        //TODO adjust for AnswerButton, for now only copied from intervals
        // small adjustment for a proper chartbuilding with achartengine
        if (list.isEmpty()) {
            list.add(0, new double[]{0, 1, 0});
        }

        mSeriesList = new double[4][list.size()+1];

        for (int i = 0; i < list.size(); i++) {
            double[] data = list.get(i);
            int currentType = (int)data[0];
            double ease = data[1];
            double cnt = data[2];

            if (currentType == Consts.CARD_TYPE_LRN) {
                ease += 5;
            } else if (currentType == 2) {
                ease += 10;
            }
            mSeriesList[0][i] = ease;
            mSeriesList[1 + currentType][i] = cnt;
            if (cnt > mMaxCards) {
                mMaxCards = (int) cnt;
            }
        }
        mSeriesList[0][list.size()] = 15;

        mFirstElement = 0.5;
        mLastElement = 14.5;
        mMcount = 100;
        mMaxElements = 15;      //bars are positioned from 1 to 14
        if(mMaxCards == 0) {
            mMaxCards = 10;
        }
        return !list.isEmpty();
    }


    private ArrayList<double[]> eases(AxisType type) {
        String lim = _getDeckFilter().replaceAll("[\\[\\]]", "");

        Vector<String> lims = new Vector<>();
        int days;

        if (lim.length() > 0) {
            lims.add(lim);
        }

        if (type == AxisType.TYPE_MONTH) {
            days = 30;
        } else if (type == AxisType.TYPE_YEAR) {
            days = 365;
        } else {
            days = -1;
        }

        if (days > 0) {
            lims.add("id > " + ((mCol.getSched().getDayCutoff() - (days * SECONDS_PER_DAY)) * 1000));
        }

        // Anki Desktop logs a '0' ease for manual reschedules, ignore them https://github.com/ankidroid/Anki-Android/issues/8008
        lims.add("ease > 0");

        lim = "where " + lims.get(0);
        for (int i = 1; i < lims.size(); i++) {
            lim += " and " + lims.get(i);
        }

        String ease4repl;
        if (mCol.schedVer() == 1) {
            ease4repl = "3";
        } else {
            ease4repl = "ease";
        }
        ArrayList<double[]> list = new ArrayList<>(3 * 4); // 3 thetypes * 4 eases
        String query = "select (case " +
                "                when type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_REV + ") then 0 " +
                "        when lastIvl < 21 then 1 " +
                "        else 2 end) as thetype, " +
                "        (case when type in (" + Consts.CARD_TYPE_NEW + "," + Consts.CARD_TYPE_REV + ") and ease = 4 then " + ease4repl +" else ease end), count() from revlog " + lim + " " +
                "        group by thetype, ease " +
                "        order by thetype, ease";
        Timber.d("AnswerButtons query: %s", query);

        try (Cursor cur = mCol.getDb()
                    .query(query)) {
            while (cur.moveToNext()) {
                list.add(new double[]{cur.getDouble(0), cur.getDouble(1), cur.getDouble(2)});
            }
        }
        return list;
    }


    /**
     * Card Types
     */
    public void calculateCardTypes(AxisType type) {
        mTitle = R.string.title_activity_template_editor;
        mBackwards = false;
        mAxisTitles = new int[] { R.string.stats_answer_type, R.string.stats_answers, R.string.stats_cumulative_correct_percentage };
        mValueLabels = new int[] {R.string.statistics_mature, R.string.statistics_young_and_learn, R.string.statistics_unlearned, R.string.statistics_suspended, R.string.statistics_buried};
        mColors = new int[] { R.attr.stats_mature, R.attr.stats_young, R.attr.stats_unseen, R.attr.stats_suspended, R.attr.stats_buried };
        mType = type;
        double[] pieData;
        String query = "select " +
                "sum(case when queue=" + Consts.QUEUE_TYPE_REV + " and ivl >= 21 then 1 else 0 end), -- mtr\n" +
                "sum(case when queue in (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") or (queue=" + Consts.QUEUE_TYPE_REV + " and ivl < 21) then 1 else 0 end), -- yng/lrn\n" +
                "sum(case when queue=" + Consts.QUEUE_TYPE_NEW + " then 1 else 0 end), -- new\n" +
                "sum(case when queue=" + Consts.QUEUE_TYPE_SUSPENDED + " then 1 else 0 end), -- susp\n" +
                "sum(case when queue in (" + Consts.QUEUE_TYPE_MANUALLY_BURIED + "," + Consts.QUEUE_TYPE_SIBLING_BURIED + ") then 1 else 0 end) -- buried\n" +
                "from cards where did in " + _limit();
        Timber.d("CardsTypes query: %s", query);

        try (Cursor cur = mCol.getDb()
                    .query(query)) {

            cur.moveToFirst();
            pieData = new double[]{ cur.getDouble(0), cur.getDouble(1), cur.getDouble(2), cur.getDouble(3), cur.getDouble(4) };
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

        mSeriesList = new double[1][5];
        mSeriesList[0] = pieData;
        mFirstElement = 0.5;
        mLastElement = 9.5;
        mMcount = 100;
        mMaxElements = 10;      //bars are positioned from 1 to 14
        if (mMaxCards == 0) {
            mMaxCards = 10;
        }
    }

    /**
     * Tools ***********************************************************************************************
     */

    private String _limit() {
        return deckLimit(mDeckId, mCol);
    }


    /**
     * Note: NOT in libanki
     * Return a string of deck ids for the provided deck and its children, suitable for an SQL query
     * @param deckId the deck id to filter on, or ALL_DECKS_ID for all decks
     * @param col collection
     * @return
     */
    public static String deckLimit(long deckId, Collection col) {
        if (deckId == ALL_DECKS_ID) {
            // All decks
            List<Deck> decks = col.getDecks().all();
            ArrayList<Long> ids = new ArrayList<>(decks.size());
            for (Deck d : decks) {
                ids.add(d.getLong("id"));
            }
            return Utils.ids2str(ids);
        } else {
            // The given deck id and its children
            java.util.Collection<Long> values = col.getDecks().children(deckId).values();
            ArrayList<Long> ids = new ArrayList<>(values.size());
            ids.add(deckId);
            ids.addAll(values);
            return Utils.ids2str(ids);
        }
    }

    private String _getDeckFilter() {
        if (mWholeCollection) {
            return "";
        } else {
            return "cid IN (SELECT id FROM cards WHERE did IN " + _limit() + ")";
        }
    }


    public static double[][] createCumulative(double[][] nonCumulative) {
        double[][] cumulativeValues = new double[2][nonCumulative[0].length];
        cumulativeValues[0][0] = nonCumulative[0][0];
        cumulativeValues[1][0] = nonCumulative[1][0];
        for (int i = 1; i < nonCumulative[0].length; i++) {
            cumulativeValues[0][i] = nonCumulative[0][i];
            cumulativeValues[1][i] = cumulativeValues[1][i - 1] + nonCumulative[1][i];
        }
        return cumulativeValues;
    }


    public static double[][] createCumulative(double[][] nonCumulative, int startAtIndex) {
        double[][] cumulativeValues = new double[2][nonCumulative[0].length - startAtIndex];
        cumulativeValues[0][0] = nonCumulative[0][startAtIndex];
        cumulativeValues[1][0] = nonCumulative[1][startAtIndex];
        for (int i = startAtIndex + 1; i < nonCumulative[0].length; i++) {
            cumulativeValues[0][i - startAtIndex] = nonCumulative[0][i];
            cumulativeValues[1][i - startAtIndex] = cumulativeValues[1][i - 1 - startAtIndex] + nonCumulative[1][i];
        }
        return cumulativeValues;
    }


    public static double[] createCumulative(double[] nonCumulative) {
        double[] cumulativeValues = new double[nonCumulative.length];
        cumulativeValues[0] = nonCumulative[0];
        for (int i = 1; i < nonCumulative.length; i++) {
            cumulativeValues[i] = cumulativeValues[i - 1] + nonCumulative[i];
        }
        return cumulativeValues;
    }

    private int _periodDays() {
        return _periodDays(mType);
    }


    private int _periodDays(AxisType type) {
        switch (type) {
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
