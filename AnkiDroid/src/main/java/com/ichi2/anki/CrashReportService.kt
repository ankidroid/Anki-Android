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
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import com.ichi2.anki.analytics.AnkiDroidCrashReportDialog
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.analytics.UsageAnalytics.sendAnalyticsException
import com.ichi2.anki.exception.ManuallyReportedException
import com.ichi2.anki.exception.UserSubmittedException
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.WebViewDebugging.setDataDirectorySuffix
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.*
import org.acra.sender.HttpSender
import timber.log.Timber
import java.util.*
import kotlin.collections.HashMap

object CrashReportService {

    // ACRA constants used for stored preferences
    const val FEEDBACK_REPORT_KEY = "reportErrorMode"
    const val FEEDBACK_REPORT_ASK = "2"
    const val FEEDBACK_REPORT_NEVER = "1"
    const val FEEDBACK_REPORT_ALWAYS = "0"

    /** Our ACRA configurations, initialized during Application.onCreate()  */
    private lateinit var mAcraCoreConfigBuilder: CoreConfigurationBuilder
    private lateinit var mApplication: Application
    private const val WEBVIEW_VER_NAME = "WEBVIEW_VER_NAME"
    private const val MIN_INTERVAL_MS = 60000
    private const val EXCEPTION_MESSAGE = "Exception report sent by user manually"

    /**
     * Temporary method to access the CoreConfigurationBuilder until all classes that require access
     *  to the CoreConfigurationBuilder are migrated to kotlin.
     */
    @JvmStatic
    @KotlinCleanup("once EVERY class using this method gets migrated to kotlin remove it and expose mAcraCoreConfigBuilder with a private setter")
    fun getAcraCoreConfigBuilder(): CoreConfigurationBuilder {
        return mAcraCoreConfigBuilder
    }

    /**
     * Use this method to initialize the ACRA CoreConfigurationBuilder in Application.onCreate().
     * The ACRA process needs a WebView for optimal UsageAnalytics values but it can't have the same
     * data directory. Analytics falls back to a sensible default if this is not set.
     */
    @JvmStatic
    fun initialize(application: Application) {
        mApplication = application
        // Setup logging and crash reporting
        mAcraCoreConfigBuilder = CoreConfigurationBuilder(application)
            .setBuildConfigClass(org.acra.dialog.BuildConfig::class.java)
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
            .setLogcatArguments("-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S")
        mAcraCoreConfigBuilder.getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
            .setReportDialogClass(AnkiDroidCrashReportDialog::class.java)
            .setResCommentPrompt(R.string.empty_string)
            .setResTitle(R.string.feedback_title)
            .setResText(R.string.feedback_default_text)
            .setResPositiveButtonText(R.string.feedback_report)
            .setResIcon(R.drawable.logo_star_144dp)
            .setEnabled(true)
        mAcraCoreConfigBuilder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder::class.java)
            .setHttpMethod(HttpSender.Method.PUT)
            .setUri(BuildConfig.ACRA_URL)
            .setEnabled(true)
        mAcraCoreConfigBuilder.getPluginConfigurationBuilder(ToastConfigurationBuilder::class.java)
            .setResText(R.string.feedback_auto_toast_text)
            .setEnabled(true)
        mAcraCoreConfigBuilder.getPluginConfigurationBuilder(LimiterConfigurationBuilder::class.java)
            .setExceptionClassLimit(1000)
            .setStacktraceLimit(1)
            .setEnabled(true)
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
    @JvmStatic
    fun setAcraReportingMode(value: String) {
        val editor = AnkiDroidApp.getSharedPrefs(mApplication).edit()
        // Set the ACRA disable value
        if (value == FEEDBACK_REPORT_NEVER) {
            editor.putBoolean(ACRA.PREF_DISABLE_ACRA, true)
        } else {
            editor.putBoolean(ACRA.PREF_DISABLE_ACRA, false)
            // Switch between auto-report via toast and manual report via dialog
            val builder: CoreConfigurationBuilder = mAcraCoreConfigBuilder
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
     * Turns ACRA reporting off completely and persists it to shared prefs
     * But expands logcat search in case developer manually re-enables it
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    @JvmStatic
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setDebugACRAConfig(prefs: SharedPreferences) {
        // Disable crash reporting
        setAcraReportingMode(FEEDBACK_REPORT_NEVER)
        prefs.edit { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_NEVER) }
        // Use a wider logcat filter in case crash reporting manually re-enabled
        val logcatArgs = arrayOf("-t", "300", "-v", "long", "ACRA:S")
        setAcraConfigBuilder(mAcraCoreConfigBuilder.setLogcatArguments(*logcatArgs))
    }

    /**
     * Puts ACRA Reporting mode into user-specified mode, with default of "ask first"
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    @JvmStatic
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setProductionACRAConfig(prefs: SharedPreferences) {
        // Enable or disable crash reporting based on user setting
        setAcraReportingMode(prefs.getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK)!!)
    }

    /**
     * Set the ACRA ConfigurationBuilder and **re-initialize the ACRA system** with the contents
     * @param acraCoreConfigBuilder the full ACRA config to initialize ACRA with
     */
    private fun setAcraConfigBuilder(acraCoreConfigBuilder: CoreConfigurationBuilder) {
        this.mAcraCoreConfigBuilder = acraCoreConfigBuilder
        ACRA.init(mApplication, acraCoreConfigBuilder)
        ACRA.getErrorReporter().putCustomData(WEBVIEW_VER_NAME, fetchWebViewInformation()[WEBVIEW_VER_NAME])
        ACRA.getErrorReporter().putCustomData("WEBVIEW_VER_CODE", fetchWebViewInformation()["WEBVIEW_VER_CODE"])
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
    @JvmStatic
    fun sendExceptionReport(message: String?, origin: String?) {
        sendExceptionReport(ManuallyReportedException(message), origin, null)
    }

    @JvmStatic
    fun sendExceptionReport(e: Throwable, origin: String?) {
        sendExceptionReport(e, origin, null)
    }

    @JvmStatic
    fun sendExceptionReport(e: Throwable, origin: String?, additionalInfo: String?) {
        sendExceptionReport(e, origin, additionalInfo, false)
    }

    @JvmStatic
    fun sendExceptionReport(e: Throwable, origin: String?, additionalInfo: String?, onlyIfSilent: Boolean) {
        sendAnalyticsException(e, false)
        AnkiDroidApp.sSentExceptionReportHack = true
        if (onlyIfSilent) {
            val reportMode = AnkiDroidApp.getSharedPrefs(mApplication.applicationContext).getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK)
            if (FEEDBACK_REPORT_ALWAYS != reportMode) {
                Timber.i("sendExceptionReport - onlyIfSilent true, but ACRA is not 'always accept'. Skipping report send.")
                return
            }
        }
        ACRA.getErrorReporter().putCustomData("origin", origin)
        ACRA.getErrorReporter().putCustomData("additionalInfo", additionalInfo)
        ACRA.getErrorReporter().handleException(e)
    }

    @JvmStatic
    fun isProperServiceProcess(): Boolean {
        return ACRA.isACRASenderServiceProcess()
    }

    @JvmStatic
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
    @JvmStatic
    fun deleteACRALimiterData(context: Context) {
        try {
            LimiterData().store(context)
        } catch (e: Exception) {
            Timber.w(e, "Unable to clear ACRA limiter data")
        }
    }

    @JvmStatic
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
    @JvmStatic
    fun sendReport(ankiActivity: AnkiActivity): Boolean {
        val preferences = AnkiDroidApp.getSharedPrefs(ankiActivity)
        val reportMode = preferences.getString(FEEDBACK_REPORT_KEY, "")
        return if (FEEDBACK_REPORT_NEVER == reportMode) {
            preferences.edit { putBoolean(ACRA.PREF_DISABLE_ACRA, false) }
            mAcraCoreConfigBuilder
                .getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
                .setEnabled(true)
            val sendStatus = sendReportFor(ankiActivity)
            mAcraCoreConfigBuilder
                .getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
                .setEnabled(false)
            preferences.edit { putBoolean(ACRA.PREF_DISABLE_ACRA, true) }
            sendStatus
        } else {
            sendReportFor(ankiActivity)
        }
    }

    private fun sendReportFor(activity: AnkiActivity): Boolean {
        val currentTimestamp = activity.col.time.intTimeMS()
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
    // Upstream issue for access to field/method: https://github.com/ACRA/acra/issues/843
    private fun getTimestampOfLastReport(activity: AnkiActivity): Long {
        try {
            // The ACRA LimiterData holds a timestamp for every generated report
            val limiterData = LimiterData.load(activity)
            val limiterDataListField = limiterData.javaClass.getDeclaredField("list")
            limiterDataListField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val limiterDataList = limiterDataListField[limiterData] as List<LimiterData.ReportMetadata>
            for (report in limiterDataList) {
                if (report.exceptionClass != UserSubmittedException::class.java.name) {
                    continue
                }
                val timestampMethod = report.javaClass.getDeclaredMethod("getTimestamp")
                timestampMethod.isAccessible = true
                val timestamp = timestampMethod.invoke(report) as Calendar
                // Limiter ensures there is only one report for the class, so if we found it, return it
                return timestamp.timeInMillis
            }
        } catch (e: Exception) {
            Timber.w(e, "Unexpected exception checking for recent reports")
        }
        return -1
    }
}
