/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.AndroidRuntimeException
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import timber.log.Timber

internal const val OLDEST_WORKING_WEBVIEW_VERSION_CODE = 443000000L
internal const val OLDEST_WORKING_WEBVIEW_VERSION = 90

/**
 * Shows a dialog if the current WebView version is older than the last supported version.
 */
fun checkWebviewVersion(activity: AnkiActivity): Boolean {
    val userVisibleCode = getChromeLikeWebViewVersionIfOutdated(activity) ?: return false

    // Provide guidance to the user if the WebView is outdated
    val webviewPackageInfo = getAndroidSystemWebViewPackageInfo(activity.packageManager)
    val legacyWebViewPackageInfo = getLegacyWebViewPackageInfo(activity.packageManager)
    // TODO modify the alert dialog text to handle the usage of developer builds for system WebView
    if (legacyWebViewPackageInfo != null) {
        Timber.w("WebView is outdated. %s: %s", legacyWebViewPackageInfo.packageName, legacyWebViewPackageInfo.versionName)
        showOutdatedWebViewDialog(activity, userVisibleCode, R.string.link_legacy_webview_update)
    } else {
        Timber.w("WebView is outdated. %s: %s", webviewPackageInfo?.packageName, webviewPackageInfo?.versionName)
        showOutdatedWebViewDialog(activity, userVisibleCode, R.string.link_webview_update)
    }
    return true
}

@MainThread
fun getWebviewUserAgent(context: Context): String? {
    try {
        return WebView(context).settings.userAgentString
    } catch (e: AndroidRuntimeException) {
        // MissingWebViewPackageException is not public
        if (e.cause.toString().contains("MissingWebViewPackageException")) {
            Timber.w(e, "MissingWebViewPackageException")
            return null // WebView not installed - don't log a crash report
        }
        CrashReportService.sendExceptionReport(e, "WebViewUtils", "some issue occurred while extracting webview user agent")
    } catch (e: Throwable) {
        CrashReportService.sendExceptionReport(e, "WebViewUtils", "some issue occurred while extracting webview user agent")
    }
    return null
}

/*
 * Returns a Chrome-like WebView version if it is outdated, otherwise null if
 * cannot be determined at all or if okay
 */
private fun getChromeLikeWebViewVersionIfOutdated(activity: AnkiActivity): Int? {
    // If we cannot get the package information at all, return null
    val webviewPackageInfo = getAndroidSystemWebViewPackageInfo(activity.packageManager) ?: return null
    val webviewVersion =
        webviewPackageInfo.versionName ?: run {
            Timber.w("Failed to obtain WebView version")
            return null
        }
    val versionCode = PackageInfoCompat.getLongVersionCode(webviewPackageInfo)
    return checkWebViewVersionComponents(webviewPackageInfo.packageName, webviewVersion, versionCode, getWebviewUserAgent(activity))
}

@VisibleForTesting
fun checkWebViewVersionComponents(
    packageName: String,
    webviewVersion: String,
    versionCode: Long,
    userAgent: String?,
): Int? {
    // Sometimes the webview version code appears too old, and the package name does as well,
    // but it's a webview that advertises modern capabilities via User-Agent in "Chrome" section
    // Our warning is purely advisory, so, let's let those through if User-Agent looks okay
    userAgent?.let {
        val chromeRegex = """Chrome/(\d+)""".toRegex()
        val matchResult = chromeRegex.find(userAgent)?.groupValues?.get(1)
        matchResult?.toInt()?.let {
            if (it >= OLDEST_WORKING_WEBVIEW_VERSION) {
                // If the User-Agent says we are modern, trust it and skip further checks.
                return null
            } else {
                // If the User-Agent is explicitly below the floor, return it immediately.
                return it
            }
        }
    }
    // Checking the version code works for most webview packages
    if (versionCode >= OLDEST_WORKING_WEBVIEW_VERSION_CODE) {
        Timber.d(
            "WebView is up to date. %s: %s(%s)",
            packageName,
            webviewVersion,
            versionCode.toString(),
        )
        return null
    }
    return webviewVersion.split('.').firstOrNull()?.toIntOrNull()
}

private fun showOutdatedWebViewDialog(
    activity: AnkiActivity,
    installedVersion: Int,
    @StringRes learnMoreUrl: Int,
) {
    AlertDialog.Builder(activity).show {
        setMessage(activity.getString(R.string.webview_update_message, installedVersion, OLDEST_WORKING_WEBVIEW_VERSION))
        setPositiveButton(R.string.scoped_storage_learn_more) { _, _ ->
            activity.openUrl(learnMoreUrl)
        }
    }
}

private fun getLegacyWebViewPackageInfo(packageManager: PackageManager): PackageInfo? =
    try {
        packageManager.getPackageInfo("com.android.webview", 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

/**
 * Returns a [PackageInfo] from the current system WebView, or `null` if unavailable
 */
private fun getAndroidSystemWebViewPackageInfo(packageManager: PackageManager): PackageInfo? {
    fun getPackage(packageName: String): PackageInfo? =
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    // The WebView is called com.android.webview by default.
    // Partner devices which ship with Google applications ship the Google-specific version
    // of the WebView called com.google.android.webview.
    // https://issues.chromium.org/issues/40419837#comment10

    return getPackage("com.google.android.webview")
        ?: getPackage("com.android.webview") // com.android.webview is used on API 24
}

/**
 * Enables debugging of web contents (HTML / CSS / JavaScript)
 * loaded into any WebViews of this application. This flag can be enabled
 * in order to facilitate debugging of web layouts and JavaScript
 * code running inside WebViews. Please refer to WebView documentation
 * for the debugging guide.
 *
 * In WebView 113.0.5656.0 and later, this is enabled automatically if the
 * app is declared as
 * [`android:debuggable="true"`](https://developer.android.com/guide/topics/manifest/application-element#debug)
 * in its manifest; otherwise, the
 * default is {@code false}.
 *
 * Enabling web contents debugging allows the state of any WebView in the
 * app to be inspected and modified by the user via adb. This is a security
 * liability and should not be enabled in production builds of apps unless
 * this is an explicitly intended use of the app. More info on
 * [secure debug settings](https://developer.android.com/topic/security/risks/android-debuggable)
 *
 * @param enabled whether to enable web contents debugging
 */
fun setWebContentsDebuggingEnabled(enabled: Boolean) =
    try {
        WebView.setWebContentsDebuggingEnabled(enabled)
    } catch (e: Exception) {
        // android.util.AndroidRuntimeException: android.webkit.WebViewFactory$MissingWebViewPackageException: Failed to load WebView provider: No WebView installed
        Timber.w(e, "setWebContentsDebuggingEnabled")
    }
