/****************************************************************************************
 * Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>                                *
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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.ichi2.anki.R;


@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 : use IncrementerNumberRangePreferenceCompat
public class IncrementerNumberRangePreference extends NumberRangePreference {

    private final LinearLayout mLinearLayout = new LinearLayout(getContext());
    private final EditText mEditText = getEditText(); // Get default EditText from parent
    private final Button mIncrementButton = new Button(getContext());
    private final Button mDecrementButton = new Button(getContext());
    private int mLastValidEntry;


    public IncrementerNumberRangePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }


    public IncrementerNumberRangePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }


    public IncrementerNumberRangePreference(Context context) {
        super(context);
        initialize();
    }


    @Override
    protected View onCreateDialogView() {
        mLinearLayout.addView(mDecrementButton);
        mLinearLayout.addView(mEditText);
        mLinearLayout.addView(mIncrementButton);

        return mLinearLayout;
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        // Need to remove Views explicitly otherwise the app crashes when the setting is accessed again
        // Remove mEditText, mIncrementButton, mDecrementButton before removing mLinearLayout
        mLinearLayout.removeAllViews();
        ViewGroup parent = (ViewGroup) mLinearLayout.getParent();
        parent.removeView(mLinearLayout);
    }


    /**
     * Performs initial configurations which are common for all constructors.
     * <p>
     * Sets appropriate Text and OnClickListener to {@link #mIncrementButton} and {@link #mDecrementButton}
     * respectively.
     * <p>
     * Sets orientation for {@link #mLinearLayout}.
     * <p>
     * Sets {@link #mEditText} width and gravity.
     */
    private void initialize() {
        // Layout parameters for mEditText
        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                3.0f
        );
        // Layout parameters for mIncrementButton and mDecrementButton
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );

        try {
            mLastValidEntry = Integer.parseInt(mEditText.getText().toString());
        } catch (NumberFormatException nfe) {
            // This should not be possible but just in case, recover with a valid minimum from superclass
            mLastValidEntry = mMin;
        }

        mEditText.setLayoutParams(editTextParams);
        // Centre text inside mEditText
        mEditText.setGravity(Gravity.CENTER_HORIZONTAL);

        mIncrementButton.setText(R.string.plus_sign);
        mDecrementButton.setText(R.string.minus_sign);
        mIncrementButton.setLayoutParams(buttonParams);
        mDecrementButton.setLayoutParams(buttonParams);
        mIncrementButton.setOnClickListener(view -> updateEditText(true));
        mDecrementButton.setOnClickListener(view -> updateEditText(false));

        mLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
    }


    /**
     * Increments/Decrements the value of {@link #mEditText} by 1 based on the parameter value.
     *
     * @param isIncrement Indicator for whether to increase or decrease the value.
     */
    private void updateEditText(boolean isIncrement) {
        int value;
        try {
            value = Integer.parseInt(mEditText.getText().toString());
        } catch (NumberFormatException e) {
            // If the user entered a non-number then incremented, restore to a good value
            value = mLastValidEntry;
        }
        value = isIncrement ? value + 1 : value - 1;
        // Make sure value is within range
        mLastValidEntry = super.getValidatedRangeFromInt(value);
        mEditText.setText(String.valueOf(mLastValidEntry));
    }
}
