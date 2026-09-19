// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.runCatchingWithReport
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

    @NeedsTest("Verify background color applies to inner WebView")
    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(color)
        webView.setBackgroundColor(color)
    }

    protected open fun createWebView() = WebView(context)

    init {
        addView(webView, webViewLayoutParams)
    }

    val settings: WebSettings get() = webView.settings

    @Suppress("DEPRECATION")
    val scale get() = webView.scale

    @MainThread
    fun setWebViewClient(webViewClient: SafeWebViewClient) {
        webViewClient.setOnRenderProcessGoneListener(this)
        webView.webViewClient = webViewClient
    }

    @MainThread
    fun setWebChromeClient(webChromeClient: WebChromeClient) {
        webView.webChromeClient = webChromeClient
    }

    @MainThread
    fun evaluateJavascript(
        script: String,
        resultCallback: ((String) -> Unit)? = null,
    ) = webView.evaluateJavascript(script) { callback ->
        resultCallback?.invoke(callback)
    }

    @MainThread
    fun addJavascriptInterface(
        javascriptInterface: Any,
        name: String,
    ) = webView.addJavascriptInterface(javascriptInterface, name)

    @MainThread
    fun loadUrl(url: String) = webView.loadUrl(url)

    @MainThread
    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) = webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)

    fun setAcceptThirdPartyCookies(accept: Boolean) = CookieManager.getInstance().setAcceptThirdPartyCookies(webView, accept)

    @MainThread
    fun goBack() = webView.goBack()

    @MainThread
    fun pageUp() = webView.pageUp(false)

    @MainThread
    fun pageDown() = webView.pageDown(false)

    @MainThread
    fun reload() = webView.reload()

    @MainThread
    fun focusOnWebView() = webView.requestFocus()

    @MainThread
    fun destroy() = webView.destroy()

    @MainThread
    fun scrollVerticallyBy(y: Int) {
        if (webView.canScrollVertically(y)) {
            webView.scrollBy(0, y)
        }
    }

    @MainThread
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

    /**
     * Destroys the internal state of the wrapped [webView]
     *
     * No other methods may be called on this WebView after destroy
     */
    @MainThread
    fun safeDestroy() {
        destroyWebView(webView, this)
    }

    companion object {
        private val webViewLayoutParams =
            ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )

        /**
         * Clean up and destroy the [webView] to prevent memory leaks.
         *
         * Stops any loading, clears callbacks, and removes the view from its [parent].
         */
        fun destroyWebView(
            webView: WebView?,
            parent: ViewGroup? = webView?.parent as? ViewGroup,
        ) {
            Timber.d("Destroying WebView")

            runCatchingWithReport("safeDestroy", onlyIfSilent = true) {
                webView?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    webChromeClient = null
                    // remove listeners this class exposes
                    setOnScrollChangeListener(null)
                }

                // remove WebView from parent view
                parent?.removeView(webView) ?: Timber.w("WebView parent is null")
            }

            // attempt to run destroy() even if the above fails
            runCatchingWithReport("safeDestroy", onlyIfSilent = true) {
                webView?.destroy()
            }
        }
    }
}

/**
 * Listener for [SafeWebViewLayout.onRenderProcessGone], called after the internal [WebView] is
 * replaced due to a render process crash or the system killing it to free memory.
 *
 * Any [Fragment] containing a [SafeWebViewLayout] **must** implement this interface. In debug builds,
 * a missing implementation will throw at [SafeWebViewLayout.onAttachedToWindow()]
 *
 * @see SafeWebViewLayout.onRenderProcessGone
 */
fun interface OnWebViewRecreatedListener {
    /**
     * Reconfigures a [WebView] after [SafeWebViewLayout.onRenderProcessGone] has replaced
     * the old instance. Implementations must reapply all clients, settings and content
     * that were configured on the original [WebView], as [SafeWebViewLayout] only handles
     * the structural replacement and cannot reapply app level configuration.
     *
     * To manually trigger this path, call `webViewLayout.loadUrl("chrome://crash")`.
     * Automated testing requires an instrumented test; a unit test is not sufficient.
     *
     * @param webView the new [WebView], already attached to [SafeWebViewLayout]
     */
    fun onWebViewRecreated(webView: WebView)
}
