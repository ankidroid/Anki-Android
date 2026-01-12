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
import android.content.DialogInterface
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.R
import com.ichi2.utils.dp
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
        private lateinit var incrementButton: MaterialButton
        private lateinit var decrementButton: MaterialButton
        private lateinit var textInputLayout: TextInputLayout

        // Reference to the system OK button
        private var positiveButton: Button? = null

        /**
         * Sets [.mEditText] width and gravity.
         */
        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            editText.apply {
                gravity = Gravity.CENTER
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)

                val padding = 8.dp.toPx(context)
                setPadding(padding, padding, padding, padding)

                background = null
            }

            textInputLayout.errorIconDrawable = null

            editText.doAfterTextChanged { updateButtonState() }
            updateButtonState()
        }

        override fun onStart() {
            super.onStart()
            positiveButton = (dialog as? androidx.appcompat.app.AlertDialog)?.positiveButton

            lastValidEntry =
                numberRangePreference.getValidatedRangeFromInt(
                    numberRangePreference.getValue(),
                )

            editText.setText(lastValidEntry.toString())
            editText.moveCursorToEnd()
            updateButtonState()
        }

        /**
         * Sets appropriate Text and OnClickListener for buttons
         *
         * Sets orientation for layout
         */
        override fun onCreateDialogView(context: Context): View {
            val density = context.resources.displayMetrics.density

            val linearLayout =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val padding = 8.dp.toPx(context)
                    setPadding(padding, padding, padding, padding)
                }

            val buttonStyle = com.google.android.material.R.attr.materialIconButtonOutlinedStyle

            incrementButton =
                createStyledButton(context, buttonStyle).apply {
                    setIconResource(R.drawable.ic_add)
                    contentDescription = context.getString(R.string.plus_sign)
                }
            decrementButton =
                createStyledButton(context, buttonStyle).apply {
                    setIconResource(R.drawable.ic_remove)
                    contentDescription = context.getString(R.string.minus_sign)
                }

            val buttonHeight = (64 * density).toInt()
            val buttonWidth = (72 * density).toInt()
            val buttonParams =
                LinearLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                    val margin = 8.dp.toPx(context)
                    marginStart = margin
                    marginEnd = margin

                    gravity = Gravity.CENTER_VERTICAL
                }

            val dialogView = super.onCreateDialogView(context)!!
            val editText: EditText = dialogView.findViewById(android.R.id.edit)
            (editText.parent as ViewGroup).removeView(editText)

            textInputLayout =
                TextInputLayout(context).apply {
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    boxStrokeWidth = (1 * density).toInt()
                    val radius = 12f * density
                    setBoxCornerRadii(radius, radius, radius, radius)

                    isErrorEnabled = true
                    errorIconDrawable = null
                    isHelperTextEnabled = false

                    addView(editText)
                }

            val textParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }

            incrementButton.setOnClickListener { updateEditText(true) }
            decrementButton.setOnClickListener { updateEditText(false) }

            linearLayout.addView(decrementButton, buttonParams)
            linearLayout.addView(textInputLayout, textParams)
            linearLayout.addView(incrementButton, buttonParams)

            return linearLayout
        }

        private fun createStyledButton(
            context: Context,
            styleAttr: Int,
        ): MaterialButton =
            MaterialButton(context, null, styleAttr).apply {
                val density = context.resources.displayMetrics.density
                val size = (48 * density).toInt()

                setPadding(0, 0, 0, 0)
                iconPadding = 0
                insetTop = 0
                insetBottom = 0

                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                gravity = Gravity.CENTER

                iconSize = (24 * density).toInt()

                layoutParams = ViewGroup.LayoutParams(size, size)

                shapeAppearanceModel =
                    ShapeAppearanceModel
                        .builder()
                        .setAllCornerSizes(12f * density)
                        .build()
            }

        override fun onCancel(dialog: DialogInterface) {
            super.onCancel(dialog)

            lastValidEntry =
                numberRangePreference.getValidatedRangeFromInt(
                    numberRangePreference.getValue(),
                )
        }

        /**
         * Increments/Decrements the value of [.mEditText] by 1 based on the parameter value.
         *
         * @param isIncrement Indicator for whether to increase or decrease the value.
         */
        private fun updateEditText(isIncrement: Boolean) {
            val current = editText.text.toString().toIntOrNull() ?: lastValidEntry

            // Stop early if already at bounds
            if (isIncrement && current >= numberRangePreference.max) return
            if (!isIncrement && current <= numberRangePreference.min) return

            val newValue = if (isIncrement) current + 1 else current - 1
            lastValidEntry = numberRangePreference.getValidatedRangeFromInt(newValue)

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
            textInputLayout.error = null

            when (result) {
                ValidationResult.VALID -> {
                    lastValidEntry = value!!
                    positiveButton?.isEnabled = true

                    incrementButton.isEnabled = lastValidEntry < numberRangePreference.max
                    decrementButton.isEnabled = lastValidEntry > numberRangePreference.min
                }
                ValidationResult.OVERFLOW -> {
                    decrementButton.isEnabled = true
                    textInputLayout.error = getString(R.string.maximum_value_is, numberRangePreference.max)
                }
                ValidationResult.UNDERFLOW -> {
                    incrementButton.isEnabled = true
                    textInputLayout.error = getString(R.string.minimum_value_is, numberRangePreference.min)
                }
                ValidationResult.INVALID -> {
                    textInputLayout.error = getString(R.string.invalid_value)
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
