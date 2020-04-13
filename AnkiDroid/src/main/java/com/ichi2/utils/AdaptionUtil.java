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
    private static boolean sHasRunWebBrowserCheck = false;
    private static boolean sHasWebBrowser = true;

    public static boolean hasWebBrowser(Context context) {
        if (sHasRunWebBrowserCheck) {
            return sHasWebBrowser;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (list.size() == 0) {
            sHasWebBrowser = false;
        } else {
            sHasWebBrowser = false;
            for (ResolveInfo ri:list) {
                String pacagename = ri.activityInfo.packageName;
                if (isSystemApp(pacagename, pm)) {
                    sHasWebBrowser = true;
                    break;
                }
            }
        }
        sHasRunWebBrowserCheck = true;
        return sHasWebBrowser;
    }

    private static boolean isSystemApp(String packageName, PackageManager pm){
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

    public static boolean hasReducedPreferences(){
        return Build.MANUFACTURER.equalsIgnoreCase("Xiaomi") && (Build.PRODUCT.equalsIgnoreCase("Archytas") || Build.PRODUCT.equalsIgnoreCase("Archimedes"));
    }
}
