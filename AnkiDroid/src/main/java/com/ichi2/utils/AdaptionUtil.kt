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

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.ichi2.anki.AnkiDroidApp;

import java.util.List;
import java.util.Locale;

import timber.log.Timber;

public class AdaptionUtil {
    private static boolean sHasRunRestrictedLearningDeviceCheck = false;
    private static boolean sIsRestrictedLearningDevice = false;
    private static boolean sHasRunWebBrowserCheck = false;
    private static boolean sHasWebBrowser = true;
    private static Boolean sIsRunningMiUI = null;

    public static boolean hasWebBrowser(Context context) {
        if (sHasRunWebBrowserCheck) {
            return sHasWebBrowser;
        }

        sHasWebBrowser = checkHasWebBrowser(context);
        sHasRunWebBrowserCheck = true;
        return sHasWebBrowser;
    }

    public static boolean isUserATestClient() {
        try {
            return
                    ActivityManager.isUserAMonkey() ||
                            isRunningUnderFirebaseTestLab();
        } catch (Exception e) {
            Timber.w(e);
            return false;
        }
    }


    public static boolean isRunningUnderFirebaseTestLab() {
        try {
            return isRunningUnderFirebaseTestLab(AnkiDroidApp.getInstance().getContentResolver());
        } catch (Exception e) {
            Timber.w(e);
            return false;
        }
    }


    private static boolean isRunningUnderFirebaseTestLab(ContentResolver contentResolver) {
        // https://firebase.google.com/docs/test-lab/android/android-studio#modify_instrumented_test_behavior_for
        String testLabSetting = Settings.System.getString(contentResolver, "firebase.test.lab");
        return "true".equals(testLabSetting);
    }


    private static boolean checkHasWebBrowser(Context context) {
        // The test monkey often gets stuck on the Shared Decks WebView, ignore it as it shouldn't crash.
        if (isUserATestClient()) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : list) {
            if (!isValidBrowser(ri)) {
                continue;
            }

            // If we aren't a restricted device, any browser will do
            if (!isRestrictedLearningDevice()) {
                return true;
            }
            // If we are a restricted device, only a system browser will do
            if (isSystemApp(ri.activityInfo.packageName, pm)) {
                return true;
            }
        }
        // Either there are no web browsers, or we're a restricted learning device and there's no system browsers.
        return false;
    }


    private static boolean isValidBrowser(ResolveInfo ri) {
        // https://stackoverflow.com/a/57223246/
        return ri != null && ri.activityInfo != null && ri.activityInfo.exported;
    }


    private static boolean isSystemApp(String packageName, PackageManager pm) {
        if (packageName != null) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                return (info != null) && (info.applicationInfo != null) &&
                        ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            } catch (PackageManager.NameNotFoundException e) {
                Timber.w(e);
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

    public static boolean canUseContextMenu() {
        return !isRunningMiui();
    }

    private static boolean isRunningMiui() {
        if (sIsRunningMiUI == null) {
            sIsRunningMiUI = queryIsMiui();
        }
        return sIsRunningMiUI;
    }

    // https://stackoverflow.com/questions/47610456/how-to-detect-miui-rom-programmatically-in-android
    private static boolean isIntentResolved(Context ctx, Intent intent) {
        return (intent != null && ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null);
    }

    private static boolean queryIsMiui() {
        Context ctx = AnkiDroidApp.getInstance();
        return isIntentResolved(ctx, new Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
                || isIntentResolved(ctx, new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                || isIntentResolved(ctx, new Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT))
                || isIntentResolved(ctx, new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")));
    }


    @SuppressWarnings( {"unused", "RedundantSuppression"})
    public static boolean shouldCurrentUserBuyDifferentPhone() {
        return isRunningMiui();
    }


    /** See: https://en.wikipedia.org/wiki/Vivo_(technology_company) */
    public static boolean isVivo() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer == null) {
            return false;
        }
        return manufacturer.toLowerCase(Locale.ROOT).equals("vivo");
    }
}
