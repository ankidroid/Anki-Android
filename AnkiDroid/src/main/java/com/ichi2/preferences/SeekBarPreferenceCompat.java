/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *      The following code was written by Matthew Wiggins
 *      and is released under the APACHE 2.0 license
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  adjusted by Norbert Nagold 2011 <norbert.nagold@gmail.com>
 *  adjusted by David Allison 2021 <davidallisongithub@gmail.com>
 *    * Converted to androidx.preference.DialogPreference
 *    * Split into SeekBarPreferenceCompat and SeekBarDialogFragmentCompat
 */

package com.ichi2.preferences;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.ui.FixedTextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceDialogFragmentCompat;

public class SeekBarPreferenceCompat extends androidx.preference.DialogPreference {
    private static final String androidns = "http://schemas.android.com/apk/res/android";

    private String mSuffix;
    private int mDefault;
    private int mMax;
    private int mMin;
    private int mInterval;
    private int mValue = 0;
    @StringRes
    private int mXLabel;
    @StringRes
    private int mYLabel;

    public SeekBarPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupVariables(attrs);
    }


    public SeekBarPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupVariables(attrs);
    }


    public SeekBarPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupVariables(attrs);
    }


    public SeekBarPreferenceCompat(Context context) {
        super(context);
    }

    private void setupVariables(AttributeSet attrs) {
        mSuffix = attrs.getAttributeValue(androidns, "text");
        mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
        mMax = attrs.getAttributeIntValue(androidns, "max", 100);
        mMin = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0);
        mInterval = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "interval", 1);
        mXLabel = attrs.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "xlabel", 0);
        mYLabel = attrs.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "ylabel", 0);
    }



    @SuppressWarnings("deprecation") // 5019 - onSetInitialValue
    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue) {
        super.onSetInitialValue(restore, defaultValue);
        mValue = getPersistedInt(mDefault);
        if (restore) {
            mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
        } else {
            mValue = (Integer) defaultValue;
        }
    }

    public int getValue() {
        if (mValue == 0) {
            return getPersistedInt(mDefault);
        } else {
            return mValue;
        }
    }

    public void setValue(int value) {
        mValue = value;
        persistInt(value);
    }

    private void onCompleted() {
        if (shouldPersist()) {
            persistInt(mValue);
        }
        callChangeListener(mValue);
    }

    private String getValueText() {
        String t = String.valueOf(mValue);
        return mSuffix == null ? t : t + mSuffix;
    }

    // TODO: These could do with some thought as to either documentation, or defining the coupling between here and
    // SeekBarDialogFragmentCompat

    private void setRelativeValue(int value) {
        mValue = (value * mInterval) + mMin;
    }

    private int getRelativeMax() {
        return (mMax - mMin) / mInterval;
    }

    private int getRelativeProgress() {
        return (mValue - mMin) / mInterval;
    }

    private void setupTempValue() {
        if (!shouldPersist()) {
            return;
        }
        mValue = getPersistedInt(mDefault);
    }

    public static class SeekBarDialogFragmentCompat extends PreferenceDialogFragmentCompat
            implements SeekBar.OnSeekBarChangeListener {

        public static SeekBarDialogFragmentCompat newInstance(@NonNull String key) {
            SeekBarDialogFragmentCompat fragment = new SeekBarDialogFragmentCompat();
            Bundle b = new Bundle(1);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }


        private LinearLayout mSeekLine;
        private SeekBar mSeekBar;
        private TextView mValueText;

        @Override
        public SeekBarPreferenceCompat getPreference() {
            return (SeekBarPreferenceCompat) super.getPreference();
        }


        @Override
        public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
            if (fromUser) {
                getPreference().setRelativeValue(value);
                onValueUpdated();
            }
        }


        protected void onValueUpdated() {
            mValueText.setText(getPreference().getValueText());
        }


        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // intentionally left blank
        }


        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            getPreference().onCompleted();
            this.getDialog().dismiss();
        }


        @Override
        public void onDialogClosed(boolean positiveResult) {
            // nothing needed - see onStopTrackingTouch
        }


        @Override
        protected void onBindDialogView(View v) {
            super.onBindDialogView(v);
            mSeekBar.setMax(getPreference().getRelativeMax());
            mSeekBar.setProgress(getPreference().getRelativeProgress());
        }


        @Override
        protected void onPrepareDialogBuilder(androidx.appcompat.app.AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            builder.setNegativeButton(null, null);
            builder.setPositiveButton(null, null);
            builder.setTitle(null);
        }


        @Override
        protected View onCreateDialogView(Context context) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(6, 6, 6, 6);

            mValueText = new FixedTextView(context);
            mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
            mValueText.setTextSize(32);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            layout.addView(mValueText, params);

            mSeekBar = new SeekBar(context);
            mSeekBar.setOnSeekBarChangeListener(this);

            layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            SeekBarPreferenceCompat preference = getPreference();

            if (preference.mXLabel != 0 && preference.mYLabel != 0) {
                LinearLayout.LayoutParams params_seekbar = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params_seekbar.setMargins(0, 12, 0, 0);
                mSeekLine = new LinearLayout(context);
                mSeekLine.setOrientation(LinearLayout.HORIZONTAL);
                mSeekLine.setPadding(6, 6, 6, 6);
                addLabelsBelowSeekBar(context);
                layout.addView(mSeekLine, params_seekbar);
            }

            preference.setupTempValue();

            mSeekBar.setMax(preference.getRelativeMax());
            mSeekBar.setProgress(preference.getRelativeProgress());

            onValueUpdated();
            return layout;
        }




        private void addLabelsBelowSeekBar(Context context) {
            int[] labels = { getPreference().mXLabel, getPreference().mYLabel };
            for (int count = 0; count < 2; count++) {
                TextView textView = new FixedTextView(context);
                textView.setText(context.getString(labels[count]));
                textView.setGravity(Gravity.START);
                mSeekLine.addView(textView);
                if(context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR)
                    textView.setLayoutParams((count == 1) ? getLayoutParams(0.0f) : getLayoutParams(1.0f));
                else
                    textView.setLayoutParams((count == 0) ? getLayoutParams(0.0f) : getLayoutParams(1.0f));
            }
        }

        LinearLayout.LayoutParams getLayoutParams(float weight) {
            return new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        }

    }
}
