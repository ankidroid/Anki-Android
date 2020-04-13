package com.ichi2.compat;

import android.annotation.TargetApi;
import android.widget.TimePicker;

/** Implementation of {@link Compat} for SDK level 23 */
@TargetApi(23)
public class CompatV23 extends CompatV21 implements Compat {

    @Override
    public void setTime(TimePicker picker, int hour, int minute) {
        picker.setHour(hour);
        picker.setMinute(minute);
    }

    @Override
    public int getHour(TimePicker picker) { return picker.getHour(); }

    @Override
    public int getMinute(TimePicker picker) { return picker.getMinute(); }

}
