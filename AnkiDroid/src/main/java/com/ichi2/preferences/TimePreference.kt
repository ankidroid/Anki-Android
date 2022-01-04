//noinspection MissingCopyrightHeader #8659
package com.ichi2.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import com.ichi2.compat.CompatHelper;


@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
public class TimePreference extends android.preference.DialogPreference {
    public static final String DEFAULT_VALUE = "00:00";

    private TimePicker mTimepicker;
    private int mHours;
    private int mMinutes;

    public TimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    protected View onCreateDialogView() {
        mTimepicker = new TimePicker(getContext());

        mTimepicker.setIs24HourView(true);

        return mTimepicker;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String time;

        if (restorePersistedValue) {
            if (null == defaultValue) {
                time = getPersistedString(DEFAULT_VALUE);
            } else {
                time = getPersistedString(defaultValue.toString());
            }
        } else {
            time = defaultValue.toString();
        }

        mHours = parseHours(time);
        mMinutes = parseMinutes(time);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        CompatHelper.getCompat().setTime(mTimepicker, mHours, mMinutes);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            mHours = CompatHelper.getCompat().getHour(mTimepicker);
            mMinutes = CompatHelper.getCompat().getMinute(mTimepicker);

            final String time = String.format("%1$02d:%2$02d", mHours, mMinutes);

            if (callChangeListener(time)) {
                persistString(time);
            }
        }
    }

    public static int parseHours(String time) {
        return (Integer.parseInt(time.split(":")[0]));
    }

    public static int parseMinutes(String time) {
        return (Integer.parseInt(time.split(":")[1]));
    }
}
