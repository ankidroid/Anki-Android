/****************************************************************************************
 * Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>                                *
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>                      *
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
import android.widget.EditText
import android.widget.LinearLayout
import com.ichi2.anki.R

/** Marker class to be used in preferences */
class IncrementerNumberRangePreferenceCompat : NumberRangePreferenceCompat, DialogFragmentProvider {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    class IncrementerNumberRangeDialogFragmentCompat : NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat() {
        private var mLastValidEntry = 0

        /**
         * Sets [.mEditText] width and gravity.
         */
        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            // Layout parameters for mEditText
            val editTextParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    3.0f,
                )

            mLastValidEntry =
                try {
                    editText.text.toString().toInt()
                } catch (nfe: NumberFormatException) {
                    // This should not be possible but just in case, recover with a valid minimum from superclass
                    numberRangePreference.min
                }
            editText.layoutParams = editTextParams
            // Centre text inside mEditText
            editText.gravity = Gravity.CENTER_HORIZONTAL
        }

        /**
         * Sets appropriate Text and OnClickListener for buttons
         *
         * Sets orientation for layout
         */
        override fun onCreateDialogView(context: Context): View {
            val linearLayout = LinearLayout(context)

            val incrementButton = Button(context)
            val decrementButton = Button(context)
            val dialogView = super.onCreateDialogView(context)!!
            val editText: EditText = dialogView.findViewById(android.R.id.edit)
            (editText.parent as ViewGroup).removeView(editText)

            // Layout parameters for incrementButton and decrementButton
            val buttonParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f,
                )

            incrementButton.setText(R.string.plus_sign)
            decrementButton.setText(R.string.minus_sign)
            incrementButton.layoutParams = buttonParams
            decrementButton.layoutParams = buttonParams
            incrementButton.setOnClickListener { updateEditText(true) }
            decrementButton.setOnClickListener { updateEditText(false) }
            linearLayout.orientation = LinearLayout.HORIZONTAL

            linearLayout.addView(decrementButton)
            linearLayout.addView(editText)
            linearLayout.addView(incrementButton)
            return linearLayout
        }

        /**
         * Increments/Decrements the value of [.mEditText] by 1 based on the parameter value.
         *
         * @param isIncrement Indicator for whether to increase or decrease the value.
         */
        private fun updateEditText(isIncrement: Boolean) {
            var value: Int =
                try {
                    editText.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    // If the user entered a non-number then incremented, restore to a good value
                    mLastValidEntry
                }
            value = if (isIncrement) value + 1 else value - 1

            // Make sure value is within range
            mLastValidEntry = numberRangePreference.getValidatedRangeFromInt(value)
            editText.setText(mLastValidEntry.toString())
            editText.setSelection(editText.text.length)
        }
    }

    override fun makeDialogFragment() = IncrementerNumberRangeDialogFragmentCompat()
}
