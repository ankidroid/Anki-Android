package com.ichi2.anki.multimediacard.utils;

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
}
