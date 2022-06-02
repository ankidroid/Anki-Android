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

package com.wildplot.android.rendering.graphics.wrapper

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Wrapper of swing/awt graphics class for android use.
 *
 * NOTE: for performance reasons(usage in onDraw) this class has declared dependencies on Paint and
 * Canvas which must be manually supplied before using any of its properties/methods.
 *
 * @author Michael Goldbach
 */
class GraphicsWrap {
    lateinit var canvas: Canvas
    lateinit var paint: Paint

    var stroke: StrokeWrap
        get() = StrokeWrap(paint.strokeWidth)
        set(stroke) {
            paint.strokeWidth = stroke.strokeSize
        }

    val clipBounds: RectangleWrap
        get() = RectangleWrap(canvas.clipBounds)

    var color: ColorWrap
        get() = ColorWrap(paint.color)
        set(color) {
            paint.color = color.colorValue
        }

    val fontMetrics: FontMetricsWrap
        get() = FontMetricsWrap(this)

    var fontSize: Float
        get() = paint.textSize
        set(size) {
            paint.textSize = size
        }

    var typeface: Typeface?
        get() = paint.typeface
        set(typeface) {
            paint.typeface = typeface
        }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL_AND_STROKE
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.style = oldStyle
    }

    fun drawRect(x: Float, y: Float, width: Float, height: Float) {
        val oldStyle = paint.style
        paint.style = Paint.Style.STROKE
        canvas.drawRect(x, y, x + width, y + height, paint)
        paint.style = oldStyle
    }

    fun fillRect(x: Float, y: Float, width: Float, height: Float) {
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawRect(x, y, x + width, y + height, paint)
        paint.style = oldStyle
    }

    fun drawArc(x: Float, y: Float, width: Float, height: Float, startAngle: Float, arcAngle: Float) {
        if (arcAngle == 0f) {
            return
        }
        val oldStyle = paint.style
        paint.style = Paint.Style.STROKE
        val rectF = RectF(x, y, x + width, y + height)
        canvas.drawArc(rectF, startAngle, arcAngle, true, paint)
        paint.style = oldStyle
    }

    fun fillArc(x: Float, y: Float, width: Float, height: Float, startAngle: Float, arcAngle: Float) {
        if (arcAngle == 0f) {
            return
        }
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        val rectF = RectF(x, y, x + width, y + height)
        canvas.drawArc(rectF, startAngle, arcAngle, true, paint)
        paint.style = oldStyle
    }

    fun drawString(text: String?, x: Float, y: Float) {
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawText(text!!, x, y, paint)
        paint.style = oldStyle
    }

    fun save(): Int {
        return canvas.save()
    }

    fun restore() {
        canvas.restore()
    }

    fun rotate(degree: Float, x: Float, y: Float) {
        canvas.rotate(degree, x, y)
    }
}
