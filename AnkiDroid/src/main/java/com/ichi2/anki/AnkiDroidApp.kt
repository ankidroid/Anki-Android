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

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.system.Os
import android.webkit.CookieManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import com.ichi2.anki.CrashReportService.sendExceptionReport
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.logging.FragmentLifecycleLogger
import com.ichi2.anki.logging.LogType
import com.ichi2.anki.logging.ProductionCrashReportingTree
import com.ichi2.anki.logging.RobolectricDebugTree
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.services.BootService
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.ui.dialogs.ActivityAgnosticDialogs
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.ExceptionUtil
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.Locale

/**
 * Application class.
 */
@KotlinCleanup("lots to do")
@KotlinCleanup("IDE Lint")
open class AnkiDroidApp : Application() {
    /** An exception if the WebView subsystem fails to load  */
    private var webViewError: Throwable? = null
    private val notifications = MutableLiveData<Void?>()

    lateinit var activityAgnosticDialogs: ActivityAgnosticDialogs

    /** Used to avoid showing extra progress dialogs when one already shown. */
    var progressDialogShown = false

    @KotlinCleanup("analytics can be moved to attachBaseContext()")
    /**
     * On application creation.
     */
    override fun onCreate() {
        try {
            Os.setenv("PLATFORM", syncPlatform(), false)
            // enable debug logging of sync actions
            if (BuildConfig.DEBUG) {
                Os.setenv("RUST_LOG", "info,anki::sync=debug,anki::media=debug,fsrs=error", false)
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
        val preferences = this.sharedPrefs()

        CrashReportService.initialize(this)
        val logType = LogType.value
        when (logType) {
            LogType.DEBUG -> Timber.plant(DebugTree())
            LogType.ROBOLECTRIC -> Timber.plant(RobolectricDebugTree())
            LogType.PRODUCTION -> Timber.plant(ProductionCrashReportingTree())
        }
        if (BuildConfig.ENABLE_LEAK_CANARY) {
            LeakCanaryConfiguration.setInitialConfigFor(this)
        } else {
            LeakCanaryConfiguration.disable()
        }
        Timber.tag(TAG)
        Timber.d("Startup - Application Start")
        Timber.i("Timber config: $logType")

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

        makeBackendUsable(this)

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
        notifications.observeForever { NotificationService.triggerNotificationFor(this) }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Timber.i("${activity::class.simpleName}::onCreate")
                (activity as? FragmentActivity)
                    ?.supportFragmentManager
                    ?.registerFragmentLifecycleCallbacks(
                        FragmentLifecycleLogger(activity),
                        true
                    )
            }

            override fun onActivityStarted(activity: Activity) {
                Timber.i("${activity::class.simpleName}::onStart")
            }

            override fun onActivityResumed(activity: Activity) {
                Timber.i("${activity::class.simpleName}::onResume")
            }

            override fun onActivityPaused(activity: Activity) {
                Timber.i("${activity::class.simpleName}::onPause")
            }

            override fun onActivityStopped(activity: Activity) {
                Timber.i("${activity::class.simpleName}::onStop")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                Timber.i("${activity::class.simpleName}::onSaveInstanceState")
            }

            override fun onActivityDestroyed(activity: Activity) {
                Timber.i("${activity::class.simpleName}::onDestroy")
            }
        })

        activityAgnosticDialogs = ActivityAgnosticDialogs.register(this)
        TtsVoices.launchBuildLocalesJob()
        // enable {{tts-voices:}} field filter
        TtsVoicesFieldFilter.ensureApplied()
    }

    /**
     * @return the app version, OS version and device model, provided when syncing.
     */
    private fun syncPlatform(): String {
        // AnkiWeb reads this string and uses , and : as delimiters, so we remove them.
        val model = Build.MODEL.replace(',', ' ').replace(':', ' ')
        return String.format(
            Locale.US,
            "android:%s:%s:%s",
            BuildConfig.VERSION_NAME,
            Build.VERSION.RELEASE,
            model
        )
    }

    fun scheduleNotification() {
        notifications.postValue(null)
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
            webViewError = e
            sendExceptionReport(e, "setAcceptFileSchemeCookies")
            Timber.e(e, "setAcceptFileSchemeCookies")
            false
        }
    }

    companion object {

        /**
         * [CoroutineScope] tied to the [Application], allowing executing of tasks which should
         * execute as long as the app is running
         *
         * This scope is bound by default to [Dispatchers.Main.immediate][kotlinx.coroutines.MainCoroutineDispatcher.immediate].
         * Use an alternate dispatcher if the main thread is not required: [Dispatchers.Default] or [Dispatchers.IO]
         *
         * This scope will not be cancelled; exceptions are handled by [SupervisorJob]
         *
         * See: [Operations that shouldn't be cancelled in Coroutines](https://medium.com/androiddevelopers/coroutines-patterns-for-work-that-shouldnt-be-cancelled-e26c40f142ad#d425)
         *
         * This replicates the manner which `lifecycleScope`/`viewModelScope` is exposed in Android
         */
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        /**
         * A [SharedPreferencesProvider] which does not require [onCreate] when run from tests
         *
         * @see sharedPreferencesTestingOverride
         */
        val sharedPreferencesProvider get() = SharedPreferencesProvider { sharedPrefs() }

        /** Running under instrumentation. a "/androidTest" directory will be created which contains a test collection  */
        var INSTRUMENTATION_TESTING = false
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
         * An override for Shared Preferences to use for unit tests
         *
         * This does not depend on an instance of AnkiDroidApp and therefore has no Android
         * implementations
         */
        @VisibleForTesting
        var sharedPreferencesTestingOverride: SharedPreferences? = null

        /**
         * A test-friendly accessor to Shared Preferences.
         *
         * In tests, this can avoid an instance of `AnkiDroidApp`, which is slow
         * This was added to avoid code churn
         */
        fun sharedPrefs() = sharedPreferencesTestingOverride ?: instance.sharedPrefs()

        /**
         * The latest package version number that included important changes to the database integrity check routine. All
         * collections being upgraded to (or after) this version must run an integrity check as it will contain fixes that
         * all collections should have.
         */
        const val CHECK_DB_AT_VERSION = 21000172

        /** HACK: Whether an exception report has been thrown - TODO: Rewrite an ACRA Listener to do this  */
        @VisibleForTesting
        var sentExceptionReportHack = false

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

        /** Load the libraries to allow access to Anki-Android-Backend */
        @NeedsTest("Not calling this in the ContentProvider should have failed a test")
        fun makeBackendUsable(context: Context) {
            // Robolectric uses RustBackendLoader.ensureSetup()
            if (Build.FINGERPRINT == "robolectric") return

            // Prevent sqlite throwing error 6410 due to the lack of /tmp on Android
            Os.setenv("TMPDIR", context.cacheDir.path, false)
            // Load backend library
            System.loadLibrary("rsdroid")
        }

        val appResources: Resources
            get() = instance.resources
        val isSdCardMounted: Boolean
            get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

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
                when (LanguageUtil.getCurrentLocaleTag()) {
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
                when (LanguageUtil.getCurrentLocaleTag()) {
                    "ja" -> appResources.getString(R.string.link_manual_ja)
                    "zh" -> appResources.getString(R.string.link_manual_zh)
                    "ar" -> appResources.getString(R.string.link_manual_ar)
                    else -> appResources.getString(R.string.link_manual)
                }

        fun webViewFailedToLoad(): Boolean {
            return instance.webViewError != null
        }

        val webViewErrorMessage: String?
            get() {
                val error = instance.webViewError
                if (error == null) {
                    Timber.w("getWebViewExceptionMessage called without webViewFailedToLoad check")
                    return null
                }
                return ExceptionUtil.getExceptionMessage(error)
            }
    }
}
