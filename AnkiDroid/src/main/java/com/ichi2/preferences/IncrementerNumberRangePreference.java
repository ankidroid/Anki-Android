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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;


@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
public class IncrementerNumberRangePreference extends NumberRangePreference {

    private final LinearLayout mLinearLayout = new LinearLayout(getContext());
    private final EditText mEditText = getEditText(); // Get default EditText from parent
    private final Button mIncrementButton = new Button(getContext());
    private final Button mDecrementButton = new Button(getContext());


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


    /**
     * Performs initial configurations which are common for all constructors.
     * <p>
     * Sets appropriate Text and OnClickListener to {@link #mIncrementButton} and {@link #mDecrementButton}
     * respectively.
     */
    private void initialize() {
        mIncrementButton.setText("+");
        mDecrementButton.setText("-");

        mIncrementButton.setOnClickListener(view -> {
            int value = Integer.parseInt(String.valueOf(mEditText.getText()));
            // Check (value + 1) is in range
            value = IncrementerNumberRangePreference.super.getValidatedRangeFromInt(value + 1);
            mEditText.setText(String.valueOf(value));
        });

        mDecrementButton.setOnClickListener(view -> {
            int value = Integer.parseInt(String.valueOf(mEditText.getText()));
            // Check (value - 1) is in range
            value = IncrementerNumberRangePreference.super.getValidatedRangeFromInt(value - 1);
            mEditText.setText(String.valueOf(value));
        });
    }


    @Override // TODO: Edit layout style to fill entire width
    protected View onCreateDialogView() {
        mLinearLayout.addView(mIncrementButton);
        mLinearLayout.addView(mEditText);
        mLinearLayout.addView(mDecrementButton);

        return mLinearLayout;
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        // Remove mEditText, mIncrementButton, mDecrementButton before removing mLinearLayout
        mLinearLayout.removeAllViews();
        ViewGroup parent = (ViewGroup) mLinearLayout.getParent();
        parent.removeView(mLinearLayout);
    }
}
