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
import org.acra.ReportField;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraHttpSender;
import org.acra.annotation.AcraLimiter;
import org.acra.annotation.AcraToast;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.ToastConfigurationBuilder;
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
@AcraCore(
        buildConfigClass = org.acra.dialog.BuildConfig.class,
        excludeMatchingSharedPreferencesKeys = {"username","hkey"},
        reportContent = {
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
            //ReportField.APPLICATION_LOG,
            ReportField.MEDIA_CODEC_LIST,
            ReportField.THREAD_DETAILS
            //ReportField.USER_IP
        },
        logcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" }
)
@AcraDialog(
        reportDialogClass = AnkiDroidCrashReportDialog.class,
        resCommentPrompt =  R.string.empty_string,
        resTitle =  R.string.feedback_title,
        resText =  R.string.feedback_default_text,
        resPositiveButtonText = R.string.feedback_report
)
@AcraHttpSender(
        httpMethod = HttpSender.Method.PUT,
        uri = BuildConfig.ACRA_URL
)
@AcraToast(
        resText = R.string.feedback_auto_toast_text
)
@AcraLimiter(
        exceptionClassLimit = 1000,
        stacktraceLimit = 1
)
public class AnkiDroidApp extends Application {

    public static final String XML_CUSTOM_NAMESPACE = "http://arbitrary.app.namespace/com.ichi2.anki";

    // ACRA constants used for stored preferences
    public static final String FEEDBACK_REPORT_KEY = "reportErrorMode";
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
    public static final int CHECK_DB_AT_VERSION = 20805202;

    /**
     * The latest package version number that included changes to the preferences that requires handling. All
     * collections being upgraded to (or after) this version must update preferences.
     */
    public static final int CHECK_PREFERENCES_AT_VERSION = 20500225;

    /** Our ACRA configurations, initialized during onCreate() */
    private CoreConfigurationBuilder acraCoreConfigBuilder;


    /**
     * Get the ACRA ConfigurationBuilder - use this followed by setting it to modify the config
     * @return ConfigurationBuilder for the current ACRA config
     */
    public CoreConfigurationBuilder getAcraCoreConfigBuilder() {
        return acraCoreConfigBuilder;
    }


    /**
     * Set the ACRA ConfigurationBuilder and <b>re-initialize the ACRA system</b> with the contents
     * @param acraCoreConfigBuilder the full ACRA config to initialize ACRA with
     */
    private void setAcraConfigBuilder(CoreConfigurationBuilder acraCoreConfigBuilder) {
        this.acraCoreConfigBuilder = acraCoreConfigBuilder;
        ACRA.init(this, acraCoreConfigBuilder);
    }


    /**
     * On application creation.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // Get preferences
        SharedPreferences preferences = getSharedPrefs(this);

        // Setup logging and crash reporting
        acraCoreConfigBuilder = new CoreConfigurationBuilder(this);
        if (BuildConfig.DEBUG) {
            // Enable verbose error logging and do method tracing to put the Class name as log tag
            Timber.plant(new Timber.DebugTree());
            setDebugACRAConfig(preferences);
        } else {
            // Disable verbose error logging and use fixed log tag "AnkiDroid"
            Timber.plant(new ProductionCrashReportingTree());
            setProductionACRAConfig(preferences);
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
        ACRA.getErrorReporter().putCustomData("origin", origin);
        ACRA.getErrorReporter().putCustomData("additionalInfo", additionalInfo);
        ACRA.getErrorReporter().handleException(e);
    }


    /**
     * If you want to make sure that the next exception of any time is posted, you need to clear limiter data
     *
     * There is an enhancement request in ACRA to do this via API, until then they blessed deleting file directly
     * @param context
     */
    public static void deleteACRALimiterData(Context context) {
        context.getFileStreamPath("ACRA-limiter.json").delete();
    }


    /**
     * Sets the user language.
     *
     * @param localeCode The locale code of the language to set
     */
    public static void setLanguage(String localeCode) {
        Configuration config = getInstance().getResources().getConfiguration();
        Locale newLocale = LanguageUtil.getLocale(localeCode);
        config.locale = newLocale;
        getInstance().getResources().updateConfiguration(config, getInstance().getResources().getDisplayMetrics());
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
     * Turns ACRA reporting off completely and persists it to shared prefs
     * But expands logcat search in case developer manually re-enables it
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    private void setDebugACRAConfig(SharedPreferences prefs) {
        // Disable crash reporting
        setAcraReportingMode(FEEDBACK_REPORT_NEVER);
        prefs.edit().putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_NEVER).apply();
        // Use a wider logcat filter in case crash reporting manually re-enabled
        String [] logcatArgs = { "-t", "300", "-v", "long", "ACRA:S"};
        setAcraConfigBuilder(getAcraCoreConfigBuilder().setLogcatArguments(logcatArgs));
    }


    /**
     * Puts ACRA Reporting mode into user-specified mode, with default of "ask first"
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    private void setProductionACRAConfig(SharedPreferences prefs) {
        // Enable or disable crash reporting based on user setting
        setAcraReportingMode(prefs.getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK));
    }


    /**
     * Set the reporting mode for ACRA based on the value of the FEEDBACK_REPORT_KEY preference
     * @param value value of FEEDBACK_REPORT_KEY preference
     */
    public void setAcraReportingMode(String value) {
        SharedPreferences.Editor editor = getSharedPrefs(this).edit();
        // Set the ACRA disable value
        if (value.equals(FEEDBACK_REPORT_NEVER)) {
            editor.putBoolean(ACRA.PREF_DISABLE_ACRA, true);
        } else {
            editor.putBoolean(ACRA.PREF_DISABLE_ACRA, false);
            // Switch between auto-report via toast and manual report via dialog
            CoreConfigurationBuilder builder = getAcraCoreConfigBuilder();
            if (value.equals(FEEDBACK_REPORT_ALWAYS)) {
                // Toast text defaults to always, we just need to disable the dialog
                builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
                        .setEnabled(false);
            } else if (value.equals(FEEDBACK_REPORT_ASK)) {
                // Both are enabled via annotation, just need to alter toast text
                builder.getPluginConfigurationBuilder(ToastConfigurationBuilder.class)
                        .setResText(R.string.feedback_manual_toast_text);
            }
            setAcraConfigBuilder(builder);
        }
        editor.apply();
    }

    /**
     * Get the url for the feedback page
     * @return
     */
    public static String getFeedbackUrl() {
        if (isCurrentLanguage("ja")) {
            return sInstance.getResources().getString(R.string.link_help_ja);
        } else if (isCurrentLanguage("zh")) {
            return sInstance.getResources().getString(R.string.link_help_zh);
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
        } else if (isCurrentLanguage("zh")) {
            return sInstance.getResources().getString(R.string.link_manual_zh);
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
