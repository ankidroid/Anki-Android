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

import com.wildplot.android.rendering.graphics.wrapper.BasicStrokeWrap
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import com.wildplot.android.rendering.interfaces.Drawable
import com.wildplot.android.rendering.interfaces.Legendable

/**
 * The LinesPoints objects draw points from a data array and connect them with lines on.
 * These LinesPoints are drawn onto a PlotSheet object
 */
class Lines
/**
 * Constructor for points connected with lines without drawn points
 *
 * @param plotSheet the sheet the lines and points will be drawn onto
 * @param pointList x- , y-positions of given points
 * @param color     point and line color
 */(private val plotSheet: PlotSheet, private val pointList: Array<DoubleArray>, override var color: ColorWrap) : Drawable, Legendable {
    private var mHasShadow = false
    private var mShadowDx = 0.0f
    private var mShadowDy = 0.0f
    private var mShadowColor = ColorWrap.BLACK
    override var name: String = ""
        set(value) {
            field = value
            mNameIsSet = true
        }
    private var mNameIsSet = false
    private var size = 0f
    fun setSize(size: Float) {
        this.size = size
    }

    override fun paint(g: GraphicsWrap) {
        val oldColor = g.color
        val field = g.clipBounds
        g.color = color
        val oldStroke = g.stroke
        g.stroke = BasicStrokeWrap(size) // set stroke width of 10
        var coordStart = plotSheet.toGraphicPoint(pointList[0][0], pointList[1][0], field)
        var coordEnd: FloatArray
        for (i in 0 until pointList[0].size) {
            coordEnd = coordStart
            coordStart = plotSheet.toGraphicPoint(pointList[0][i], pointList[1][i], field)
            if (mHasShadow) {
                val oldShadowLessStroke = g.stroke
                g.stroke = BasicStrokeWrap(size * 1.5f) // set stroke width of 10
                val shadowColor = ColorWrap(mShadowColor.red, mShadowColor.green, mShadowColor.blue, 80)
                g.color = shadowColor
                g.drawLine(coordStart[0] + mShadowDx, coordStart[1] + mShadowDy, coordEnd[0] + mShadowDx, coordEnd[1] + mShadowDy)
                g.color = color
                g.stroke = oldShadowLessStroke
            }
            g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1])
        }
        g.stroke = oldStroke
        g.color = oldColor
    }

    override fun isOnFrame(): Boolean {
        return false
    }

    override fun isClusterable(): Boolean {
        return true
    }

    override fun isCritical(): Boolean {
        return false
    }

    override fun nameIsSet(): Boolean {
        return mNameIsSet
    }

    fun setShadow(dx: Float, dy: Float, color: ColorWrap) {
        mHasShadow = true
        mShadowDx = dx
        mShadowDy = dy
        mShadowColor = color
    }
}
