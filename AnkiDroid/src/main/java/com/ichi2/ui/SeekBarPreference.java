/* The following code was written by Matthew Wiggins
 * and is released under the APACHE 2.0 license
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * adjusted by Norbert Nagold 2011 <norbert.nagold@gmail.com>
 */

package com.ichi2.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ichi2.anki.AnkiDroidApp;

public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
    private static final String androidns = "http://schemas.android.com/apk/res/android";

    private SeekBar mSeekBar;
    private TextView mValueText;
    private Context mContext;

    private String mSuffix;
    private int mDefault, mMax, mMin, mInterval, mValue = 0;


    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mSuffix = attrs.getAttributeValue(androidns, "text");
        mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
        mMax = attrs.getAttributeIntValue(androidns, "max", 100);
        mMin = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0);
        mInterval = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "interval", 1);
    }


    @Override
    protected View onCreateDialogView() {
        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6, 6, 6, 6);

        mValueText = new TextView(mContext);
        mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
        mValueText.setTextSize(32);
        params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(mValueText, params);

        mSeekBar = new SeekBar(mContext);
        mSeekBar.setOnSeekBarChangeListener(this);

        layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (shouldPersist()) {
            mValue = getPersistedInt(mDefault);
        }

        mSeekBar.setMax((mMax - mMin) / mInterval);
        mSeekBar.setProgress((mValue - mMin) / mInterval);

        String t = String.valueOf(mValue);
        mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
        return layout;
    }


    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        mSeekBar.setMax((mMax - mMin) / mInterval);
        mSeekBar.setProgress((mValue - mMin) / mInterval);
    }


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


    public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
        if (fromTouch) {
            mValue = (value * mInterval) + mMin;
            String t = String.valueOf(mValue);
            mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
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

    public void onStartTrackingTouch(SeekBar seek) {
    }


    public void onStopTrackingTouch(SeekBar seek) {
        if (shouldPersist()) {
            persistInt(mValue);
        }
        callChangeListener(mValue);
        this.getDialog().dismiss();
    }


    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNegativeButton(null, null);
        builder.setPositiveButton(null, null);
        builder.setTitle(null);
    }
}
