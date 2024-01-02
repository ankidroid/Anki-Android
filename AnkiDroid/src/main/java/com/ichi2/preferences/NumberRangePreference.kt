/****************************************************************************************
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import com.ichi2.anki.AnkiDroidApp
import timber.log.Timber

@Suppress("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 : use NumberRangePreferenceCompat
open class NumberRangePreference : android.preference.EditTextPreference, AutoFocusable {
    protected val min: Int
    private val max: Int

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        min = getMinFromAttributes(attrs)
        max = getMaxFromAttributes(attrs)
        updateSettings()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        min = getMinFromAttributes(attrs)
        max = getMaxFromAttributes(attrs)
        updateSettings()
    }

    constructor(context: Context?) : super(context) {
        min = getMinFromAttributes(null)
        max = getMaxFromAttributes(null)
        updateSettings()
    }

    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)
        autoFocusAndMoveCursorToEnd(editText)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val validated = getValidatedRangeFromString(editText.text.toString())
            value = validated
        }
    }

    /*
     * Since this preference deals with integers only, it makes sense to only store and retrieve integers. However,
     * since it is extending EditTextPreference, the persistence and retrieval methods that are called are for a String
     * type. The two methods below intercept the persistence and retrieval methods for Strings and replaces them with
     * their Integer equivalents.
     */
    override fun getPersistedString(defaultReturnValue: String?): String? {
        return getPersistedInt(min).toString()
    }

    override fun persistString(value: String): Boolean {
        return persistInt(value.toInt())
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
     * Return the integer rounded to the nearest bound if it is outside of the acceptable range.
     *
     * @param input Integer to validate.
     * @return The input value within acceptable range.
     */
    protected fun getValidatedRangeFromInt(input: Int): Int {
        var result = input
        if (input < min) {
            result = min
        } else if (input > max) {
            result = max
        }
        return result
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
     * Update settings to only allow integer input and set the maximum number of digits allowed in the text field based
     * on the current value of the [.mMax] field.
     *
     *
     * This method should only be called once from the constructor.
     */
    private fun updateSettings() {
        // Only allow integer input
        editText.inputType = InputType.TYPE_CLASS_NUMBER

        // Set max number of digits
        val maxLength = max.toString().length
        // Clone the existing filters so we don't override them, then append our one at the end.
        val filters = editText.filters
        val newFilters = arrayOfNulls<InputFilter>(filters.size + 1)
        System.arraycopy(filters, 0, newFilters, 0, filters.size)
        newFilters[newFilters.size - 1] = LengthFilter(maxLength)
        editText.filters = newFilters
    }
    var value: Int
        /**
         * Get the persisted value held by this preference.
         *
         * @return the persisted value.
         */
        get() = getPersistedInt(min)

        /**
         * Set this preference's value. The value is validated and persisted as an Integer.
         *
         * @param value to set.
         */
        set(value) {
            val validated = getValidatedRangeFromInt(value)
            text = validated.toString()
            persistInt(validated)
        }
}
