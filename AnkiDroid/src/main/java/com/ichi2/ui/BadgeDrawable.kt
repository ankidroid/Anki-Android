/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper

class BadgeDrawable(dr: Drawable?) : DrawableWrapper(dr) {
    private val mPaint: Paint = Paint()
    private var mBadge: Drawable? = null
    private var mText: String? = null
    private var mTextX = 0f
    private var mTextY = 0f
    fun setBadgeDrawable(view: Drawable) {
        mBadge = view
        invalidateSize()
    }

    private fun invalidateSize() {
        // This goes out of bounds - it seems to be fine
        val size = (intrinsicWidth * iconScale).toInt()
        mPaint.textSize = (size * 0.8).toFloat()
        val left = left.toInt()
        val bottom = bottom.toInt()
        val right = left + size
        val top = bottom - size
        if (mBadge != null) {
            mBadge!!.setBounds(left, top, right, bottom)
        }
        val vcenter = (top + bottom) / 2.0f
        mTextX = (left + right) / 2.0f
        mTextY = vcenter - (mPaint.descent() + mPaint.ascent()) / 2
    }

    private val bottom: Double
        get() {
            val h = intrinsicHeight
            return if (isShowingText) {
                h * 0.45
            } else {
                h * iconScale
            }
        }
    private val left: Double
        get() {
            val w = intrinsicWidth
            return if (isShowingText) {
                w * 0.55
            } else {
                w - w * iconScale
            }
        }
    private val iconScale: Double
        get() = if (isShowingText) {
            ICON_SCALE_TEXT
        } else {
            ICON_SCALE_BARE
        }
    private val isShowingText: Boolean
        get() = mText != null && mText!!.isNotEmpty()

    fun setText(c: Char) {
        mText = String(charArrayOf(c))
        invalidateSize()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (mBadge != null) {
            mBadge!!.draw(canvas)
            if (mText != null) {
                canvas.drawText(mText!!, mTextX, mTextY, mPaint)
            }
        }
    }

    companion object {
        const val ICON_SCALE_TEXT = 0.70
        const val ICON_SCALE_BARE = 0.40
    }

    /**
     * Creates a new wrapper around the specified drawable.
     *
     * @param dr the drawable to wrap
     */
    init {
        mPaint.typeface = Typeface.DEFAULT_BOLD
        mPaint.textAlign = Paint.Align.CENTER
        mPaint.color = Color.WHITE
    }
}
