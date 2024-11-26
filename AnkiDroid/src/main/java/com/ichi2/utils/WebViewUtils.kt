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
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import timber.log.Timber

internal const val OLDEST_WORKING_WEBVIEW_VERSION_CODE = 386507305L
internal const val OLDEST_WORKING_WEBVIEW_VERSION = 77

/**
 * Shows a dialog if the current WebView version is older than the last supported version.
 */
fun checkWebviewVersion(packageManager: PackageManager, activity: AnkiActivity) {
    val webviewPackageInfo = getAndroidSystemWebViewPackageInfo(packageManager) ?: return
    val webviewVersion = webviewPackageInfo.versionName ?: run {
        Timber.w("Failed to obtain WebView version")
        return
    }
    val versionCode = PackageInfoCompat.getLongVersionCode(webviewPackageInfo)
    // TODO modify the alert dialog text to handle the usage of developer builds for system WebView
    val userVisibleCode = runCatching {
        webviewVersion.split(".")[0].toInt()
    }.getOrNull() ?: 0
    if (versionCode >= OLDEST_WORKING_WEBVIEW_VERSION_CODE) {
        Timber.d(
            "WebView is up to date. %s: %s(%s)",
            webviewPackageInfo.packageName,
            webviewVersion,
            versionCode.toString()
        )
        return
    }

    val legacyWebViewPackageInfo = getLegacyWebViewPackageInfo(packageManager)
    if (legacyWebViewPackageInfo != null) {
        Timber.w("WebView is outdated. %s: %s", legacyWebViewPackageInfo.packageName, legacyWebViewPackageInfo.versionName)
        showOutdatedWebViewDialog(activity, userVisibleCode, activity.getString(R.string.link_legacy_webview_update))
    } else {
        Timber.w("WebView is outdated. %s: %s", webviewPackageInfo.packageName, webviewPackageInfo.versionName)
        showOutdatedWebViewDialog(activity, userVisibleCode, activity.getString(R.string.link_webview_update))
    }
}

@MainThread
fun getWebviewUserAgent(context: Context): String? {
    try {
        return WebView(context).settings.userAgentString
    } catch (e: Throwable) {
        CrashReportService.sendExceptionReport(e, "WebViewUtils", "some issue occurred while extracting webview user agent")
    }
    return null
}

private fun showOutdatedWebViewDialog(activity: AnkiActivity, installedVersion: Int, learnMoreUrl: String) {
    AlertDialog.Builder(activity).show {
        setMessage(activity.getString(R.string.webview_update_message, installedVersion, OLDEST_WORKING_WEBVIEW_VERSION))
        setPositiveButton(R.string.scoped_storage_learn_more) { _, _ ->
            activity.openUrl(learnMoreUrl)
        }
    }
}

private fun getLegacyWebViewPackageInfo(packageManager: PackageManager): PackageInfo? {
    return try {
        packageManager.getPackageInfo("com.android.webview", 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

/**
 * Returns a [PackageInfo] from the current system WebView, or `null` if unavailable
 */
private fun getAndroidSystemWebViewPackageInfo(packageManager: PackageManager): PackageInfo? {
    fun getPackage(packageName: String): PackageInfo? {
        return try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    // The WebView is called com.android.webview by default.
    // Partner devices which ship with Google applications ship the Google-specific version
    // of the WebView called com.google.android.webview.
    // https://issues.chromium.org/issues/40419837#comment10

    return getPackage("com.google.android.webview")
        ?: getPackage("com.android.webview") // com.android.webview is used on API 24
}
