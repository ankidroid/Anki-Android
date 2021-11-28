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

package com.ichi2.anki.stats;

import com.ichi2.libanki.stats.Stats;

/**
 * Interface between Stats.java and AdvancedStatistics.java
 */
public class StatsMetaInfo {
    boolean mDynamicAxis = false;
    boolean mHasColoredCumulative = false;
    Stats.AxisType mType;
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

    boolean mStatsCalculated;
    boolean mDataAvailable;

    public boolean isStatsCalculated() {
        return mStatsCalculated;
    }

    public void setStatsCalculated(boolean statsCalculated) {
        this.mStatsCalculated = statsCalculated;
    }

    public double[][] getmSeriesList() {
        return mSeriesList;
    }

    public void setmSeriesList(double[][] seriesList) {
        this.mSeriesList = seriesList;
    }

    public boolean isDataAvailable() {
        return mDataAvailable;
    }

    public void setDataAvailable(boolean dataAvailable) {
        this.mDataAvailable = dataAvailable;
    }

    public boolean ismDynamicAxis() {
        return mDynamicAxis;
    }

    public void setmDynamicAxis(boolean dynamicAxis) {
        this.mDynamicAxis = dynamicAxis;
    }

    public boolean ismHasColoredCumulative() {
        return mHasColoredCumulative;
    }

    public void setmHasColoredCumulative(boolean hasColoredCumulative) {
        this.mHasColoredCumulative = hasColoredCumulative;
    }

    public Stats.AxisType getmType() {
        return mType;
    }

    public void setmType(Stats.AxisType type) {
        this.mType = type;
    }

    public int getmTitle() {
        return mTitle;
    }

    public void setmTitle(int title) {
        this.mTitle = title;
    }

    public boolean ismBackwards() {
        return mBackwards;
    }

    public void setmBackwards(boolean backwards) {
        this.mBackwards = backwards;
    }

    public int[] getmValueLabels() {
        return mValueLabels;
    }

    public void setmValueLabels(int[] valueLabels) {
        this.mValueLabels = valueLabels;
    }

    public int[] getmColors() {
        return mColors;
    }

    public void setmColors(int[] colors) {
        this.mColors = colors;
    }

    public int[] getmAxisTitles() {
        return mAxisTitles;
    }

    public void setmAxisTitles(int[] axisTitles) {
        this.mAxisTitles = axisTitles;
    }

    public int getmMaxCards() {
        return mMaxCards;
    }

    public void setmMaxCards(int maxCards) {
        this.mMaxCards = maxCards;
    }

    public int getmMaxElements() {
        return mMaxElements;
    }

    public void setmMaxElements(int maxElements) {
        this.mMaxElements = maxElements;
    }

    public double getmFirstElement() {
        return mFirstElement;
    }

    public void setmFirstElement(double firstElement) {
        this.mFirstElement = firstElement;
    }

    public double getmLastElement() {
        return mLastElement;
    }

    public void setmLastElement(double lastElement) {
        this.mLastElement = lastElement;
    }

    public int getmZeroIndex() {
        return mZeroIndex;
    }

    public void setmZeroIndex(int zeroIndex) {
        this.mZeroIndex = zeroIndex;
    }

    public double[][] getmCumulative() {
        return mCumulative;
    }

    public void setmCumulative(double[][] cumulative) {
        this.mCumulative = cumulative;
    }

    public double getmMcount() {
        return mMcount;
    }

    public void setmMcount(double Mcount) {
        this.mMcount = Mcount;
    }
}
