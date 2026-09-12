/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.workarounds

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import timber.log.Timber

fun interface OnRenderProcessGoneListener {
    fun onRenderProcessGone(webView: WebView)
}

open class SafeWebViewClient : WebViewClient() {
    private var onRenderProcessGoneListener: OnRenderProcessGoneListener? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        Timber.w("onRenderProcessGone (didCrash %b)", detail.didCrash())
        onRenderProcessGoneListener?.onRenderProcessGone(view)
            ?: throw IllegalStateException("onRenderProcessGoneListener must not be null")
        return true
    }

    fun setOnRenderProcessGoneListener(onRenderProcessGoneListener: OnRenderProcessGoneListener) {
        this.onRenderProcessGoneListener = onRenderProcessGoneListener
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        if (isLoopbackHost(error)) {
            Timber.w("Proceeding through SSL error for local WebView URL: %s", error?.url)
            handler?.proceed()
            return
        }
        super.onReceivedSslError(view, handler, error)
    }

    private fun isLoopbackHost(error: SslError?): Boolean {
        val host = (error?.url ?: return false).toUri().host?.lowercase() ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }
}
