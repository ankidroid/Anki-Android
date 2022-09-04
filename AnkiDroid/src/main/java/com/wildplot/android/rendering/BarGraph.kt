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
import com.wildplot.android.rendering.interfaces.Legendable

/**
 * BarGraph uses a point matrix or a function to render bar graphs on PlotSheet object
 *
 *
 * Constructor for BarGraph object
 *
 * @param plotSheet the sheet the bar will be drawn onto
 * @param size      absolute x-width of the bar
 * @param points    start points (x,y) from each bar
 * @param color     color of the bar
*/
class BarGraph(
    private val plotSheet: PlotSheet,
    private val size: Double,
    private val points: Array<DoubleArray>,
    override val color: ColorWrap
) : Drawable, Legendable {
    private var mName = ""
    private var mNameIsSet = false
    private var fillColor: ColorWrap? = null
    private var filling = false

    /**
     * Set filling for a bar graph true or false
     */
    fun setFilling(filling: Boolean) {
        this.filling = filling
        if (this.fillColor == null) {
            this.fillColor = this.color
        }
    }

    /**
     * Set filling color for bar graph
     *
     * @param fillColor of the bar graph
     */
    fun setFillColor(fillColor: ColorWrap?) {
        this.fillColor = fillColor
    }

    override fun paint(g: GraphicsWrap) {
        val oldColor = g.color
        val field = g.clipBounds
        g.color = color
        for (i in 0 until points[0].size) {
            if (points.size == 3) {
                drawBar(points[0][i], points[1][i], g, field, points[2][i])
            } else {
                drawBar(points[0][i], points[1][i], g, field)
            }
        }
        g.color = oldColor
    }
    /**
     * draw a single bar at given coordinates with given graphics object and bounds and specific size
     *
     * @param x     x-coordinate of bar
     * @param y     height of bar
     * @param g     graphics object for drawing
     * @param field bounds of plot
     * @param size  specific size for this bar
     */
    /**
     * draw a single bar at given coordinates with given graphics object and bounds
     *
     * @param x     x-coordinate of bar
     * @param y     height of bar
     * @param g     graphics object for drawing
     * @param field bounds of plot
     */
    private fun drawBar(
        x: Double,
        y: Double,
        g: GraphicsWrap,
        field: RectangleWrap,
        size: Double = this.size
    ) {
        val pointUpLeft = plotSheet.toGraphicPoint(x - size / 2, y, field)
        val pointUpRight = plotSheet.toGraphicPoint(x + size / 2, y, field)
        val pointBottomLeft = plotSheet.toGraphicPoint(x - size / 2, 0.0, field)
        if (filling) {
            val oldColor = g.color
            if (this.fillColor != null) {
                g.color = this.fillColor!!
            }
            if (y < 0) {
                g.fillRect(
                    pointUpLeft[0],
                    plotSheet.yToGraphic(0.0, field),
                    pointUpRight[0] - pointUpLeft[0],
                    pointUpLeft[1] - pointBottomLeft[1]
                )
            } else {
                g.fillRect(
                    pointUpLeft[0],
                    pointUpLeft[1],
                    pointUpRight[0] - pointUpLeft[0],
                    pointBottomLeft[1] - pointUpLeft[1]
                )
            }
            g.color = oldColor
        } else {
            if (y < 0) {
                g.drawRect(
                    pointUpLeft[0],
                    plotSheet.yToGraphic(0.0, field),
                    pointUpRight[0] - pointUpLeft[0],
                    pointUpLeft[1] - pointBottomLeft[1]
                )
            } else {
                g.drawRect(
                    pointUpLeft[0],
                    pointUpLeft[1],
                    pointUpRight[0] - pointUpLeft[0],
                    pointBottomLeft[1] - pointUpLeft[1]
                )
            }
        }
    }

    /**
     * returns true if this BarGraph can draw on the outer frame of plot (normally not)
     */
    override fun isOnFrame(): Boolean {
        return false
    }

    override fun isClusterable(): Boolean {
        return true
    }

    override fun isCritical(): Boolean {
        return false
    }

    override var name: String
        get() = mName
        set(name) {
            mName = name
            mNameIsSet = true
        }

    override fun nameIsSet(): Boolean {
        return mNameIsSet
    }
}
