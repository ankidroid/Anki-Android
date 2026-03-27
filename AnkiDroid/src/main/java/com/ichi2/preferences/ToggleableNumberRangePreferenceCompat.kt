package com.ichi2.preferences

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.ichi2.anki.R
import timber.log.Timber

/**
 * A preference that extends NumberRangePreferenceCompat to add a toggle switch.
 * When toggled off, the feature is disabled. When toggled on, the user can enter a value.
 */
open class ToggleableNumberRangePreferenceCompat(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.editTextPreferenceStyle,
    defStyleRes: Int = androidx.preference.R.style.Preference_DialogPreference_EditTextPreference,
) : NumberRangePreferenceCompat(context, attrs, defStyleAttr, defStyleRes),
    DialogFragmentProvider {

    companion object {
        //value to indicate feature is disabled
        private const val DISABLED_VALUE = -1
    }

    // check if this preference is currently enable
    fun isFeatureEnabled(): Boolean {
        return try {
            val value = getValue()
            value != DISABLED_VALUE
        } catch (e: Exception) {
            Timber.w(e, "Error checking if feature is enabled")
            false
        }
    }

    //Set feature enabled/disabled state
    fun setFeatureEnabled(enabled: Boolean) {
        if (enabled) {
            // If enabling and no value was set - use min value
            if (!isFeatureEnabled()) {
                setValue(min)
            }
        } else {
            // Store disabled state as -1
            persistInt(DISABLED_VALUE)
            text = DISABLED_VALUE.toString()
        }
    }

    // create the dialog fragment for this preference
    override fun makeDialogFragment() = ToggleableNumberRangeDialogFragmentCompat()

    // handles the ui for the toggle + number input dialog
    open class ToggleableNumberRangeDialogFragmentCompat :
        NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat() {

        // Property to safely cast the preference
        private val toggleablePreference: ToggleableNumberRangePreferenceCompat
            get() = preference as ToggleableNumberRangePreferenceCompat

        private lateinit var toggle: SwitchCompat
        private lateinit var inputContainer: LinearLayout

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            // find toggle and input container from the layout
            toggle = view.findViewById(R.id.toggle_enable)
                ?: throw IllegalStateException("toggle_enable not found in dialog layout")
            inputContainer = view.findViewById(R.id.input_container)
                ?: throw IllegalStateException("input_container not found in dialog layout")

            // Check current state: is the feature enabled?
            val isEnabled = toggleablePreference.isFeatureEnabled()

            // set toggle to match current state
            toggle.isChecked = isEnabled

            // show/hide input based on toggle state
            inputContainer.visibility = if (isEnabled) View.VISIBLE else View.GONE

            // listen for toggle changes
            toggle.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                // when user toggles, show/hide the input container
                inputContainer.visibility = if (isChecked) View.VISIBLE else View.GONE

                // if toggling ON and field is empty, set default to min value
                if (isChecked && editText.text.isEmpty()) {
                    editText.setText(toggleablePreference.min.toString())
                }
            }
        }

        // this is where we save the user's choice
        override fun onDialogClosed(positiveResult: Boolean) {
            // only proceed if user pressed positive button (OK)
            if (!positiveResult) {
                return
            }

            // If toggle is OFF, disable the feature (store -1)
            if (!toggle.isChecked) {
                toggleablePreference.setFeatureEnabled(false)
                return
            }

            // If toggle is ON but input is empty, don't change value
            if (editText.text.isEmpty()) {
                return
            }

            // Validate the input: ensure it's within min/max range
            val newValue = toggleablePreference.getValidatedRangeFromString(editText.text.toString())

            // Call change listener and save if listener returns true
            if (toggleablePreference.callChangeListener(newValue)) {
                toggleablePreference.setValue(newValue)
            }
        }
    }
}