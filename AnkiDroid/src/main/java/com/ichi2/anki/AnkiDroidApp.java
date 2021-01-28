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
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;

import com.ichi2.anki.analytics.AnkiDroidCrashReportDialog;
import com.ichi2.anki.contextmenu.AnkiCardContextMenu;
import com.ichi2.anki.contextmenu.CardBrowserContextMenu;
import com.ichi2.anki.exception.ManuallyReportedException;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.anki.services.BootService;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.ExceptionUtil;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.utils.Permissions;
import com.ichi2.utils.WebViewDebugging;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraHttpSender;
import org.acra.annotation.AcraLimiter;
import org.acra.annotation.AcraToast;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.LimiterData;
import org.acra.config.ToastConfigurationBuilder;
import org.acra.sender.HttpSender;

import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;
import static timber.log.Timber.DebugTree;

/**
 * Application class.
 */
@SuppressLint("NonConstantResourceId") // https://github.com/ACRA/acra/issues/810
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
    public static final int CHECK_DB_AT_VERSION = 21000172;

    /**
     * The latest package version number that included changes to the preferences that requires handling. All
     * collections being upgraded to (or after) this version must update preferences.
     */
    public static final int CHECK_PREFERENCES_AT_VERSION = 20500225;

    /** Our ACRA configurations, initialized during onCreate() */
    private CoreConfigurationBuilder acraCoreConfigBuilder;

    /** An exception if the WebView subsystem fails to load */
    @Nullable
    private Throwable mWebViewError;


    @NonNull
    public static InputStream getResourceAsStream(@NonNull String name) {
        return sInstance.getApplicationContext().getClassLoader().getResourceAsStream(name);
    }


    public static boolean isInitialized() {
        return sInstance == null;
    }


    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void simulateRestoreFromBackup() {
        sInstance = null;
    }


    public static boolean isAcraEnbled(Context context, boolean defaultValue) {
        if (!getSharedPrefs(context).contains(ACRA.PREF_DISABLE_ACRA)) {
            // we shouldn't use defaultValue below, as it would be inverted which complicated understanding.
            return defaultValue;
        }
        return !getSharedPrefs(context).getBoolean(ACRA.PREF_DISABLE_ACRA, true);
    }


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

    @Override
    protected void attachBaseContext(Context base) {
        //update base context with preferred app language before attach
        //possible since API 17, only supported way since API 25
        //for API < 17 we update the configuration directly
        super.attachBaseContext(updateContextWithLanguage(base));

        // DO NOT INIT A WEBVIEW HERE (Moving Analytics to this method)
        // Crashes only on a Physical API 19 Device - #7135
        // After we move past API 19, we're good to go.
    }

    /**
     * On application creation.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (sInstance != null) {
            Timber.i("onCreate() called multiple times");
            //5887 - fix crash.
            if (sInstance.getResources() == null) {
                Timber.w("Skipping re-initialisation - no resources. Maybe uninstalling app?");
                return;
            }
        }
        sInstance = this;
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

        Timber.d("Startup - Application Start");

        // The ACRA process needs a WebView for optimal UsageAnalytics values but it can't have the same data directory.
        // Analytics falls back to a sensible default if this is not set.
        if (ACRA.isACRASenderServiceProcess() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebViewDebugging.setDataDirectorySuffix("acra");
            } catch (Exception e) {
                Timber.w(e, "Failed to set WebView data directory");
            }
        }

        // analytics after ACRA, they both install UncaughtExceptionHandlers but Analytics chains while ACRA does not
        UsageAnalytics.initialize(this);
        if (BuildConfig.DEBUG) {
            UsageAnalytics.setDryRun(true);
        }

        //Stop after analytics and logging are initialised.
        if (ACRA.isACRASenderServiceProcess()) {
            Timber.d("Skipping AnkiDroidApp.onCreate from ACRA sender process");
            return;
        }

        if (AdaptionUtil.isUserATestClient()) {
            UIUtils.showThemedToast(this.getApplicationContext(), getString(R.string.user_is_a_robot), false);
        }

        CardBrowserContextMenu.ensureConsistentStateWithSharedPreferences(this);
        AnkiCardContextMenu.ensureConsistentStateWithSharedPreferences(this);
        NotificationChannels.setup(getApplicationContext());

        // Configure WebView to allow file scheme pages to access cookies.
        try {
            CookieManager.setAcceptFileSchemeCookies(true);
        } catch (Throwable e) {
            // 5794: Errors occur if the WebView fails to load
            // android.webkit.WebViewFactory.MissingWebViewPackageException.MissingWebViewPackageException
            // Error may be excessive, but I expect a UnsatisfiedLinkError to be possible here.
            this.mWebViewError = e;
            sendExceptionReport(e, "setAcceptFileSchemeCookies");
            Timber.e(e, "setAcceptFileSchemeCookies");
            return;
        }

        // Set good default values for swipe detection
        final ViewConfiguration vc = ViewConfiguration.get(this);
        DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledPagingTouchSlop();
        DEFAULT_SWIPE_THRESHOLD_VELOCITY = vc.getScaledMinimumFlingVelocity();

        // Forget the last deck that was used in the CardBrowser
        CardBrowser.clearLastDeckId();

        // Create the AnkiDroid directory if missing. Send exception report if inaccessible.
        if (Permissions.hasStorageAccessPermission(this)) {
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

        Timber.i("AnkiDroidApp: Starting Services");
        new BootService().onReceive(this, new Intent(this, BootService.class));

        // Register BroadcastReceiver NotificationService
        NotificationService ns = new NotificationService();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(ns, new IntentFilter(NotificationService.INTENT_ACTION));
    }


    /**
     * Convenience method for accessing Shared preferences
     *
     * @param context Context to get preferences for.
     * @return A SharedPreferences object for this instance of the app.
     */
    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    public static SharedPreferences getSharedPrefs(Context context) {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(context);
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

    /** Used when we don't have an exception to throw, but we know something is wrong and want to diagnose it */
    public static void sendExceptionReport(@NonNull String message, String origin) {
        sendExceptionReport(new ManuallyReportedException(message), origin, null);
    }

    public static void sendExceptionReport(Throwable e, String origin) {
        sendExceptionReport(e, origin, null);
    }


    public static void sendExceptionReport(Throwable e, String origin, @Nullable String additionalInfo) {
        sendExceptionReport(e, origin, additionalInfo, false);
    }


    public static void sendExceptionReport(Throwable e, String origin, @Nullable String additionalInfo, boolean onlyIfSilent) {
        UsageAnalytics.sendAnalyticsException(e, false);

        if (onlyIfSilent) {
            String reportMode = getSharedPrefs(getInstance().getApplicationContext()).getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK);
            if (!FEEDBACK_REPORT_ALWAYS.equals(reportMode)) {
                Timber.i("sendExceptionReport - onlyIfSilent true, but ACRA is not 'always accept'. Skipping report send.");
                return;
            }
        }

        ACRA.getErrorReporter().putCustomData("origin", origin);
        ACRA.getErrorReporter().putCustomData("additionalInfo", additionalInfo);
        ACRA.getErrorReporter().handleException(e);
    }

    /**
     * If you want to make sure that the next exception of any time is posted, you need to clear limiter data
     *
     * @param context the context leading to the directory with ACRA limiter data
     */
    public static void deleteACRALimiterData(Context context) {
        try {
            new LimiterData().store(context);
        } catch (Exception e) {
            Timber.w(e, "Unable to clear ACRA limiter data");
        }
    }

    /**
     *  Returns a Context with the correct, saved language, to be attached using attachBase().
     *  For old APIs directly sets language using deprecated functions
     *
     * @param remoteContext The base context offered by attachBase() to be passed to super.attachBase().
     *                      Can be modified here to set correct GUI language.
     */
    @SuppressWarnings("deprecation")
    @NonNull
    public static Context updateContextWithLanguage(@NonNull Context remoteContext) {
        try {
            SharedPreferences preferences;
            //sInstance (returned by getInstance() ) set during application OnCreate()
            //if getInstance() is null, the method is called during applications attachBaseContext()
            // and preferences need mBase directly (is provided by remoteContext during attachBaseContext())
            if (getInstance() != null) {
                preferences = getSharedPrefs(getInstance().getBaseContext());
            } else {
                preferences = getSharedPrefs(remoteContext);
            }
            Configuration langConfig = getLanguageConfig(remoteContext.getResources().getConfiguration(), preferences);
            return remoteContext.createConfigurationContext(langConfig);
        } catch (Exception e) {
            Timber.e(e, "failed to update context with new language");
            //during AnkiDroidApp.attachBaseContext() ACRA is not initialized, so the exception report will not be sent
            sendExceptionReport(e,"AnkiDroidApp.updateContextWithLanguage");
            return remoteContext;
        }
    }

    /**
     *  Creates and returns a new configuration with the chosen GUI language that is saved in the preferences
     *
     * @param remoteConfig The configuration of the remote context to set the language for
     * @param prefs
     */
    @SuppressWarnings("deprecation")
    @NonNull
    private static Configuration getLanguageConfig(@NonNull Configuration remoteConfig, @NonNull SharedPreferences prefs) {
        Configuration newConfig = new Configuration(remoteConfig);
        Locale newLocale = LanguageUtil.getLocale(prefs.getString(Preferences.LANGUAGE, ""), prefs);
        Timber.d("AnkiDroidApp::getLanguageConfig - setting locale to %s", newLocale);
        //API level >=24
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //Build list of locale strings, separated by commas: newLocale as first element
            String strLocaleList = newLocale.toLanguageTag();
            //if Anki locale from settings is no equal to system default, add system default as second item
            //LocaleList must not contain language tags twice, will crash otherwise!
            if (!strLocaleList.contains(Locale.getDefault().toLanguageTag())) {
                strLocaleList = strLocaleList + "," + Locale.getDefault().toLanguageTag();
            }

            LocaleList newLocaleList = LocaleList.forLanguageTags(strLocaleList);
            //first element of setLocales() is automatically setLocal()
            newConfig.setLocales(newLocaleList);
        } else {
            //API level >=17 but <24
            newConfig.setLocale(newLocale);
        }

        return newConfig;
    }


    public static boolean initiateGestures(SharedPreferences preferences) {
        boolean enabled = preferences.getBoolean("gestures", false);
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
            DialogConfigurationBuilder dialogBuilder = builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class);
            ToastConfigurationBuilder toastBuilder = builder.getPluginConfigurationBuilder(ToastConfigurationBuilder.class);
            if (value.equals(FEEDBACK_REPORT_ALWAYS)) {
                dialogBuilder.setEnabled(false);
                toastBuilder.setResText(R.string.feedback_auto_toast_text);
            } else if (value.equals(FEEDBACK_REPORT_ASK)) {
                dialogBuilder.setEnabled(true);
                toastBuilder.setResText(R.string.feedback_manual_toast_text);
            }
            setAcraConfigBuilder(builder);
        }
        editor.apply();
    }


    public static Intent getMarketIntent(Context context) {
        final String uri = context.getString(CompatHelper.isKindle() ? R.string.link_market_kindle : R.string.link_market);
        Uri parsed = Uri.parse(uri);
        return new Intent(Intent.ACTION_VIEW, parsed);
    }


    /**
     * Get the url for the feedback page
     * @return
     */
    public static String getFeedbackUrl() {
        //TODO actually this can be done by translating "link_help" string for each language when the App is
        // properly translated
        if (isCurrentLanguage("ja")) {
            return getAppResources().getString(R.string.link_help_ja);
        } else if (isCurrentLanguage("zh")) {
            return getAppResources().getString(R.string.link_help_zh);
        } else if (isCurrentLanguage("ar")) {
            return getAppResources().getString(R.string.link_help_ar);
        } else {
            return getAppResources().getString(R.string.link_help);
        }
    }

    /**
     * Get the url for the manual
     * @return
     */
    public static String getManualUrl() {
        //TODO actually this can be done by translating "link_manual" string for each language when the App is
        // properly translated
        if (isCurrentLanguage("ja")) {
            return getAppResources().getString(R.string.link_manual_ja);
        } else if (isCurrentLanguage("zh")) {
            return getAppResources().getString(R.string.link_manual_zh);
        } else if (isCurrentLanguage("ar")) {
            return getAppResources().getString(R.string.link_manual_ar);
        } else {
            return getAppResources().getString(R.string.link_manual);
        }
    }

    /**
     * Check whether l is the currently set language code
     * @param l ISO2 language code
     * @return
     */
    private static boolean isCurrentLanguage(String l) {
        String pref = getSharedPrefs(sInstance).getString(Preferences.LANGUAGE, "");
        return pref.equals(l) || "".equals(pref) && Locale.getDefault().getLanguage().equals(l);
    }


    public static boolean webViewFailedToLoad() {
        return getInstance().mWebViewError != null;
    }


    @Nullable
    public static String getWebViewErrorMessage() {
        Throwable error = getInstance().mWebViewError;
        if (error == null) {
            Timber.w("getWebViewExceptionMessage called without webViewFailedToLoad check");
            return null;
        }
        return ExceptionUtil.getExceptionMessage(error);
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

                // --- this is not present in the Timber.DebugTree copy/paste ---
                // We are in production and should not crash the app for a logging failure
                return TAG + " unknown class";
                //throw new IllegalStateException(
                //        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
                // --- end of alteration from upstream Timber.DebugTree.getTag ---
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
