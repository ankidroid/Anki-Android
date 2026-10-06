// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.AssetHelper.guessMimeType
import com.ichi2.utils.toRGBHex
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : SafeWebViewClient() {
    /**
     * The URL of the trusted local server.
     *
     * Allows us to distinguish our URLs from URLs served by other origins.
     *
     * Without this, another app could host on localhost, and we could not tell them apart:
     *
     * - http://127.0.0.1:12345/graphs
     * - http://127.0.0.1:22345/graphs
     *
     * SECURITY: Set this via [PageFragment], this must never be inferred from page navigation.
     */
    internal var serverUrl: Uri? = null

    val onPageFinishedCallbacks: MutableList<OnPageFinishedCallback> = mutableListOf()

    private fun isInternalUrl(url: Uri): Boolean =
        serverUrl?.let { url.scheme == it.scheme && url.encodedAuthority == it.encodedAuthority } == true

    /**
     * Keeps bundled routes in this WebView and opens external HTTP(S) main-frame links outside it.
     *
     * WebView skips this callback for POST navigations and app-initiated [WebView.loadUrl] calls.
     */
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url ?: return true
        if (isInternalUrl(url)) {
            return SvelteKitPage.fromPath(url.path.orEmpty()) == null
        }
        if (request.isForMainFrame && url.scheme in listOf("http", "https")) {
            view?.context?.openUrl(url)
        }
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.path
        if (request.method != "GET" || path == null || !isInternalUrl(request.url)) return null
        if (path == "/favicon.png") {
            return WebResourceResponse("image/x-icon", null, ByteArrayInputStream(byteArrayOf()))
        }

        val page = SvelteKitPage.fromPath(path)
        val assetPath =
            if (path.startsWith("/_app/")) {
                "backend/sveltekit/app/${path.substring(6)}"
            } else if (page != null) {
                "backend/sveltekit/index.html"
            } else {
                return null
            }

        try {
            val mimeType = guessMimeType(assetPath)
            val inputStream = view.context.assets.open(assetPath)
            val response = WebResourceResponse(mimeType, null, inputStream)
            if ("immutable" in path) {
                response.responseHeaders = mapOf("Cache-Control" to "max-age=31536000")
            }
            return response
        } catch (_: IOException) {
            Timber.w("Not found %s", assetPath)
        }
        return null
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        view?.let { webView ->
            val bgColor = MaterialColors.getColor(webView, android.R.attr.colorBackground).toRGBHex()
            webView.evaluateAfterDOMContentLoaded(
                """document.body.style.setProperty("background-color", "$bgColor", "important");
                    console.log("Background color set");""",
            )
        }
    }

    /**
     * Shows the WebView after the page is loaded
     *
     * This may be overridden if additional 'screen ready' logic is provided by the backend
     * @see DeckOptions
     */
    open fun onShowWebView(webView: WebView) {
        Timber.v("Displaying WebView")
        webView.isVisible = true
        (webView.parent as? SafeWebViewLayout)?.isVisible = true
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        if (view == null) return
        onPageFinishedCallbacks.map { callback -> callback.onPageFinished(view) }
        /* webView is invisible by default to avoid flashes while
         * the page is loaded, and can be made visible again after it finishes loading */
        onShowWebView(view)
    }
}

fun WebView.evaluateAfterDOMContentLoaded(
    script: String,
    resultCallback: ValueCallback<String>? = null,
) {
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
        resultCallback,
    )
}
