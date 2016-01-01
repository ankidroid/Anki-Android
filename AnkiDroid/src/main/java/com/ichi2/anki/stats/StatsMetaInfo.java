package com.ichi2.anki.stats;

/**
 * Created by Jeffrey on 1-1-2016.
 */
public class StatsMetaInfo {
    boolean mDynamicAxis = false;
    boolean mHasColoredCumulative = false;
    int mType;
    int mTitle;
    boolean mBackwards;
    int[] mValueLabels;
    int[] mColors;
    int[] mAxisTitles;
    int mMaxCards = 0;
    int mMaxElements = 0;
    double mFirstElement = 0;
    double mLastElement = 0;
    int mZeroIndex = 0;
    double[][] mCumulative = null;
    double mMcount;

    double[][] mSeriesList;

    boolean statsCalculated;
    boolean dataAvailable;

    public boolean isStatsCalculated() {
        return statsCalculated;
    }

    public void setStatsCalculated(boolean statsCalculated) {
        this.statsCalculated = statsCalculated;
    }

    public double[][] getmSeriesList() {
        return mSeriesList;
    }

    public void setmSeriesList(double[][] mSeriesList) {
        this.mSeriesList = mSeriesList;
    }

    public boolean isDataAvailable() {
        return dataAvailable;
    }

    public void setDataAvailable(boolean dataAvailable) {
        this.dataAvailable = dataAvailable;
    }

    public boolean ismDynamicAxis() {
        return mDynamicAxis;
    }

    public void setmDynamicAxis(boolean mDynamicAxis) {
        this.mDynamicAxis = mDynamicAxis;
    }

    public boolean ismHasColoredCumulative() {
        return mHasColoredCumulative;
    }

    public void setmHasColoredCumulative(boolean mHasColoredCumulative) {
        this.mHasColoredCumulative = mHasColoredCumulative;
    }

    public int getmType() {
        return mType;
    }

    public void setmType(int mType) {
        this.mType = mType;
    }

    public int getmTitle() {
        return mTitle;
    }

    public void setmTitle(int mTitle) {
        this.mTitle = mTitle;
    }

    public boolean ismBackwards() {
        return mBackwards;
    }

    public void setmBackwards(boolean mBackwards) {
        this.mBackwards = mBackwards;
    }

    public int[] getmValueLabels() {
        return mValueLabels;
    }

    public void setmValueLabels(int[] mValueLabels) {
        this.mValueLabels = mValueLabels;
    }

    public int[] getmColors() {
        return mColors;
    }

    public void setmColors(int[] mColors) {
        this.mColors = mColors;
    }

    public int[] getmAxisTitles() {
        return mAxisTitles;
    }

    public void setmAxisTitles(int[] mAxisTitles) {
        this.mAxisTitles = mAxisTitles;
    }

    public int getmMaxCards() {
        return mMaxCards;
    }

    public void setmMaxCards(int mMaxCards) {
        this.mMaxCards = mMaxCards;
    }

    public int getmMaxElements() {
        return mMaxElements;
    }

    public void setmMaxElements(int mMaxElements) {
        this.mMaxElements = mMaxElements;
    }

    public double getmFirstElement() {
        return mFirstElement;
    }

    public void setmFirstElement(double mFirstElement) {
        this.mFirstElement = mFirstElement;
    }

    public double getmLastElement() {
        return mLastElement;
    }

    public void setmLastElement(double mLastElement) {
        this.mLastElement = mLastElement;
    }

    public int getmZeroIndex() {
        return mZeroIndex;
    }

    public void setmZeroIndex(int mZeroIndex) {
        this.mZeroIndex = mZeroIndex;
    }

    public double[][] getmCumulative() {
        return mCumulative;
    }

    public void setmCumulative(double[][] mCumulative) {
        this.mCumulative = mCumulative;
    }

    public double getmMcount() {
        return mMcount;
    }

    public void setmMcount(double mMcount) {
        this.mMcount = mMcount;
    }
}
