package com.ichi2.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

import java.util.List;

public class AdaptionUtil {
    public static boolean hasWebBrowser(Context context){
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size()==0 ? false : true;
    }

    public static boolean hasReducedPreferences(){
        return Build.MANUFACTURER.equalsIgnoreCase("Xiaomi") && (Build.PRODUCT.equalsIgnoreCase("Archytas")||Build.PRODUCT.equalsIgnoreCase("Archimedes"));
    }
}
