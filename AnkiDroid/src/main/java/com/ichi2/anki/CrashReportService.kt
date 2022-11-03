/****************************************************************************************
 * Copyright (c) 2022 lukstbit <lukstbit@users.noreply.github.com>                      *
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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import com.ichi2.anki.analytics.AnkiDroidCrashReportDialog
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.analytics.UsageAnalytics.sendAnalyticsException
import com.ichi2.anki.exception.ManuallyReportedException
import com.ichi2.anki.exception.UserSubmittedException
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.WebViewDebugging.setDataDirectorySuffix
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.*
import org.acra.sender.HttpSender
import timber.log.Timber
import java.util.*

object CrashReportService {

    // ACRA constants used for stored preferences
    const val FEEDBACK_REPORT_KEY = "reportErrorMode"
    const val FEEDBACK_REPORT_ASK = "2"
    const val FEEDBACK_REPORT_NEVER = "1"
    const val FEEDBACK_REPORT_ALWAYS = "0"

    /** Our ACRA configurations, initialized during Application.onCreate()  */
    @JvmStatic
    private var logcatArgs = arrayOf(
        "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S"
    )
    @JvmStatic
    private var dialogEnabled = true
    @JvmStatic
    private lateinit var toastText: String
    @JvmStatic
    lateinit var acraCoreConfigBuilder: CoreConfigurationBuilder
        private set
    private lateinit var mApplication: Application
    private const val WEBVIEW_VER_NAME = "WEBVIEW_VER_NAME"
    private const val MIN_INTERVAL_MS = 60000
    private const val EXCEPTION_MESSAGE = "Exception report sent by user manually"

    private enum class ToastType(@StringRes private val toastMessageRes: Int) {
        AUTO_TOAST(R.string.feedback_auto_toast_text),
        MANUAL_TOAST(R.string.feedback_for_manual_toast_text);

        fun getToastMessage(context: Context) = context.getString(toastMessageRes)
    }

    private fun createAcraCoreConfigBuilder(): CoreConfigurationBuilder {
        val builder = CoreConfigurationBuilder()
            .withBuildConfigClass(com.ichi2.anki.BuildConfig::class.java) // AnkiDroid BuildConfig - Acrarium#319
            .withExcludeMatchingSharedPreferencesKeys("username", "hkey")
            .withSharedPreferencesName("acra")
            .withReportContent(
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
            .withLogcatArguments(*logcatArgs)
            .withPluginConfigurations(
                DialogConfigurationBuilder()
                    .withReportDialogClass(AnkiDroidCrashReportDialog::class.java)
                    .withCommentPrompt(mApplication.getString(R.string.empty_string))
                    .withTitle(mApplication.getString(R.string.feedback_title))
                    .withText(mApplication.getString(R.string.feedback_default_text))
                    .withPositiveButtonText(mApplication.getString(R.string.feedback_report))
                    .withResIcon(R.drawable.logo_star_144dp)
                    .withEnabled(dialogEnabled)
                    .build(),
                HttpSenderConfigurationBuilder()
                    .withHttpMethod(HttpSender.Method.PUT)
                    .withUri(BuildConfig.ACRA_URL)
                    .withEnabled(true)
                    .build(),
                ToastConfigurationBuilder()
                    .withText(toastText)
                    .withEnabled(true)
                    .build(),
                LimiterConfigurationBuilder()
                    .withExceptionClassLimit(1000)
                    .withStacktraceLimit(1)
                    .withDeleteReportsOnAppUpdate(true)
                    .withResetLimitsOnAppUpdate(true)
                    .withEnabled(true)
                    .build()
            )
        ACRA.init(mApplication, builder)
        acraCoreConfigBuilder = builder
        fetchWebViewInformation().let {
            ACRA.errorReporter.putCustomData(WEBVIEW_VER_NAME, it[WEBVIEW_VER_NAME] ?: "")
            ACRA.errorReporter.putCustomData("WEBVIEW_VER_CODE", it["WEBVIEW_VER_CODE"] ?: "")
        }
        return builder
    }

    /**
     * Use this method to initialize the ACRA CoreConfigurationBuilder in Application.onCreate().
     * The ACRA process needs a WebView for optimal UsageAnalytics values but it can't have the same
     * data directory. Analytics falls back to a sensible default if this is not set.
     */
    @JvmStatic
    fun initialize(application: Application) {
        mApplication = application
        // FIXME ACRA needs to reinitialize after language is changed, but with the new language
        //   this is difficult because the Application (AnkiDroidApp) does not change it's baseContext
        //   perhaps a solution could be to change AnkiDroidApp to have a context wrapper that it sets
        //   as baseContext, and that wrapper allows a resources/configuration update, then
        //   in GeneralSettingsFragment for the language dialog change listener, the context wrapper
        //   could be updated directly with the new locale code so that calling getString on would fetch
        //   the new language string ?
        toastText = ToastType.AUTO_TOAST.getToastMessage(mApplication)

        // Setup logging and crash reporting
        if (BuildConfig.DEBUG) {
            setDebugACRAConfig(AnkiDroidApp.getSharedPrefs(mApplication))
        } else {
            setProductionACRAConfig(AnkiDroidApp.getSharedPrefs(mApplication))
        }
        if (ACRA.isACRASenderServiceProcess() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                setDataDirectorySuffix("acra")
            } catch (e: java.lang.Exception) {
                Timber.w(e, "Failed to set WebView data directory")
            }
        }
    }

    /**
     * Set the reporting mode for ACRA based on the value of the FEEDBACK_REPORT_KEY preference
     * @param value value of FEEDBACK_REPORT_KEY preference
     */
    fun setAcraReportingMode(value: String) {
        AnkiDroidApp.getSharedPrefs(mApplication).edit {
            // Set the ACRA disable value
            if (value == FEEDBACK_REPORT_NEVER) {
                putBoolean(ACRA.PREF_DISABLE_ACRA, true)
            } else {
                putBoolean(ACRA.PREF_DISABLE_ACRA, false)
                // Switch between auto-report via toast and manual report via dialog
                if (value == FEEDBACK_REPORT_ALWAYS) {
                    dialogEnabled = false
                    toastText = ToastType.AUTO_TOAST.getToastMessage(mApplication)
                } else if (value == FEEDBACK_REPORT_ASK) {
                    createAcraCoreConfigBuilder()
                    dialogEnabled = true
                    toastText = ToastType.MANUAL_TOAST.getToastMessage(mApplication)
                }
                createAcraCoreConfigBuilder()
            }
        }
    }

    /**
     * Turns ACRA reporting off completely and persists it to shared prefs
     * But expands logcat search in case developer manually re-enables it
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setDebugACRAConfig(prefs: SharedPreferences) {
        // Disable crash reporting
        setAcraReportingMode(FEEDBACK_REPORT_NEVER)
        prefs.edit { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_NEVER) }
        // Use a wider logcat filter in case crash reporting manually re-enabled
        logcatArgs = arrayOf("-t", "300", "-v", "long", "ACRA:S")
        createAcraCoreConfigBuilder()
    }

    /**
     * Puts ACRA Reporting mode into user-specified mode, with default of "ask first"
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setProductionACRAConfig(prefs: SharedPreferences) {
        // Enable or disable crash reporting based on user setting
        setAcraReportingMode(prefs.getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK)!!)
    }

    private fun fetchWebViewInformation(): HashMap<String, String> {
        val webViewInfo = hashMapOf<String, String>()
        webViewInfo[WEBVIEW_VER_NAME] = ""
        webViewInfo["WEBVIEW_VER_CODE"] = ""
        try {
            val pi = WebViewCompat.getCurrentWebViewPackage(mApplication)
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

    /** Used when we don't have an exception to throw, but we know something is wrong and want to diagnose it  */
    fun sendExceptionReport(message: String?, origin: String?) {
        sendExceptionReport(ManuallyReportedException(message), origin, null)
    }

    fun sendExceptionReport(e: Throwable, origin: String?) {
        sendExceptionReport(e, origin, null)
    }

    fun sendExceptionReport(e: Throwable, origin: String?, additionalInfo: String?) {
        sendExceptionReport(e, origin, additionalInfo, false)
    }

    fun sendExceptionReport(e: Throwable, origin: String?, additionalInfo: String?, onlyIfSilent: Boolean) {
        sendAnalyticsException(e, false)
        AnkiDroidApp.sentExceptionReportHack = true
        val reportMode = AnkiDroidApp.getSharedPrefs(mApplication.applicationContext).getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK)
        if (onlyIfSilent) {
            if (FEEDBACK_REPORT_ALWAYS != reportMode) {
                Timber.i("sendExceptionReport - onlyIfSilent true, but ACRA is not 'always accept'. Skipping report send.")
                return
            }
        }
        if (FEEDBACK_REPORT_NEVER != reportMode) {
            ACRA.errorReporter.putCustomData("origin", origin ?: "")
            ACRA.errorReporter.putCustomData("additionalInfo", additionalInfo ?: "")
            ACRA.errorReporter.handleException(e)
        }
    }

    fun isProperServiceProcess(): Boolean {
        return ACRA.isACRASenderServiceProcess()
    }

    fun isAcraEnabled(context: Context, defaultValue: Boolean): Boolean {
        if (!AnkiDroidApp.getSharedPrefs(context).contains(ACRA.PREF_DISABLE_ACRA)) {
            // we shouldn't use defaultValue below, as it would be inverted which complicated understanding.
            Timber.w("No default value for '%s'", ACRA.PREF_DISABLE_ACRA)
            return defaultValue
        }
        return !AnkiDroidApp.getSharedPrefs(context).getBoolean(ACRA.PREF_DISABLE_ACRA, true)
    }

    /**
     * If you want to make sure that the next exception of any time is posted, you need to clear limiter data
     *
     * @param context the context leading to the directory with ACRA limiter data
     */
    fun deleteACRALimiterData(context: Context) {
        try {
            LimiterData().store(context)
        } catch (e: Exception) {
            Timber.w(e, "Unable to clear ACRA limiter data")
        }
    }

    fun onPreferenceChanged(ctx: Context, newValue: String) {
        setAcraReportingMode(newValue)
        // If the user changed error reporting, make sure future reports have a chance to post
        deleteACRALimiterData(ctx)
        // We also need to re-chain our UncaughtExceptionHandlers
        UsageAnalytics.reInitialize()
    }

    /**
     * @return the status of the report, true if the report was sent, false if the report is already
     *  submitted
     */
    fun sendReport(ankiActivity: AnkiActivity): Boolean {
        val preferences = AnkiDroidApp.getSharedPrefs(ankiActivity)
        val reportMode = preferences.getString(FEEDBACK_REPORT_KEY, "")
        return if (FEEDBACK_REPORT_NEVER == reportMode) {
            preferences.edit { putBoolean(ACRA.PREF_DISABLE_ACRA, false) }
            toastText = ToastType.MANUAL_TOAST.getToastMessage(mApplication)
            createAcraCoreConfigBuilder()
            val sendStatus = sendReportFor(ankiActivity)
            dialogEnabled = false
            createAcraCoreConfigBuilder()
            preferences.edit { putBoolean(ACRA.PREF_DISABLE_ACRA, true) }
            sendStatus
        } else {
            sendReportFor(ankiActivity)
        }
    }

    private fun sendReportFor(activity: AnkiActivity): Boolean {
        val currentTimestamp = TimeManager.time.intTimeMS()
        val lastReportTimestamp = getTimestampOfLastReport(activity)
        return if (currentTimestamp - lastReportTimestamp > MIN_INTERVAL_MS) {
            deleteACRALimiterData(activity)
            sendExceptionReport(
                UserSubmittedException(EXCEPTION_MESSAGE),
                "AnkiDroidApp.HelpDialog"
            )
            true
        } else {
            false
        }
    }

    /**
     * Check the ACRA report store and return the timestamp of the last report.
     *
     * @param activity the Activity used for Context access when interrogating ACRA reports
     * @return the timestamp of the most recent report, or -1 if no reports at all
     */
    private fun getTimestampOfLastReport(activity: AnkiActivity): Long {
        return LimiterData.load(activity).reportMetadata
            .filter { it.exceptionClass == UserSubmittedException::class.java.name }
            .maxOfOrNull { it.timestamp?.timeInMillis ?: -1L } ?: -1L
    }
}
