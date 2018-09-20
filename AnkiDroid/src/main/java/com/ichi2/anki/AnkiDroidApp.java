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

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;

import com.ichi2.anki.dialogs.AnkiDroidCrashReportDialog;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.anki.services.BootService;
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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;
import static timber.log.Timber.DebugTree;

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
        resPositiveButtonText = R.string.feedback_report,
        resIcon = R.drawable.logo_star_144dp
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
    public static final int CHECK_DB_AT_VERSION = 40;

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
            Timber.plant(new DebugTree());

            setDebugACRAConfig(preferences);
        } else {
            Timber.plant(new ProductionCrashReportingTree());
            setProductionACRAConfig(preferences);
        }
        Timber.tag(TAG);

        sInstance = this;
        setLanguage(preferences.getString(Preferences.LANGUAGE, ""));
        NotificationChannels.setup(getApplicationContext());

        // Configure WebView to allow file scheme pages to access cookies.
        CookieManager.setAcceptFileSchemeCookies(true);

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
        new BootService().onReceive(this, new Intent(this, BootService.class));
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
     * @param localeCode The locale code of the language to set, system language if empty
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

    /**
     * A tree which logs necessary data for crash reporting.
     *
     * Requirements:
     * 1) ignore verbose and debug log levels
     * 2) use the fixed AnkiDroidApp.TAG log tag (ACRA filters logcat for it when reporting errors)
     * 3) dynamically discover the class name and prepend it to the message for warn and error
     */
    @SuppressLint("LogNotTimber")
    public static class ProductionCrashReportingTree extends Timber.Tree {

        // ----  BEGIN copied from Timber.DebugTree because DebugTree.getTag() is package private ----

        private static final int CALL_STACK_INDEX = 6;
        private static final Pattern ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$");


        /**
         * Extract the tag which should be used for the message from the {@code element}. By default
         * this will use the class name without any anonymous class suffixes (e.g., {@code Foo$1}
         * becomes {@code Foo}).
         * <p>
         * Note: This will not be called if an API with a manual tag was called with a non-null tag
         */
        @Nullable
        String createStackElementTag(@NonNull StackTraceElement element) {
            String tag = element.getClassName();
            Matcher m = ANONYMOUS_CLASS.matcher(tag);
            if (m.find()) {
                tag = m.replaceAll("");
            }
            return tag.substring(tag.lastIndexOf('.') + 1);
        }


        final String getTag() {

            // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
            // because Robolectric runs them on the JVM but on Android the elements are different.
            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            if (stackTrace.length <= CALL_STACK_INDEX) {
                throw new IllegalStateException(
                        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
            }
            return createStackElementTag(stackTrace[CALL_STACK_INDEX]);
        }
        // ----  END copied from Timber.DebugTree because DebugTree.getTag() is package private ----



        @Override
        protected void log(int priority, String tag, @NonNull String message, Throwable t) {

            switch (priority) {
                case Log.VERBOSE:
                case Log.DEBUG:
                    break;

                case Log.INFO:
                    Log.i(AnkiDroidApp.TAG, message, t);
                    break;

                case Log.WARN:
                    Log.w(AnkiDroidApp.TAG, getTag() + "/ " + message, t);
                    break;

                case Log.ERROR:
                case Log.ASSERT:
                    Log.e(AnkiDroidApp.TAG, getTag() + "/ " + message, t);
                    break;
            }
        }
    }
}
