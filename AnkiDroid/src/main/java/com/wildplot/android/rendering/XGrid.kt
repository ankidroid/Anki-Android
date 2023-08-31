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
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap
import com.wildplot.android.rendering.interfaces.Drawable

/**
 * This class represents grid lines parallel to the x-axis
 */
class XGrid
/**
 * Constructor for an X-Grid object
 *
 * @param plotSheet the sheet the grid will be drawn onto
 * @param ticStart  start point for relative positioning of grid
 * @param tic       the space between two grid lines
 */(
    /**
     * the Sheet the grid lines will be drawn onto
     */
    private val plotSheet: PlotSheet,
    /**
     * start point for relative positioning of grid
     */
    private val ticStart: Double,
    /**
     * the space between two grid lines
     */
    private val tic: Double
) : Drawable {
    /**
     * the color of the grid lines
     */
    private var color = ColorWrap.LIGHT_GRAY

    /**
     * maximal distance from x axis the grid will be drawn
     */
    private var xLength = 10.0
    override fun paint(g: GraphicsWrap) {
        xLength = Math.max(
            Math.abs(plotSheet.getxRange()[0]),
            Math.abs(
                plotSheet.getxRange()[1]
            )
        )
        val yLength = Math.max(
            Math.abs(plotSheet.getyRange()[0]),
            Math.abs(
                plotSheet.getyRange()[1]
            )
        )
        val oldColor = g.color
        val field = g.clipBounds
        g.color = color
        val tics = ((ticStart - (0 - yLength)) / tic).toInt()
        var downStart = ticStart - tic * tics
        if (downStart < 0) {
            while (downStart < 0) {
                downStart += tic
            }
        }
        var currentY = downStart
        while (currentY <= yLength) {
            drawGridLine(currentY, g, field)
            currentY += tic
        }
        g.color = oldColor
    }

    /**
     * Draw a grid line in specified graphics object
     *
     * @param y     x-position the vertical line shall be drawn
     * @param g     graphic the line shall be drawn onto
     * @param field definition of the graphic boundaries
     */
    private fun drawGridLine(y: Double, g: GraphicsWrap, field: RectangleWrap) {
        g.drawLine(
            plotSheet.xToGraphic(0.0, field),
            plotSheet.yToGraphic(y, field),
            plotSheet.xToGraphic(-xLength, field),
            plotSheet.yToGraphic(y, field)
        )
        g.drawLine(
            plotSheet.xToGraphic(0.0, field),
            plotSheet.yToGraphic(y, field),
            plotSheet.xToGraphic(xLength, field),
            plotSheet.yToGraphic(y, field)
        )
    }

    override fun isOnFrame(): Boolean {
        return false
    }

    override fun isClusterable(): Boolean {
        return true
    }

    override fun isCritical(): Boolean {
        return true
    }

    fun setColor(color: ColorWrap) {
        this.color = color
    }
}
