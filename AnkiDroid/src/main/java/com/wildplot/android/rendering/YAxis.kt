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

import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap
import com.wildplot.android.rendering.interfaces.Drawable
import java.text.DecimalFormat

/**
 * This Class represents a Drawable x-axis
 */
class YAxis
/**
 * Constructor for an Y-axis object
 *
 * @param plotSheet the sheet the axis will be drawn onto
 * @param ticStart  the start of the axis markers used for relative alignment of other markers
 * @param tic       the space between two markers
 */(
    /**
     * the PlotSheet object the x-axis is drawn onto
     */
    private val plotSheet: PlotSheet,
    /**
     * the start of x-axis marker, used for relative alignment of further marks
     */
    private val ticStart: Double,
    /**
     * the space between two marks
     */
    private val tic: Double,
    /**
     * the space between two minor marks
     */
    private val minorTic: Double
) : Drawable {
    private var mHasNumbersRotated = false
    private var maxTextWidth = 0f
    private var isIntegerNumbering = false
    private var isOnRightSide = false
    private val isLog = false

    /**
     * offset to move axis left or right
     */
    private var xOffset = 0.0
    private var name = "Y"
    private var mHasName = false

    /**
     * Format that is used to print numbers under markers
     */
    private val df = DecimalFormat("##0.0#")
    private val dfScience = DecimalFormat("0.0###E0")
    private val dfInteger = DecimalFormat("#.#")
    private var isScientific = false

    /**
     * start of drawn x-axis
     */
    private var start = 0.0

    /**
     * end of drawn x-axis
     */
    private var end = 100.0

    /**
     * true if the marker should be drawn into the direction above the axis
     */
    private var markOnLeft = true

    /**
     * true if the marker should be drawn into the direction under the axis
     */
    private var markOnRight = true

    /**
     * length of a marker in pixel, length is only for one side
     */
    private val markerLength = 5f
    private var isOnFrame = false

    /*
     * (non-Javadoc)
     * @see rendering.Drawable#paint(java.awt.Graphics)
     */
    override fun paint(g: GraphicsWrap) {
        val field = g.clipBounds
        start = plotSheet.getyRange()[0]
        end = plotSheet.getyRange()[1]
        if (tic < 1e-2 || tic > 1e2) {
            isScientific = true
        }
        val coordStart = plotSheet.toGraphicPoint(xOffset, start, field)
        val coordEnd = plotSheet.toGraphicPoint(xOffset, end, field)
        if (!isOnFrame) {
            g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
        }
        drawMarkers(g)
        drawMinorMarkers(g)
    }

    /**
     * draw markers on the axis
     *
     * @param g graphic object used for drawing
     */
    private fun drawMarkers(g: GraphicsWrap) {
        val field = g.clipBounds
        val yOffset = 0.0
        val cleanSpace =
            17f // space in pixel that will be unmarked on the end of the axis for arrow and description
        val tics = ((ticStart - start) / tic).toInt().toFloat()
        var leftStart = ticStart - tic * tics
        var logStart = 0.0
        if (isLog) {
            logStart = Math.ceil(Math.log10(start))
            leftStart = Math.pow(10.0, logStart++)
        }
        var currentY = leftStart
        while (currentY <= end) {
            if (!isOnFrame && plotSheet.yToGraphic(currentY, field) >= plotSheet.yToGraphic(
                    end,
                    field
                ) + cleanSpace && plotSheet.yToGraphic(currentY, field) <= plotSheet.yToGraphic(
                        start,
                        field
                    ) - cleanSpace && plotSheet.yToGraphic(
                        currentY,
                        field
                    ) <= field.y + field.height - cleanSpace && plotSheet.yToGraphic(
                        currentY,
                        field
                    ) >= field.y + cleanSpace ||
                isOnFrame && currentY <= plotSheet.getyRange()[1] && currentY >= plotSheet.getyRange()[0]
            ) {
                if (markOnRight) {
                    drawRightMarker(g, field, currentY)
                }
                if (markOnLeft) {
                    drawLeftMarker(g, field, currentY)
                }
                if (!(Math.abs(currentY) < yOffset + 0.001 && !isOnFrame)) {
                    if (isOnRightSide) {
                        drawNumberingOnRightSide(g, field, currentY)
                    } else {
                        drawNumbering(g, field, currentY)
                    }
                }
            }
            if (isLog) {
                currentY = Math.pow(10.0, logStart++)
            } else {
                currentY += tic
            }
        }
        val fm = g.fontMetrics
        val width = fm.stringWidth(name)
        // arrow
        val arrowheadPos = floatArrayOf(
            plotSheet.xToGraphic(xOffset, field),
            plotSheet.yToGraphic(
                Math.min(
                    plotSheet.getyRange()[1],
                    end
                ),
                field
            )
        )
        if (!isOnFrame) {
            g.drawLine(
                arrowheadPos[0] - 1,
                arrowheadPos[1] + 1,
                arrowheadPos[0] - 3,
                arrowheadPos[1] + 6
            )
            g.drawLine(
                arrowheadPos[0] + 1,
                arrowheadPos[1] + 1,
                arrowheadPos[0] + 3,
                arrowheadPos[1] + 6
            )
            if (mHasName) {
                g.drawString(name, arrowheadPos[0] - 13 - width, arrowheadPos[1] + 10)
            }
        } else {
            var spacerValue = maxTextWidth
            if (mHasNumbersRotated) {
                spacerValue = g.fontMetrics.height
            }
            g.save()
            val middlePosition =
                floatArrayOf(plotSheet.xToGraphic(xOffset, field), plotSheet.yToGraphic(0.0, field))
            if (isOnRightSide) {
                g.rotate(90f, middlePosition[0] + spacerValue * 1.4f, field.height / 2 - width / 2)
                if (mHasName) {
                    g.drawString(
                        name,
                        middlePosition[0] + spacerValue * 1.4f,
                        field.height / 2 - width / 2
                    )
                }
            } else {
                g.rotate(-90f, middlePosition[0] - spacerValue * 1.4f, field.height / 2 + width / 2)
                if (mHasName) {
                    g.drawString(
                        name,
                        middlePosition[0] - spacerValue * 1.4f,
                        field.height / 2 + width / 2
                    )
                }
            }
            g.restore()
        }
    }

    /**
     * draw number left to a marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param position     position of number
     */
    private fun drawNumbering(g: GraphicsWrap, field: RectangleWrap, position: Double) {
        var y = position
        if (tic < 1 && Math.abs(ticStart - y) < tic * tic) {
            y = ticStart
        }
        val coordStart = plotSheet.toGraphicPoint(xOffset, y, field)
        val fm = g.fontMetrics
        val fontHeight = fm.height
        var font = df.format(y)
        var width = fm.stringWidth(font)
        if (isScientific && !isIntegerNumbering) {
            font = dfScience.format(y)
            width = fm.stringWidth(font)
        } else if (isIntegerNumbering) {
            font = dfInteger.format(y)
            width = fm.stringWidth(font)
        }
        g.save()
        if (mHasNumbersRotated) {
            val middlePosition =
                floatArrayOf(plotSheet.xToGraphic(xOffset, field), plotSheet.yToGraphic(y, field))
            g.rotate(-90f, middlePosition[0] - width * 0.1f, middlePosition[1] + width / 2.0f)
            g.drawString(font, middlePosition[0] - width * 0.1f, middlePosition[1] + width / 2.0f)
        } else {
            g.drawString(
                font,
                Math.round(coordStart[0] - width * 1.1f).toFloat(),
                Math.round(
                    coordStart[1] + fontHeight * 0.4f
                ).toFloat()
            )
        }
        g.restore()
        if (width > maxTextWidth) {
            maxTextWidth = width
        }
    }

    /**
     * draw number left to a marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param position     position of number
     */
    private fun drawNumberingOnRightSide(g: GraphicsWrap, field: RectangleWrap, position: Double) {
        var y = position
        if (tic < 1 && Math.abs(ticStart - y) < tic * tic) {
            y = ticStart
        }
        val coordStart = plotSheet.toGraphicPoint(xOffset, y, field)
        val fm = g.fontMetrics
        val fontHeight = fm.height
        var font = df.format(y)
        var width = fm.stringWidth(font)
        g.save()
        if (isScientific && !isIntegerNumbering) {
            font = dfScience.format(y)
            width = fm.stringWidth(font)
        } else if (isIntegerNumbering) {
            font = dfInteger.format(y)
            width = fm.stringWidth(font)
        }
        if (mHasNumbersRotated) {
            val middlePosition =
                floatArrayOf(plotSheet.xToGraphic(xOffset, field), plotSheet.yToGraphic(y, field))
            g.rotate(90f, middlePosition[0] + width * 0.1f, middlePosition[1] - width / 2.0f)
            g.drawString(font, middlePosition[0] + width * 0.1f, middlePosition[1] - width / 2.0f)
        } else {
            g.drawString(
                font,
                Math.round(coordStart[0] + width * 0.1f).toFloat(),
                Math.round(
                    coordStart[1] + fontHeight * 0.4f
                ).toFloat()
            )
        }
        g.restore()
        if (width > maxTextWidth) {
            maxTextWidth = width
        }
    }

    /**
     * draws an left marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private fun drawLeftMarker(g: GraphicsWrap, field: RectangleWrap, y: Double) {
        val coordStart = plotSheet.toGraphicPoint(xOffset, y, field)
        val coordEnd = floatArrayOf(coordStart[0] - markerLength, coordStart[1])
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
    }

    /**
     * draws an right marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private fun drawRightMarker(g: GraphicsWrap, field: RectangleWrap, y: Double) {
        val coordStart = plotSheet.toGraphicPoint(xOffset, y, field)
        val coordEnd = floatArrayOf(coordStart[0] + markerLength + 1, coordStart[1])
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
    }

    /**
     * set the axis to draw on the border between outer frame and plot
     */
    fun setOnFrame() {
        isOnFrame = true
        xOffset = plotSheet.getxRange()[0]
        markOnLeft = false
    }

    fun setOnRightSideFrame() {
        isOnFrame = true
        xOffset = plotSheet.getxRange()[1]
        markOnRight = false
        isOnRightSide = true
    }

    /*
     * (non-Javadoc)
     * @see rendering.Drawable#isOnFrame()
     */
    override fun isOnFrame(): Boolean {
        return isOnFrame
    }

    /**
     * set name of axis
     *
     * @param name name of axis
     */
    fun setName(name: String) {
        this.name = name
        mHasName = "" != name
    }

    private fun drawMinorMarkers(g: GraphicsWrap) {
        val field = g.clipBounds
        val cleanSpace =
            17 // space in pixel that will be unmarked on the end of the axis for arrow and description
        val tics = ((ticStart - start) / tic).toInt()
        var leftStart = ticStart - tic * tics
        var factor = 1
        var logStart = 0.0
        if (isLog) {
            logStart = Math.floor(Math.log10(start))
            leftStart = Math.pow(10.0, logStart) * factor++
        }
        var currentY = leftStart
        while (currentY <= end) {
            if (!isOnFrame && plotSheet.yToGraphic(currentY, field) >= plotSheet.yToGraphic(
                    end,
                    field
                ) + cleanSpace && plotSheet.yToGraphic(currentY, field) <= plotSheet.yToGraphic(
                        start,
                        field
                    ) - cleanSpace && plotSheet.yToGraphic(
                        currentY,
                        field
                    ) <= field.y + field.height - cleanSpace && plotSheet.yToGraphic(
                        currentY,
                        field
                    ) >= field.y + cleanSpace ||
                isOnFrame && currentY <= plotSheet.getyRange()[1] && currentY >= plotSheet.getyRange()[0]
            ) {
                if (markOnRight) {
                    drawRightMinorMarker(g, field, currentY)
                }
                if (markOnLeft) {
                    drawLeftMinorMarker(g, field, currentY)
                }
            }
            if (isLog) {
                if (factor == 10) {
                    factor = 1
                    logStart++
                }
                currentY = Math.pow(10.0, logStart) * factor++
            } else {
                currentY += minorTic
            }
        }
    }

    /**
     * draws an left minor marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private fun drawLeftMinorMarker(g: GraphicsWrap, field: RectangleWrap, y: Double) {
        val coordStart = plotSheet.toGraphicPoint(xOffset, y, field)
        val coordEnd = floatArrayOf((coordStart[0] - 0.5 * markerLength).toFloat(), coordStart[1])
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
    }

    /**
     * draws an right minor marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private fun drawRightMinorMarker(g: GraphicsWrap, field: RectangleWrap, y: Double) {
        val coordStart = plotSheet.toGraphicPoint(xOffset, y, field)
        val coordEnd =
            floatArrayOf((coordStart[0] + 0.5 * markerLength + 1).toFloat(), coordStart[1])
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
    }

    fun setIntegerNumbering(isIntegerNumbering: Boolean) {
        this.isIntegerNumbering = isIntegerNumbering
    }

    override fun isClusterable(): Boolean {
        return true
    }

    override fun isCritical(): Boolean {
        return true
    }

    fun setHasNumbersRotated() {
        mHasNumbersRotated = true
    }
}
