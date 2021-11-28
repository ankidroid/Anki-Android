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

package com.ichi2.anki.stats

import android.annotation.SuppressLint
import com.ichi2.libanki.stats.Stats.AxisType
import com.ichi2.utils.KotlinCleanup

/**
 * Interface between Stats.java and AdvancedStatistics.java
 */
@KotlinCleanup("Remove field prefixes, make properties private")
@SuppressLint("VariableNamingDetector")
class StatsMetaInfo {
    var mDynamicAxis = false
    var mHasColoredCumulative = false
    var mType: AxisType? = null
    var mTitle = 0
    var mBackwards = false
    var mValueLabels: IntArray? = null
    var mColors: IntArray? = null
    var mAxisTitles: IntArray? = null
    var mMaxCards = 0
    var mMaxElements = 0
    var mFirstElement = 0.0
    var mLastElement = 0.0
    var mZeroIndex = 0
    var mCumulative: Array<DoubleArray>? = null
    var mMcount = 0.0
    var mSeriesList: Array<DoubleArray>? = null
    var isStatsCalculated = false
    var isDataAvailable = false
    fun getmSeriesList(): Array<DoubleArray>? {
        return mSeriesList
    }

    fun setmSeriesList(seriesList: Array<DoubleArray>) {
        mSeriesList = seriesList
    }

    fun ismDynamicAxis(): Boolean {
        return mDynamicAxis
    }

    fun setmDynamicAxis(dynamicAxis: Boolean) {
        mDynamicAxis = dynamicAxis
    }

    fun ismHasColoredCumulative(): Boolean {
        return mHasColoredCumulative
    }

    fun setmHasColoredCumulative(hasColoredCumulative: Boolean) {
        mHasColoredCumulative = hasColoredCumulative
    }

    fun getmType(): AxisType? {
        return mType
    }

    fun setmType(type: AxisType?) {
        mType = type
    }

    fun getmTitle(): Int {
        return mTitle
    }

    fun setmTitle(title: Int) {
        mTitle = title
    }

    fun ismBackwards(): Boolean {
        return mBackwards
    }

    fun setmBackwards(backwards: Boolean) {
        mBackwards = backwards
    }

    fun getmValueLabels(): IntArray? {
        return mValueLabels
    }

    fun setmValueLabels(valueLabels: IntArray?) {
        mValueLabels = valueLabels
    }

    fun getmColors(): IntArray? {
        return mColors
    }

    fun setmColors(colors: IntArray?) {
        mColors = colors
    }

    fun getmAxisTitles(): IntArray? {
        return mAxisTitles
    }

    fun setmAxisTitles(axisTitles: IntArray?) {
        mAxisTitles = axisTitles
    }

    fun getmMaxCards(): Int {
        return mMaxCards
    }

    fun setmMaxCards(maxCards: Int) {
        mMaxCards = maxCards
    }

    fun getmMaxElements(): Int {
        return mMaxElements
    }

    fun setmMaxElements(maxElements: Int) {
        mMaxElements = maxElements
    }

    fun getmFirstElement(): Double {
        return mFirstElement
    }

    fun setmFirstElement(firstElement: Double) {
        mFirstElement = firstElement
    }

    fun getmLastElement(): Double {
        return mLastElement
    }

    fun setmLastElement(lastElement: Double) {
        mLastElement = lastElement
    }

    fun getmZeroIndex(): Int {
        return mZeroIndex
    }

    fun setmZeroIndex(zeroIndex: Int) {
        mZeroIndex = zeroIndex
    }

    fun getmCumulative(): Array<DoubleArray>? {
        return mCumulative
    }

    fun setmCumulative(cumulative: Array<DoubleArray>?) {
        mCumulative = cumulative
    }

    fun getmMcount(): Double {
        return mMcount
    }

    fun setmMcount(Mcount: Double) {
        mMcount = Mcount
    }
}
