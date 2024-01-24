/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.ichi2.anki.R
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
        result: JsResult?
    ): Boolean {
        try {
            AlertDialog.Builder(ContextThemeWrapper(view.context, R.style.AlertDialogStyle)).show {
                message?.let { message(text = message) }
                positiveButton(R.string.dialog_ok) { result?.confirm() }
                setOnCancelListener { result?.cancel() }
            }
        } catch (e: WindowManager.BadTokenException) {
            Timber.w("onJsAlert", e)
            return false
        }

        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String?,
        message: String?,
        result: JsResult?
    ): Boolean {
        try {
            AlertDialog.Builder(ContextThemeWrapper(view.context, R.style.AlertDialogStyle)).show {
                message?.let { message(text = message) }
                positiveButton(R.string.dialog_ok) { result?.confirm() }
                negativeButton(R.string.dialog_cancel) { result?.cancel() }
                cancelable(false)
            }
        } catch (e: WindowManager.BadTokenException) {
            Timber.w("onJsConfirm", e)
            return false // unhandled - shown in WebView
        }
        return true
    }
}
