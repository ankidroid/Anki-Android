/****************************************************************************************
 * Copyright (c) 2022 Saurav Rao <sauravrao637@gmail.com>                               *
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
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.PackageInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.webkit.WebViewCompat
import com.ichi2.anki.CardBrowser.Companion.clearLastDeckId
import com.ichi2.anki.NotificationChannels.setup
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.AnkiDroidCrashReportDialog
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.anki.exception.ManuallyReportedException
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.services.BootService
import com.ichi2.anki.services.NotificationService
import com.ichi2.compat.CompatHelper.Companion.isKindle
import com.ichi2.utils.AdaptionUtil.isRunningAsUnitTest
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.ExceptionUtil.getExceptionMessage
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.LanguageUtil.getLocale
import com.ichi2.utils.Permissions.hasStorageAccessPermission
import com.ichi2.utils.WebViewDebugging.setDataDirectorySuffix
import leakcanary.AppWatcher.isInstalled
import leakcanary.AppWatcher.manualInstall
import leakcanary.DefaultOnHeapAnalyzedListener.Companion.create
import leakcanary.LeakCanary.config
import leakcanary.LeakCanary.showLeakDisplayActivityLauncherIcon
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.*
import org.acra.sender.HttpSender
import shark.*
import timber.log.Timber
import timber.log.Timber.Forest.plant
import java.io.InputStream
import java.util.*
import java.util.regex.Pattern

/**
 * Application class.
 */
open class AnkiDroidApp : Application() {

    /** Our ACRA configurations, initialized during onCreate()  */
    private var mAcraCoreConfigBuilder: CoreConfigurationBuilder? = null

    /** An exception if the WebView subsystem fails to load  */
    private var mWebViewError: Throwable? = null

    /**
     * Get the ACRA ConfigurationBuilder - use this followed by setting it to modify the config
     * @return ConfigurationBuilder for the current ACRA config
     */
    fun getAcraCoreConfigBuilder(): CoreConfigurationBuilder {
        return mAcraCoreConfigBuilder!!
    }

    /**
     * Set the ACRA ConfigurationBuilder and **re-initialize the ACRA system** with the contents
     * @param acraCoreConfigBuilder the full ACRA config to initialize ACRA with
     */
    private fun setAcraConfigBuilder(acraCoreConfigBuilder: CoreConfigurationBuilder) {
        mAcraCoreConfigBuilder = acraCoreConfigBuilder
        ACRA.init(this, acraCoreConfigBuilder)
        ACRA.getErrorReporter().putCustomData(WEBVIEW_VER_NAME, fetchWebViewInformation()[WEBVIEW_VER_NAME])
        ACRA.getErrorReporter().putCustomData("WEBVIEW_VER_CODE", fetchWebViewInformation()["WEBVIEW_VER_CODE"])
    }

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
        super.onCreate()
        if (sInstance != null) {
            Timber.i("onCreate() called multiple times")
            // 5887 - fix crash.
            if (sInstance?.resources == null) {
                Timber.w("Skipping re-initialisation - no resources. Maybe uninstalling app?")
                return
            }
        }
        sInstance = this

        // Get preferences
        val preferences = getSharedPrefs(this)

        // Setup logging and crash reporting
        mAcraCoreConfigBuilder = CoreConfigurationBuilder(this).setBuildConfigClass(org.acra.dialog.BuildConfig::class.java)
            .setExcludeMatchingSharedPreferencesKeys("username", "hkey")
            .setReportContent(
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
                ReportField.CRASH_CONFIGURATION,
                ReportField.USER_COMMENT,
                ReportField.USER_APP_START_DATE,
                ReportField.USER_CRASH_DATE,
                ReportField.LOGCAT,
                ReportField.INSTALLATION_ID,
                ReportField.ENVIRONMENT,
                ReportField.SHARED_PREFERENCES,
                ReportField.MEDIA_CODEC_LIST,
                ReportField.THREAD_DETAILS
            )
            .setLogcatArguments("-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", "$TAG:D", "*:S")

        mAcraCoreConfigBuilder?.let {
            it.apply {
                getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java).setReportDialogClass(AnkiDroidCrashReportDialog::class.java)
                    .setResCommentPrompt(R.string.empty_string)
                    .setResTitle(R.string.feedback_title)
                    .setResText(R.string.feedback_default_text)
                    .setResPositiveButtonText(R.string.feedback_report)
                    .setResIcon(R.drawable.logo_star_144dp)
                    .setEnabled(true)
                getPluginConfigurationBuilder(HttpSenderConfigurationBuilder::class.java)
                    .setHttpMethod(HttpSender.Method.PUT)
                    .setUri(BuildConfig.ACRA_URL)
                    .setEnabled(true)
                getPluginConfigurationBuilder(ToastConfigurationBuilder::class.java)
                    .setResText(R.string.feedback_auto_toast_text)
                    .setEnabled(true)
                getPluginConfigurationBuilder(LimiterConfigurationBuilder::class.java)
                    .setExceptionClassLimit(1000)
                    .setStacktraceLimit(1)
                    .setEnabled(true)
            }
        }

        if (BuildConfig.DEBUG) {
            // Enable verbose error logging and do method tracing to put the Class name as log tag
            plant(Timber.DebugTree())
            setDebugACRAConfig(preferences)
            val referenceMatchers: List<ReferenceMatcher> = ArrayList()
            // Add known memory leaks to 'referenceMatchers'
            matchKnownMemoryLeaks(referenceMatchers)

            // AppWatcher manual install if not already installed
            if (!isInstalled) {
                manualInstall(this)
            }

            // Show 'Leaks' app launcher. It has been removed by default via constants.xml.
            showLeakDisplayActivityLauncherIcon(true)
        } else {
            plant(ProductionCrashReportingTree())
            setProductionACRAConfig(preferences)
            disableLeakCanary()
        }
        Timber.tag(TAG)
        Timber.d("Startup - Application Start")

        // The ACRA process needs a WebView for optimal UsageAnalytics values but it can't have the same data directory.
        // Analytics falls back to a sensible default if this is not set.
        if (ACRA.isACRASenderServiceProcess() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                setDataDirectorySuffix("acra")
            } catch (e: Exception) {
                Timber.w(e, "Failed to set WebView data directory")
            }
        }

        // analytics after ACRA, they both install UncaughtExceptionHandlers but Analytics chains while ACRA does not
        UsageAnalytics.initialize(this)
        if (BuildConfig.DEBUG) {
            UsageAnalytics.setDryRun(true)
        }

        // Stop after analytics and logging are initialised.
        if (ACRA.isACRASenderServiceProcess()) {
            Timber.d("Skipping AnkiDroidApp.onCreate from ACRA sender process")
            return
        }
        if (isUserATestClient) {
            showThemedToast(this.applicationContext, getString(R.string.user_is_a_robot), false)
        }

        // make default HTML / JS debugging true for debug build and disable for unit/android tests
        if (BuildConfig.DEBUG && !isRunningAsUnitTest) {
            preferences.edit().putBoolean("html_javascript_debugging", true).apply()
        }
        CardBrowserContextMenu.ensureConsistentStateWithSharedPreferences(this)
        AnkiCardContextMenu.ensureConsistentStateWithSharedPreferences(this)
        setup(applicationContext)

        // Configure WebView to allow file scheme pages to access cookies.
        if (!acceptFileSchemeCookies()) {
            return
        }

        // Forget the last deck that was used in the CardBrowser
        clearLastDeckId()

        // Create the AnkiDroid directory if missing. Send exception report if inaccessible.
        if (hasStorageAccessPermission(this)) {
            try {
                val dir = CollectionHelper.getCurrentAnkiDroidDirectory(this)
                CollectionHelper.initializeAnkiDroidDirectory(dir)
            } catch (e: StorageAccessException) {
                Timber.e(e, "Could not initialize AnkiDroid directory")
                val defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory(this)
                if (isSdCardMounted() && CollectionHelper.getCurrentAnkiDroidDirectory(this) == defaultDir) {
                    // Don't send report if the user is using a custom directory as SD cards trip up here a lot
                    sendExceptionReport(e, "AnkiDroidApp.onCreate")
                }
            }
        }
        Timber.i("AnkiDroidApp: Starting Services")
        BootService().onReceive(this, Intent(this, BootService::class.java))

        // Register BroadcastReceiver NotificationService
        val ns = NotificationService()
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.registerReceiver(ns, IntentFilter(NotificationService.INTENT_ACTION))
    }

    // 7109: setAcceptFileSchemeCookies
    @Suppress("deprecation")
    private fun acceptFileSchemeCookies(): Boolean {
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
     * Turns ACRA reporting off completely and persists it to shared prefs
     * But expands logcat search in case developer manually re-enables it
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    private fun setDebugACRAConfig(prefs: SharedPreferences) {
        // Disable crash reporting
        setAcraReportingMode(FEEDBACK_REPORT_NEVER)
        prefs.edit().putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_NEVER).apply()
        // Use a wider logcat filter in case crash reporting manually re-enabled
        val logcatArgs = arrayOf("-t", "300", "-v", "long", "ACRA:S")
        setAcraConfigBuilder(getAcraCoreConfigBuilder().setLogcatArguments(*logcatArgs))
    }

    /**
     * Puts ACRA Reporting mode into user-specified mode, with default of "ask first"
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    private fun setProductionACRAConfig(prefs: SharedPreferences) {
        // Enable or disable crash reporting based on user setting
        setAcraReportingMode(prefs.getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK))
    }

    /**
     * Set the reporting mode for ACRA based on the value of the FEEDBACK_REPORT_KEY preference
     * @param value value of FEEDBACK_REPORT_KEY preference
     */
    fun setAcraReportingMode(value: String?) {
        val editor = getSharedPrefs(this).edit()
        // Set the ACRA disable value
        if (value == FEEDBACK_REPORT_NEVER) {
            editor.putBoolean(ACRA.PREF_DISABLE_ACRA, true)
        } else {
            editor.putBoolean(ACRA.PREF_DISABLE_ACRA, false)
            // Switch between auto-report via toast and manual report via dialog
            val builder = getAcraCoreConfigBuilder()
            val dialogBuilder = builder.getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
            val toastBuilder = builder.getPluginConfigurationBuilder(ToastConfigurationBuilder::class.java)
            if (value == FEEDBACK_REPORT_ALWAYS) {
                dialogBuilder.setEnabled(false)
                toastBuilder.setResText(R.string.feedback_auto_toast_text)
            } else if (value == FEEDBACK_REPORT_ASK) {
                dialogBuilder.setEnabled(true)
                toastBuilder.setResText(R.string.feedback_for_manual_toast_text)
            }
            setAcraConfigBuilder(builder)
        }
        editor.apply()
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
            var tag = element.className
            val m = ANONYMOUS_CLASS.matcher(tag)
            if (m.find()) {
                tag = m.replaceAll("")
            }
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
                Log.WARN -> Log.w(TAG, "$tag/ $message", t)
                Log.ERROR, Log.ASSERT -> Log.e(TAG, "$tag/ $message", t)
            }
        }

        // ----  BEGIN copied from Timber.DebugTree because DebugTree.getTag() is package private ----
        companion object {
            private const val CALL_STACK_INDEX = 6
            private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")
        }
    }

    private fun fetchWebViewInformation(): HashMap<String?, String?> {
        val webViewInfo = HashMap<String?, String?>()
        webViewInfo[WEBVIEW_VER_NAME] = ""
        webViewInfo["WEBVIEW_VER_CODE"] = ""
        try {
            val pi = WebViewCompat.getCurrentWebViewPackage(this)
            if (pi == null) {
                Timber.w("Could not get WebView package information")
                return webViewInfo
            }
            webViewInfo[WEBVIEW_VER_NAME] = pi.versionName
            webViewInfo["WEBVIEW_VER_CODE"] = PackageInfoCompat.getLongVersionCode(pi).toString()
        } catch (e: Throwable) {
            Timber.w(e)
        }
        return webViewInfo
    }

    /**
     * Matching known library leaks or leaks which have been already reported previously.
     */
    @KotlinCleanup("Only pass referenceMatchers to copy() method after conversion to Kotlin")
    private fun matchKnownMemoryLeaks(knownLeaks: List<ReferenceMatcher>) {
        val referenceMatchers: MutableList<ReferenceMatcher> = AndroidReferenceMatchers.appDefaults.toMutableList()
        referenceMatchers.addAll(knownLeaks)

        // Passing default values will not be required after migration to Kotlin.
        config = config.copy(
            dumpHeap = true,
            dumpHeapWhenDebugging = false,
            5,
            referenceMatchers,
            AndroidObjectInspectors.appDefaults,
            create(),
            AndroidMetadataExtractor,
            true,
            7,
            false,
            KeyedWeakReferenceFinder,
            false
        )
    }

    /**
     * Disable LeakCanary
     */
    @KotlinCleanup("Only pass relevant arguments to copy() method after conversion to Kotlin")
    private fun disableLeakCanary() {
        config = config.copy(
            dumpHeap = false,
            dumpHeapWhenDebugging = false,
            0,
            AndroidReferenceMatchers.appDefaults,
            AndroidObjectInspectors.appDefaults,
            create(),
            AndroidMetadataExtractor,
            false,
            0,
            false,
            KeyedWeakReferenceFinder,
            false
        )
    }

    companion object {
        // TAG for logging
        const val TAG = "AnkiDroid"

        private var sInstance: AnkiDroidApp? = null

        @JvmStatic
        fun getInstance() = sInstance

        /** HACK: Whether an exception report has been thrown - TODO: Rewrite an ACRA Listener to do this  */
        @VisibleForTesting
        @JvmStatic
        var sSentExceptionReportHack = false

        /** Running under instrumentation. a "/androidTest" directory will be created which contains a test collection */
        @JvmStatic
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
        @JvmStatic
        var TESTING_SCOPED_STORAGE = false

        /**
         * Toggles opening the collection using schema 16 via the Rust backend
         * and using the V16 versions of the major 'col' classes: models, decks, dconf, conf, tags
         *
         * UNSTABLE: DO NOT USE THIS ON A COLLECTION YOU CARE ABOUT.
         *
         * Set this and [com.ichi2.libanki.Consts.SCHEMA_VERSION] to 16.
         */
        var TESTING_USE_V16_BACKEND = false

        private const val WEBVIEW_VER_NAME = "WEBVIEW_VER_NAME"

        const val XML_CUSTOM_NAMESPACE = "http://arbitrary.app.namespace/com.ichi2.anki"

        // ACRA constants used for stored preferences
        const val FEEDBACK_REPORT_KEY = "reportErrorMode"
        const val FEEDBACK_REPORT_ASK = "2"
        const val FEEDBACK_REPORT_NEVER = "1"
        const val FEEDBACK_REPORT_ALWAYS = "0"

        // Constants for gestures
        var sSwipeMinDistance = -1
        var sSwipeThresholdVelocity = -1

        /**
         * The latest package version number that included important changes to the database integrity check routine. All
         * collections being upgraded to (or after) this version must run an integrity check as it will contain fixes that
         * all collections should have.
         */
        @JvmStatic
        val CHECK_DB_AT_VERSION = 21000172

        /**
         * Convenience method for accessing Shared preferences
         *
         * @param context Context to get preferences for.
         * @return A SharedPreferences object for this instance of the app.
         */
        // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
        @Suppress("deprecation")
        @JvmStatic
        fun getSharedPrefs(context: Context?): SharedPreferences {
            return android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        }

        // ACRA methods
        /** Used when we don't have an exception to throw, but we know something is wrong and want to diagnose it  */
        @JvmStatic
        fun sendExceptionReport(message: String?, origin: String?) {
            sendExceptionReport(ManuallyReportedException(message), origin, null)
        }

        @JvmStatic
        fun sendExceptionReport(e: Throwable?, origin: String?) {
            sendExceptionReport(e, origin, null)
        }

        @JvmStatic
        fun sendExceptionReport(e: Throwable?, origin: String?, additionalInfo: String?) {
            sendExceptionReport(e, origin, additionalInfo, false)
        }

        @JvmStatic
        fun sendExceptionReport(e: Throwable?, origin: String?, additionalInfo: String?, onlyIfSilent: Boolean) {
            UsageAnalytics.sendAnalyticsException(e!!, false)
            sSentExceptionReportHack = true
            if (onlyIfSilent) {
                val reportMode = getSharedPrefs(getInstance()?.applicationContext).getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK)
                if (FEEDBACK_REPORT_ALWAYS != reportMode) {
                    Timber.i("sendExceptionReport - onlyIfSilent true, but ACRA is not 'always accept'. Skipping report send.")
                    return
                }
            }
            ACRA.getErrorReporter().putCustomData("origin", origin)
            ACRA.getErrorReporter().putCustomData("additionalInfo", additionalInfo)
            ACRA.getErrorReporter().handleException(e)
        }

        fun isAcraEnabled(context: Context, defaultValue: Boolean): Boolean {
            if (!getSharedPrefs(context).contains(ACRA.PREF_DISABLE_ACRA)) {
                // we shouldn't use defaultValue below, as it would be inverted which complicated understanding.
                Timber.w("No default value for '%s'", ACRA.PREF_DISABLE_ACRA)
                return defaultValue
            }
            return !getSharedPrefs(context).getBoolean(ACRA.PREF_DISABLE_ACRA, true)
        }

        // sInstance Methods
        @JvmStatic
        fun getResourceAsStream(name: String): InputStream? {
            return getInstance()?.applicationContext?.classLoader?.getResourceAsStream(name)
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        @JvmStatic
        fun simulateRestoreFromBackup() {
            sInstance = null
        }

        @JvmStatic
        fun getCacheStorageDirectory(): String? {
            return sInstance?.cacheDir?.absolutePath
        }

        @JvmStatic
        fun getAppResources(): Resources? {
            return getInstance()?.resources
        }

        @JvmStatic
        fun isInitialized(): Boolean {
            return sInstance != null
        }

        @JvmStatic
        fun isSdCardMounted(): Boolean {
            return Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
        }

        fun getMarketIntent(context: Context): Intent {
            val uri = context.getString(if (isKindle) R.string.link_market_kindle else R.string.link_market)
            val parsed = Uri.parse(uri)
            return Intent(Intent.ACTION_VIEW, parsed)
        }

        fun webViewFailedToLoad(): Boolean {
            return sInstance?.mWebViewError != null
        }

        /**
         * If you want to make sure that the next exception of any time is posted, you need to clear limiter data
         *
         * @param context the context leading to the directory with ACRA limiter data
         */
        @JvmStatic
        fun deleteACRALimiterData(context: Context?) {
            try {
                LimiterData().store(context!!)
            } catch (e: Exception) {
                Timber.w(e, "Unable to clear ACRA limiter data")
            }
        }

        /**
         * Get the url for the feedback page
         * @return
         */
        fun getFeedbackUrl(): String {
            // TODO actually this can be done by translating "link_help" string for each language when the App is
            // properly translated
            return when (getCurrentLanguage()) {
                "ja" -> getAppResources()!!.getString(R.string.link_help_ja)
                "ar" -> getAppResources()!!.getString(R.string.link_help_ar)
                "zh" -> getAppResources()!!.getString(R.string.link_help_zh)
                else -> getAppResources()!!.getString(R.string.link_help)
            }
        }

        /**
         * Get the url for the manual
         * @return
         */
        fun getManualUrl(): String {
            // TODO actually this can be done by translating "link_manual" string for each language when the App is
            // properly translated
            return when (getCurrentLanguage()) {
                "ja" -> getAppResources()!!.getString(R.string.link_manual_ja)
                "zh" -> getAppResources()!!.getString(R.string.link_manual_zh)
                "ar" -> getAppResources()!!.getString(R.string.link_manual_ar)
                else -> getAppResources()!!.getString(R.string.link_manual)
            }
        }

        /**
         * Check whether l is the currently set language code
         * @param l ISO2 language code
         * @return
         */
        private fun isCurrentLanguage(l: String): Boolean {
            val pref = getSharedPrefs(getInstance()).getString(Preferences.LANGUAGE, "")
            return pref == l || "" == pref && Locale.getDefault().language == l
        }

        /**
         * Returns a Context with the correct, saved language, to be attached using attachBase().
         * For old APIs directly sets language using deprecated functions
         *
         * @param remoteContext The base context offered by attachBase() to be passed to super.attachBase().
         * Can be modified here to set correct GUI language.
         */
        // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
        @Suppress("deprecation")
        @JvmStatic
        fun updateContextWithLanguage(remoteContext: Context): Context {
            return try {
                // sInstance set during application OnCreate()
                // if sInstance is null, the method is called during applications attachBaseContext()
                // and preferences need mBase directly (is provided by remoteContext during attachBaseContext())
                val preferences: SharedPreferences = getSharedPrefs(
                    sInstance?.also { it.baseContext }
                        ?: remoteContext
                )
                val langConfig = getLanguageConfig(remoteContext.resources.configuration, preferences)
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
        @Suppress("deprecation")
        private fun getLanguageConfig(remoteConfig: Configuration, prefs: SharedPreferences): Configuration {
            val newConfig = Configuration(remoteConfig)
            val newLocale = getLocale(prefs.getString(Preferences.LANGUAGE, ""), prefs)
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

        private fun getCurrentLanguage(): String? = getSharedPrefs(getInstance()).getString(Preferences.LANGUAGE, Locale.getDefault().language)

        @JvmStatic
        fun getWebViewErrorMessage(): String? {
            val error = getInstance()?.mWebViewError
            if (error == null) {
                Timber.w("getWebViewExceptionMessage called without webViewFailedToLoad check")
                return null
            }
            return getExceptionMessage(error)
        }
    }
}
