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
    @JvmField
    var dynamicAxis = false
    @JvmField
    var hasColoredCumulative = false
    @JvmField
    var type: AxisType? = null
    @JvmField
    var title = 0
    @JvmField
    var backwards = false
    @JvmField
    var valueLabels: IntArray? = null
    @JvmField
    var colors: IntArray? = null
    @JvmField
    var axisTitles: IntArray? = null
    @JvmField
    var maxCards = 0
    @JvmField
    var maxElements = 0
    @JvmField
    var firstElement = 0.0
    @JvmField
    var lastElement = 0.0
    @JvmField
    var zeroIndex = 0
    @JvmField
    var cumulative: Array<DoubleArray>? = null
    @JvmField
    var mcount = 0.0
    @JvmField
    var seriesList: Array<DoubleArray>? = null
    @JvmField
    var isStatsCalculated = false
    @JvmField
    var isDataAvailable = false
}
