package com.ichi2.utils;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import timber.log.Timber;

public class JSONUtils {

    @CheckResult
    public static int getIntOrSetDefaultWithWarn(@NonNull JSONObject jsonObject, String propertyName, int defaultValue) {
        //noinspection ConstantConditions
        if (jsonObject == null) {
            return defaultValue;
        }

        if (!jsonObject.has(propertyName)) {
            Timber.w("No value for '%s' set. Setting default to '%d'", propertyName, defaultValue);
            trySet(jsonObject, propertyName, defaultValue);
            return defaultValue;
        }

        try {
            return jsonObject.getInt(propertyName);
        } catch (Exception e) {
            //It feels unsafe to perform a mutation if we have a truly unexpected error
            //(for example: a string value instead of an int).
            Timber.w(e, "Exception setting '%s'. Returning default: '%d'", propertyName, defaultValue);
            return defaultValue;
        }
    }

    private static void trySet(@NonNull JSONObject jsonObject, String propertyName, int defaultValue) {
        try {
            jsonObject.put(propertyName, defaultValue);
        } catch (Exception e) {
            //Don't want to log here due to doubling noise from getIntOrSetDefaultWithWarn
        }
    }
}
