/****************************************************************************************
 * Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>                                *
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

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.ichi2.anki.R
import com.ichi2.utils.KotlinCleanup
import java.lang.NumberFormatException

// TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 : use IncrementerNumberRangePreferenceCompat
@Suppress("deprecation")
@KotlinCleanup("_editText")
class IncrementerNumberRangePreference : NumberRangePreference {
    private val linearLayout = LinearLayout(context)
    private val _editText = editText // Get default EditText from parent
    private val incrementButton = Button(context)
    private val decrementButton = Button(context)
    private var lastValidEntry = 0

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context?) : super(context) {
        initialize()
    }

    override fun onCreateDialogView(): View {
        linearLayout.addView(decrementButton)
        linearLayout.addView(_editText)
        linearLayout.addView(incrementButton)
        return linearLayout
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)

        // Need to remove Views explicitly otherwise the app crashes when the setting is accessed again
        // Remove mEditText, mIncrementButton, mDecrementButton before removing mLinearLayout
        linearLayout.removeAllViews()
        val parent = linearLayout.parent as ViewGroup
        parent.removeView(linearLayout)
    }

    /**
     * Performs initial configurations which are common for all constructors.
     *
     *
     * Sets appropriate Text and OnClickListener to [.mIncrementButton] and [.mDecrementButton]
     * respectively.
     *
     *
     * Sets orientation for [.mLinearLayout].
     *
     *
     * Sets [.mEditText] width and gravity.
     */
    private fun initialize() {
        // Layout parameters for mEditText
        val editTextParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            3.0f
        )
        // Layout parameters for mIncrementButton and mDecrementButton
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        )
        lastValidEntry = try {
            _editText.text.toString().toInt()
        } catch (nfe: NumberFormatException) {
            // This should not be possible but just in case, recover with a valid minimum from superclass
            min
        }
        _editText.layoutParams = editTextParams
        // Centre text inside mEditText
        _editText.gravity = Gravity.CENTER_HORIZONTAL
        incrementButton.setText(R.string.plus_sign)
        decrementButton.setText(R.string.minus_sign)
        incrementButton.layoutParams = buttonParams
        decrementButton.layoutParams = buttonParams
        incrementButton.setOnClickListener { updateEditText(true) }
        decrementButton.setOnClickListener { updateEditText(false) }
        linearLayout.orientation = LinearLayout.HORIZONTAL
    }

    /**
     * Increments/Decrements the value of [.mEditText] by 1 based on the parameter value.
     *
     * @param isIncrement Indicator for whether to increase or decrease the value.
     */
    private fun updateEditText(isIncrement: Boolean) {
        var value: Int = try {
            _editText.text.toString().toInt()
        } catch (e: NumberFormatException) {
            // If the user entered a non-number then incremented, restore to a good value
            lastValidEntry
        }
        value = if (isIncrement) value + 1 else value - 1
        // Make sure value is within range
        lastValidEntry = super.getValidatedRangeFromInt(value)
        _editText.setText(lastValidEntry.toString())
        _editText.setSelection(_editText.text.length)
    }
}
