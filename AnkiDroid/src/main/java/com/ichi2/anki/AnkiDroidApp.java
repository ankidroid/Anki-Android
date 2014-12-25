/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.crashlytics.android.Crashlytics;
import com.ichi2.anki.exception.AnkiDroidErrorReportException;
import com.ichi2.async.Connection;
import com.ichi2.compat.Compat;
import com.ichi2.compat.CompatV12;
import com.ichi2.compat.CompatV15;
import com.ichi2.compat.CompatV15NookHdOrHdPlus;
import com.ichi2.compat.CompatV16;
import com.ichi2.compat.CompatV7;
import com.ichi2.compat.CompatV7Nook;
import com.ichi2.compat.CompatV8;
import com.ichi2.compat.CompatV9;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.hooks.Hooks;
import com.ichi2.utils.LanguageUtil;

import io.fabric.sdk.android.Fabric;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Application class.
 */
public class AnkiDroidApp extends Application {

    public static final int SDK_VERSION = android.os.Build.VERSION.SDK_INT;
    public static final String LIBANKI_VERSION = "1.2.5";
    public static final String DROPBOX_PUBLIC_DIR = "/dropbox/Public/Anki";
    public static final String APP_NAMESPACE = "http://schemas.android.com/apk/res/com.ichi2.anki";


    /**
     * Tag for logging messages.
     */
    public static final String TAG = "AnkiDroid";
    public static final Boolean LOGGING_ENABLED = true;

    public static final String COLLECTION_PATH = "/collection.anki2";

    /**
     * Singleton instance of this class.
     */
    private static AnkiDroidApp sInstance;
    private static boolean sSyncInProgress = false;
    private Collection mCurrentCollection;
    private int mAccessThreadCount = 0;
    private static final Lock mLock = new ReentrantLock();
    private static Message sStoredDialogHandlerMessage;

    /** Global hooks */
    private Hooks mHooks;

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

    private static int DEFAULT_SWIPE_MIN_DISTANCE;
    private static int DEFAULT_SWIPE_THRESHOLD_VELOCITY;

    /**
     * The latest package version number that included important changes to the database integrity check routine. All
     * collections being upgraded to (or after) this version must run an integrity check as it will contain fixes that
     * all collections should have.
     */
    public static final int CHECK_DB_AT_VERSION = 40;

    /**
     * The latest package version number that included changes to the preferences that requires handling. All
     * collections being upgraded to (or after) this version must update preferences.
     */
    public static final int CHECK_PREFERENCES_AT_VERSION = 20200170;


    /**
     * On application creation.
     */
    @TargetApi(Build.VERSION_CODES.FROYO)
    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());

        if (isNookHdOrHdPlus() && AnkiDroidApp.SDK_VERSION == 15) {
            mCompat = new CompatV15NookHdOrHdPlus();
        } else if (AnkiDroidApp.SDK_VERSION >= 16) {
            mCompat = new CompatV16();
        } else if (AnkiDroidApp.SDK_VERSION >= 15) {
            mCompat = new CompatV15();
        } else if (AnkiDroidApp.SDK_VERSION >= 12) {
            mCompat = new CompatV12();
        } else if (AnkiDroidApp.SDK_VERSION >= 9) {
            mCompat = new CompatV9();
        } else if (AnkiDroidApp.SDK_VERSION >= 8) {
            mCompat = new CompatV8();
        } else if (isNook() && AnkiDroidApp.SDK_VERSION == 7) {
            mCompat = new CompatV7Nook();
        } else {
            mCompat = new CompatV7();
        }

        sInstance = this;

        Connection.setContext(getApplicationContext());

        // Error Reporter
        /*CustomExceptionHandler customExceptionHandler = CustomExceptionHandler.getInstance();
        customExceptionHandler.init(sInstance.getApplicationContext());
        Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler);*/

		// Configure WebView to allow file scheme pages to access cookies.
		mCompat.enableCookiesForFileSchemePages();
		
        SharedPreferences preferences = getSharedPrefs(this);
        sInstance.mHooks = new Hooks(preferences);
        setLanguage(preferences.getString(Preferences.LANGUAGE, ""));
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
        // Get good default values for swipe detection
        final ViewConfiguration vc = ViewConfiguration.get(this);
        if (AnkiDroidApp.SDK_VERSION >= 8) {
            DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledPagingTouchSlop();
        } else {
            DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledTouchSlop()*2;
        }
        DEFAULT_SWIPE_THRESHOLD_VELOCITY = vc.getScaledMinimumFlingVelocity();

    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserve the language from the settings, e.g. when the device is rotated
        setLanguage(getSharedPrefs(this).getString(Preferences.LANGUAGE, ""));
    }


    private boolean isNookHdOrHdPlus() {
        return isNookHd() || isNookHdPlus();
    }
    
    private boolean isNookHdPlus() {
        return android.os.Build.BRAND.equals("NOOK") && android.os.Build.PRODUCT.equals("HDplus")
                && android.os.Build.DEVICE.equals("ovation");
    }
    
    private boolean isNookHd () {
        return android.os.Build.MODEL.equalsIgnoreCase("bntv400") && android.os.Build.BRAND.equals("NOOK");
    }


    public static boolean isNook() {
        return android.os.Build.MODEL.equalsIgnoreCase("nook") || android.os.Build.DEVICE.equalsIgnoreCase("nook");
    }


    public static boolean isChromebook() {
        return android.os.Build.BRAND.equalsIgnoreCase("chromium") || android.os.Build.MANUFACTURER.equalsIgnoreCase("chromium");
    }


    /**
     * Convenience method for accessing Shared preferences
     * 
     * @param context Context to get preferences for.
     * @return A SharedPreferences object for this instance of the app.
     */
    public static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }


    public static AnkiDroidApp getInstance() {
        return sInstance;
    }


    private static String getStorageDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }


    public static String getCacheStorageDirectory() {
        return sInstance.getCacheDir().getAbsolutePath();
    }


    public static String getCollectionPath() {
        return getCurrentAnkiDroidDirectory() + AnkiDroidApp.COLLECTION_PATH;
    }


    private static String getDefaultAnkiDroidDirectory() {
        return getStorageDirectory() + "/AnkiDroid";
    }


    public static String getCurrentAnkiDroidDirectory() {
        SharedPreferences prefs = getSharedPrefs(sInstance.getApplicationContext());
        return prefs.getString("deckPath", AnkiDroidApp.getDefaultAnkiDroidDirectory());
    }


    public static void createDirectoryIfMissing(File decksDirectory) {
        if (!decksDirectory.isDirectory()) {
            decksDirectory.mkdirs();
        }
        try {
            new File(decksDirectory.getAbsolutePath() + "/.nomedia").createNewFile();
        } catch (IOException e) {
            AnkiDroidApp.Log(Log.ERROR, "Nomedia file could not be created");
        }
        createNoMediaFileIfMissing(decksDirectory);
    }


    public static void createNoMediaFileIfMissing(File decksDirectory) {
        File mediaFile = new File(decksDirectory.getAbsolutePath() + "/.nomedia");
        if (!mediaFile.exists()) {
            try {
                mediaFile.createNewFile();
            } catch (IOException e) {
                AnkiDroidApp.Log(Log.ERROR, "Nomedia file could not be created in path " + decksDirectory.getAbsolutePath());
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
    public static String getAppName() {
        String pkgName = TAG;
        Context context = sInstance.getApplicationContext();

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pkgName = context.getString(pInfo.applicationInfo.labelRes);
        } catch (PackageManager.NameNotFoundException e) {
            Log(Log.ERROR, "Couldn't find package named " + context.getPackageName(), e);
        }

        return pkgName;
    }


    /**
     * Get the package versionName as defined in the manifest.
     * 
     * @return the package version.
     */
    public static String getPkgVersionName() {
        String pkgVersion = "?";
        Context context = sInstance.getApplicationContext();

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pkgVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            AnkiDroidApp.Log(Log.ERROR, "Couldn't find package named " + context.getPackageName(), e);
        }

        return pkgVersion;
    }


    /**
     * Get the package versionCode as defined in the manifest.
     * 
     * @return
     */
    public static int getPkgVersionCode() {
        Context context = sInstance.getApplicationContext();
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            AnkiDroidApp.Log(Log.ERROR, "Couldn't find package named " + context.getPackageName(), e);
        }
        return 0;
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


    public static void saveExceptionReportFile(String origin, String additionalInfo) {
        try {
            throw new AnkiDroidErrorReportException();
        } catch (AnkiDroidErrorReportException e) {
            saveExceptionReportFile(e, origin, additionalInfo);
        }
    }
    
    
    public static void saveExceptionReportFile(Throwable e, String origin) {
        saveExceptionReportFile(e, origin, null);
    }
    
    
    public static void saveExceptionReportFile(Throwable e, String origin, String additionalInfo) {
        //CustomExceptionHandler.getInstance().uncaughtException(null, e, origin, additionalInfo);
        Crashlytics.setString("origin", origin);
        Crashlytics.setString("additionalInfo", additionalInfo);
        Crashlytics.logException(e);
    }


    /**
     * Sets the user language.
     * 
     * @param localeCode The locale code of the language to set
     * @return True if the language has changed, else false
     */
    public static boolean setLanguage(String localeCode) {
        boolean languageChanged = false;
        Configuration config = getInstance().getResources().getConfiguration();
        Locale newLocale = LanguageUtil.getLocale(localeCode);
        if (!config.locale.equals(newLocale)) {
            languageChanged = true;
            config.locale = newLocale;
            getInstance().getResources().updateConfiguration(config, getInstance().getResources().getDisplayMetrics());
        }
        return languageChanged;
    }


    public static Hooks getHooks() {
        return sInstance.mHooks;
    }


    public static boolean initiateGestures(Context context, SharedPreferences preferences) {
        mGesturesEnabled = preferences.getBoolean("gestures", false);

        if (mGesturesEnabled) {
            int sensitivity = preferences.getInt("swipeSensitivity", 100);
            if (sensitivity != 100) {
                float sens = 100.0f/sensitivity;
                sSwipeMinDistance = (int) (DEFAULT_SWIPE_MIN_DISTANCE * sens + 0.5f);
                sSwipeThresholdVelocity = (int) (DEFAULT_SWIPE_THRESHOLD_VELOCITY * sens  + 0.5f);
            } else {
                sSwipeMinDistance = DEFAULT_SWIPE_MIN_DISTANCE;
                sSwipeThresholdVelocity = DEFAULT_SWIPE_THRESHOLD_VELOCITY;
            }
        }
        return mGesturesEnabled;
    }


    public static Compat getCompat() {
        return sInstance.mCompat;
    }


    public static synchronized Collection openCollection(String path) {
        return openCollection(path, false);
    }

    public static synchronized Collection openCollection(String path, boolean force) {
        mLock.lock();
        AnkiDroidApp.Log(Log.INFO, "openCollection: " + path);
        try {
            if (!colIsOpen() || !sInstance.mCurrentCollection.getPath().equals(path) || force) {
                if (colIsOpen()) {
                    // close old collection prior to opening new one
                    sInstance.mCurrentCollection.close();
                    sInstance.mAccessThreadCount = 0;
                }
                sInstance.mCurrentCollection = Storage.Collection(path, false, true);
                sInstance.mAccessThreadCount++;
                AnkiDroidApp.Log(Log.INFO, "Access to collection is requested: collection has been opened");
            } else {
                sInstance.mAccessThreadCount++;
                AnkiDroidApp.Log(Log.INFO, "Access to collection is requested: collection has not been reopened (count: " + sInstance.mAccessThreadCount + ")");
            }
            return sInstance.mCurrentCollection;
        } finally {
            mLock.unlock();
        }
    }


    public static Collection getCol() {
        return sInstance.mCurrentCollection;
    }


    public static void closeCollection(boolean save) {
        mLock.lock();
        AnkiDroidApp.Log(Log.INFO, "closeCollection");
        try {
            if (sInstance.mAccessThreadCount > 0) {
                sInstance.mAccessThreadCount--;
            }
            AnkiDroidApp.Log(Log.INFO, "Access to collection has been closed: (count: " + sInstance.mAccessThreadCount + ")");
            if (sInstance.mAccessThreadCount == 0 && sInstance.mCurrentCollection != null) {
                Collection col = sInstance.mCurrentCollection;
                sInstance.mCurrentCollection = null;
                col.close(save);
            }
        } finally {
            mLock.unlock();
        }

    }


    public static boolean colIsOpen() {
        return sInstance.mCurrentCollection != null && sInstance.mCurrentCollection.getDb() != null
                && sInstance.mCurrentCollection.getDb().getDatabase() != null
                && sInstance.mCurrentCollection.getDb().getDatabase().isOpen();
    }


    public static void resetAccessThreadCount() {
        sInstance.mAccessThreadCount = 0;
        sInstance.mCurrentCollection = null;
        AnkiDroidApp.Log(Log.INFO, "Access has been reset to 0");
    }

    public static void setStoredDialogHandlerMessage(Message msg) {
        sStoredDialogHandlerMessage = msg;
    }

    public static void Log(int priority, String msg, Exception e) {
        Log(priority, msg + "\n" + e.getMessage());
    }

    public static void Log(int priority, String msg) {
        if (LOGGING_ENABLED) {
            Crashlytics.log(priority, TAG, msg);
        }
    }

    public static Message getStoredDialogHandlerMessage() {
        return sStoredDialogHandlerMessage;
    }

    public static void setSyncInProgress(boolean value) {
        sSyncInProgress = value;
    }

    public static boolean getSyncInProgress() {
        return sSyncInProgress;
    }
}
