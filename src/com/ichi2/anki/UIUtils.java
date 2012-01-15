package com.ichi2.anki;import com.ichi2.anki2.R;

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

}
