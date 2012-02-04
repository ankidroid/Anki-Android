package com.ichi2.anki;import java.util.Calendar;
import java.util.GregorianCalendar;

import com.ichi2.anki2.R;

import android.content.Context;
import android.os.Build;

public class UIUtils {

	/** Returns the API level of this device. */
    public static int getApiLevel() {
        try {
            return Integer.parseInt(Build.VERSION.SDK);
        } catch (NumberFormatException e) {
            // If there is an error, return the minimum supported version.
            return 3;
        }
    }

    public static float getDensityAdjustedValue(Context context, float value) {
    	return context.getResources().getDisplayMetrics().density * value;
    }

    public static long getDayStart() {
		Calendar cal = GregorianCalendar.getInstance();
		if (cal.get(Calendar.HOUR_OF_DAY) < 4) {
			cal.roll(Calendar.DAY_OF_YEAR, -1);
		}
		cal.set(Calendar.HOUR_OF_DAY, 4);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
    }
}
