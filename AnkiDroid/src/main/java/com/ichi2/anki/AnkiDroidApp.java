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
import android.webkit.CookieManager;

import com.ichi2.anki.contextmenu.AnkiCardContextMenu;
import com.ichi2.anki.contextmenu.CardBrowserContextMenu;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.anki.services.BootService;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.ExceptionUtil;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.utils.Permissions;

import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

import static com.ichi2.anki.CrashReportService.sendExceptionReport;
import static timber.log.Timber.DebugTree;

/**
 * Application class.
 */
public class AnkiDroidApp extends Application {

    /** Running under instrumentation. a "/androidTest" directory will be created which contains a test collection */
    public static boolean INSTRUMENTATION_TESTING = false;

    /**
     * Toggles Scoped Storage functionality introduced in later commits <p>
     * Can be set to true or false only by altering the declaration itself.
     * This restriction ensures that this flag will only be used by developers for testing <p>
     * Set to false by default, so won't migrate data or use new scoped dirs <p>
     * If true, enables data migration & use of scoped dirs in later commits <p>
     * Should be set to true for testing Scoped Storage <p>
     * TODO: Should be removed once app is fully functional under Scoped Storage
     */
    public static boolean TESTING_SCOPED_STORAGE = false;

    /**
     * Toggles opening the collection using schema 16 via the Rust backend
     * and using the V16 versions of the major 'col' classes: models, decks, dconf, conf, tags
     *
     * UNSTABLE: DO NOT USE THIS ON A COLLECTION YOU CARE ABOUT.
     *
     * Set this and {@link com.ichi2.libanki.Consts#SCHEMA_VERSION} to 16.
     */
    public static boolean TESTING_USE_V16_BACKEND = false;

    public static final String XML_CUSTOM_NAMESPACE = "http://arbitrary.app.namespace/com.ichi2.anki";

    // Tag for logging messages.
    public static final String TAG = "AnkiDroid";
    // Singleton instance of this class.
    private static AnkiDroidApp sInstance;
    // Constants for gestures
    public static int sSwipeMinDistance = -1;
    public static int sSwipeThresholdVelocity = -1;

    /**
     * The latest package version number that included important changes to the database integrity check routine. All
     * collections being upgraded to (or after) this version must run an integrity check as it will contain fixes that
     * all collections should have.
     */
    public static final int CHECK_DB_AT_VERSION = 21000172;

    /** An exception if the WebView subsystem fails to load */
    @Nullable
    private Throwable mWebViewError;

    /** HACK: Whether an exception report has been thrown - TODO: Rewrite an ACRA Listener to do this */
    @VisibleForTesting
    public static boolean sSentExceptionReportHack;

    @NonNull
    public static InputStream getResourceAsStream(@NonNull String name) {
        return sInstance.getApplicationContext().getClassLoader().getResourceAsStream(name);
    }


    public static boolean isInitialized() {
        return sInstance != null;
    }


    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void simulateRestoreFromBackup() {
        sInstance = null;
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

        CrashReportService.initialize(this);

        if (BuildConfig.DEBUG) {
            // Enable verbose error logging and do method tracing to put the Class name as log tag
            Timber.plant(new DebugTree());
            LeakCanaryConfiguration.setInitialConfigFor(this);
        } else {
            Timber.plant(new ProductionCrashReportingTree());
            LeakCanaryConfiguration.disable();
        }
        Timber.tag(TAG);

        Timber.d("Startup - Application Start");

        // analytics after ACRA, they both install UncaughtExceptionHandlers but Analytics chains while ACRA does not
        UsageAnalytics.initialize(this);
        if (BuildConfig.DEBUG) {
            UsageAnalytics.setDryRun(true);
        }

        //Stop after analytics and logging are initialised.
        if (CrashReportService.isProperServiceProcess()) {
            Timber.d("Skipping AnkiDroidApp.onCreate from ACRA sender process");
            return;
        }

        if (AdaptionUtil.isUserATestClient()) {
            UIUtils.showThemedToast(this.getApplicationContext(), getString(R.string.user_is_a_robot), false);
        }

        // make default HTML / JS debugging true for debug build and disable for unit/android tests
        if (BuildConfig.DEBUG && !AdaptionUtil.isRunningAsUnitTest()) {
            preferences.edit().putBoolean("html_javascript_debugging", true).apply();
        }
        
        CardBrowserContextMenu.ensureConsistentStateWithSharedPreferences(this);
        AnkiCardContextMenu.ensureConsistentStateWithSharedPreferences(this);
        NotificationChannels.setup(getApplicationContext());

        // Configure WebView to allow file scheme pages to access cookies.
        if (!acceptFileSchemeCookies()) {
            return;
        }

        // Forget the last deck that was used in the CardBrowser
        CardBrowser.clearLastDeckId();

        // Create the AnkiDroid directory if missing. Send exception report if inaccessible.
        if (Permissions.hasStorageAccessPermission(this)) {
            try {
                String dir = CollectionHelper.getCurrentAnkiDroidDirectory(this);
                CollectionHelper.initializeAnkiDroidDirectory(dir);
            } catch (StorageAccessException e) {
                Timber.e(e, "Could not initialize AnkiDroid directory");
                String defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory(this);
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

    @SuppressWarnings("deprecation") // 7109: setAcceptFileSchemeCookies
    protected boolean acceptFileSchemeCookies() {
        try {
            CookieManager.setAcceptFileSchemeCookies(true);
            return true;
        } catch (Throwable e) {
            // 5794: Errors occur if the WebView fails to load
            // android.webkit.WebViewFactory.MissingWebViewPackageException.MissingWebViewPackageException
            // Error may be excessive, but I expect a UnsatisfiedLinkError to be possible here.
            this.mWebViewError = e;
            sendExceptionReport(e, "setAcceptFileSchemeCookies");
            Timber.e(e, "setAcceptFileSchemeCookies");
            return false;
        }
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
