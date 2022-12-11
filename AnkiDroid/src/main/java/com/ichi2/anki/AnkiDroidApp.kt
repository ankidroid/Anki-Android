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
package com.ichi2.anki

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.system.Os
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.ichi2.anki.CrashReportService.sendExceptionReport
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.services.BootService
import com.ichi2.anki.services.NotificationService
import com.ichi2.compat.CompatHelper
import com.ichi2.themes.Themes
import com.ichi2.utils.*
import com.ichi2.utils.LanguageUtil.getCurrentLanguage
import com.ichi2.utils.LanguageUtil.getLanguage
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.InputStream
import java.util.*
import java.util.regex.Pattern

/**
 * Application class.
 */
@KotlinCleanup("lots to do")
@KotlinCleanup("IDE Lint")
open class AnkiDroidApp : Application() {
    /** An exception if the WebView subsystem fails to load  */
    private var mWebViewError: Throwable? = null
    private val mNotifications = MutableLiveData<Void?>()
    @KotlinCleanup("can move analytics here now")
    override fun attachBaseContext(base: Context) {
        // update base context with preferred app language before attach
        // possible since API 17, only supported way since API 25
        // for API < 17 we update the configuration directly
        super.attachBaseContext(updateContextWithLanguage(base))

        // DO NOT INIT A WEBVIEW HERE (Moving Analytics to this method)
        // Crashes only on a Physical API 19 Device - #7135
        // After we move past API 19, we're good to go.
    }

    /**
     * On application creation.
     */
    override fun onCreate() {
        BackendFactory.defaultLegacySchema = BuildConfig.LEGACY_SCHEMA
        try {
            // enable debug logging of sync actions
            if (BuildConfig.DEBUG) {
                Os.setenv("RUST_LOG", "info,anki::sync=debug,anki::media=debug", false)
            }
        } catch (_: Exception) {
        }
        // Uncomment the following lines to see a log of all SQL statements
        // executed by the backend. The log may be delayed by 100ms, so you should not
        // assume than a given SQL statement has run after a Timber.* line just
        // because the SQL statement appeared later.
        //   Os.setenv("TRACESQL", "1", false);
        super.onCreate()
        if (isInitialized) {
            Timber.i("onCreate() called multiple times")
            // 5887 - fix crash.
            if (instance.resources == null) {
                Timber.w("Skipping re-initialisation - no resources. Maybe uninstalling app?")
                return
            }
        }
        instance = this

        // Get preferences
        val preferences = getSharedPrefs(this)

        // TODO remove the following if-block once AnkiDroid uses the new schema by default
        if (BuildConfig.LEGACY_SCHEMA) {
            val isNewSchemaEnabledByPref =
                preferences.getBoolean(getString(R.string.pref_rust_backend_key), false)
            if (isNewSchemaEnabledByPref) {
                Timber.i("New schema enabled by preference")
                BackendFactory.defaultLegacySchema = false
            }
        }
        CrashReportService.initialize(this)
        if (BuildConfig.DEBUG) {
            // Enable verbose error logging and do method tracing to put the Class name as log tag
            Timber.plant(DebugTree())
            LeakCanaryConfiguration.setInitialConfigFor(this)
        } else {
            Timber.plant(ProductionCrashReportingTree())
            LeakCanaryConfiguration.disable()
        }
        Timber.tag(TAG)
        Timber.d("Startup - Application Start")

        // analytics after ACRA, they both install UncaughtExceptionHandlers but Analytics chains while ACRA does not
        UsageAnalytics.initialize(this)
        if (BuildConfig.DEBUG) {
            UsageAnalytics.setDryRun(true)
        }

        // Stop after analytics and logging are initialised.
        if (CrashReportService.isProperServiceProcess()) {
            Timber.d("Skipping AnkiDroidApp.onCreate from ACRA sender process")
            return
        }
        if (AdaptionUtil.isUserATestClient) {
            showThemedToast(this.applicationContext, getString(R.string.user_is_a_robot), false)
        }

        // make default HTML / JS debugging true for debug build and disable for unit/android tests
        if (BuildConfig.DEBUG && !AdaptionUtil.isRunningAsUnitTest) {
            preferences.edit { putBoolean("html_javascript_debugging", true) }
        }
        CardBrowserContextMenu.ensureConsistentStateWithPreferenceStatus(
            this,
            preferences.getBoolean(
                getString(R.string.card_browser_external_context_menu_key),
                false
            )
        )
        AnkiCardContextMenu.ensureConsistentStateWithPreferenceStatus(
            this,
            preferences.getBoolean(getString(R.string.anki_card_external_context_menu_key), true)
        )
        CompatHelper.compat.setupNotificationChannel(applicationContext)

        // Configure WebView to allow file scheme pages to access cookies.
        if (!acceptFileSchemeCookies()) {
            return
        }

        // Forget the last deck that was used in the CardBrowser
        CardBrowser.clearLastDeckId()
        LanguageUtil.setDefaultBackendLanguages()

        // Create the AnkiDroid directory if missing. Send exception report if inaccessible.
        if (Permissions.hasStorageAccessPermission(this)) {
            try {
                val dir = CollectionHelper.getCurrentAnkiDroidDirectory(this)
                CollectionHelper.initializeAnkiDroidDirectory(dir)
            } catch (e: StorageAccessException) {
                Timber.e(e, "Could not initialize AnkiDroid directory")
                val defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory(this)
                if (isSdCardMounted && CollectionHelper.getCurrentAnkiDroidDirectory(this) == defaultDir) {
                    // Don't send report if the user is using a custom directory as SD cards trip up here a lot
                    sendExceptionReport(e, "AnkiDroidApp.onCreate")
                }
            }
        }
        Timber.i("AnkiDroidApp: Starting Services")
        BootService().onReceive(this, Intent(this, BootService::class.java))

        // Register for notifications
        mNotifications.observeForever { NotificationService.triggerNotificationFor(this) }
        Themes.systemIsInNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        Themes.updateCurrentTheme()
    }

    fun scheduleNotification() {
        mNotifications.postValue(null)
    }

    @Suppress("deprecation") // 7109: setAcceptFileSchemeCookies
    protected fun acceptFileSchemeCookies(): Boolean {
        return try {
            CookieManager.setAcceptFileSchemeCookies(true)
            true
        } catch (e: Throwable) {
            // 5794: Errors occur if the WebView fails to load
            // android.webkit.WebViewFactory.MissingWebViewPackageException.MissingWebViewPackageException
            // Error may be excessive, but I expect a UnsatisfiedLinkError to be possible here.
            mWebViewError = e
            sendExceptionReport(e, "setAcceptFileSchemeCookies")
            Timber.e(e, "setAcceptFileSchemeCookies")
            false
        }
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
    class ProductionCrashReportingTree : Timber.Tree() {
        /**
         * Extract the tag which should be used for the message from the `element`. By default
         * this will use the class name without any anonymous class suffixes (e.g., `Foo$1`
         * becomes `Foo`).
         *
         *
         * Note: This will not be called if an API with a manual tag was called with a non-null tag
         */
        fun createStackElementTag(element: StackTraceElement): String {
            val m = ANONYMOUS_CLASS.matcher(element.className)
            val tag = if (m.find()) m.replaceAll("") else element.className
            return tag.substring(tag.lastIndexOf('.') + 1)
        } // --- this is not present in the Timber.DebugTree copy/paste ---

        // We are in production and should not crash the app for a logging failure
        // throw new IllegalStateException(
        //        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
        // --- end of alteration from upstream Timber.DebugTree.getTag ---
        // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
        // because Robolectric runs them on the JVM but on Android the elements are different.
        val tag: String
            get() {
                // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
                // because Robolectric runs them on the JVM but on Android the elements are different.
                val stackTrace = Throwable().stackTrace
                return if (stackTrace.size <= CALL_STACK_INDEX) {

                    // --- this is not present in the Timber.DebugTree copy/paste ---
                    // We are in production and should not crash the app for a logging failure
                    "$TAG unknown class"
                    // throw new IllegalStateException(
                    //        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
                    // --- end of alteration from upstream Timber.DebugTree.getTag ---
                } else createStackElementTag(stackTrace[CALL_STACK_INDEX])
            }

        // ----  END copied from Timber.DebugTree because DebugTree.getTag() is package private ----
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            when (priority) {
                Log.VERBOSE, Log.DEBUG -> {}
                Log.INFO -> Log.i(TAG, message, t)
                Log.WARN -> Log.w(TAG, "${this.tag}/ $message", t)
                Log.ERROR, Log.ASSERT -> Log.e(TAG, "${this.tag}/ $message", t)
            }
        }

        companion object {
            // ----  BEGIN copied from Timber.DebugTree because DebugTree.getTag() is package private ----
            private const val CALL_STACK_INDEX = 6
            private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")
        }
    }

    companion object {
        /** Running under instrumentation. a "/androidTest" directory will be created which contains a test collection  */
        var INSTRUMENTATION_TESTING = false

        /**
         * Toggles Scoped Storage functionality introduced in later commits
         *
         *
         * Can be set to true or false only by altering the declaration itself.
         * This restriction ensures that this flag will only be used by developers for testing
         *
         *
         * Set to false by default, so won't migrate data or use new scoped dirs
         *
         *
         * If true, enables data migration & use of scoped dirs in later commits
         *
         *
         * Should be set to true for testing Scoped Storage
         *
         *
         * TODO: Should be removed once app is fully functional under Scoped Storage
         */
        var TESTING_SCOPED_STORAGE = false
        const val XML_CUSTOM_NAMESPACE = "http://arbitrary.app.namespace/com.ichi2.anki"
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        // Tag for logging messages.
        const val TAG = "AnkiDroid"

        /** Singleton instance of this class.
         * Note: this may not be initialized if AnkiDroid is run via BackupManager
         */
        lateinit var instance: AnkiDroidApp
            private set

        /**
         * The latest package version number that included important changes to the database integrity check routine. All
         * collections being upgraded to (or after) this version must run an integrity check as it will contain fixes that
         * all collections should have.
         */
        const val CHECK_DB_AT_VERSION = 21000172

        /** HACK: Whether an exception report has been thrown - TODO: Rewrite an ACRA Listener to do this  */
        @VisibleForTesting
        var sentExceptionReportHack = false
        fun getResourceAsStream(name: String): InputStream {
            return instance.applicationContext.classLoader.getResourceAsStream(name)
        }

        @get:JvmName("isInitialized")
        val isInitialized: Boolean
            get() = this::instance.isInitialized

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun simulateRestoreFromBackup() {
            val field = AnkiDroidApp::class.java.getDeclaredField("instance")

            with(field) {
                isAccessible = true
                set(field, null)
            }
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun internalSetInstanceValue(value: AnkiDroidApp) {
            val field = AnkiDroidApp::class.java.getDeclaredField("instance")

            with(field) {
                isAccessible = true
                set(field, value)
            }
        }

        /**
         * Convenience method for accessing Shared preferences
         *
         * @param context Context to get preferences for.
         * @return A SharedPreferences object for this instance of the app.
         */
        @Suppress("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
        fun getSharedPrefs(context: Context?): SharedPreferences {
            return android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        }

        val cacheStorageDirectory: String
            get() = instance.cacheDir.absolutePath
        val appResources: Resources
            get() = instance.resources
        val isSdCardMounted: Boolean
            get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

        /**
         * Returns a Context with the correct, saved language, to be attached using attachBase().
         * For old APIs directly sets language using deprecated functions
         *
         * @param remoteContext The base context offered by attachBase() to be passed to super.attachBase().
         * Can be modified here to set correct GUI language.
         */
        fun updateContextWithLanguage(remoteContext: Context): Context {
            return try {
                // sInstance (returned by getInstance() ) set during application OnCreate()
                // if getInstance() is null, the method is called during applications attachBaseContext()
                // and preferences need mBase directly (is provided by remoteContext during attachBaseContext())
                val preferences = if (isInitialized) {
                    getSharedPrefs(instance.baseContext)
                } else {
                    getSharedPrefs(remoteContext)
                }
                val langConfig =
                    getLanguageConfig(remoteContext.resources.configuration, preferences)
                remoteContext.createConfigurationContext(langConfig)
            } catch (e: Exception) {
                Timber.e(e, "failed to update context with new language")
                // during AnkiDroidApp.attachBaseContext() ACRA is not initialized, so the exception report will not be sent
                sendExceptionReport(e, "AnkiDroidApp.updateContextWithLanguage")
                remoteContext
            }
        }

        /**
         * Creates and returns a new configuration with the chosen GUI language that is saved in the preferences
         *
         * @param remoteConfig The configuration of the remote context to set the language for
         * @param prefs
         */
        private fun getLanguageConfig(
            remoteConfig: Configuration,
            prefs: SharedPreferences
        ): Configuration {
            val newConfig = Configuration(remoteConfig)
            val newLocale = LanguageUtil.getLocale(prefs.getLanguage(), prefs)
            Timber.d("AnkiDroidApp::getLanguageConfig - setting locale to %s", newLocale)
            // API level >=24
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Build list of locale strings, separated by commas: newLocale as first element
                var strLocaleList = newLocale.toLanguageTag()
                // if Anki locale from settings is no equal to system default, add system default as second item
                // LocaleList must not contain language tags twice, will crash otherwise!
                if (!strLocaleList.contains(Locale.getDefault().toLanguageTag())) {
                    strLocaleList = strLocaleList + "," + Locale.getDefault().toLanguageTag()
                }
                val newLocaleList = LocaleList.forLanguageTags(strLocaleList)
                // first element of setLocales() is automatically setLocal()
                newConfig.setLocales(newLocaleList)
            } else {
                // API level >=17 but <24
                newConfig.setLocale(newLocale)
            }
            return newConfig
        }

        fun getMarketIntent(context: Context): Intent {
            val uri =
                context.getString(if (CompatHelper.isKindle) R.string.link_market_kindle else R.string.link_market)
            val parsed = Uri.parse(uri)
            return Intent(Intent.ACTION_VIEW, parsed)
        } // TODO actually this can be done by translating "link_help" string for each language when the App is
        // properly translated
        /**
         * Get the url for the feedback page
         * @return
         */
        val feedbackUrl: String
            get() = // TODO actually this can be done by translating "link_help" string for each language when the App is
                // properly translated
                when (getSharedPrefs(instance).getCurrentLanguage()) {
                    "ja" -> appResources.getString(R.string.link_help_ja)
                    "zh" -> appResources.getString(R.string.link_help_zh)
                    "ar" -> appResources.getString(R.string.link_help_ar)
                    else -> appResources.getString(R.string.link_help)
                } // TODO actually this can be done by translating "link_manual" string for each language when the App is
        // properly translated
        /**
         * Get the url for the manual
         * @return
         */
        val manualUrl: String
            get() = // TODO actually this can be done by translating "link_manual" string for each language when the App is
                // properly translated
                when (getSharedPrefs(instance).getCurrentLanguage()) {
                    "ja" -> appResources.getString(R.string.link_manual_ja)
                    "zh" -> appResources.getString(R.string.link_manual_zh)
                    "ar" -> appResources.getString(R.string.link_manual_ar)
                    else -> appResources.getString(R.string.link_manual)
                }

        fun webViewFailedToLoad(): Boolean {
            return instance.mWebViewError != null
        }

        val webViewErrorMessage: String?
            get() {
                val error = instance.mWebViewError
                if (error == null) {
                    Timber.w("getWebViewExceptionMessage called without webViewFailedToLoad check")
                    return null
                }
                return ExceptionUtil.getExceptionMessage(error)
            }
    }
}
