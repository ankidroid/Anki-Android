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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.util.Log;
import android.view.ViewConfiguration;

import com.ichi2.anki.dialogs.AnkiDroidCrashReportDialog;
import com.ichi2.anki.exception.StorageAccessException;
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

import org.acra.ACRA;
import org.acra.ACRAConfigurationException;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Application class.
 */
@ReportsCrashes(
        reportDialogClass = AnkiDroidCrashReportDialog.class,
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
        formUri = "https://ankidroid.org/acra/report",
        mode = ReportingInteractionMode.DIALOG,
        resDialogCommentPrompt =  R.string.empty_string,
        resDialogTitle =  R.string.feedback_title,
        resDialogText =  R.string.feedback_default_text,
        resToastText = R.string.feedback_auto_toast_text,
        resDialogPositiveButtonText = R.string.feedback_report,
        additionalSharedPreferences = {"com.ichi2.anki"},
        excludeMatchingSharedPreferencesKeys = {"username","hkey"},
        customReportContent = {
            ReportField.REPORT_ID,
            ReportField.APP_VERSION_CODE,
            ReportField.APP_VERSION_NAME,
            ReportField.PACKAGE_NAME,
            ReportField.FILE_PATH,
            ReportField.PHONE_MODEL,
            ReportField.ANDROID_VERSION,
            ReportField.BUILD,
            ReportField.BRAND,
            ReportField.PRODUCT,
            ReportField.TOTAL_MEM_SIZE,
            ReportField.AVAILABLE_MEM_SIZE,
            ReportField.BUILD_CONFIG,
            ReportField.CUSTOM_DATA,
            ReportField.STACK_TRACE,
            ReportField.STACK_TRACE_HASH,
            //ReportField.INITIAL_CONFIGURATION,
            ReportField.CRASH_CONFIGURATION,
            //ReportField.DISPLAY,
            ReportField.USER_COMMENT,
            ReportField.USER_APP_START_DATE,
            ReportField.USER_CRASH_DATE,
            //ReportField.DUMPSYS_MEMINFO,
            //ReportField.DROPBOX,
            ReportField.LOGCAT,
            //ReportField.EVENTSLOG,
            //ReportField.RADIOLOG,
            //ReportField.IS_SILENT,
            ReportField.INSTALLATION_ID,
            //ReportField.USER_EMAIL,
            //ReportField.DEVICE_FEATURES,
            ReportField.ENVIRONMENT,
            //ReportField.SETTINGS_SYSTEM,
            //ReportField.SETTINGS_SECURE,
            //ReportField.SETTINGS_GLOBAL,
            ReportField.SHARED_PREFERENCES,
            ReportField.APPLICATION_LOG,
            ReportField.MEDIA_CODEC_LIST,
            ReportField.THREAD_DETAILS
            //ReportField.USER_IP
        },
        logcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" }
)
public class AnkiDroidApp extends Application {

    public static final int SDK_VERSION = android.os.Build.VERSION.SDK_INT;
    public static final String APP_NAMESPACE = "http://schemas.android.com/apk/res/com.ichi2.anki";
    public static final String FEEDBACK_REPORT_ASK = "2";
    public static final String FEEDBACK_REPORT_NEVER = "1";
    public static final String FEEDBACK_REPORT_ALWAYS = "0";



    /**
     * Tag for logging messages.
     */
    public static final String TAG = "AnkiDroid";

    public static final String COLLECTION_FILENAME = "collection.anki2";

    /**
     * Singleton instance of this class.
     */
    private static AnkiDroidApp sInstance;
    private Collection mCurrentCollection;
    private int mAccessThreadCount = 0;
    private static final Lock mLock = new ReentrantLock();
    private static Message sStoredDialogHandlerMessage;

    /**
     * Global boolean flags
     */
    public static boolean sSyncInProgressFlag = false;
    public static boolean sDatabaseCorruptFlag = false;
    public static boolean sStorageAccessExceptionFlag = false;

    /** Global hooks */
    private Hooks mHooks;

    /** Compatibility interface, Used to perform operation in a platform specific way. */
    private Compat mCompat;

    /**
     * The name of the shared preferences for this class, as supplied to
     * {@link Context#getSharedPreferences(String, int)}.
     */
    public static final String SHARED_PREFS_NAME = AnkiDroidApp.class.getPackage().getName();

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
    public static final int CHECK_PREFERENCES_AT_VERSION = 20400203;


    /**
     * On application creation.
     */
    @TargetApi(Build.VERSION_CODES.FROYO)
    @Override
    public void onCreate() {
        super.onCreate();
        // Get preferences
        SharedPreferences preferences = getSharedPrefs(this);

        // Initialize crash reporting module
        ACRA.init(this);

        // Setup logging and crash reporting
        if (BuildConfig.DEBUG) {
            // Enable verbose error logging and do method tracing to put the Class name as log tag
            Timber.plant(new Timber.DebugTree());
            // Don't report crashes, regardless of user setting
            // note: manually changing crash report mode from within app can re-enable this
            setAcraReportingMode(FEEDBACK_REPORT_NEVER);
            // Use a wider logcat filter incase crash reporting manually re-enabled
            String [] logcatArgs = { "-t", "300", "-v", "long", "ACRA:S"};
            ACRA.getConfig().setLogcatArguments(logcatArgs);
        } else {
            // Disable verbose error logging and use fixed log tag "AnkiDroid"
            Timber.plant(new ProductionCrashReportingTree());
            // Enable or disable crash reporting based on user setting
            setAcraReportingMode(preferences.getString("reportErrorMode", FEEDBACK_REPORT_ASK));
        }
        Timber.tag(TAG);

        if (isNookHdOrHdPlus() && SDK_VERSION == 15) {
            mCompat = new CompatV15NookHdOrHdPlus();
        } else if (SDK_VERSION >= 16) {
            mCompat = new CompatV16();
        } else if (SDK_VERSION >= 15) {
            mCompat = new CompatV15();
        } else if (SDK_VERSION >= 12) {
            mCompat = new CompatV12();
        } else if (SDK_VERSION >= 9) {
            mCompat = new CompatV9();
        } else if (SDK_VERSION >= 8) {
            mCompat = new CompatV8();
        } else if (isNook() && SDK_VERSION == 7) {
            mCompat = new CompatV7Nook();
        } else {
            mCompat = new CompatV7();
        }

        sInstance = this;
        sInstance.mHooks = new Hooks(preferences);
        setLanguage(preferences.getString(Preferences.LANGUAGE, ""));

        // Configure WebView to allow file scheme pages to access cookies.
        mCompat.enableCookiesForFileSchemePages();

        // Set good default values for swipe detection
        final ViewConfiguration vc = ViewConfiguration.get(this);
        if (SDK_VERSION >= 8) {
            DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledPagingTouchSlop();
        } else {
            DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledTouchSlop()*2;
        }
        DEFAULT_SWIPE_THRESHOLD_VELOCITY = vc.getScaledMinimumFlingVelocity();

        // Create the AnkiDroid directory if missing
        try {
            initializeAnkiDroidDirectory(getCurrentAnkiDroidDirectory());
            sStorageAccessExceptionFlag = false;
        } catch (StorageAccessException e) {
            Timber.e(e, "Could not initialize AnkiDroid directory");
            sendExceptionReport(e, "AnkiDroidApp.onCreate");
            sStorageAccessExceptionFlag = true;
        }
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

    public static boolean isKindle() {
        return Build.BRAND.equalsIgnoreCase("amazon") || Build.MANUFACTURER.equalsIgnoreCase("amazon");
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


    public static String getCacheStorageDirectory() {
        return sInstance.getCacheDir().getAbsolutePath();
    }


    public static String getCollectionPath() {
        return new File(getCurrentAnkiDroidDirectory(), COLLECTION_FILENAME).getAbsolutePath();
    }

    /**
     * Get the absolute path to a directory that is suitable to be the default starting location
     * for the AnkiDroid folder. This is a folder named "AnkiDroid" at the top level of the
     * external storage directory.
     */
    private static String getDefaultAnkiDroidDirectory() {
        return new File(Environment.getExternalStorageDirectory(), "AnkiDroid").getAbsolutePath();
    }


    /**
     * Get the absolute path to the AnkiDroid directory.
     */
    public static String getCurrentAnkiDroidDirectory() {
        SharedPreferences prefs = getSharedPrefs(sInstance.getApplicationContext());
        return prefs.getString("deckPath", getDefaultAnkiDroidDirectory());
    }


    /**
     * Create the AnkiDroid directory if it doesn't exist and add a .nomedia file to it if needed.
     *
     * The AnkiDroid directory is a user preference stored under the "deckPath" key, and a sensible
     * default is chosen if the preference hasn't been created yet (i.e., on the first run).
     *
     * The presence of a .nomedia file indicates to media scanners that the directory must be
     * excluded from their search. We need to include this to avoid media scanners including
     * media files from the collection.media directory. The .nomedia file works at the directory
     * level, so placing it in the AnkiDroid directory will ensure media scanners will also exclude
     * the collection.media sub-directory.
     *
     * @param path  Directory to initialize
     * @throws StorageAccessException If no write access to directory
     */
    public static void initializeAnkiDroidDirectory(String path) throws StorageAccessException {
        // Create specified directory if it doesn't exit
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new StorageAccessException("Failed to create AnkiDroid directory");
        }
        if (!dir.canWrite()) {
            throw new StorageAccessException("No write access to AnkiDroid directory");
        }
        // Add a .nomedia file to it if it doesn't exist
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException e) {
                throw new StorageAccessException("Failed to create .nomedia file");
            }
        }
    }


    public static Resources getAppResources() {
        return sInstance.getResources();
    }


    public static boolean isSdCardMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }


    /**
     * Get package name as defined in the manifest.
     */
    public static String getAppName() {
        String pkgName = TAG;
        Context context = sInstance.getApplicationContext();

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
        if (sInstance != null) {
            Context context = sInstance.getApplicationContext();

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
        Context context = sInstance.getApplicationContext();
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Couldn't find package named %s", context.getPackageName());
        }
        return 0;
    }


    public static void sendExceptionReport(Throwable e, String origin) {
        sendExceptionReport(e, origin, null);
    }


    public static void sendExceptionReport(Throwable e, String origin, String additionalInfo) {
        //CustomExceptionHandler.getInstance().uncaughtException(null, e, origin, additionalInfo);
        ACRA.getErrorReporter().putCustomData("origin", origin);
        ACRA.getErrorReporter().putCustomData("additionalInfo", additionalInfo);
        ACRA.getErrorReporter().handleException(e);
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


    public static boolean initiateGestures(SharedPreferences preferences) {
        Boolean enabled = preferences.getBoolean("gestures", false);
        if (enabled) {
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
        return enabled;
    }


    public static Compat getCompat() {
        return sInstance.mCompat;
    }


    public static synchronized Collection openCollection(String path) {
        return openCollection(path, false);
    }


    public static synchronized Collection openCollection(String path, boolean force) {
        mLock.lock();
        Timber.i("openCollection: %s", path);
        try {
            if (!colIsOpen() || !sInstance.mCurrentCollection.getPath().equals(path) || force) {
                if (colIsOpen()) {
                    // close old collection prior to opening new one
                    sInstance.mCurrentCollection.close();
                    sInstance.mAccessThreadCount = 0;
                }
                sInstance.mCurrentCollection = Storage.Collection(path, false, true);
                sInstance.mAccessThreadCount++;
                Timber.d("Access to collection is requested: collection has been opened");
            } else {
                sInstance.mAccessThreadCount++;
                Timber.d("Access to collection is requested: collection has not been reopened (count: %d)", sInstance.mAccessThreadCount);
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
        Timber.i("closeCollection");
        try {
            if (sInstance.mAccessThreadCount > 0) {
                sInstance.mAccessThreadCount--;
            }
            Timber.d("Access to collection has been closed: (count: %d)", sInstance.mAccessThreadCount);
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


    public static void setStoredDialogHandlerMessage(Message msg) {
        sStoredDialogHandlerMessage = msg;
    }

    public static Message getStoredDialogHandlerMessage() {
        return sStoredDialogHandlerMessage;
    }


    /**
     * Set the reporting mode for ACRA based on the value of the reportErrorMode preference
     * @param value value of reportErrorMode preference
     */
    public void setAcraReportingMode(String value) {
        SharedPreferences.Editor editor = ACRA.getACRASharedPreferences().edit();
        // Set the ACRA disable value
        if (value.equals(FEEDBACK_REPORT_NEVER)) {
            editor.putBoolean("acra.disable", true);
        } else {
            editor.putBoolean("acra.disable", false);
            // Switch between auto-report via toast and manual report via dialog
            try {
                if (value.equals(FEEDBACK_REPORT_ALWAYS)) {
                    ACRA.getConfig().setMode(ReportingInteractionMode.TOAST);
                    ACRA.getConfig().setResToastText(R.string.feedback_auto_toast_text);
                } else if (value.equals(FEEDBACK_REPORT_ASK)) {
                    ACRA.getConfig().setMode(ReportingInteractionMode.DIALOG);
                    ACRA.getConfig().setResToastText(R.string.feedback_manual_toast_text);
                }
            } catch (ACRAConfigurationException e) {
                Timber.e("Could not set ACRA report mode");
            }
        }
        editor.commit();
    }

    /**
     * Get the url for the feedback page
     * @return
     */
    public static String getFeedbackUrl() {
        if (isCurrentLanguage("ja")) {
            return sInstance.getResources().getString(R.string.link_help_ja);
        } else {
            return sInstance.getResources().getString(R.string.link_help);
        }
    }

    /**
     * Get the url for the manual
     * @return
     */
    public static String getManualUrl() {
        if (isCurrentLanguage("ja")) {
            return sInstance.getResources().getString(R.string.link_manual_ja);
        } else {
            return sInstance.getResources().getString(R.string.link_manual);
        }
    }

    /**
     * Check whether l is the currently set language code
     * @param l ISO2 language code
     * @return
     */
    private static boolean isCurrentLanguage(String l) {
        String pref = getSharedPrefs(sInstance).getString(Preferences.LANGUAGE, "");
        return pref.equals(l) || pref.equals("") && Locale.getDefault().getLanguage().equals(l);
    }

    /** A tree which logs necessary data for crash reporting. */
    public static class ProductionCrashReportingTree extends Timber.HollowTree {
        private static final ThreadLocal<String> NEXT_TAG = new ThreadLocal<String>();
        private static final Pattern ANONYMOUS_CLASS = Pattern.compile("\\$\\d+$");

        @Override public void e(String message, Object... args) {
            Log.e(TAG, createTag() + "/ " + formatString(message, args)); // Just add to the log.
        }

        @Override public void e(Throwable t, String message, Object... args) {
            Log.e(TAG, createTag() + "/ " + formatString(message, args), t); // Just add to the log.
        }

        @Override public void w(String message, Object... args) {
            Log.w(TAG, createTag() + "/ " + formatString(message, args)); // Just add to the log.
        }

        @Override public void w(Throwable t, String message, Object... args) {
            Log.w(TAG, createTag() + "/ " + formatString(message, args), t); // Just add to the log.
        }

        @Override public void i(String message, Object... args) {
            // Skip createTag() to improve  performance. message should be descriptive enough without it
            Log.i(TAG, formatString(message, args)); // Just add to the log.
        }

        @Override public void i(Throwable t, String message, Object... args) {
            // Skip createTag() to improve  performance. message should be descriptive enough without it
            Log.i(TAG, formatString(message, args), t); // Just add to the log.
        }

        // Ignore logs below INFO level --> Non-overridden methods go to HollowTree

        static String formatString(String message, Object... args) {
            // If no varargs are supplied, treat it as a request to log the string without formatting.
            try {
                return args.length == 0 ? message : String.format(message, args);
            } catch (Exception e) {
                return message;
            }
        }

        private static String createTag() {
            String tag = NEXT_TAG.get();
            if (tag != null) {
                NEXT_TAG.remove();
                return tag;
            }

            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            if (stackTrace.length < 6) {
                throw new IllegalStateException(
                        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
            }
            tag = stackTrace[5].getClassName();
            Matcher m = ANONYMOUS_CLASS.matcher(tag);
            if (m.find()) {
                tag = m.replaceAll("");
            }
            return tag.substring(tag.lastIndexOf('.') + 1);
        }
    }
}
