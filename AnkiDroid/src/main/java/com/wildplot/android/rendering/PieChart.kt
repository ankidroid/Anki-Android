/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@wildplot.com>                           *
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

package com.wildplot.android.rendering

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import com.wildplot.android.rendering.interfaces.Drawable
import com.wildplot.android.rendering.interfaces.Legendable

class PieChart(plotSheet: PlotSheet, values: DoubleArray, colors: Array<ColorWrap>) : Drawable, Legendable {
    private val mPlotSheet: PlotSheet
    private val mValues: DoubleArray
    private val mColors: Array<ColorWrap>
    override var name: String = ""
        set(value) {
            field = value
            mNameIsSet = true
        }
    private var mNameIsSet = false
    private val mPercent: DoubleArray
    private var mSum = 0.0
    private fun checkArguments(values: DoubleArray, colors: Array<ColorWrap>) {
        require(values.size == colors.size) { "The number of colors must match the number of values" }
    }

    override fun isOnFrame(): Boolean {
        return false
    }

    override fun paint(g: GraphicsWrap) {
        // Do not show chart if segments are all zero
        if (mSum == 0.0) {
            return
        }
        val maxSideBorders = Math.max(
            mPlotSheet.frameThickness[PlotSheet.LEFT_FRAME_THICKNESS_INDEX],
            mPlotSheet.frameThickness[PlotSheet.RIGHT_FRAME_THICKNESS_INDEX]
        )
        val maxUpperBottomBorders = Math.max(
            mPlotSheet.frameThickness[PlotSheet.UPPER_FRAME_THICKNESS_INDEX],
            mPlotSheet.frameThickness[PlotSheet.BOTTOM_FRAME_THICKNESS_INDEX]
        )
        val realBorder = Math.max(maxSideBorders, maxUpperBottomBorders) + 3
        val field = g.clipBounds
        val diameter = Math.min(field.width, field.height) - 2 * realBorder
        val xCenter = field.width / 2.0f
        val yCenter = field.height / 2.0f
        val oldColor = g.color
        drawSectors(g, diameter, xCenter, yCenter)
        drawSectorLabels(g, diameter, xCenter, yCenter)
        g.color = oldColor
    }

    private fun drawSectors(g: GraphicsWrap, diameter: Float, xCenter: Float, yCenter: Float) {
        val left = xCenter - diameter / 2f
        val top = yCenter - diameter / 2f
        var currentAngle = FIRST_SECTOR_OFFSET
        for (i in 0 until mPercent.size - 1) {
            g.color = mColors[i]
            val arcLength = (360 * mValues[i] / mSum).toFloat()
            g.fillArc(left, top, diameter, diameter, currentAngle, arcLength)
            currentAngle += arcLength
        }

        // last one does need some corrections to fill a full circle:
        g.color = lastSectorColor
        g.fillArc(
            left,
            top,
            diameter,
            diameter,
            currentAngle,
            360f + FIRST_SECTOR_OFFSET - currentAngle
        )
        g.color = ColorWrap.black
        g.drawArc(left, top, diameter, diameter, 0f, 360f)
    }

    private val lastSectorColor: ColorWrap
        get() = mColors[mColors.size - 1]

    private fun drawSectorLabels(g: GraphicsWrap, diameter: Float, xCenter: Float, yCenter: Float) {
        val labelBackground = ColorWrap(0.0f, 0.0f, 0.0f, 0.5f)
        for (j in mPercent.indices) {
            if (mValues[j] == 0.0) {
                continue
            }
            var oldPercent = 0.0
            if (j != 0) {
                oldPercent = mPercent[j - 1]
            }
            val text = "" + Math.round((mPercent[j] - oldPercent) * 100 * 100) / 100.0 + "%"
            val x = (xCenter + Math.cos(-1 * ((oldPercent + (mPercent[j] - oldPercent) * 0.5) * 360 + FIRST_SECTOR_OFFSET) * Math.PI / 180.0) * 0.375 * diameter).toFloat() - 20
            val y = (yCenter - Math.sin(-1 * ((oldPercent + (mPercent[j] - oldPercent) * 0.5) * 360 + FIRST_SECTOR_OFFSET) * Math.PI / 180.0) * 0.375 * diameter).toFloat()
            val fm = g.fontMetrics
            val width = fm.stringWidth(text)
            val height = fm.height
            g.color = labelBackground
            g.fillRect(x - 1, y - height + 3, width + 2, height)
            g.color = ColorWrap.white
            g.drawString(text, x, y)
        }
    }

    override fun isClusterable(): Boolean {
        return true
    }

    override fun isCritical(): Boolean {
        return false
    }

    override val color: ColorWrap
        get() = if (mColors.size > 0) mColors[0] else ColorWrap.WHITE

    override fun nameIsSet(): Boolean {
        return mNameIsSet
    }

    companion object {
        // First sector starts at 12 o'clock.
        private const val FIRST_SECTOR_OFFSET = -90f
    }

    init {
        checkArguments(values, colors)
        mPlotSheet = plotSheet
        mValues = values
        mColors = colors
        mPercent = DoubleArray(mValues.size)
        for (v in mValues) {
            mSum += v
        }
        val denominator: Double = if (mSum == 0.0) 1.0 else mSum
        mPercent[0] = mValues[0] / denominator
        for (i in 1 until mValues.size) {
            mPercent[i] = mPercent[i - 1] + mValues[i] / denominator
        }
    }
}
