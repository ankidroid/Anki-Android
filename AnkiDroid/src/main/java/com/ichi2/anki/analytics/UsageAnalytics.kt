/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.anki.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.WebSettings
import androidx.annotation.VisibleForTesting
import com.brsanthu.googleanalytics.GoogleAnalytics
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig
import com.brsanthu.googleanalytics.httpclient.OkHttpClientImpl
import com.brsanthu.googleanalytics.request.DefaultRequest
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.utils.DisplayUtils
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.WebViewDebugging.hasSetDataDirectory
import org.acra.ACRA
import org.acra.util.Installation
import timber.log.Timber
import java.lang.RuntimeException

@KotlinCleanup("see if we can make variables lazy, or properties without the `s` prefix")
object UsageAnalytics {
    const val ANALYTICS_OPTIN_KEY = "analyticsOptIn"
    @KotlinCleanup("lateinit")
    private var sAnalytics: GoogleAnalytics? = null
    private var sOriginalUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var sOptIn = false
    private var sAnalyticsTrackingId: String? = null
    private var sAnalyticsSamplePercentage = -1

    /**
     * Initialize the analytics provider - must be called prior to sending anything.
     * Usage after that is static
     * Note: may need to implement sampling strategy internally to limit hits, or not track Reviewer...
     *
     * @param context required to look up the analytics codes for the app
     */
    @JvmStatic
    @Synchronized
    fun initialize(context: Context): GoogleAnalytics? {
        Timber.i("initialize()")
        if (sAnalytics == null) {
            Timber.d("App tracking id 'tid' = %s", getAnalyticsTag(context))
            val gaConfig = GoogleAnalyticsConfig()
                .setBatchingEnabled(true)
                .setSamplePercentage(getAnalyticsSamplePercentage(context))
                .setBatchSize(1) // until this handles application termination we will lose hits if batch>1
            sAnalytics = GoogleAnalytics.builder()
                .withTrackingId(getAnalyticsTag(context))
                .withConfig(gaConfig)
                .withDefaultRequest(
                    AndroidDefaultRequest()
                        .setAndroidRequestParameters(context)
                        .applicationName(context.getString(R.string.app_name))
                        .applicationVersion(Integer.toString(BuildConfig.VERSION_CODE))
                        .applicationId(BuildConfig.APPLICATION_ID)
                        .trackingId(getAnalyticsTag(context))
                        .clientId(Installation.id(context))
                        .anonymizeIp(context.resources.getBoolean(R.bool.ga_anonymizeIp))
                )
                .withHttpClient(OkHttpClientImpl(gaConfig))
                .build()
        }
        installDefaultExceptionHandler()
        val userPrefs = AnkiDroidApp.getSharedPrefs(context)
        optIn = userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false)
        userPrefs.registerOnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences, key: String ->
            if (key == ANALYTICS_OPTIN_KEY) {
                val newValue = sharedPreferences.getBoolean(key, false)
                Timber.i("Setting analytics opt-in to: %b", newValue)
                optIn = newValue
            }
        }
        return sAnalytics
    }

    private fun getAnalyticsTag(context: Context): String? {
        if (sAnalyticsTrackingId == null) {
            sAnalyticsTrackingId = context.getString(R.string.ga_trackingId)
        }
        return sAnalyticsTrackingId
    }

    private fun getAnalyticsSamplePercentage(context: Context): Int {
        if (sAnalyticsSamplePercentage == -1) {
            sAnalyticsSamplePercentage = context.resources.getInteger(R.integer.ga_sampleFrequency)
        }
        return sAnalyticsSamplePercentage
    }

    fun setDevMode() {
        Timber.d("setDevMode() re-configuring for development analytics tagging")
        sAnalyticsTrackingId = "UA-125800786-2"
        sAnalyticsSamplePercentage = 100
        reInitialize()
    }

    /**
     * We want to send an analytics hit on any exception, then chain to other handlers (e.g., ACRA)
     */
    @Synchronized
    private fun installDefaultExceptionHandler() {
        sOriginalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, throwable: Throwable ->
            sendAnalyticsException(throwable, true)
            if (thread == null) {
                Timber.w("unexpected: thread was null")
                return@setDefaultUncaughtExceptionHandler
            }
            sOriginalUncaughtExceptionHandler!!.uncaughtException(thread, throwable)
        }
    }

    /**
     * Reset the default exception handler
     */
    @Synchronized
    private fun unInstallDefaultExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(sOriginalUncaughtExceptionHandler)
        sOriginalUncaughtExceptionHandler = null
    }
    /**
     * Determine whether we are disabled or not
     */
    /**
     * Allow users to enable or disable analytics
     *
     * @param optIn true allows collection of analytics information
     */
    @set:Synchronized
    private var optIn: Boolean
        get() {
            Timber.d("getOptIn() status: %s", sOptIn)
            return sOptIn
        }
        private set(optIn) {
            Timber.i("setOptIn(): from %s to %s", sOptIn, optIn)
            sOptIn = optIn
            sAnalytics!!.flush()
            sAnalytics!!.config.isEnabled = optIn
            sAnalytics!!.performSamplingElection()
            Timber.d("setOptIn() optIn / sAnalytics.config().enabled(): %s/%s", sOptIn, sAnalytics!!.config.isEnabled)
        }

    /**
     * Set the analytics up to log things, goes to hit validator. Experimental.
     *
     * @param dryRun set to true if you want to log analytics hit but not dispatch
     */
    @JvmStatic
    @Synchronized
    fun setDryRun(dryRun: Boolean) {
        Timber.i("setDryRun(): %s, warning dryRun is experimental", dryRun)
    }

    /**
     * Re-Initialize the analytics provider
     */
    @Synchronized
    fun reInitialize() {

        // send any pending async hits, re-chain default exception handlers and re-init
        Timber.i("reInitialize()")
        sAnalytics!!.flush()
        sAnalytics = null
        unInstallDefaultExceptionHandler()
        initialize(AnkiDroidApp.getInstance().applicationContext)
    }

    /**
     * Submit a screen for aggregation / analysis.
     * Intended for use to determine if / how features are being used
     *
     * @param object the result of Object.getClass().getSimpleName() will be used as the screen tag
     */
    @KotlinCleanup("rename object")
    @JvmStatic
    fun sendAnalyticsScreenView(`object`: Any) {
        sendAnalyticsScreenView(`object`.javaClass.simpleName)
    }

    /**
     * Submit a screen display with a synthetic name for aggregation / analysis
     * Intended for use if your class handles multiple screens you want to track separately
     *
     * @param screenName screenName the name to show in analysis reports
     */
    fun sendAnalyticsScreenView(screenName: String) {
        Timber.d("sendAnalyticsScreenView(): %s", screenName)
        if (!optIn) {
            return
        }
        sAnalytics!!.screenView().screenName(screenName).sendAsync()
    }

    /**
     * Send an arbitrary analytics event - these should be noun/verb pairs, e.g. "text to speech", "enabled"
     *
     * @param category the category of event, make your own but use a constant so reporting is good
     * @param action   the action the user performed
     */
    @KotlinCleanup("remove when all callers are Kotlin")
    @JvmStatic
    fun sendAnalyticsEvent(category: String, action: String) {
        sendAnalyticsEvent(category, action, Integer.MIN_VALUE, null)
    }

    /**
     * Send a detailed arbitrary analytics event, with noun/verb pairs and extra data if needed
     *
     * @param category the category of event, make your own but use a constant so reporting is good
     * @param action   the action the user performed
     * @param value    A value for the event, Integer.MIN_VALUE signifies caller shouldn't send the value
     * @param label    A label for the event, may be null
     */
    @JvmStatic
    fun sendAnalyticsEvent(category: String, action: String, value: Int = Int.MIN_VALUE, label: String? = null) {
        Timber.d("sendAnalyticsEvent() category/action/value/label: %s/%s/%s/%s", category, action, value, label)
        if (!optIn) {
            return
        }
        val event = sAnalytics!!.event().eventCategory(category).eventAction(action)
        if (label != null) {
            event.eventLabel(label)
        }
        if (value > Int.MIN_VALUE) {
            event.eventValue(value)
        }
        event.sendAsync()
    }

    /**
     * Send an exception event out for aggregation/analysis, parsed from the exception information
     *
     * @param t     Throwable to send for analysis
     * @param fatal whether it was fatal or not
     */
    @JvmStatic
    fun sendAnalyticsException(t: Throwable, fatal: Boolean) {
        sendAnalyticsException(getCause(t).toString(), fatal)
    }

    @JvmStatic
    @KotlinCleanup("convert to sequence")
    fun getCause(t: Throwable): Throwable {
        var cause: Throwable?
        var result = t
        while (null != result.cause.also { cause = it } && result != cause) {
            result = cause!!
        }
        return result
    }

    /**
     * Send an exception event out for aggregation/analysis
     *
     * @param description API limited to 100 characters, truncated here to 100 if needed
     * @param fatal       whether it was fatal or not
     */
    fun sendAnalyticsException(description: String, fatal: Boolean) {
        Timber.d("sendAnalyticsException() description/fatal: %s/%s", description, fatal)
        if (!sOptIn) {
            return
        }
        sAnalytics!!.exception().exceptionDescription(description).exceptionFatal(fatal).sendAsync()
    }

    internal fun canGetDefaultUserAgent(): Boolean {
        // #5502 - getDefaultUserAgent starts a WebView. We can't have two WebViews with the same data directory.
        // But ACRA starts an :acra process which does not terminate when AnkiDroid is restarted. https://crbug.com/558377

        // if we're not under the ACRA process then we're fine to initialize a WebView
        return if (!ACRA.isACRASenderServiceProcess()) {
            true
        } else hasSetDataDirectory()

        // If we have a custom data directory, then the crash will not occur.
    }

    // A listener on this preference handles the rest
    var isEnabled: Boolean
        get() {
            val userPrefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance())
            return userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false)
        }
        set(value) {
            // A listener on this preference handles the rest
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).edit()
                .putBoolean(ANALYTICS_OPTIN_KEY, value)
                .apply()
        }

    @JvmStatic
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun resetForTests() {
        sAnalytics = null
    }

    /**
     * An Android-specific device config generator. Without this it's "Desktop" and unknown for all hardware.
     * It is interesting to us what devices people use though (for instance: is Amazon Kindle support worth it?
     * Is anyone still using e-ink devices? How many people are on tablets? ChromeOS?)
     */
    private class AndroidDefaultRequest : DefaultRequest() {
        fun setAndroidRequestParameters(context: Context): DefaultRequest {

            // Are we running on really large screens or small screens? Send raw screen size
            try {
                val size = DisplayUtils.getDisplayDimensions(context)
                this.screenResolution(size.x.toString() + "x" + size.y)
            } catch (e: RuntimeException) {
                Timber.w(e)
                // nothing much to do here, it means we couldn't get WindowManager
            }

            // We can have up to 20 of these - there might be other things we want to know
            // but simply seeing what hardware we are running on should be useful
            this.customDimension(1, Build.VERSION.RELEASE) // systemVersion, e.g. "7.1.1"  for Android 7.1.1
            this.customDimension(2, Build.BRAND) // brand e.g. "OnePlus"
            this.customDimension(3, Build.MODEL) // model e.g. "ONEPLUS A6013" for the 6T
            this.customDimension(4, Build.BOARD) // deviceId e.g. "sdm845" for the 6T

            // This is important for google to auto-fingerprint us for default reporting
            // It is not possible to set operating system explicitly, there is no API or analytics parameter for it
            // Instead they respond that they auto-parse User-Agent strings for analytics attribution
            // For maximum analytics built-in report compatibility we will send the official WebView User-Agent string
            try {
                if (canGetDefaultUserAgent()) {
                    this.userAgent(WebSettings.getDefaultUserAgent(context))
                } else {
                    this.userAgent(System.getProperty("http.agent"))
                }
            } catch (e: RuntimeException) {
                Timber.w(e)
                // Catch RuntimeException as WebView initialization blows up in unpredictable ways
                // but analytics should never be a show-stopper
                this.userAgent(System.getProperty("http.agent"))
            }
            return this
        }
    }

    object Category {
        const val SYNC = "Sync"
        const val LINK_CLICKED = "LinkClicked"
    }

    /**
     * These Strings must not be changed as they are used for analytic comparisons between AnkiDroid versions.
     * If a new string is added here then the respective changes must also be made in AnalyticsConstantsTest.java
     * All the constant strings added here must be annotated with @AnalyticsConstant.
     */
    object Actions {
        /* Analytics actions used in Help Dialog*/
        @AnalyticsConstant
        val OPENED_HELPDIALOG = "Opened HelpDialogBox"

        @AnalyticsConstant
        val OPENED_USING_ANKIDROID = "Opened Using AnkiDroid"

        @AnalyticsConstant
        val OPENED_GET_HELP = "Opened Get Help"

        @AnalyticsConstant
        val OPENED_SUPPORT_ANKIDROID = "Opened Support AnkiDroid"

        @AnalyticsConstant
        val OPENED_COMMUNITY = "Opened Community"

        @AnalyticsConstant
        val OPENED_PRIVACY = "Opened Privacy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_TERMS_AND_CONDITIONS = "Opened AnkiWeb Terms and Conditions"

        @AnalyticsConstant
        val OPENED_ANKIDROID_PRIVACY_POLICY = "Opened AnkiDroid Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_PRIVACY_POLICY = "Opened AnkiWeb Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIDROID_MANUAL = "Opened AnkiDroid Manual"

        @AnalyticsConstant
        val OPENED_ANKI_MANUAL = "Opened Anki Manual"

        @AnalyticsConstant
        val OPENED_ANKIDROID_FAQ = "Opened AnkiDroid FAQ"

        @AnalyticsConstant
        val OPENED_MAILING_LIST = "Opened Mailing List"

        @AnalyticsConstant
        val OPENED_REPORT_BUG = "Opened Report a Bug"

        @AnalyticsConstant
        val OPENED_DONATE = "Opened Donate"

        @AnalyticsConstant
        val OPENED_TRANSLATE = "Opened Translate"

        @AnalyticsConstant
        val OPENED_DEVELOP = "Opened Develop"

        @AnalyticsConstant
        val OPENED_RATE = "Opened Rate"

        @AnalyticsConstant
        val OPENED_OTHER = "Opened Other"

        @AnalyticsConstant
        val OPENED_SEND_FEEDBACK = "Opened Send Feedback"

        @AnalyticsConstant
        val OPENED_ANKI_FORUMS = "Opened Anki Forums"

        @AnalyticsConstant
        val OPENED_REDDIT = "Opened Reddit"

        @AnalyticsConstant
        val OPENED_DISCORD = "Opened Discord"

        @AnalyticsConstant
        val OPENED_FACEBOOK = "Opened Facebook"

        @AnalyticsConstant
        val OPENED_TWITTER = "Opened Twitter"

        @AnalyticsConstant
        val EXCEPTION_REPORT = "Exception Report"

        @AnalyticsConstant
        val IMPORT_APKG_FILE = "Import APKG"

        @AnalyticsConstant
        val IMPORT_COLPKG_FILE = "Import COLPKG"
    }
}
