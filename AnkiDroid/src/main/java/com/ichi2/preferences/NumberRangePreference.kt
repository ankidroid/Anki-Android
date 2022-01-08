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

package com.ichi2.preferences;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.ichi2.anki.AnkiDroidApp;

import timber.log.Timber;

@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 : use NumberRangePreferenceCompat
public class NumberRangePreference extends android.preference.EditTextPreference {

    protected final int mMin;
    private final int mMax;


    public NumberRangePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mMin = getMinFromAttributes(attrs);
        mMax = getMaxFromAttributes(attrs);
        updateSettings();
    }


    public NumberRangePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMin = getMinFromAttributes(attrs);
        mMax = getMaxFromAttributes(attrs);
        updateSettings();
    }


    public NumberRangePreference(Context context) {
        super(context);
        mMin = getMinFromAttributes(null);
        mMax = getMaxFromAttributes(null);
        updateSettings();
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            int validated = getValidatedRangeFromString(getEditText().getText().toString());
            setValue(validated);
        }
    }


    /*
     * Since this preference deals with integers only, it makes sense to only store and retrieve integers. However,
     * since it is extending EditTextPreference, the persistence and retrieval methods that are called are for a String
     * type. The two methods below intercept the persistence and retrieval methods for Strings and replaces them with
     * their Integer equivalents.
     */

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        return String.valueOf(getPersistedInt(mMin));
    }


    @Override
    protected boolean persistString(String value) {
        return persistInt(Integer.parseInt(value));
    }


    /**
     * Return the string as an int with the number rounded to the nearest bound if it is outside of the acceptable
     * range.
     * 
     * @param input User input in text editor.
     * @return The input value within acceptable range.
     */
    private int getValidatedRangeFromString(String input) {
        if (TextUtils.isEmpty(input)) {
            return mMin;
        } else {
            try {
                return getValidatedRangeFromInt(Integer.parseInt(input));
            } catch (NumberFormatException e) {
                Timber.w(e);
                return mMin;
            }
        }
    }


    /**
     * Return the integer rounded to the nearest bound if it is outside of the acceptable range.
     * 
     * @param input Integer to validate.
     * @return The input value within acceptable range.
     */
    protected int getValidatedRangeFromInt(int input) {
        if (input < mMin) {
            input = mMin;
        } else if (input > mMax) {
            input = mMax;
        }
        return input;
    }


    /**
     * Returns the value of the min attribute, or its default value if not specified
     * <p>
     * This method should only be called once from the constructor.
     */
    private int getMinFromAttributes(AttributeSet attrs) {
        return attrs == null ? 0 : attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0);
    }


    /**
     * Returns the value of the max attribute, or its default value if not specified
     * <p>
     * This method should only be called once from the constructor.
     */
    private int getMaxFromAttributes(AttributeSet attrs) {
        return attrs == null ? Integer.MAX_VALUE : attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "max",
                Integer.MAX_VALUE);
    }


    /**
     * Update settings to only allow integer input and set the maximum number of digits allowed in the text field based
     * on the current value of the {@link #mMax} field.
     * <p>
     * This method should only be called once from the constructor.
     */
    private void updateSettings() {
        // Only allow integer input
        getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);

        // Set max number of digits
        int maxLength = String.valueOf(mMax).length();
        // Clone the existing filters so we don't override them, then append our one at the end.
        InputFilter[] filters = getEditText().getFilters();
        InputFilter[] newFilters = new InputFilter[filters.length + 1];
        System.arraycopy(filters, 0, newFilters, 0, filters.length);
        newFilters[newFilters.length - 1] = new InputFilter.LengthFilter(maxLength);
        getEditText().setFilters(newFilters);
    }


    /**
     * Get the persisted value held by this preference.
     * 
     * @return the persisted value.
     */
    public int getValue() {
        return getPersistedInt(mMin);
    }


    /**
     * Set this preference's value. The value is validated and persisted as an Integer.
     * 
     * @param value to set.
     */
    public void setValue(int value) {
        int validated = getValidatedRangeFromInt(value);
        setText(Integer.toString(validated));
        persistInt(validated);
    }
}
