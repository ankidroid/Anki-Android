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
    var dynamicAxis = false
    var hasColoredCumulative = false
    var type: AxisType? = null
    var title = 0
    var backwards = false
    var valueLabels: IntArray? = null
    var colors: IntArray? = null
    var axisTitles: IntArray? = null
    var maxCards = 0
    var maxElements = 0
    var firstElement = 0.0
    var lastElement = 0.0
    var zeroIndex = 0
    var cumulative: Array<DoubleArray>? = null
    var mcount = 0.0
    var seriesList: Array<DoubleArray>? = null
    var isStatsCalculated = false
    var isDataAvailable = false
}
