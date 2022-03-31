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

import com.ichi2.libanki.stats.Stats.AxisType

/**
 * Interface between Stats.java and AdvancedStatistics.java
 */
class StatsMetaInfo {
    private var dynamicAxis = false
    private var hasColoredCumulative = false
    private var type: AxisType? = null
    private var title = 0
    private var backwards = false
    private var valueLabels: IntArray? = null
    private var colors: IntArray? = null
    private var axisTitles: IntArray? = null
    private var maxCards = 0
    private var maxElements = 0
    private var firstElement = 0.0
    private var lastElement = 0.0
    private var zeroIndex = 0
    private var cumulative: Array<DoubleArray>? = null
    private var mcount = 0.0
    private var seriesList: Array<DoubleArray>? = null
    var isStatsCalculated = false
    var isDataAvailable = false
    fun getmSeriesList(): Array<DoubleArray>? {
        return seriesList
    }

    fun setmSeriesList(seriesList: Array<DoubleArray>) {
        this.seriesList = seriesList
    }

    fun ismDynamicAxis(): Boolean {
        return dynamicAxis
    }

    fun setmDynamicAxis(dynamicAxis: Boolean) {
        this.dynamicAxis = dynamicAxis
    }

    fun ismHasColoredCumulative(): Boolean {
        return hasColoredCumulative
    }

    fun setmHasColoredCumulative(hasColoredCumulative: Boolean) {
        this.hasColoredCumulative = hasColoredCumulative
    }

    fun getmType(): AxisType? {
        return type
    }

    fun setmType(type: AxisType?) {
        this.type = type
    }

    fun getmTitle(): Int {
        return title
    }

    fun setmTitle(title: Int) {
        this.title = title
    }

    fun ismBackwards(): Boolean {
        return backwards
    }

    fun setmBackwards(backwards: Boolean) {
        this.backwards = backwards
    }

    fun getmValueLabels(): IntArray? {
        return valueLabels
    }

    fun setmValueLabels(valueLabels: IntArray?) {
        this.valueLabels = valueLabels
    }

    fun getmColors(): IntArray? {
        return colors
    }

    fun setmColors(colors: IntArray?) {
        this.colors = colors
    }

    fun getmAxisTitles(): IntArray? {
        return axisTitles
    }

    fun setmAxisTitles(axisTitles: IntArray?) {
        this.axisTitles = axisTitles
    }

    fun getmMaxCards(): Int {
        return maxCards
    }

    fun setmMaxCards(maxCards: Int) {
        this.maxCards = maxCards
    }

    fun getmMaxElements(): Int {
        return maxElements
    }

    fun setmMaxElements(maxElements: Int) {
        this.maxElements = maxElements
    }

    fun getmFirstElement(): Double {
        return firstElement
    }

    fun setmFirstElement(firstElement: Double) {
        this.firstElement = firstElement
    }

    fun getmLastElement(): Double {
        return lastElement
    }

    fun setmLastElement(lastElement: Double) {
        this.lastElement = lastElement
    }

    fun getmZeroIndex(): Int {
        return zeroIndex
    }

    fun setmZeroIndex(zeroIndex: Int) {
        this.zeroIndex = zeroIndex
    }

    fun getmCumulative(): Array<DoubleArray>? {
        return cumulative
    }

    fun setmCumulative(cumulative: Array<DoubleArray>?) {
        this.cumulative = cumulative
    }

    fun getmMcount(): Double {
        return mcount
    }

    fun setmMcount(Mcount: Double) {
        mcount = Mcount
    }
}
