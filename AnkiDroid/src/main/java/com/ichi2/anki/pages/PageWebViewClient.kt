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

import android.graphics.Bitmap
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.utils.AssetHelper
import com.ichi2.utils.toRGBHex
import timber.log.Timber
import java.io.IOException

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : WebViewClient() {

    /** Wait for the provided promise to complete before showing the WebView */
    open val promiseToWaitFor: String? = null

    var onPageFinishedCallback: OnPageFinishedCallback? = null

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val path = request.url.path
        if (request.method == "GET" && path?.startsWith("/backend/web") == true) {
            try {
                val mime = AssetHelper.guessMimeType(path)
                val inputStream = view.context.assets.open(path.substring(1))
                return WebResourceResponse(mime, null, inputStream)
            } catch (_: IOException) {
                Timber.w("%s not found", path)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.let { webView ->
            val bgColor = MaterialColors.getColor(webView, android.R.attr.colorBackground).toRGBHex()
            webView.evaluateAfterDOMContentLoaded(
                """document.body.style.setProperty("background-color", "$bgColor", "important");
                    console.log("Background color set");"""
            )
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null) return
        onPageFinishedCallback?.onPageFinished(view)
        if (promiseToWaitFor == null) {
            /** [PageFragment.webView] is invisible by default to avoid flashes while
             * the page is loaded, and can be made visible again after it finishes loading */
            Timber.v("displaying WebView")
            view.isVisible = true
        } else {
            view.evaluateJavascript("""$promiseToWaitFor.then(() => { console.log("page-fully-loaded:"); window.location.href = "page-fully-loaded:" } )""") {
                Timber.v("waiting for '$promiseToWaitFor' before displaying WebView")
            }
        }
    }

    @Suppress("DEPRECATION") // still needed for API 23
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (view == null || url == null) return super.shouldOverrideUrlLoading(view, url)
        if (handleUrl(view, url)) {
            return true
        }
        return super.shouldOverrideUrlLoading(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (view == null || request == null) return super.shouldOverrideUrlLoading(view, request)
        if (handleUrl(view, request.url.toString())) {
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    private fun handleUrl(view: WebView, url: String): Boolean {
        if (url == "page-fully-loaded:") {
            Timber.v("displaying WebView after '$promiseToWaitFor' executed")
            view.isVisible = true
            return true
        }
        return false
    }
}

fun WebView.evaluateAfterDOMContentLoaded(script: String, resultCallback: ValueCallback<String>? = null) {
    evaluateJavascript(
        """
                var codeToRun = function() { 
                    $script
                }
                
                if (document.readyState === "loading") {
                  document.addEventListener("DOMContentLoaded", codeToRun);
                } else {
                  codeToRun();
                }
        """.trimIndent(),
        resultCallback
    )
}
