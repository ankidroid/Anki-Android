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

class AudioWaveform(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var paint = Paint()
    private var amplitudes = ArrayList<Float>()
    private var audioSpikes = ArrayList<RectF>()

    private var radius = 6f
    private var w = 9f

    private var sw = 0f
    private var sh = 400f
    private var d = 6f
    private var maxSpike = 0

    init {
        paint.color = Color.rgb(244, 81, 30)
        sw = resources.displayMetrics.widthPixels.toFloat()
        maxSpike = (sw / (w + d)).toInt()
    }

    fun addAmplitude(amp: Float) {
        val norm = Math.min(amp.toInt() / 7, 400).toFloat()
        amplitudes.add(norm)
        audioSpikes.clear()
        val amps = amplitudes.takeLast(maxSpike)
        for (a in amps.indices) {
            val left = sw - a * (w + d)
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
        audioSpikes.forEach {
            canvas.drawRoundRect(it, radius, radius, paint)
        }
    }
}
