package com.ichi2.chartdroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import java.util.List;

public class Market {

    public static final int NO_RESULT = -1;

    public static final String TAG = "ChartDroid";
    
    public static final String MARKET_PACKAGE_DETAILS_PREFIX = "market://details?id=";
    public static final String CHARTDROID_PACKAGE_NAME = "com.googlecode.chartdroid";
    public static final Uri MARKET_CHARTDROID_DETAILS_URI = Uri.parse(MARKET_PACKAGE_DETAILS_PREFIX + CHARTDROID_PACKAGE_NAME);

        public final static String APK_DOWNLOAD_DETAILS_PAGE_PREFIX = "http://code.google.com/p/chartdroid/downloads/detail?name=";
        public final static String APK_DOWNLOAD_URL_PREFIX = "http://chartdroid.googlecode.com/files/";
        public final static String APK_DOWNLOAD_FILENAME_CHARTDROID = "Chartdroid-1.9.10.apk";
        public final static Uri APK_DOWNLOAD_URI_CHARTDROID = Uri.parse(APK_DOWNLOAD_URL_PREFIX + APK_DOWNLOAD_FILENAME_CHARTDROID);


    /**
     * This wrapper function first checks whether an intent is available. If it is not,
     * then the Android Market is launched (if available) to download the appropriate
     * package.  On the other hand, if the intent is available, and if a non-negative
     * request code is passed, the Intent is launched with startActivity().
     * Otherwise, the Intent is launched with startActivityForResult()
     * @param context
     * @param intent
     * @param request_code
     * @param package_name
     */
    public static void intentLaunchWithMarketFallback(Activity context, Intent intent, int request_code, String package_name) {
        if (isIntentAvailable(context, intent)) {
            if (request_code < 0)
                context.startActivity(intent);
            else
                context.startActivityForResult(intent, request_code);
        } else {
            // Launch market intent
                Intent i = getMarketDownloadIntent(package_name);
            if (isIntentAvailable(context, i)) {
                context.startActivity(i);
            } else {
                Toast.makeText(context, "Android Market not available.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    public static Intent getMarketDownloadIntent(String package_name) {
        Uri market_uri = Uri.parse(MARKET_PACKAGE_DETAILS_PREFIX + package_name);
        return new Intent(Intent.ACTION_VIEW, market_uri);
    }

    public static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }
}
