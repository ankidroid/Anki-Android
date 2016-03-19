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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ViewConfiguration;

import com.ichi2.anki.dialogs.AnkiDroidCrashReportDialog;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.LanguageUtil;

import org.acra.ACRA;
import org.acra.ACRAConfigurationException;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
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

    public static final String XML_CUSTOM_NAMESPACE = "http://arbitrary.app.namespace/com.ichi2.anki";
    public static final String FEEDBACK_REPORT_ASK = "2";
    public static final String FEEDBACK_REPORT_NEVER = "1";
    public static final String FEEDBACK_REPORT_ALWAYS = "0";

    // Tag for logging messages.
    public static final String TAG = "AnkiDroid";
    // Singleton instance of this class.
    private static AnkiDroidApp sInstance;
    // Constants for gestures
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
    public static final int CHECK_PREFERENCES_AT_VERSION = 20500225;


    /**
     * On application creation.
     */
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
            // Disable crash reporting
            setAcraReportingMode(FEEDBACK_REPORT_NEVER);
            preferences.edit().putString("reportErrorMode", FEEDBACK_REPORT_NEVER).commit();
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


        sInstance = this;
        setLanguage(preferences.getString(Preferences.LANGUAGE, ""));

        // Configure WebView to allow file scheme pages to access cookies.
        CompatHelper.getCompat().enableCookiesForFileSchemePages();

        // Prepare Cookies to be synchronized between RAM and permanent storage.
        CompatHelper.getCompat().prepareWebViewCookies(this.getApplicationContext());

        // Set good default values for swipe detection
        final ViewConfiguration vc = ViewConfiguration.get(this);
        DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledPagingTouchSlop();
        DEFAULT_SWIPE_THRESHOLD_VELOCITY = vc.getScaledMinimumFlingVelocity();

        // Create the AnkiDroid directory if missing. Send exception report if inaccessible.
        if (CollectionHelper.hasStorageAccessPermission(this)) {
            try {
                String dir = CollectionHelper.getCurrentAnkiDroidDirectory(this);
                CollectionHelper.initializeAnkiDroidDirectory(dir);
            } catch (StorageAccessException e) {
                Timber.e(e, "Could not initialize AnkiDroid directory");
                String defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory();
                if (isSdCardMounted() && CollectionHelper.getCurrentAnkiDroidDirectory(this).equals(defaultDir)) {
                    // Don't send report if the user is using a custom directory as SD cards trip up here a lot
                    sendExceptionReport(e, "AnkiDroidApp.onCreate");
                }
            }
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserve the language from the settings, e.g. when the device is rotated
        setLanguage(getSharedPrefs(this).getString(Preferences.LANGUAGE, ""));
    }



    /**
     * Convenience method for accessing Shared preferences
     *
     * @param context Context to get preferences for.
     * @return A SharedPreferences object for this instance of the app.
     */
    public static SharedPreferences getSharedPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }


    public static AnkiDroidApp getInstance() {
        return sInstance;
    }


    public static String getCacheStorageDirectory() {
        return sInstance.getCacheDir().getAbsolutePath();
    }

    public static Resources getAppResources() {
        return sInstance.getResources();
    }


    public static boolean isSdCardMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }


    public static void sendExceptionReport(Throwable e, String origin) {
        sendExceptionReport(e, origin, null);
    }


    public static void sendExceptionReport(Throwable e, String origin, String additionalInfo) {
        //CustomExceptionHandler.getInstance().uncaughtException(null, e, origin, additionalInfo);
        SharedPreferences prefs = getSharedPrefs(getInstance());
        // Only send report if we have not sent an identical report before
        try {
            JSONObject sentReports = new JSONObject(prefs.getString("sentExceptionReports", "{}"));
            String hash = getExceptionHash(e);
            if (sentReports.has(hash)) {
                Timber.i("The exception report with hash %s has already been sent from this device", hash);
                return;
            } else {
                sentReports.put(hash, true);
                prefs.edit().putString("sentExceptionReports", sentReports.toString()).apply();
            }
        } catch (JSONException e1) {
            Timber.i(e1, "Could not get cache of sent exception reports");
        }
        ACRA.getErrorReporter().putCustomData("origin", origin);
        ACRA.getErrorReporter().putCustomData("additionalInfo", additionalInfo);
        ACRA.getErrorReporter().handleException(e);
    }

    private static String getExceptionHash(Throwable th) {
        final StringBuilder res = new StringBuilder();
        Throwable cause = th;
        while (cause != null) {
            final StackTraceElement[] stackTraceElements = cause.getStackTrace();
            for (final StackTraceElement e : stackTraceElements) {
                res.append(e.getClassName());
                res.append(e.getMethodName());
            }
            cause = cause.getCause();
        }
        return Integer.toHexString(res.toString().hashCode());
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
        private static final ThreadLocal<String> NEXT_TAG = new ThreadLocal<>();
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
