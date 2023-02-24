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

package com.ichi2.anki.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.ichi2.anki.Statistics.ChartFragment
import com.ichi2.utils.KotlinCleanup
import com.wildplot.android.rendering.PlotSheet
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import timber.log.Timber

class ChartView : View {
    private var mFragment: ChartFragment? = null
    private var mPlotSheet: PlotSheet? = null
    private var mDataIsSet = false

    @KotlinCleanup("is this really needed?")
    private val drawingBoundsRect = Rect()
    private val paint = Paint(Paint.LINEAR_TEXT_FLAG)
    private val graphicsWrap = GraphicsWrap()

    // The following constructors are needed for the layout inflater
    constructor(context: Context?) : super(context) {
        setWillNotDraw(false)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setWillNotDraw(false)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setWillNotDraw(false)
    }

    public override fun onDraw(canvas: Canvas) {
        // Timber.d("drawing chart");
        if (mDataIsSet) {
            // Paint paint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
            paint.apply {
                reset()
                isAntiAlias = true
                style = Paint.Style.STROKE
            }
            graphicsWrap.paint = paint
            graphicsWrap.canvas = canvas
            drawingBoundsRect.setEmpty()
            getDrawingRect(drawingBoundsRect)
            mPlotSheet?.paint(graphicsWrap) ?: super.onDraw(canvas)
        } else {
            super.onDraw(canvas)
        }
    }

    fun addFragment(fragment: ChartFragment?) {
        mFragment = fragment
    }

    fun setData(plotSheet: PlotSheet?) {
        mPlotSheet = plotSheet
        mDataIsSet = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Timber.d("ChartView sizeChange!")
        if (mFragment != null) {
            mFragment!!.checkAndUpdate()
        }
    }
}
