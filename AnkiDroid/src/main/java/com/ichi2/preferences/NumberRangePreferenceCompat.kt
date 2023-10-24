/****************************************************************************************
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.core.content.withStyledAttributes
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.annotations.NeedsTest
import timber.log.Timber

open class NumberRangePreferenceCompat
@JvmOverloads // fixes: Error inflating class com.ichi2.preferences.NumberRangePreferenceCompat
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.editTextPreferenceStyle,
    defStyleRes: Int = androidx.preference.R.style.Preference_DialogPreference_EditTextPreference
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes), DialogFragmentProvider {

    var defaultValue: String? = null

    var min = 0
        protected set
    var max = 0
        private set

    init {
        min = attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0) ?: 0
        max = attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "max", Int.MAX_VALUE) ?: Int.MAX_VALUE
        defaultValue = attrs?.getAttributeValue("http://schemas.android.com/apk/res/android", "defaultValue")

        context.withStyledAttributes(attrs, R.styleable.CustomPreference) {
            val summaryFormat = this.getString(R.styleable.CustomPreference_summaryFormat)
            if (summaryFormat != null) {
                setSummaryProvider {
                    String.format(summaryFormat, text)
                }
            }
        }
    }

    /** The maximum available number of digits */
    val maxDigits: Int get() = max.toString().length

    /*
     * Since this preference deals with integers only, it makes sense to only store and retrieve integers. However,
     * since it is extending EditTextPreference, the persistence and retrieval methods that are called are for a String
     * type. The two methods below intercept the persistence and retrieval methods for Strings and replaces them with
     * their Integer equivalents.
     */
    override fun getPersistedString(defaultReturnValue: String?): String? {
        return getPersistedInt(getDefaultValue()).toString()
    }

    override fun persistString(value: String): Boolean {
        return persistInt(value.toInt())
    }

    /**
     * Return the integer rounded to the nearest bound if it is outside of the acceptable range.
     *
     * @param input Integer to validate.
     * @return The input value within acceptable range.
     */
    fun getValidatedRangeFromInt(input: Int): Int {
        if (input < min) {
            return min
        } else if (input > max) {
            return max
        }
        return input
    }

    /**
     * Return the string as an int with the number rounded to the nearest bound if it is outside of the acceptable
     * range.
     *
     * @param input User input in text editor.
     * @return The input value within acceptable range.
     */
    private fun getValidatedRangeFromString(input: String): Int {
        return if (input.isEmpty()) {
            min
        } else {
            try {
                getValidatedRangeFromInt(input.toInt())
            } catch (e: NumberFormatException) {
                Timber.w(e)
                min
            }
        }
    }

    /**
     * Get the persisted value held by this preference.
     *
     * @return the persisted value.
     */
    fun getValue(): Int {
        return getPersistedInt(getDefaultValue())
    }

    private fun getDefaultValue(): Int {
        return try {
            return defaultValue?.toInt() ?: min
        } catch (e: Exception) {
            min
        }
    }

    /**
     * Set this preference's value. The value is validated and persisted as an Integer.
     *
     * @param value to set.
     */
    fun setValue(value: Int) {
        val validated = getValidatedRangeFromInt(value)
        text = validated.toString()
        persistInt(validated)
    }

    /**
     * Set this preference's value. The value is validated and persisted as an Integer.
     *
     * @param value to set.
     */
    fun setValue(value: String) {
        val fromString = getValidatedRangeFromString(value)
        text = fromString.toString()
        persistInt(fromString)
    }

    open class NumberRangeDialogFragmentCompat : EditTextPreferenceDialogFragmentCompat() {

        val numberRangePreference: NumberRangePreferenceCompat get() = preference as NumberRangePreferenceCompat

        lateinit var editText: EditText

        /**
         * Update settings to only allow integer input and set the maximum number of digits allowed in the text field based
         * on the current value of the [.mMax] field.
         */
        override fun onBindDialogView(view: View) {
            editText = view.findViewById(android.R.id.edit)!!

            // Only allow integer input
            editText.inputType = InputType.TYPE_CLASS_NUMBER

            // Clone the existing filters so we don't override them, then append our one at the end.
            editText.filters = arrayOf(*editText.filters, InputFilter.LengthFilter(numberRangePreference.maxDigits))

            super.onBindDialogView(view)
        }

        @NeedsTest("value is set to preference previous value if text is blank")
        override fun onDialogClosed(positiveResult: Boolean) {
            // don't change the value if the dialog was cancelled or closed without any text
            if (!positiveResult || editText.text.isEmpty()) {
                return
            }
            val newValue = editText.text.toString().toInt()
            if (numberRangePreference.callChangeListener(newValue)) {
                numberRangePreference.setValue(newValue)
            }
        }
    }

    enum class ShouldShowDialog { Yes, No }

    var onClickListener: () -> ShouldShowDialog = { ShouldShowDialog.Yes }

    override fun onClick() {
        if (onClickListener() == ShouldShowDialog.Yes) { super.onClick() }
    }

    override fun makeDialogFragment() = NumberRangeDialogFragmentCompat()
}
