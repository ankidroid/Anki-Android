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

package com.ichi2.anki.analytics;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.WebSettings;

import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig;
import com.brsanthu.googleanalytics.httpclient.OkHttpClientImpl;
import com.brsanthu.googleanalytics.request.DefaultRequest;
import com.brsanthu.googleanalytics.request.EventHit;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BuildConfig;
import com.ichi2.anki.R;
import com.ichi2.utils.WebViewDebugging;

import org.acra.ACRA;
import org.acra.util.Installation;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

public class UsageAnalytics {

    public static final String ANALYTICS_OPTIN_KEY = "analyticsOptIn";
    private static GoogleAnalytics sAnalytics;
    private static Thread.UncaughtExceptionHandler sOriginalUncaughtExceptionHandler;
    private static boolean sOptIn = false;
    private static String sAnalyticsTrackingId;
    private static int sAnalyticsSamplePercentage = -1;


    /**
     * Initialize the analytics provider - must be called prior to sending anything.
     * Usage after that is static
     * Note: may need to implement sampling strategy internally to limit hits, or not track Reviewer...
     *
     * @param context required to look up the analytics codes for the app
     */
    synchronized public static GoogleAnalytics initialize(Context context) {
        Timber.i("initialize()");
        if (sAnalytics == null) {
            Timber.d("App tracking id 'tid' = %s", getAnalyticsTag(context));
            GoogleAnalyticsConfig gaConfig = new GoogleAnalyticsConfig()
                    .setBatchingEnabled(true)
                    .setSamplePercentage(getAnalyticsSamplePercentage(context))
                    .setBatchSize(1); // until this handles application termination we will lose hits if batch>1
            sAnalytics = GoogleAnalytics.builder()
                    .withTrackingId(getAnalyticsTag(context))
                    .withConfig(gaConfig)
                    .withDefaultRequest(new AndroidDefaultRequest()
                            .setAndroidRequestParameters(context)
                            .applicationName(context.getString(R.string.app_name))
                            .applicationVersion(Integer.toString(BuildConfig.VERSION_CODE))
                            .applicationId(BuildConfig.APPLICATION_ID)
                            .trackingId(getAnalyticsTag(context))
                            .clientId(Installation.id(context))
                            .anonymizeIp(context.getResources().getBoolean(R.bool.ga_anonymizeIp))
                    )
                    .withHttpClient(new OkHttpClientImpl(gaConfig))
                    .build();
        }

        installDefaultExceptionHandler();

        SharedPreferences userPrefs = AnkiDroidApp.getSharedPrefs(context);
        setOptIn(userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false));
        userPrefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if (key.equals(ANALYTICS_OPTIN_KEY)) {
                setOptIn(sharedPreferences.getBoolean(key, false));
            }
        });

        return sAnalytics;
    }


    private static String getAnalyticsTag(Context context) {
        if (sAnalyticsTrackingId == null) {
            sAnalyticsTrackingId = context.getString(R.string.ga_trackingId);
        }
        return sAnalyticsTrackingId;
    }


    private static int getAnalyticsSamplePercentage(Context context) {
        if (sAnalyticsSamplePercentage == -1) {
            sAnalyticsSamplePercentage = context.getResources().getInteger(R.integer.ga_sampleFrequency);
        }
        return sAnalyticsSamplePercentage;
    }


    public static void setDevMode() {
        Timber.d("setDevMode() re-configuring for development analytics tagging");
        sAnalyticsTrackingId = "UA-125800786-2";
        sAnalyticsSamplePercentage = 100;
        reInitialize();
    }


    /**
     * We want to send an analytics hit on any exception, then chain to other handlers (e.g., ACRA)
     */
    synchronized private static void installDefaultExceptionHandler() {
        sOriginalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            sendAnalyticsException(throwable, true);
            sOriginalUncaughtExceptionHandler.uncaughtException(thread, throwable);
        });
    }


    /**
     * Reset the default exception handler
     */
    synchronized private static void unInstallDefaultExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(sOriginalUncaughtExceptionHandler);
        sOriginalUncaughtExceptionHandler = null;
    }


    /**
     * Allow users to enable or disable analytics
     *
     * @param optIn true allows collection of analytics information
     */
    synchronized private static void setOptIn(boolean optIn) {
        Timber.i("setOptIn(): from %s to %s", sOptIn, optIn);
        sOptIn = optIn;
        sAnalytics.flush();
        sAnalytics.getConfig().setEnabled(optIn);
        sAnalytics.performSamplingElection();
        Timber.d("setOptIn() optIn / sAnalytics.config().enabled(): %s/%s", sOptIn, sAnalytics.getConfig().isEnabled());
    }


    /**
     * Determine whether we are disabled or not
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean getOptIn() {
        Timber.d("getOptIn() status: %s", sOptIn);
        return sOptIn;
    }


    /**
     * Set the analytics up to log things, goes to hit validator. Experimental.
     *
     * @param dryRun set to true if you want to log analytics hit but not dispatch
     */
    synchronized public static void setDryRun(boolean dryRun) {
        Timber.i("setDryRun(): %s, warning dryRun is experimental", dryRun);
    }


    /**
     * Re-Initialize the analytics provider
     */
    synchronized public static void reInitialize() {

        // send any pending async hits, re-chain default exception handlers and re-init
        Timber.i("reInitialize()");
        sAnalytics.flush();
        sAnalytics = null;
        unInstallDefaultExceptionHandler();
        initialize(AnkiDroidApp.getInstance().getApplicationContext());
    }


    /**
     * Submit a screen for aggregation / analysis.
     * Intended for use to determine if / how features are being used
     *
     * @param object the result of Object.getClass().getSimpleName() will be used as the screen tag
     */
    public static void sendAnalyticsScreenView(Object object) {
        sendAnalyticsScreenView(object.getClass().getSimpleName());
    }


    /**
     * Submit a screen display with a synthetic name for aggregation / analysis
     * Intended for use if your class handles multiple screens you want to track separately
     *
     * @param screenName screenName the name to show in analysis reports
     */
    public static void sendAnalyticsScreenView(String screenName) {
        Timber.d("sendAnalyticsScreenView(): %s", screenName);
        if (!getOptIn()) {
            return;
        }
        sAnalytics.screenView().screenName(screenName).sendAsync();
    }


    /**
     * Send an arbitrary analytics event - these should be noun/verb pairs, e.g. "text to speech", "enabled"
     *
     * @param category the category of event, make your own but use a constant so reporting is good
     * @param action   the action the user performed
     */
    public static void sendAnalyticsEvent(@NonNull String category, @NonNull String action) {
        sendAnalyticsEvent(category, action, Integer.MIN_VALUE, null);
    }


    /**
     * Send a detailed arbitrary analytics event, with noun/verb pairs and extra data if needed
     *
     * @param category the category of event, make your own but use a constant so reporting is good
     * @param action   the action the user performed
     * @param value    A value for the event, Integer.MIN_VALUE signifies caller shouldn't send the value
     * @param label    A label for the event, may be null
     */
    @SuppressWarnings("WeakerAccess")
    public static void sendAnalyticsEvent(@NonNull String category, @NonNull String action, int value, String label) {
        Timber.d("sendAnalyticsEvent() category/action/value/label: %s/%s/%s/%s", category, action, value, label);
        if (!getOptIn()) {
            return;
        }
        EventHit event = sAnalytics.event().eventCategory(category).eventAction(action);
        if (label != null) {
            event.eventLabel(label);
        }
        if (value > Integer.MIN_VALUE) {
            event.eventValue(value);
        }
        event.sendAsync();
    }


    /**
     * Send an exception event out for aggregation/analysis, parsed from the exception information
     *
     * @param t     Throwable to send for analysis
     * @param fatal whether it was fatal or not
     */
    public static void sendAnalyticsException(@NonNull Throwable t, boolean fatal) {
        sendAnalyticsException(getCause(t).toString(), fatal);
    }


    public static Throwable getCause(Throwable t) {
        Throwable cause;
        Throwable result = t;

        while (null != (cause = result.getCause()) && (!result.equals(cause))) {
            result = cause;
        }
        return result;
    }


    /**
     * Send an exception event out for aggregation/analysis
     *
     * @param description API limited to 100 characters, truncated here to 100 if needed
     * @param fatal       whether it was fatal or not
     */
    @SuppressWarnings("WeakerAccess")
    public static void sendAnalyticsException(@NonNull String description, boolean fatal) {
        Timber.d("sendAnalyticsException() description/fatal: %s/%s", description, fatal);
        if (!sOptIn) {
            return;
        }
        sAnalytics.exception().exceptionDescription(description).exceptionFatal(fatal).sendAsync();
    }


    protected static boolean canGetDefaultUserAgent() {
        // #5502 - getDefaultUserAgent starts a WebView. We can't have two WebViews with the same data directory.
        // But ACRA starts an :acra process which does not terminate when AnkiDroid is restarted. https://crbug.com/558377

        // if we're not under the ACRA process then we're fine to initialize a WebView
        if (!ACRA.isACRASenderServiceProcess()) {
            return true;
        }

        // If we have a custom data directory, then the crash will not occur.
        return WebViewDebugging.hasSetDataDirectory();
    }


    /**
     * An Android-specific device config generator. Without this it's "Desktop" and unknown for all hardware.
     * It is interesting to us what devices people use though (for instance: is Amazon Kindle support worth it?
     * Is anyone still using e-ink devices? How many people are on tablets? ChromeOS?)
     */
    private static class AndroidDefaultRequest extends DefaultRequest {
        private DefaultRequest setAndroidRequestParameters(Context context) {

            // Are we running on really large screens or small screens? Send raw screen size
            try {
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                Display display = wm.getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                this.screenResolution(size.x + "x" + size.y);
            } catch (RuntimeException e) {
                // nothing much to do here, it means we couldn't get WindowManager
            }

            // We can have up to 20 of these - there might be other things we want to know
            // but simply seeing what hardware we are running on should be useful
            this.customDimension(1, Build.VERSION.RELEASE); // systemVersion, e.g. "7.1.1"  for Android 7.1.1
            this.customDimension(2, Build.BRAND); // brand e.g. "OnePlus"
            this.customDimension(3, Build.MODEL); // model e.g. "ONEPLUS A6013" for the 6T
            this.customDimension(4, Build.BOARD); // deviceId e.g. "sdm845" for the 6T

            // This is important for google to auto-fingerprint us for default reporting
            // It is not possible to set operating system explicitly, there is no API or analytics parameter for it
            // Instead they respond that they auto-parse User-Agent strings for analytics attribution
            // For maximum analytics built-in report compatibility we will send the official WebView User-Agent string
            try {
                if (canGetDefaultUserAgent()) {
                    this.userAgent(WebSettings.getDefaultUserAgent(context));
                } else {
                    this.userAgent(System.getProperty("http.agent"));
                }
            } catch (RuntimeException e) {
                // Catch RuntimeException as WebView initialization blows up in unpredictable ways
                // but analytics should never be a show-stopper
                this.userAgent(System.getProperty("http.agent"));
            }

            return this;
        }
    }

    public static boolean isEnabled() {
        SharedPreferences userPrefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        return userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false);
    }



    public static class Category {
        public static final String SYNC = "Sync";
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE) // TOOD: Make this package-protected
    public static void resetForTests() {
        sAnalytics = null;
    }
}
