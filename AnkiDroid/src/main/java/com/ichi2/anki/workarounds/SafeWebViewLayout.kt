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

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import com.ichi2.anki.BuildConfig
import timber.log.Timber

open class SafeWebViewLayout :
    FrameLayout,
    OnRenderProcessGoneListener {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var webView: WebView = createWebView()

    var scrollBars: Int = webView.scrollBarStyle
        set(value) {
            webView.scrollBarStyle = value
            field = value
        }

    protected open fun createWebView() = WebView(context)

    init {
        addView(webView, webViewLayoutParams)
    }

    val settings: WebSettings get() = webView.settings

    @Suppress("DEPRECATION")
    val scale get() = webView.scale

    fun setWebViewClient(webViewClient: SafeWebViewClient) {
        webViewClient.setOnRenderProcessGoneListener(this)
        webView.webViewClient = webViewClient
    }

    fun setWebChromeClient(webChromeClient: WebChromeClient) {
        webView.webChromeClient = webChromeClient
    }

    fun evaluateJavascript(
        script: String,
        resultCallback: ((String) -> Unit)? = null,
    ) = webView.evaluateJavascript(script) { callback ->
        resultCallback?.invoke(callback)
    }

    fun addJavascriptInterface(
        javascriptInterface: Any,
        name: String,
    ) = webView.addJavascriptInterface(javascriptInterface, name)

    fun loadUrl(url: String) = webView.loadUrl(url)

    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) = webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)

    fun setAcceptThirdPartyCookies(accept: Boolean) = CookieManager.getInstance().setAcceptThirdPartyCookies(webView, accept)

    fun goBack() = webView.goBack()

    fun pageUp() = webView.pageUp(false)

    fun pageDown() = webView.pageDown(false)

    fun reload() = webView.reload()

    fun focusOnWebView() = webView.requestFocus()

    fun destroy() = webView.destroy()

    fun createPrintDocumentAdapter(documentName: String) = webView.createPrintDocumentAdapter(documentName)

    override fun setOnScrollChangeListener(l: OnScrollChangeListener?) = webView.setOnScrollChangeListener(l)

    override fun onRenderProcessGone(webView: WebView) {
        removeView(webView)
        webView.destroy()

        this.webView = createWebView()
        addView(this.webView, webViewLayoutParams)

        val fragment = findFragment<Fragment>()
        (fragment as? OnWebViewRecreatedListener)?.onWebViewRecreated(this.webView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val fragment =
            try {
                findFragment<Fragment>()
            } catch (e: IllegalStateException) {
                // findFragment throws if the View is not attached to a Fragment.
                // This can happen in scenarios like Android Studio previews
                // or if the view is added directly to an Activity.
                if (BuildConfig.DEBUG && !isInEditMode) {
                    throw IllegalStateException(
                        "SafeWebViewLayout must be used within a Fragment",
                        e,
                    )
                } else {
                    Timber.w(e, "SafeWebViewLayout not attached to a Fragment")
                }
                return
            }

        if (fragment !is OnWebViewRecreatedListener) {
            if (BuildConfig.DEBUG && !isInEditMode) {
                throw IllegalStateException(
                    "Fragment '${fragment::class.simpleName}' must implement OnWebViewRecreatedListener",
                )
            } else {
                Timber.w("Fragment does not implement OnWebViewRecreatedListener. WebView recreation may not be handled")
            }
        }
    }

    companion object {
        private val webViewLayoutParams =
            ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )
    }
}

fun interface OnWebViewRecreatedListener {
    fun onWebViewRecreated(webView: WebView)
}
