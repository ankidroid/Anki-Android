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
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.ichi2.anki.AnkiDroidApp
import timber.log.Timber

open class NumberRangePreferenceCompat : EditTextPreference {
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        min = getMinFromAttributes(attrs)
        max = getMaxFromAttributes(attrs)
        defaultValue = getDefaultValueFromAttributes(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        min = getMinFromAttributes(attrs)
        max = getMaxFromAttributes(attrs)
        defaultValue = getDefaultValueFromAttributes(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        min = getMinFromAttributes(attrs)
        max = getMaxFromAttributes(attrs)
        defaultValue = getDefaultValueFromAttributes(attrs)
    }
    constructor(context: Context?) : super(context) {
        defaultValue = null
    }

    val defaultValue: String?

    var min = 0
        protected set
    var max = 0
        private set

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
        return if (TextUtils.isEmpty(input)) {
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
     * Returns the value of the min attribute, or its default value if not specified
     *
     *
     * This method should only be called once from the constructor.
     */
    private fun getMinFromAttributes(attrs: AttributeSet?): Int {
        return attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0) ?: 0
    }

    /**
     * Returns the value of the max attribute, or its default value if not specified
     *
     *
     * This method should only be called once from the constructor.
     */
    private fun getMaxFromAttributes(attrs: AttributeSet?): Int {
        return attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "max", Int.MAX_VALUE)
            ?: Int.MAX_VALUE
    }

    /**
     * Returns the default Value, or null if not specified
     *
     * This method should only be called once from the constructor.
     */
    private fun getDefaultValueFromAttributes(attrs: AttributeSet?): String? {
        return attrs?.getAttributeValue("http://schemas.android.com/apk/res/android", "defaultValue")
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
        override fun onBindDialogView(view: View?) {
            super.onBindDialogView(view)

            editText = view?.findViewById(android.R.id.edit)!!

            // Only allow integer input
            editText.inputType = InputType.TYPE_CLASS_NUMBER

            // Clone the existing filters so we don't override them, then append our one at the end.
            val filters: Array<InputFilter> = editText.filters
            val newFilters = arrayOfNulls<InputFilter>(filters.size + 1)
            System.arraycopy(filters, 0, newFilters, 0, filters.size)
            newFilters[newFilters.size - 1] = InputFilter.LengthFilter(numberRangePreference.maxDigits)
            editText.filters = newFilters
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (!positiveResult) {
                return
            }

            numberRangePreference.setValue(editText.text.toString())
        }

        companion object {
            @JvmStatic
            fun newInstance(key: String?): NumberRangeDialogFragmentCompat {
                val fragment = NumberRangeDialogFragmentCompat()
                val b = Bundle(1)
                b.putString(ARG_KEY, key)
                fragment.arguments = b
                return fragment
            }
        }
    }
}
