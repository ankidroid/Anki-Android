//noinspection MissingCopyrightHeader #8659
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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ichi2.anki.AnkiDroidApp;

import androidx.annotation.StringRes;

@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 see: SeekBarPreferenceCompat
public class SeekBarPreference extends android.preference.DialogPreference implements SeekBar.OnSeekBarChangeListener {
    private static final String androidns = "http://schemas.android.com/apk/res/android";

    private LinearLayout mSeekLine;
    private SeekBar mSeekBar;
    private TextView mValueText;
    private final Context mContext;

    private final String mSuffix;
    private final int mDefault;
    private final int mMax;
    private final int mMin;
    private final int mInterval;
    private int mValue = 0;
    @StringRes
    private final int mXLabel;
    @StringRes
    private final int mYLabel;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mSuffix = attrs.getAttributeValue(androidns, "text");
        mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
        mMax = attrs.getAttributeIntValue(androidns, "max", 100);
        mMin = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0);
        mInterval = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "interval", 1);
        mXLabel = attrs.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "xlabel", 0);
        mYLabel = attrs.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "ylabel", 0);
    }


    @Override
    protected View onCreateDialogView() {
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6, 6, 6, 6);

        mValueText = new FixedTextView(mContext);
        mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
        mValueText.setTextSize(32);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(mValueText, params);

        mSeekBar = new SeekBar(mContext);
        mSeekBar.setOnSeekBarChangeListener(this);

        layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));


        if (mXLabel != 0 && mYLabel != 0) {
            LinearLayout.LayoutParams params_seekbar = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params_seekbar.setMargins(0, 12, 0, 0);
            mSeekLine = new LinearLayout(mContext);
            mSeekLine.setOrientation(LinearLayout.HORIZONTAL);
            mSeekLine.setPadding(6, 6, 6, 6);
            addLabelsBelowSeekBar();
            layout.addView(mSeekLine, params_seekbar);
        }

        if (shouldPersist()) {
            mValue = getPersistedInt(mDefault);
        }

        mSeekBar.setMax((mMax - mMin) / mInterval);
        mSeekBar.setProgress((mValue - mMin) / mInterval);

        String t = String.valueOf(mValue);
        mValueText.setText(mSuffix == null ? t : t + mSuffix);
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
            mValueText.setText(mSuffix == null ? t : t + mSuffix);
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

    private void addLabelsBelowSeekBar() {
        int labels[] = {mXLabel, mYLabel};
        for (int count = 0; count < 2; count++) {
            TextView textView = new FixedTextView(mContext);
            textView.setText(mContext.getString(labels[count]));
            textView.setGravity(Gravity.START);
            mSeekLine.addView(textView);
            if(mContext.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR)
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
