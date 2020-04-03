package com.ichi2.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class Permissions {
    private Permissions() { }

    public static boolean canUseCamera(@NonNull Context context) {
        return hasPermission(context, Manifest.permission.CAMERA);
    }

    public static boolean canRecordAudio(@NonNull Context context) {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO);
    }

    private static boolean hasPermission(@NonNull Context context, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }


    /**
     * Check if we have permission to access the external storage
     * @param context
     * @return
     */
    public static boolean hasStorageAccessPermission(Context context) {
        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }


    public static boolean canUseWakeLock(Context context) {
        return hasPermission(context, Manifest.permission.WAKE_LOCK);
    }
}
