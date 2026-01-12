/*
 * Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.ichi2.anki.R
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.positiveButton

/** Marker class to be used in preferences */
class IncrementerNumberRangePreferenceCompat :
    NumberRangePreferenceCompat,
    DialogFragmentProvider {
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

    class IncrementerNumberRangeDialogFragmentCompat : NumberRangeDialogFragmentCompat() {
        private var lastValidEntry = 0
        private lateinit var incrementButton: Button
        private lateinit var decrementButton: Button

        // Reference to the system OK button
        private var positiveButton: Button? = null

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

            lastValidEntry =
                try {
                    editText.text.toString().toInt()
                } catch (nfe: NumberFormatException) {
                    // This should not be possible but just in case, recover with a valid minimum from superclass
                    numberRangePreference.min
                }
            editText.layoutParams = editTextParams
            // Centre text inside mEditText
            editText.gravity = Gravity.CENTER_HORIZONTAL

            // Validate the final value, not individual character changes
            editText.doAfterTextChanged { updateButtonState() }

            // Initial check to set correct button states on open
            updateButtonState()
        }

        override fun onStart() {
            super.onStart()
            positiveButton = (dialog as? androidx.appcompat.app.AlertDialog)?.positiveButton

            // Rerun validation now that we have the OK button reference
            updateButtonState()
        }

        /**
         * Sets appropriate Text and OnClickListener for buttons
         *
         * Sets orientation for layout
         */
        override fun onCreateDialogView(context: Context): View {
            val linearLayout = LinearLayout(context)

            incrementButton = Button(context)
            decrementButton = Button(context)
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
                    lastValidEntry
                }
            value = if (isIncrement) value + 1 else value - 1

            // Make sure value is within range
            lastValidEntry = numberRangePreference.getValidatedRangeFromInt(value)
            editText.setText(lastValidEntry.toString())
            editText.moveCursorToEnd()

            updateButtonState()
        }

        /**
         * Validates the current input and updates the state of buttons
         *
         * Displays specific error messages for overflow and range limits
         */
        private fun updateButtonState() {
            val text = editText.text.toString()
            val value = text.toIntOrNull()

            val result = validate(text, value, numberRangePreference.min, numberRangePreference.max)

            incrementButton.isEnabled = false
            decrementButton.isEnabled = false
            positiveButton?.isEnabled = false
            editText.error = null

            when (result) {
                ValidationResult.VALID -> {
                    positiveButton?.isEnabled = true
                    // Even if valid, we might be at the edge of the range, so update +/- buttons
                    if (value != null) {
                        incrementButton.isEnabled = value < numberRangePreference.max
                        decrementButton.isEnabled = value > numberRangePreference.min
                    }
                }
                ValidationResult.OVERFLOW -> {
                    decrementButton.isEnabled = true
                    editText.error = getString(R.string.maximum_value_is, numberRangePreference.max)
                }
                ValidationResult.UNDERFLOW -> {
                    incrementButton.isEnabled = true
                    editText.error = getString(R.string.minimum_value_is, numberRangePreference.min)
                }
                ValidationResult.INVALID -> {
                    editText.error = getString(R.string.invalid_value)
                }
                ValidationResult.EMPTY -> {
                    // Empty input is invalid for submission, but doesn't warrant an error message yet
                }
            }
        }
    }

    companion object {
        enum class ValidationResult {
            VALID,
            INVALID,
            EMPTY,
            OVERFLOW,
            UNDERFLOW,
        }

        /**
         * Validation logic for the preference input.
         *
         * @param text The raw string from the EditText.
         * @param value The parsed integer value (or null if parsing failed).
         * @param min The minimum allowed value.
         * @param max The maximum allowed value.
         * @return A [ValidationResult] representing the state of the input.
         */
        fun validate(
            text: String,
            value: Int?,
            min: Int,
            max: Int,
        ): ValidationResult =
            when {
                text.isEmpty() -> ValidationResult.EMPTY
                value == null -> ValidationResult.INVALID
                value > max -> ValidationResult.OVERFLOW
                value < min -> ValidationResult.UNDERFLOW
                else -> ValidationResult.VALID
            }
    }

    override fun makeDialogFragment() = IncrementerNumberRangeDialogFragmentCompat()
}
