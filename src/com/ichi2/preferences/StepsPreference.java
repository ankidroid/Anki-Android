
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
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

import org.json.JSONArray;
import org.json.JSONException;

public class StepsPreference extends EditTextPreference {

    private final boolean mAllowEmpty;


    public StepsPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAllowEmpty = getAllowEmptyFromAttributes(attrs);
    }


    public StepsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAllowEmpty = getAllowEmptyFromAttributes(attrs);
    }


    public StepsPreference(Context context) {
        super(context);
        mAllowEmpty = getAllowEmptyFromAttributes(null);
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String validated = getValidatedStepsInput(getEditText().getText().toString());
            if (validated == null) {
                Themes.showThemedToast(getContext(),
                        getContext().getResources().getString(R.string.steps_error), false);
            } else if (TextUtils.isEmpty(validated) && !mAllowEmpty) {
                Themes.showThemedToast(getContext(),
                        getContext().getResources().getString(R.string.steps_min_error), false);
            } else {
                setText(validated);
            }
        }
    }


    /**
     * Check if the string is a valid format for steps and return that string, reformatted for better
     * usability if needed.
     * @param steps User input in text editor.
     * @return The correctly formatted string or null if the input is not valid.
     */
    private String getValidatedStepsInput(String steps) {
        JSONArray ja = convertToJSON(steps);
        if (ja == null) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            try {
                for (int i = 0; i < ja.length(); i++) {
                    sb.append(ja.getString(i)).append(" ");
                }
                return sb.toString().trim();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
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
        try {
            for (int i = 0; i < a.length(); i++) {
                sb.append(a.getString(i)).append(" ");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return sb.toString().trim();
    }


    /**
     * Convert steps format. For better usability, rounded floats are converted to integers (e.g.,
     * 1.0 is converted to 1).
     *
     * @param steps String representation of steps.
     * @return The steps as a JSONArray or null if the steps are not valid.
     */
    public static JSONArray convertToJSON(String steps) {
        JSONArray ja = new JSONArray();
        steps = steps.trim();
        if (TextUtils.isEmpty(steps)) {
            return ja;
        }
        try {
            for (String s : steps.split("\\s+")) {
                float f = Float.parseFloat(s);
                // 0 or less is not a valid step.
                if (f <= 0) {
                    return null;
                }
                // Use whole numbers if we can (but still allow decimals)
                int i = (int) f;
                if (i == f) {
                    ja.put(i);
                } else {
                    ja.put(f);
                }
            }
        } catch (NumberFormatException e) {
            return null;
        } catch (JSONException e) {
            // Can't serialize float. Value likely too big/small.
            return null;
        }
        return ja;
    }


    private boolean getAllowEmptyFromAttributes(AttributeSet attrs) {
        return attrs == null ? true : attrs.getAttributeBooleanValue(AnkiDroidApp.APP_NAMESPACE, "allowEmpty", true);
    }
}
