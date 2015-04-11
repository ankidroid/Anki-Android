package com.ichi2.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.ichi2.anki.AnkiDroidApp;

import timber.log.Timber;

/**
 * Created by Tim on 11/04/2015.
 */
public class VersionUtils {


    /**
     * Get package name as defined in the manifest.
     */
    public static String getAppName() {
        String pkgName = AnkiDroidApp.TAG;
        Context context = AnkiDroidApp.getInstance();

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pkgName = context.getString(pInfo.applicationInfo.labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Couldn't find package named %s", context.getPackageName());
        }

        return pkgName;
    }


    /**
     * Get the package versionName as defined in the manifest.
     */
    public static String getPkgVersionName() {
        String pkgVersion = "?";
        Context context = AnkiDroidApp.getInstance();
        if (context != null) {
            try {
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                pkgVersion = pInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Timber.e(e, "Couldn't find package named %s", context.getPackageName());
            }
        }
        return pkgVersion;
    }


    /**
     * Get the package versionCode as defined in the manifest.
     */
    public static int getPkgVersionCode() {
        Context context = AnkiDroidApp.getInstance();
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Couldn't find package named %s", context.getPackageName());
        }
        return 0;
    }

    /**
     * Return whether the package version code is set to that for release version
     * @return whether build number in manifest version code is '3'
     */
    public static boolean isReleaseVersion() {
        String versionCode = Integer.toString(getPkgVersionCode());
        return versionCode.charAt(versionCode.length()-3)=='3';
    }
}
