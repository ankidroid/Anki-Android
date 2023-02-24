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
class XAxis
/**
 * Constructor for an X-axis object
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
    private var isIntegerNumbering = false

    /**
     * believe it or not, this axis can be placed higher or lower than y=0
     */
    private var yOffset = 0.0

    /**
     * Name of axis
     */
    private var name = "X"
    private var mHasName = false

    /**
     * Format that is used to print numbers under markers
     */
    private val df = DecimalFormat("##0.0#")

    /**
     * format for very big or small values
     */
    private val dfScience = DecimalFormat("0.0##E0")
    private val dfInteger = DecimalFormat("#.#")

    /**
     * is set to true if scientific format (e.g. 1E-3) should be used
     */
    private var isScientific = false

    /**
     * the estimated size between two major tics in auto tic mode
     */
    private var pixelDistance = 25f

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
    private val markOnUpside = true

    /**
     * true if the marker should be drawn into the direction under the axis
     */
    private var markOnDownside = true

    /**
     * length of a marker in pixel, length is only for one side
     */
    private val markerLength = 5f

    /**
     * true if this  axis is drawn onto the frame
     */
    private var isOnFrame = false
    private var mTickNameList: Array<String>? = null
    private var mTickPositions: DoubleArray? = null
    override fun paint(g: GraphicsWrap) {
        val field = g.clipBounds
        start = plotSheet.getxRange()[0]
        end = plotSheet.getxRange()[1]
        if (isOnFrame) {
            yOffset = plotSheet.getyRange()[0]
        }
        pixelDistance = Math.abs(
            plotSheet.xToGraphic(0.0, field) - plotSheet.xToGraphic(
                tic,
                field
            )
        )
        if (tic < 1e-2 || tic > 1e2) {
            isScientific = true
        }
        val coordStart = plotSheet.toGraphicPoint(start, yOffset, field)
        val coordEnd = plotSheet.toGraphicPoint(end, yOffset, field)
        if (!isOnFrame) {
            g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
        }
        drawMarkers(g)
        if (mTickPositions == null) {
            drawMinorMarkers(g)
        }
    }

    /**
     * draw markers on the axis
     *
     * @param g graphic object used for drawing
     */
    private fun drawMarkers(g: GraphicsWrap) {
        val field = g.clipBounds
        if (mTickPositions == null) {
            drawImplicitMarker(g)
        } else {
            drawExplicitMarkers(g)
        }

        // arrow
        val arrowheadPos = floatArrayOf(
            plotSheet.xToGraphic(
                Math.min(
                    plotSheet.getxRange()[1],
                    end
                ),
                field
            ),
            plotSheet.yToGraphic(yOffset, field)
        )
        val fm = g.fontMetrics
        val fontHeight = fm.height
        val width = fm.stringWidth(name)
        if (!isOnFrame) {
            g.drawLine(
                arrowheadPos[0] - 1,
                arrowheadPos[1] - 1,
                arrowheadPos[0] - 6,
                arrowheadPos[1] - 3
            )
            g.drawLine(
                arrowheadPos[0] - 1,
                arrowheadPos[1] + 1,
                arrowheadPos[0] - 6,
                arrowheadPos[1] + 3
            )
            if (mHasName) {
                g.drawString(name, arrowheadPos[0] - 14 - width, arrowheadPos[1] + 12)
            }
        } else {
            val middlePosition =
                floatArrayOf(plotSheet.xToGraphic(0.0, field), plotSheet.yToGraphic(yOffset, field))
            if (mHasName) {
                g.drawString(
                    name,
                    field.width / 2 - width / 2,
                    Math.round(
                        middlePosition[1] + fontHeight * 2.5f
                    ).toFloat()
                )
            }
        }
    }

    private fun drawImplicitMarker(g: GraphicsWrap) {
        val field = g.clipBounds
        val tics = ((ticStart - start) / tic).toInt()
        var currentX = ticStart - tic * tics
        while (currentX <= end) {
            if (!isOnFrame && plotSheet.xToGraphic(currentX, field) <= plotSheet.xToGraphic(
                    end,
                    field
                ) - 45 && plotSheet.xToGraphic(currentX, field) <= field.x + field.width - 45 ||
                isOnFrame && currentX <= plotSheet.getxRange()[1] && currentX >= plotSheet.getxRange()[0]
            ) {
                if (markOnDownside) {
                    drawDownwardsMarker(g, field, currentX)
                }
                if (markOnUpside) {
                    drawUpwardsMarker(g, field, currentX)
                }
                drawNumbering(g, field, currentX, -1)
            }
            currentX += tic
        }
    }

    private fun drawExplicitMarkers(g: GraphicsWrap) {
        val field = g.clipBounds
        for (i in mTickPositions!!.indices) {
            val currentX = mTickPositions!![i]
            if (!isOnFrame && plotSheet.xToGraphic(currentX, field) <= plotSheet.xToGraphic(
                    end,
                    field
                ) - 45 && plotSheet.xToGraphic(currentX, field) <= field.x + field.width - 45 ||
                isOnFrame && currentX <= plotSheet.getxRange()[1] && currentX >= plotSheet.getxRange()[0]
            ) {
                if (markOnDownside) {
                    drawDownwardsMarker(g, field, currentX)
                }
                if (markOnUpside) {
                    drawUpwardsMarker(g, field, currentX)
                }
                drawNumbering(g, field, currentX, i)
            }
        }
    }

    /**
     * draw number under a marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param x     position of number
     */
    private fun drawNumbering(g: GraphicsWrap, field: RectangleWrap, x: Double, index: Int) {
        var position = x
        if (tic < 1 && Math.abs(ticStart - position) < tic * tic) {
            position = ticStart
        }
        val fm = g.fontMetrics
        val fontHeight = fm.height
        val coordStart = plotSheet.toGraphicPoint(position, yOffset, field)
        if (Math.abs(position) - Math.abs(0) < 0.001 && !isOnFrame) {
            coordStart[0] += 10f
            coordStart[1] -= 10f
        }
        var text = if (mTickNameList == null) df.format(position) else mTickNameList!![index]
        var width = fm.stringWidth(text)
        if (isScientific || width > pixelDistance) {
            text = if (mTickNameList == null) dfScience.format(position) else mTickNameList!![index]
            width = fm.stringWidth(text)
            g.drawString(
                text,
                coordStart[0] - width / 2,
                Math.round(coordStart[1] + if (isOnFrame) Math.round(fontHeight * 1.5) else 20)
                    .toFloat()
            )
        } else if (isIntegerNumbering) {
            text = if (mTickNameList == null) dfInteger.format(position) else mTickNameList!![index]
            width = fm.stringWidth(text)
            g.drawString(
                text,
                coordStart[0] - width / 2,
                Math.round(coordStart[1] + if (isOnFrame) Math.round(fontHeight * 1.5) else 20)
                    .toFloat()
            )
        } else {
            width = fm.stringWidth(text)
            g.drawString(
                text,
                coordStart[0] - width / 2,
                Math.round(coordStart[1] + if (isOnFrame) Math.round(fontHeight * 1.5) else 20)
                    .toFloat()
            )
        }
    }

    /**
     * draws an upwards marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param x     position of marker
     */
    private fun drawUpwardsMarker(g: GraphicsWrap, field: RectangleWrap, x: Double) {
        val coordStart = plotSheet.toGraphicPoint(x, yOffset, field)
        val coordEnd = floatArrayOf(coordStart[0], coordStart[1] - markerLength)
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
    }

    /**
     * draws an downwards marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param x     position of marker
     */
    private fun drawDownwardsMarker(g: GraphicsWrap, field: RectangleWrap, x: Double) {
        val coordStart = plotSheet.toGraphicPoint(x, yOffset, field)
        val coordEnd = floatArrayOf(coordStart[0], coordStart[1] + markerLength)
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
    }

    /**
     * set the axis to draw on the border between outer frame and plot
     */
    fun setOnFrame() {
        isOnFrame = true
        yOffset = plotSheet.getyRange()[0]
        markOnDownside = false
    }

    override fun isOnFrame(): Boolean {
        return isOnFrame
    }

    /**
     * set name description of axis
     *
     * @param name of axis
     */
    fun setName(name: String) {
        this.name = name
        mHasName = "" != name
    }

    /**
     * draw minor markers on the axis
     *
     * @param g graphic object used for drawing
     */
    private fun drawMinorMarkers(g: GraphicsWrap) {
        val field = g.clipBounds
        val tics = ((ticStart - start) / tic).toInt()
        var currentX = ticStart - tic * tics
        while (currentX <= end) {
            if (!isOnFrame && plotSheet.xToGraphic(currentX, field) <= plotSheet.xToGraphic(
                    end,
                    field
                ) - 45 && plotSheet.xToGraphic(currentX, field) <= field.x + field.width - 45 ||
                isOnFrame && currentX <= plotSheet.getxRange()[1] && currentX >= plotSheet.getxRange()[0]
            ) {
                if (markOnDownside) {
                    drawDownwardsMinorMarker(g, field, currentX)
                }
                if (markOnUpside) {
                    drawUpwardsMinorMarker(g, field, currentX)
                }
            }
            currentX += minorTic
        }
    }

    /**
     * draws an upwards minor marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param x     position of marker
     */
    private fun drawUpwardsMinorMarker(g: GraphicsWrap, field: RectangleWrap, x: Double) {
        val coordStart = plotSheet.toGraphicPoint(x, yOffset, field)
        val coordEnd =
            floatArrayOf(coordStart[0], (coordStart[1] - 0.5 * markerLength).toInt().toFloat())
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
    }

    /**
     * draws an downwards minor marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param x     position of marker
     */
    private fun drawDownwardsMinorMarker(g: GraphicsWrap, field: RectangleWrap, x: Double) {
        val coordStart = plotSheet.toGraphicPoint(x, yOffset, field)
        val coordEnd =
            floatArrayOf(coordStart[0], (coordStart[1] + 0.5 * markerLength).toInt().toFloat())
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

    fun setExplicitTicks(tickPositions: DoubleArray?, tickNameList: Array<String>?) {
        mTickPositions = tickPositions
        mTickNameList = tickNameList
    }
}
