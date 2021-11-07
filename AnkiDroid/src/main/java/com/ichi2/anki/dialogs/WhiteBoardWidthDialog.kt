/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.ui.FixedTextView
import java.util.function.Consumer

class WhiteBoardWidthDialog(private val context: Context, private var wbStrokeWidth: Int) {
    private var mWbStrokeWidthText: FixedTextView? = null
    var onStrokeWidthChanged: Consumer<Int>? = null
    private val seekBarChangeListener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, value: Int, b: Boolean) {
            wbStrokeWidth = value
            mWbStrokeWidthText!!.text = "" + value
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            // intentionally blank
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            // intentionally blank
        }
    }

    fun showStrokeWidthDialog() {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)
        mWbStrokeWidthText = FixedTextView(context)
        mWbStrokeWidthText!!.gravity = Gravity.CENTER_HORIZONTAL
        mWbStrokeWidthText!!.textSize = 30f
        mWbStrokeWidthText!!.text = "" + wbStrokeWidth
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layout.addView(mWbStrokeWidthText, params)
        val seekBar = SeekBar(context)
        seekBar.progress = wbStrokeWidth
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener)
        layout.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        MaterialDialog.Builder(context)
            .title(R.string.whiteboard_stroke_width)
            .positiveText(R.string.save)
            .negativeText(R.string.dialog_cancel)
            .customView(layout, true)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                if (onStrokeWidthChanged != null) {
                    onStrokeWidthChanged!!.accept(wbStrokeWidth)
                }
            }
            .show()
    }

    fun onStrokeWidthChanged(c: Consumer<Int>?) {
        onStrokeWidthChanged = c
    }
}
