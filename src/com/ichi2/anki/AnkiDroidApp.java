/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
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

package com.ichi2.anki;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.ichi2.async.Connection;
import com.ichi2.compat.Compat;
import com.ichi2.compat.CompatV11;
import com.ichi2.compat.CompatV4;
import com.ichi2.libanki.hooks.Hooks;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Application class. This file mainly contains Veecheck stuff.
 */
public class AnkiDroidApp extends Application {

    public static final String LIBANKI_VERSION = "1.2.5";
    public static final String DROPBOX_PUBLIC_DIR = "/dropbox/Public/Anki";

    public static final int RESULT_TO_HOME = 501;

    /**
     * Tag for logging messages.
     */
    public static final String TAG = "AnkiDroid";

    public static final String COLLECTION_PATH = "/collection.anki2";

    /**
     * Singleton instance of this class.
     */
    private static AnkiDroidApp sInstance;

    /** Global hooks */
    private Hooks mHooks;
    
    /** Application locale */
    private String mLanguage;
    
    /** Compatibility interface, Used to perform operation in a platform specific way. */
    private Compat mCompat;


    /**
     * The name of the shared preferences for this class, as supplied to
     * {@link Context#getSharedPreferences(String, int)}.
     */
    public static final String SHARED_PREFS_NAME = AnkiDroidApp.class.getPackage().getName();

    private static boolean mGesturesEnabled;
    public static int sSwipeMinDistance = -1;
    public static int sSwipeThresholdVelocity = -1;
    public static int sSwipeMaxOffPath = -1;

    private static final int SWIPE_MIN_DISTANCE_DIP = 65;
    private static final int SWIPE_MAX_OFF_PATH_DIP = 120;
    private static final int SWIPE_THRESHOLD_VELOCITY_DIP = 120;

    /**
     * On application creation.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        if (UIUtils.getApiLevel() >= 11) {
            mCompat = new CompatV11();
        } else {
            mCompat = new CompatV4();
        }

        sInstance = this;
        sInstance.mHooks = new Hooks();

        Connection.setContext(getApplicationContext());

        // Error Reporter
        CustomExceptionHandler customExceptionHandler = CustomExceptionHandler.getInstance();
        customExceptionHandler.init(sInstance.getApplicationContext());
        Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler);

        SharedPreferences preferences = getSharedPrefs(this);
        sInstance.mLanguage = mLanguage = preferences.getString("language", "");
        // Assign some default settings if necessary
        if (!preferences.contains("deckPath")) {
            Editor editor = preferences.edit();
            // Create the folder "AnkiDroid", if not exists, where the decks
            // will be stored by default
            String deckPath = getDefaultAnkiDroidDirectory();
            createDirectoryIfMissing(new File(deckPath));
            // Put the base path in preferences pointing to the default "AnkiDroid" folder
            editor.putString("deckPath", deckPath);
            // Using commit instead of apply even though we don't need a return value.
            // Reason: apply() not available on Android 1.5
            editor.commit();
        }
    }

    /**
     * Convenience method for accessing Shared preferences
     * @param context Context to get preferences for.
     * @return A SharedPreferences object for this instance of the app. 
     */
    public static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }


    public static AnkiDroidApp getInstance() {
        return sInstance;
    }


    public static String getStorageDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }


    public static String getCollectionPath() {
        String deckPath = getSharedPrefs(sInstance.getApplicationContext()).getString("deckPath",
                AnkiDroidApp.getDefaultAnkiDroidDirectory());
        return deckPath + AnkiDroidApp.COLLECTION_PATH;
    }


    public static String getDefaultAnkiDroidDirectory() {
        return getStorageDirectory() + "/AnkiDroid";
    }


    public static void createDirectoryIfMissing(File decksDirectory) {
        if (!decksDirectory.isDirectory()) {
            decksDirectory.mkdirs();
        }
        try {
            new File(decksDirectory.getAbsolutePath() + "/.nomedia").createNewFile();
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "Nomedia file could not be created");
        }
        createNoMediaFileIfMissing(decksDirectory);
    }


    public static void createNoMediaFileIfMissing(File decksDirectory) {
        File mediaFile = new File(decksDirectory.getAbsolutePath() + "/.nomedia");
        if (!mediaFile.exists()) {
            try {
                mediaFile.createNewFile();
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, "Nomedia file could not be created in path " + decksDirectory.getAbsolutePath());
            }
        }
    }


    public static Resources getAppResources() {
        return sInstance.getResources();
    }


    public static boolean isSdCardMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }


    public static int getDisplayHeight() {
        Display display = ((WindowManager) sInstance.getApplicationContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        return display.getHeight();
    }


    public static int getDisplayWidth() {
        Display display = ((WindowManager) sInstance.getApplicationContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        return display.getWidth();
    }


    /**
     * Get package name as defined in the manifest.
     * 
     * @return the package name.
     */
    public static String getPkgName() {
        String pkgName = TAG;
        Context context = sInstance.getApplicationContext();

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pkgName = context.getString(pInfo.applicationInfo.labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package named " + context.getPackageName(), e);
        }

        return pkgName;
    }


    /**
     * Get the package version as defined in the manifest.
     * 
     * @return the package version.
     */
    public static String getPkgVersion() {
        String pkgVersion = "?";
        Context context = sInstance.getApplicationContext();

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pkgVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package named " + context.getPackageName(), e);
        }

        return pkgVersion;
    }


    /**
     * Get the DropBox folder
     * 
     * @return the absolute path to the DropBox public folder, or null if it is not found
     */
    public static String getDropboxDir() {
        File f = new File(AnkiDroidApp.getStorageDirectory() + DROPBOX_PUBLIC_DIR);
        if (f.exists() && f.isDirectory()) {
            return f.getAbsolutePath();
        }
        return null;
    }


    public static void saveExceptionReportFile(Throwable e, String origin) {
        CustomExceptionHandler.getInstance().uncaughtException(null, e, origin);
    }


    public static boolean isDonutOrLater() {
        return getSdkVersion() > 3;
    }


    public static boolean isEclairOrLater() {
        return getSdkVersion() > 4;
    }


    public static boolean isFroyoOrLater() {
        return getSdkVersion() > 7;
    }


    public static boolean isHoneycombOrLater() {
        return getSdkVersion() > 11;
    }


    public static int getSdkVersion() {
        return Integer.valueOf(android.os.Build.VERSION.SDK);
    }

    public static String getLanguage() {
        return getInstance().mLanguage;
    }

    public static void setLanguage(String language) {
        Locale locale;
        if (language.equals("")) {
            locale = Locale.getDefault();
        } else {
            locale = new Locale(language);
        }
        Configuration config = new Configuration();
        config.locale = locale;
        getInstance().getResources().updateConfiguration(config, getInstance().getResources().getDisplayMetrics());
        getInstance().mLanguage = language;
    }


    public static Hooks getHooks() {
        return sInstance.mHooks;
    }

    public static boolean initiateGestures(Context context, SharedPreferences preferences) {
    	mGesturesEnabled = preferences.getBoolean("swipe", false);
    	if (mGesturesEnabled && sSwipeMinDistance == -1) {
            // Convert dip to pixel, code in parts from http://code.google.com/p/k9mail/
            final float gestureScale = context.getResources().getDisplayMetrics().density;
            int sensibility = preferences.getInt("swipeSensibility", 100);
            if (sensibility != 100) {
                float sens = (200 - sensibility) / 100.0f;
                sSwipeMinDistance = (int) (SWIPE_MIN_DISTANCE_DIP * sens * gestureScale + 0.5f);
                sSwipeThresholdVelocity = (int) (SWIPE_THRESHOLD_VELOCITY_DIP * sens * gestureScale + 0.5f);
                sSwipeMaxOffPath = (int) (SWIPE_MAX_OFF_PATH_DIP * Math.sqrt(sens) * gestureScale + 0.5f);
            } else {
                sSwipeMinDistance = (int) (SWIPE_MIN_DISTANCE_DIP * gestureScale + 0.5f);
                sSwipeThresholdVelocity = (int) (SWIPE_THRESHOLD_VELOCITY_DIP * gestureScale + 0.5f);
                sSwipeMaxOffPath = (int) (SWIPE_MAX_OFF_PATH_DIP * gestureScale + 0.5f);
            }    		
    	}
        return mGesturesEnabled;
    }

    public Compat getCompat() {
        return mCompat;
    }

}
