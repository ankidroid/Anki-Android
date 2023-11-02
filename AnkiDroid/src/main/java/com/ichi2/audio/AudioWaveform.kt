/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
// TODO : Middle blue line should move left->mid https://github.com/ankidroid/Anki-Android/pull/14591#issuecomment-1791037102
class AudioWaveform(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var paint = Paint()
    private var linePaint = Paint()
    private var bgPaint = Paint()
    private var amplitudes = ArrayList<Float>()
    private var audioSpikes = ArrayList<RectF>()

    private var radius = 3f
    private var w = 6f

    private var sw = 0f
    private var sh = 300f
    private var d = 4f
    private var maxSpike = 0

    init {
        paint.color = Color.rgb(244, 81, 30)
        sw = (resources.displayMetrics.widthPixels / 2).toFloat()
        maxSpike = (sw / (w + d)).toInt()
    }

    fun addAmplitude(amp: Float) {
        // minimum height 6 is assigned by default to avoid blank spikes, making the UI consistent
        val norm = (amp.toInt() / 7).coerceAtMost(300).coerceAtLeast(6).toFloat()
        amplitudes.add(norm)
        audioSpikes.clear()
        val amps = amplitudes.takeLast(maxSpike)
        for (a in amps.indices) {
            val left = a * (w + d)
            val top = sh / 2 - amps[a] / 2
            val right = left + w
            val bottom = top + amps[a]
            audioSpikes.add(RectF(left, top, right, bottom))
        }
        invalidate()
    }

    fun clear(): ArrayList<*> {
        val amps = amplitudes.clone() as ArrayList<*>
        amplitudes.clear()
        audioSpikes.clear()
        invalidate()
        return amps
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val backgroundPaint = bgPaint.apply {
            color = Color.argb(20, 229, 228, 226)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        audioSpikes.forEach {
            canvas.drawRoundRect(it, radius, radius, paint)
        }
        val centerX = width / 2f
        val startY = 0f
        val endY = height.toFloat()
        val verticalLine = linePaint.apply {
            color = Color.rgb(33, 150, 243)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawLine(centerX, startY, centerX, endY, verticalLine)
    }
}
