package com.ichi2.utils;

import android.os.StatFs;

import com.ichi2.compat.CompatHelper;

import java.io.File;

import timber.log.Timber;

public class FileUtil {
    /** Gets the free disk space given a file */
    public static long getFreeDiskSpace(File file, long defaultValue) {
        try {
            return CompatHelper.getCompat().getAvailableBytes(new StatFs(file.getParentFile().getPath()));
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Free space could not be retrieved");
            return defaultValue;
        }
    }
}
