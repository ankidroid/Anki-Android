// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.utils.cancelable
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber

open class PageChromeClient : WebChromeClient() {
    override fun onJsAlert(
        view: WebView,
        url: String?,
        message: String?,
        result: JsResult?,
    ): Boolean {
        Timber.d("Displaying alert() dialog")
        try {
            AlertDialog.Builder(view.context).show {
                message?.let { message(text = message) }
                positiveButton(R.string.dialog_ok) { result?.confirm() }
                setOnCancelListener { result?.cancel() }
            }
        } catch (e: IllegalStateException) {
            // window count is over max!!
            Timber.w(e, "onJsAlert: message ignored")
            // we want to know what went wrong
            CrashReportService.sendExceptionReport("$url: $message", "onJsAlert:windowCount")
            return false
        } catch (e: WindowManager.BadTokenException) {
            Timber.w(e, "onJsAlert")
            return false
        }

        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String?,
        message: String?,
        result: JsResult?,
    ): Boolean {
        Timber.d("Displaying confirm() dialog")
        try {
            AlertDialog.Builder(view.context).show {
                message?.let { message(text = message) }
                positiveButton(R.string.dialog_ok) { result?.confirm() }
                negativeButton(R.string.dialog_cancel) { result?.cancel() }
                cancelable(false)
            }
        } catch (e: WindowManager.BadTokenException) {
            Timber.w(e, "onJsConfirm")
            return false // unhandled - shown in WebView
        }
        return true
    }
}
