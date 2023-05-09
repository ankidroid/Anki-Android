/****************************************************************************************
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
package com.ichi2.anki.stats

import android.R
import android.graphics.Paint
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.stats.Stats
import com.ichi2.libanki.stats.Stats.AxisType
import com.ichi2.libanki.stats.Stats.ChartType
import com.ichi2.themes.Themes.getColorFromAttr
import com.wildplot.android.rendering.*
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap
import timber.log.Timber
import kotlin.math.*

class ChartBuilder(private val chartView: ChartView, private val collectionData: Collection, private val deckId: DeckId, private val chartType: ChartType) {
    private var mMaxCards = 0
    private var mBackwards = false
    private lateinit var mValueLabels: IntArray
    private lateinit var mColors: IntArray
    private lateinit var mAxisTitles: IntArray
    private lateinit var mSeriesList: Array<DoubleArray>
    private var mLastElement = 0.0
    private var mCumulative: Array<DoubleArray>? = null
    private var mFirstElement = 0.0
    private var mHasColoredCumulative = false
    private var mMcount = 0.0
    private var mDynamicAxis = false
    private fun calcStats(type: AxisType) {
        val stats = Stats(collectionData, deckId)
        when (chartType) {
            ChartType.FORECAST -> stats.calculateDue(chartView.context, type)
            ChartType.REVIEW_COUNT -> stats.calculateReviewCount(type)
            ChartType.REVIEW_TIME -> stats.calculateReviewTime(type)
            ChartType.INTERVALS -> stats.calculateIntervals(chartView.context, type)
            ChartType.HOURLY_BREAKDOWN -> stats.calculateBreakdown(type)
            ChartType.WEEKLY_BREAKDOWN -> stats.calculateWeeklyBreakdown(type)
            ChartType.ANSWER_BUTTONS -> stats.calculateAnswerButtons(type)
            ChartType.CARDS_TYPES -> stats.calculateCardTypes(type)
            else -> {}
        }
        mCumulative = stats.cumulative
        mSeriesList = stats.seriesList!!
        val metaData = stats.metaInfo
        mBackwards = metaData[2] as Boolean
        mValueLabels = metaData[3] as IntArray
        mColors = metaData[4] as IntArray
        mAxisTitles = metaData[5] as IntArray
        mMaxCards = metaData[7] as Int
        mLastElement = metaData[10] as Double
        mFirstElement = metaData[9] as Double
        mHasColoredCumulative = metaData[19] as Boolean
        mMcount = metaData[18] as Double
        mDynamicAxis = metaData[20] as Boolean
    }

    fun renderChart(type: AxisType): PlotSheet? {
        calcStats(type)
        val paint = Paint(Paint.LINEAR_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        val height = chartView.measuredHeight
        val width = chartView.measuredWidth
        Timber.d("height: %d, width: %d, %d", height, width, chartView.width)
        if (height <= 0 || width <= 0) {
            return null
        }
        val rect = RectangleWrap(width, height)
        val textSize = (AnkiStatsTaskHandler.getInstance(collectionData).standardTextSize) * 0.85f
        paint.textSize = textSize
        val fontHeight = paint.textSize
        val desiredPixelDistanceBetweenTicks = (paint.measureText("100000") * 2.6f).roundToInt()
        val frameThickness = (fontHeight * 4.0f).roundToInt()

        // System.out.println("frame thickness: " + mFrameThickness);
        val plotSheet = PlotSheet(mFirstElement - 0.5, mLastElement + 0.5, 0.0, mMaxCards * Y_AXIS_STRETCH_FACTOR)
        plotSheet.setFrameThickness(frameThickness * 0.66f, frameThickness * 0.66f, frameThickness.toFloat(), frameThickness * 0.9f)
        plotSheet.setFontSize(textSize)
        plotSheet.setBackgroundColor(ColorWrap(getColorFromAttr(chartView.context, R.attr.colorBackground)))
        plotSheet.textColor = ColorWrap(getColorFromAttr(chartView.context, R.attr.textColor))
        plotSheet.setIsBackwards(mBackwards)
        if (chartType == ChartType.CARDS_TYPES) {
            return createPieChart(plotSheet)
        }
        val hiddenPlotSheet = PlotSheet(mFirstElement - 0.5, mLastElement + 0.5, 0.0, mMcount * Y_AXIS_STRETCH_FACTOR) // for second y-axis
        hiddenPlotSheet.setFrameThickness(frameThickness * 0.66f, frameThickness * 0.66f, frameThickness.toFloat(), frameThickness * 0.9f)
        setupCumulative(plotSheet, hiddenPlotSheet)
        setupBarGraphs(plotSheet, hiddenPlotSheet)
        val xTicks = ticksCalcX(desiredPixelDistanceBetweenTicks, rect, mFirstElement, mLastElement)
        setupXaxis(plotSheet, xTicks, true)
        val yTicks = ticksCalcY(desiredPixelDistanceBetweenTicks, rect, 0.0, mMaxCards * Y_AXIS_STRETCH_FACTOR)
        setupYaxis(plotSheet, hiddenPlotSheet, yTicks, mAxisTitles[1], isOnRight = false, hasName = true)

        // 0 = X-axis title
        // 1 = Y-axis title left
        // 2 = Y-axis title right (optional)
        if (mAxisTitles.size == 3) {
            val rightYtics = ticsCalc(desiredPixelDistanceBetweenTicks, rect, mMcount * Y_AXIS_STRETCH_FACTOR)
            setupYaxis(plotSheet, hiddenPlotSheet, rightYtics, mAxisTitles[2], isOnRight = true, hasName = true)
        }
        setupGrid(plotSheet, yTicks * 0.5, xTicks * 0.5)
        return plotSheet
    }

    private fun createPieChart(plotSheet: PlotSheet): PlotSheet {
        val colors = arrayOf(
            ColorWrap(getColorFromAttr(chartView.context, mColors[0])),
            ColorWrap(getColorFromAttr(chartView.context, mColors[1])),
            ColorWrap(getColorFromAttr(chartView.context, mColors[2])),
            ColorWrap(getColorFromAttr(chartView.context, mColors[3])),
            ColorWrap(getColorFromAttr(chartView.context, mColors[4]))
        )
        val pieChart = PieChart(plotSheet, mSeriesList[0], colors)
        pieChart.name = chartView.resources.getString(mValueLabels[0]) + ": " + mSeriesList[0][0].toInt()
        val legendDrawable1 = LegendDrawable()
        val legendDrawable2 = LegendDrawable()
        val legendDrawable3 = LegendDrawable()
        val legendDrawable4 = LegendDrawable()
        legendDrawable1.color = ColorWrap(getColorFromAttr(chartView.context, mColors[1]))
        legendDrawable2.color = ColorWrap(getColorFromAttr(chartView.context, mColors[2]))
        legendDrawable3.color = ColorWrap(getColorFromAttr(chartView.context, mColors[3]))
        legendDrawable4.color = ColorWrap(getColorFromAttr(chartView.context, mColors[4]))
        legendDrawable1.name = chartView.resources.getString(mValueLabels[1]) + ": " + mSeriesList[0][1].toInt()
        legendDrawable2.name = chartView.resources.getString(mValueLabels[2]) + ": " + mSeriesList[0][2].toInt()
        legendDrawable3.name = chartView.resources.getString(mValueLabels[3]) + ": " + mSeriesList[0][3].toInt()
        legendDrawable4.name = chartView.resources.getString(mValueLabels[4]) + ": " + mSeriesList[0][4].toInt()
        plotSheet.unsetBorder()
        plotSheet.addDrawable(pieChart)
        plotSheet.addDrawable(legendDrawable1)
        plotSheet.addDrawable(legendDrawable2)
        plotSheet.addDrawable(legendDrawable3)
        plotSheet.addDrawable(legendDrawable4)
        return plotSheet
    }

    private fun setupBarGraphs(plotSheet: PlotSheet, hiddenPlotSheet: PlotSheet) {
        var length = mSeriesList.size
        if (chartType == ChartType.HOURLY_BREAKDOWN || chartType == ChartType.WEEKLY_BREAKDOWN) {
            length-- // there is data in hourly breakdown that is never used (even in Anki-Desktop)
        }
        for (i in 1 until length) {
            val bars = arrayOf(mSeriesList[0], mSeriesList[i])
            var usedPlotSheet = plotSheet
            var barThickness = STARTING_BAR_THICKNESS
            if (chartType == ChartType.HOURLY_BREAKDOWN || chartType == ChartType.WEEKLY_BREAKDOWN) {
                barThickness = 0.8
                if (i == 2) {
                    usedPlotSheet = hiddenPlotSheet
                    barThickness = 0.2
                }
            }
            val color: ColorWrap = when (chartType) {
                ChartType.ANSWER_BUTTONS, ChartType.HOURLY_BREAKDOWN, ChartType.WEEKLY_BREAKDOWN, ChartType.INTERVALS -> ColorWrap(getColorFromAttr(chartView.context, mColors[i - 1]), BAR_OPACITY)
                ChartType.REVIEW_COUNT, ChartType.REVIEW_TIME, ChartType.FORECAST -> {
                    if (i == 1) {
                        ColorWrap(getColorFromAttr(chartView.context, mColors[i - 1]), BAR_OPACITY)
                    } else {
                        ColorWrap(getColorFromAttr(chartView.context, mColors[i - 1]))
                    }
                }
                else -> ColorWrap(getColorFromAttr(chartView.context, mColors[i - 1]))
            }
            val barGraph = BarGraph(usedPlotSheet, barThickness, bars, color)
            barGraph.setFilling(true)
            barGraph.name = chartView.resources.getString(mValueLabels[i - 1])
            // barGraph.setFillColor(Color.GREEN.darker());
            barGraph.setFillColor(color)
            plotSheet.addDrawable(barGraph)
        }
    }

    private fun setupCumulative(plotSheet: PlotSheet, hiddenPlotSheet: PlotSheet) {
        if (mCumulative == null) {
            return
        }
        for (i in 1 until mCumulative!!.size) {
            val cumulative = arrayOf(mCumulative!![0], mCumulative!![i])
            var usedColor = ColorWrap(getColorFromAttr(chartView.context, com.ichi2.anki.R.attr.stats_cumulative))
            var name = chartView.resources.getString(com.ichi2.anki.R.string.stats_cumulative)
            if (mHasColoredCumulative) { // also non colored Cumulatives have names!
                usedColor = ColorWrap(getColorFromAttr(chartView.context, mColors[i - 1]))
            } else {
                if (chartType == ChartType.INTERVALS) {
                    name = chartView.resources.getString(com.ichi2.anki.R.string.stats_cumulative_percentage)
                }
            }
            val lines = Lines(hiddenPlotSheet, cumulative, usedColor)
            lines.setSize(3f)
            lines.setShadow(2f, 2f, ColorWrap.BLACK)
            if (!mHasColoredCumulative) {
                lines.name = name
            }
            plotSheet.addDrawable(lines)
        }
    }

    private fun setupXaxis(plotSheet: PlotSheet, xTicks: Double, hasName: Boolean) {
        val xAxis = XAxis(plotSheet, 0.0, xTicks, xTicks / 2.0)
        xAxis.setOnFrame()
        if (hasName) {
            if (mDynamicAxis) {
                xAxis.setName(chartView.resources.getStringArray(com.ichi2.anki.R.array.due_x_axis_title)[mAxisTitles[0]])
            } else {
                xAxis.setName(chartView.resources.getString(mAxisTitles[0]))
            }
        }
        val timePositions: DoubleArray
        when (chartType) {
            ChartType.ANSWER_BUTTONS -> if (collectionData.schedVer() == 1) {
                timePositions = doubleArrayOf(1.0, 2.0, 3.0, 6.0, 7.0, 8.0, 9.0, 11.0, 12.0, 13.0, 14.0)
                xAxis.setExplicitTicks(timePositions, chartView.resources.getStringArray(com.ichi2.anki.R.array.stats_eases_ticks))
            } else {
                timePositions = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 6.0, 7.0, 8.0, 9.0, 11.0, 12.0, 13.0, 14.0)
                xAxis.setExplicitTicks(timePositions, chartView.resources.getStringArray(com.ichi2.anki.R.array.stats_eases_ticks_schedv2))
            }
            ChartType.HOURLY_BREAKDOWN -> {
                timePositions = doubleArrayOf(0.0, 6.0, 12.0, 18.0, 23.0)
                xAxis.setExplicitTicks(timePositions, chartView.resources.getStringArray(com.ichi2.anki.R.array.stats_day_time_strings))
            }
            ChartType.WEEKLY_BREAKDOWN -> {
                timePositions = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
                xAxis.setExplicitTicks(timePositions, chartView.resources.getStringArray(com.ichi2.anki.R.array.stats_week_days))
            }
            else -> {}
        }
        xAxis.setIntegerNumbering(true)
        plotSheet.addDrawable(xAxis)
    }

    private fun setupYaxis(
        plotSheet: PlotSheet,
        hiddenPlotSheet: PlotSheet?,
        yTicks: Double,
        title: Int,
        isOnRight: Boolean,
        hasName: Boolean
    ) {
        val yAxis: YAxis = if (isOnRight && hiddenPlotSheet != null) {
            YAxis(hiddenPlotSheet, 0.0, yTicks, yTicks / 2.0)
        } else {
            YAxis(plotSheet, 0.0, yTicks, yTicks / 2.0)
        }
        yAxis.setIntegerNumbering(true)
        if (hasName) {
            yAxis.setName(chartView.resources.getString(title))
        }
        if (isOnRight) {
            yAxis.setOnRightSideFrame()
        } else {
            yAxis.setOnFrame()
        }
        yAxis.setHasNumbersRotated()
        plotSheet.addDrawable(yAxis)
    }

    private fun setupGrid(plotSheet: PlotSheet, yTicks: Double, xTicks: Double) {
        val red = ColorWrap.LIGHT_GRAY.red
        val green = ColorWrap.LIGHT_GRAY.green
        val blue = ColorWrap.LIGHT_GRAY.blue
        val newGridColor = ColorWrap(red, green, blue, 222)
        val xGrid = XGrid(plotSheet, 0.0, yTicks) // ticks are not wrong, xgrid is vertical to yaxis -> yticks
        val yGrid = YGrid(plotSheet, 0.0, xTicks)
        val timePositions: DoubleArray
        when (chartType) {
            ChartType.ANSWER_BUTTONS -> {
                timePositions = if (collectionData.schedVer() == 1) {
                    doubleArrayOf(1.0, 2.0, 3.0, 6.0, 7.0, 8.0, 9.0, 11.0, 12.0, 13.0, 14.0)
                } else {
                    doubleArrayOf(1.0, 2.0, 3.0, 4.0, 6.0, 7.0, 8.0, 9.0, 11.0, 12.0, 13.0, 14.0)
                }
                yGrid.setExplicitTicks(timePositions)
            }
            ChartType.HOURLY_BREAKDOWN -> {
                timePositions = doubleArrayOf(0.0, 6.0, 12.0, 18.0, 23.0)
                yGrid.setExplicitTicks(timePositions)
            }
            ChartType.WEEKLY_BREAKDOWN -> {
                timePositions = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
                yGrid.setExplicitTicks(timePositions)
            }
            else -> {}
        }
        xGrid.setColor(newGridColor)
        yGrid.setColor(newGridColor)
        plotSheet.addDrawable(xGrid)
        plotSheet.addDrawable(yGrid)
    }

    private fun ticksCalcX(pixelDistance: Int, field: RectangleWrap, start: Double, end: Double): Double {
        val deltaRange = end - start
        val ticlimit = field.width / pixelDistance
        var tics = 10.0.pow(log10(deltaRange / ticlimit))
        while (2.0 * (deltaRange / tics) <= ticlimit) {
            tics /= 2.0
        }
        while (deltaRange / tics / 2 >= ticlimit) {
            tics *= 2.0
        }
        return tics
    }

    private fun ticksCalcY(pixelDistance: Int, field: RectangleWrap, start: Double, end: Double): Double {
        val size = ticsCalc(pixelDistance, field, end - start)
        Timber.d("ChartBuilder ticksCalcY: pixelDistance: %d, ticks: %,.2f, start: %,.2f, end: %,.2f, height: %d", pixelDistance, size, start, end, field.height)
        return size
    }

    fun ticsCalc(pixelDistance: Int, field: RectangleWrap, deltaRange: Double): Double {
        // Make approximation of number of ticks based on desired number of pixels per tick
        val numTicks = (field.height / pixelDistance).toDouble()

        // Compute size of one tick in graph-units
        val delta = deltaRange / numTicks

        // Write size of one tick in the form norm * magn
        val dec = floor(ln(delta) / ln(10.0))
        val magn = 10.0.pow(dec)
        val norm = delta / magn // norm is between 1.0 and 10.0

        // Write size of one tick in the form size * magn
        // Where size in (1, 2, 2.5, 5, 10)
        var size: Double
        if (norm < 1.5) {
            size = 1.0
        } else if (norm < 3) {
            size = 2.0
            // special case for 2.5, requires an extra decimal
            if (norm > 2.25) {
                size = 2.5
            }
        } else if (norm < 7.5) {
            size = 5.0
        } else {
            size = 10.0
        }

        // Compute size * magn so that we return one number
        size *= magn
        Timber.d("ChartBuilder ticksCalc : pixelDistance: %d, ticks: %,.2f, deltaRange: %,.2f, height: %d", pixelDistance, size, deltaRange, field.height)
        return size
    }

    companion object {
        private const val BAR_OPACITY = 0.7f
        private const val STARTING_BAR_THICKNESS = 0.6
        private const val Y_AXIS_STRETCH_FACTOR = 1.05
    }
}
