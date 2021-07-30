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
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;

import timber.log.Timber;

@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
public class StepsPreference extends android.preference.EditTextPreference {

    private final boolean mAllowEmpty;


    public StepsPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAllowEmpty = getAllowEmptyFromAttributes(attrs);
        updateSettings();
    }


    public StepsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAllowEmpty = getAllowEmptyFromAttributes(attrs);
        updateSettings();
    }


    public StepsPreference(Context context) {
        super(context);
        mAllowEmpty = getAllowEmptyFromAttributes(null);
        updateSettings();
    }


    /**
     * Update settings to show a numeric keyboard instead of the default keyboard.
     * <p>
     * This method should only be called once from the constructor.
     */
    private void updateSettings() {
        // Use the number pad but still allow normal text for spaces and decimals.
        getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_CLASS_TEXT);
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String validated = getValidatedStepsInput(getEditText().getText().toString());
            if (validated == null) {
                UIUtils.showThemedToast(getContext(), getContext().getResources().getString(R.string.steps_error), false);
            } else if (TextUtils.isEmpty(validated) && !mAllowEmpty) {
                UIUtils.showThemedToast(getContext(), getContext().getResources().getString(R.string.steps_min_error),
                        false);
            } else {
                setText(validated);
            }
        }
    }


    /**
     * Check if the string is a valid format for steps and return that string, reformatted for better usability if
     * needed.
     * 
     * @param steps User input in text editor.
     * @return The correctly formatted string or null if the input is not valid.
     */
    private String getValidatedStepsInput(String steps) {
        JSONArray stepsAr = convertToJSON(steps);
        if (stepsAr == null) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            for (String step: stepsAr.stringIterable()) {
                sb.append(step).append(" ");
            }
            return sb.toString().trim();
        }
    }


    /**
     * Convert steps format.
     * 
     * @param a JSONArray representation of steps.
     * @return The steps as a space-separated string.
     */
    public static String convertFromJSON(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        for (String s: a.stringIterable()) {
            sb.append(s).append(" ");
        }
        return sb.toString().trim();
    }


    /**
     * Convert steps format. For better usability, rounded floats are converted to integers (e.g., 1.0 is converted to
     * 1).
     * 
     * @param steps String representation of steps.
     * @return The steps as a JSONArray or null if the steps are not valid.
     */
    public static JSONArray convertToJSON(String steps) {
        JSONArray stepsAr = new JSONArray();
        steps = steps.trim();
        if (TextUtils.isEmpty(steps)) {
            return stepsAr;
        }
        try {
            for (String s : steps.split("\\s+")) {
                double d = Double.parseDouble(s);
                // 0 or less is not a valid step.
                if (d <= 0) {
                    return null;
                }
                // Use whole numbers if we can (but still allow decimals)
                int i = (int) d;
                if (i == d) {
                    stepsAr.put(i);
                } else {
                    stepsAr.put(d);
                }
            }
        } catch (NumberFormatException | JSONException e) {
            // Can't serialize float. Value likely too big/small.
            Timber.w(e);
            return null;
        }
        return stepsAr;
    }


    private boolean getAllowEmptyFromAttributes(AttributeSet attrs) {
        if (attrs == null) {
            return true;
        }
        return attrs.getAttributeBooleanValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "allowEmpty", true);
    }
}
