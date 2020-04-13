package com.ichi2.preferences;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import com.ichi2.compat.CompatHelper;


public class TimePreference extends DialogPreference {
    public static final String DEFAULT_VALUE = "00:00";

    private TimePicker timePicker;
    private int hours;
    private int minutes;

    public TimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    protected View onCreateDialogView() {
        timePicker = new TimePicker(getContext());

        timePicker.setIs24HourView(true);

        return timePicker;
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

        hours = parseHours(time);
        minutes = parseMinutes(time);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        CompatHelper.getCompat().setTime(timePicker, hours, minutes);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            hours = CompatHelper.getCompat().getHour(timePicker);
            minutes = CompatHelper.getCompat().getMinute(timePicker);

            final String time = String.format("%1$02d:%2$02d", hours, minutes);

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
