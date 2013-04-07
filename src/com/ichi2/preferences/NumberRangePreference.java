package com.ichi2.preferences;

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

import com.ichi2.anki.AnkiDroidApp;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.AttributeSet;

public class NumberRangePreference extends EditTextPreference {
   
    private final int mMin;
    private final int mMax;
    
    public NumberRangePreference(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
    }
    
    public NumberRangePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        mMin = attrs.getAttributeIntValue(AnkiDroidApp.APP_NAMESPACE, "min", 0);
        mMax = attrs.getAttributeIntValue(AnkiDroidApp.APP_NAMESPACE, "max", Integer.MAX_VALUE);
        
        // Cap number of digits in input
        int maxLength = String.valueOf(mMax).length();
        setMaxLength(maxLength);
    }
    
    public NumberRangePreference(Context context) {
        super(context);
        mMin = 0;
        mMax = Integer.MAX_VALUE;
    }
    
    
    /**
     * Set the maximum number of digits that can appear in the text editor.
     * @param max Number of digits.
     */
    private void setMaxLength(int max) {
        // Clone the existing filters so we don't override them, then append our one
        // at the end.
        InputFilter[] filters = getEditText().getFilters();
        InputFilter[] newFilters = new InputFilter[filters.length + 1];
        System.arraycopy(filters, 0, newFilters, 0, filters.length);
        newFilters[newFilters.length-1] = new InputFilter.LengthFilter(max);
        this.getEditText().setFilters(newFilters);
    }
    
    
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String validated = getValidatedRange(getEditText().getText().toString());
            this.setText(validated);
        }
    }
    
    
    /**
     * Return the input number with the number rounded to the nearest bound if it is outside
     * of the acceptable range.
     * @param input User input in text editor.
     * @return The input value within acceptable range.
     */
    private String getValidatedRange(String input) {
        int valueInt;
        if (TextUtils.isEmpty(input)) {
            valueInt = mMin;
        } else {
            try {
                valueInt = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                valueInt = mMin;
            }
        }
        if (valueInt < mMin) {
            valueInt = mMin;
        } else if (valueInt > mMax) {
            valueInt = mMax;
        }
        return String.valueOf(valueInt);
    }
}
