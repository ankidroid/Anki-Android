/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.ichi2.anki.AnkiDroidApp;

import androidx.core.content.pm.PackageInfoCompat;
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
    public static long getPkgVersionCode() {
        Context context = AnkiDroidApp.getInstance();
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);
            Timber.d("getPkgVersionCode() is %s", versionCode);
            return versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Couldn't find package named %s", context.getPackageName());
        } catch (NullPointerException npe) {
            if (context.getPackageManager() == null) {
                Timber.e("getPkgVersionCode() null package manager?");
            } else if (context.getPackageName() == null) {
                Timber.e("getPkgVersionCode() null package name?");
            }
            AnkiDroidApp.sendExceptionReport(npe, "Unexpected exception getting version code?");
            Timber.e(npe, "Unexpected exception getting version code?");
        }
        return 0;
    }

    /**
     * Return whether the package version code is set to that for release version
     * @return whether build number in manifest version code is '3'
     */
    public static boolean isReleaseVersion() {
        String versionCode = Long.toString(getPkgVersionCode());
        Timber.d("isReleaseVersion() versionCode: %s", versionCode);
        return versionCode.charAt(versionCode.length()-3)=='3';
    }
}
