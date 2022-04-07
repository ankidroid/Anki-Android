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
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.libanki.Collection
import com.ichi2.utils.VersionUtils.pkgVersionName
import org.acra.util.Installation
import timber.log.Timber
import java.util.function.Supplier

object DebugInfoService {
    @JvmStatic
    fun getDebugInfo(info: Context, col: Supplier<Collection>): String {
        var schedName = "Not found"
        try {
            schedName = col.get().sched.name
        } catch (e: Throwable) {
            Timber.e(e, "Sched name not found")
        }
        var dbV2Enabled: Boolean? = null
        try {
            dbV2Enabled = col.get().isUsingRustBackend
        } catch (e: Throwable) {
            Timber.w(e, "Unable to detect Rust Backend")
        }
        val webviewUserAgent = getWebviewUserAgent(info)
        return """
               AnkiDroid Version = $pkgVersionName
               
               Android Version = ${Build.VERSION.RELEASE}
               
               Manufacturer = ${Build.MANUFACTURER}
               
               Model = ${Build.MODEL}
               
               Hardware = ${Build.HARDWARE}
               
               Webview User Agent = $webviewUserAgent
               
               ACRA UUID = ${Installation.id(info)}
               
               Scheduler = $schedName
               
               Crash Reports Enabled = ${isSendingCrashReports(info)}
               
               DatabaseV2 Enabled = $dbV2Enabled
               
        """.trimIndent()
    }

    private fun getWebviewUserAgent(context: Context): String? {
        try {
            return WebView(context).settings.userAgentString
        } catch (e: Throwable) {
            AnkiDroidApp.sendExceptionReport(e, "Info::copyDebugInfo()", "some issue occurred while extracting webview user agent")
        }
        return null
    }

    private fun isSendingCrashReports(context: Context): Boolean {
        return AnkiDroidApp.isAcraEnabled(context, false)
    }
}
