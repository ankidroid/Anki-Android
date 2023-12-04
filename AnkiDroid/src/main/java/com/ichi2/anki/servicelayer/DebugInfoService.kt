/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import android.content.Context
import android.os.Build
import android.webkit.WebView
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CrashReportService
import com.ichi2.utils.VersionUtils.pkgVersionName
import org.acra.util.Installation

object DebugInfoService {
    fun getDebugInfo(info: Context): String {
        val dbV2Enabled = true
        val webviewUserAgent = getWebviewUserAgent(info)
        return """
               AnkiDroid Version = $pkgVersionName (${BuildConfig.GIT_COMMIT_HASH})
               
               Android Version = ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
               
               ProductFlavor = ${BuildConfig.FLAVOR}
               
               Manufacturer = ${Build.MANUFACTURER}
               
               Model = ${Build.MODEL}
               
               Hardware = ${Build.HARDWARE}
               
               Webview User Agent = $webviewUserAgent
               
               ACRA UUID = ${Installation.id(info)}
               
               Crash Reports Enabled = ${isSendingCrashReports(info)}
               
               DatabaseV2 Enabled = $dbV2Enabled
               
        """.trimIndent()
    }

    private fun getWebviewUserAgent(context: Context): String? {
        try {
            return WebView(context).settings.userAgentString
        } catch (e: Throwable) {
            CrashReportService.sendExceptionReport(e, "Info::copyDebugInfo()", "some issue occurred while extracting webview user agent")
        }
        return null
    }

    private fun isSendingCrashReports(context: Context): Boolean {
        return CrashReportService.isAcraEnabled(context, false)
    }
}
