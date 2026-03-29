package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.databinding.DialogToggleableNumberRangeBinding
import com.ichi2.anki.ui.NonLeadingZeroInputFilter
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.positiveButton

/** Marker class to be used in preferences */
class ToggleableNumberRangePreferenceCompat :
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

    class ToggleableNumberRangeDialogFragmentCompat : NumberRangeDialogFragmentCompat() {
        private var bindingRef: DialogToggleableNumberRangeBinding? = null
        private val binding get() = bindingRef!!
        private var lastValidEntry = 0

        // Reference to the system OK button
        private var positiveButton: Button? = null

        /**
         * Sets [.mEditText] width and gravity.
         */
        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            // Set toggle based on whether the feature is currently enabled
            val pref = preference as ToggleableNumberRangePreferenceCompat
            val isEnabled = pref.isFeatureEnabled()

            binding.toggleEnable.isChecked = isEnabled
            binding.inputContainer.visibility = if (isEnabled) View.VISIBLE else View.GONE

            binding.toggleEnable.setOnCheckedChangeListener { _, checked ->
                binding.inputContainer.visibility = if (checked) View.VISIBLE else View.GONE
                updateButtonState()
            }

            val persistedValue = pref.getPersistedString(numberRangePreference.min.toString())
            val persistedInt = persistedValue?.toIntOrNull()

            lastValidEntry =
                try {
                    val currentValue = editText.text.toString().toInt()
                    if (currentValue == DISABLED_VALUE) {
                        1
                    } else {
                        currentValue
                    }
                } catch (nfe: NumberFormatException) {
                    1
                }

            // If currently disabled (stored as 0), show a normal editable default value
            if ((editText.text.toString().toIntOrNull() ?: DISABLED_VALUE) == DISABLED_VALUE) {
                editText.setText(lastValidEntry.toString())
            }

            editText.filters += NonLeadingZeroInputFilter
            // Validate the final value, not individual character changes
            editText.doAfterTextChanged { updateButtonState() }

            // Initial check to set correct button states on open
            updateButtonState()
        }

        override fun onStart() {
            super.onStart()
            positiveButton = (dialog as? androidx.appcompat.app.AlertDialog)?.positiveButton
            positiveButton?.text = TR.actionsSave()

            // Rerun validation now that we have the OK button reference
            updateButtonState()
        }

        /**
         * Sets appropriate Text and OnClickListener for buttons
         *
         * Sets orientation for layout
         */
        override fun onCreateDialogView(context: Context): View {
            bindingRef = DialogToggleableNumberRangeBinding.inflate(LayoutInflater.from(context))

            binding.incrementButton.setOnClickListener { updateEditText(true) }
            binding.decrementButton.setOnClickListener { updateEditText(false) }

            return binding.root
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

        override fun onDestroyView() {
            super.onDestroyView()
            bindingRef = null
            positiveButton = null
        }

        /**
         * Validates the current input and updates the state of buttons
         *
         * Displays specific error messages for overflow and range limits
         */
        private fun updateButtonState() {
            if (!binding.toggleEnable.isChecked) {
                binding.textInputLayout.error = null
                binding.textInputLayout.isErrorEnabled = false
                binding.incrementButton.isEnabled = false
                binding.decrementButton.isEnabled = false
                positiveButton?.isEnabled = true
                return
            }
            val text = editText.text.toString()
            val value = text.toIntOrNull()

            val result = validate(text, value, numberRangePreference.min, numberRangePreference.max)

            binding.textInputLayout.error = null
            binding.textInputLayout.isErrorEnabled = false

            binding.incrementButton.isEnabled = false
            binding.decrementButton.isEnabled = false
            positiveButton?.isEnabled = false

            when (result) {
                ValidationResult.VALID -> {
                    positiveButton?.isEnabled = true
                    // Even if valid, we might be at the edge of the range, so update +/- buttons
                    if (value != null) {
                        binding.incrementButton.isEnabled = value < numberRangePreference.max
                        binding.decrementButton.isEnabled = value > numberRangePreference.min
                    }
                }
                ValidationResult.OVERFLOW -> {
                    binding.decrementButton.isEnabled = true
                    binding.textInputLayout.error = getString(R.string.maximum_value_is, numberRangePreference.max)
                }
                ValidationResult.UNDERFLOW -> {
                    binding.incrementButton.isEnabled = true
                    binding.textInputLayout.error = getString(R.string.minimum_value_is, numberRangePreference.min)
                }
                ValidationResult.INVALID -> {
                    binding.textInputLayout.error = getString(R.string.invalid_value)
                }
                ValidationResult.EMPTY -> {
                    // Empty input is invalid for submission, but doesn't warrant an error message yet
                }
            }
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (!positiveResult) {
                return
            }

            val pref = preference as ToggleableNumberRangePreferenceCompat
            // If switch is OFF, save 0 to disable
            if (!binding.toggleEnable.isChecked) {
                editText.setText(DISABLED_VALUE.toString())
                super.onDialogClosed(true)
                pref.notifyChanged()
                return
            }

            // If switch is ON, let parent save the entered number normally
            super.onDialogClosed(true)
            pref.notifyChanged()
        }
    }

    // Returns true if the feature is enabled.
    fun isFeatureEnabled(): Boolean {
        val persistedValue = getPersistedString(min.toString())
        return persistedValue != DISABLED_VALUE.toString()
    }

    // Enables or disables the feature by explicitly persisting the value.
    fun setFeatureEnabled(enabled: Boolean) {
        if (enabled) {
            if (!isFeatureEnabled()) {
                val enabledValue = if (min <= 0) 1 else min
                persistString(enabledValue.toString())
            }
        } else {
            persistString(DISABLED_VALUE.toString())
        }
        notifyChanged()
    }

    // Shows a custom summary on the settings screen.
    override fun getSummary(): CharSequence? {
        val persistedValue = getPersistedString(min.toString())
        return if (persistedValue == DISABLED_VALUE.toString()) {
            "Disabled"
        } else {
            super.getSummary()
        }
    }

    companion object {
        const val DISABLED_VALUE = 0
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

    override fun makeDialogFragment() = ToggleableNumberRangeDialogFragmentCompat()
}
