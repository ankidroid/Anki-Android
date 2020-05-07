/****************************************************************************************
 * Copyright (c) 2020 gaoyingjun@xiaomi.com                                             *
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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

import java.util.List;

public class AdaptionUtil {
    private static boolean sHasRunRestrictedLearningDeviceCheck = false;
    private static boolean sIsRestrictedLearningDevice = false;
    private static boolean sHasRunWebBrowserCheck = false;
    private static boolean sHasWebBrowser = true;

    public static boolean hasWebBrowser(Context context) {
        if (sHasRunWebBrowserCheck) {
            return sHasWebBrowser;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        sHasWebBrowser = false;
        for (ResolveInfo ri : list) {
            // If we aren't a restricted device, any browser will do
            if (!isRestrictedLearningDevice()) {
                sHasWebBrowser = true;
                break;
            }
            // If we are a restricted device, only a system browser will do
            if (isSystemApp(ri.activityInfo.packageName, pm)) {
                sHasWebBrowser = true;
                break;
            }
        }
        sHasRunWebBrowserCheck = true;
        return sHasWebBrowser;
    }

    private static boolean isSystemApp(String packageName, PackageManager pm) {
        if (packageName != null) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                return (info != null) && (info.applicationInfo != null) &&
                        ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean isRestrictedLearningDevice() {
        if (!sHasRunRestrictedLearningDeviceCheck) {
            sIsRestrictedLearningDevice =
                    "Xiaomi".equalsIgnoreCase(Build.MANUFACTURER) &&
                            ("Archytas".equalsIgnoreCase(Build.PRODUCT) || "Archimedes".equalsIgnoreCase(Build.PRODUCT));
            sHasRunRestrictedLearningDeviceCheck = true;
        }
        return sIsRestrictedLearningDevice;
    }
}
