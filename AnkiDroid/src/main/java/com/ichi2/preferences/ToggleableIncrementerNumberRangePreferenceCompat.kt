// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.core.widget.doAfterTextChanged
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.databinding.DialogToggleableIncrementerPreferenceBinding
import com.ichi2.anki.ui.NonLeadingZeroInputFilter
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat.Companion.ValidationResult
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat.Companion.validate
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.positiveButton

class ToggleableIncrementerNumberRangePreferenceCompat :
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

    class ToggleableDialogFragmentCompat : NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat() {
        private var bindingRef: DialogToggleableIncrementerPreferenceBinding? = null
        private val binding get() = bindingRef!!
        private var lastValidEntry = 0
        private var positiveButton: Button? = null

        override fun onCreateDialogView(context: Context): View {
            bindingRef = DialogToggleableIncrementerPreferenceBinding.inflate(LayoutInflater.from(context))

            binding.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                binding.incrementerContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (isChecked && editText.text.toString() == "0") {
                    val defaultValue = maxOf(1, numberRangePreference.min)
                    editText.setText(defaultValue.toString())
                    editText.moveCursorToEnd()
                    lastValidEntry = defaultValue
                }
                updateButtonState()
            }

            binding.incrementButton.setOnClickListener { updateEditText(true) }
            binding.decrementButton.setOnClickListener { updateEditText(false) }

            return binding.root
        }

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            lastValidEntry =
                try {
                    editText.text.toString().toInt()
                } catch (nfe: NumberFormatException) {
                    numberRangePreference.min
                }

            editText.filters += NonLeadingZeroInputFilter
            editText.doAfterTextChanged { updateButtonState() }

            val isEnabled = lastValidEntry != 0
            binding.toggleSwitch.isChecked = isEnabled
            binding.incrementerContainer.visibility = if (isEnabled) View.VISIBLE else View.GONE

            updateButtonState()
        }

        override fun onStart() {
            super.onStart()
            positiveButton = (dialog as? androidx.appcompat.app.AlertDialog)?.positiveButton
            positiveButton?.text = TR.actionsSave()
            updateButtonState()
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (!positiveResult) return

            val newValue =
                if (binding.toggleSwitch.isChecked) {
                    numberRangePreference.getValidatedRangeFromString(editText.text.toString())
                } else {
                    0
                }

            if (numberRangePreference.callChangeListener(newValue)) {
                numberRangePreference.setValue(newValue)
            }
        }

        private fun updateEditText(isIncrement: Boolean) {
            var value: Int =
                try {
                    editText.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    lastValidEntry
                }
            value = if (isIncrement) value + 1 else value - 1

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

        private fun updateButtonState() {
            if (!binding.toggleSwitch.isChecked) {
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
                    // Empty input doesn't warrant an error message yet
                }
            }
        }
    }

    override fun makeDialogFragment() = ToggleableDialogFragmentCompat()
}
