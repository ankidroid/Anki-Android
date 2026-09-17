// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.content.Context
import android.print.PrintDocumentAdapter
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.core.view.ancestors
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

    private enum class WebViewState {
        ACTIVE,
        DESTROYED_RECOVERABLE,
        DESTROYED_TERMINAL,
    }

    private var webView: WebView = createWebView()

    private var webViewState = WebViewState.ACTIVE

    var scrollBars: Int = webView.scrollBarStyle
        set(value) {
            field = value
            if (warnIfNotActive("scrollBars setter")) return
            webView.scrollBarStyle = value
        }

    @NeedsTest("Verify background color applies to inner WebView")
    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(color)
        if (warnIfNotActive("setBackgroundColor")) return
        webView.setBackgroundColor(color)
    }

    protected open fun createWebView() = WebView(context)

    init {
        addView(webView, webViewLayoutParams)
    }

    // Not guarded when not [WebViewState.ACTIVE]: callers rarely use these after destroy and a no-op is impossible.
    val settings: WebSettings get() = webView.settings

    @Suppress("DEPRECATION")
    val scale get() = webView.scale

    @MainThread
    fun zoomBy(zoomFactor: Float) = webView.zoomBy(zoomFactor)

    @MainThread
    fun setWebViewClient(webViewClient: SafeWebViewClient) {
        if (warnIfNotActive("setWebViewClient")) return
        webViewClient.setOnRenderProcessGoneListener(this)
        webView.webViewClient = webViewClient
    }

    @MainThread
    fun setWebChromeClient(webChromeClient: WebChromeClient) {
        if (warnIfNotActive("setWebChromeClient")) return
        webView.webChromeClient = webChromeClient
    }

    @MainThread
    fun evaluateJavascript(
        script: String,
        resultCallback: ((String) -> Unit)? = null,
    ) {
        if (warnIfNotActive("evaluateJavascript")) return
        webView.evaluateJavascript(script) { callback ->
            resultCallback?.invoke(callback)
        }
    }

    @MainThread
    fun addJavascriptInterface(
        javascriptInterface: Any,
        name: String,
    ) {
        if (warnIfNotActive("addJavascriptInterface")) return
        webView.addJavascriptInterface(javascriptInterface, name)
    }

    @MainThread
    fun loadUrl(url: String) {
        if (warnIfNotActive("loadUrl")) return
        webView.loadUrl(url)
    }

    @MainThread
    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        if (warnIfNotActive("loadDataWithBaseURL")) return
        webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    fun setAcceptThirdPartyCookies(accept: Boolean) {
        if (warnIfNotActive("setAcceptThirdPartyCookies")) return
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, accept)
    }

    @MainThread
    fun goBack() {
        if (warnIfNotActive("goBack")) return
        webView.goBack()
    }

    @MainThread
    fun pageUp(): Boolean {
        if (warnIfNotActive("pageUp")) return false
        return webView.pageUp(false)
    }

    @MainThread
    fun pageDown(): Boolean {
        if (warnIfNotActive("pageDown")) return false
        return webView.pageDown(false)
    }

    @MainThread
    fun reload() {
        if (warnIfNotActive("reload")) return
        webView.reload()
    }

    @MainThread
    fun focusOnWebView() {
        if (warnIfNotActive("focusOnWebView")) return
        webView.requestFocus()
    }

    @MainThread
    fun destroy() {
        when (webViewState) {
            WebViewState.ACTIVE -> webView.destroy()
            // Crash cleanup already destroyed the native WebView; promote to terminal so reattach
            // does not recover.
            WebViewState.DESTROYED_RECOVERABLE -> Unit
            WebViewState.DESTROYED_TERMINAL -> {
                Timber.w("destroy called after WebView was destroyed")
                return
            }
        }
        webViewState = WebViewState.DESTROYED_TERMINAL
    }

    @MainThread
    fun scrollVerticallyBy(y: Int) {
        if (warnIfNotActive("scrollVerticallyBy")) return
        if (webView.canScrollVertically(y)) {
            webView.scrollBy(0, y)
        }
    }

    @MainThread
    fun createPrintDocumentAdapter(documentName: String): PrintDocumentAdapter? {
        if (warnIfNotActive("createPrintDocumentAdapter")) return null
        return webView.createPrintDocumentAdapter(documentName)
    }

    override fun setOnScrollChangeListener(l: OnScrollChangeListener?) {
        if (warnIfNotActive("setOnScrollChangeListener")) return
        webView.setOnScrollChangeListener(l)
    }

    /**
     * Replaces the terminated inner [WebView] after a render process crash when recreation is possible.
     *
     * When recreation is skipped (layout not in a usable fragment/window state), the terminated
     * [WebView] is destroyed and not replaced; further calls on this layout are guarded until
     * [onAttachedToWindow] recreates the inner [WebView] from [WebViewState.DESTROYED_RECOVERABLE].
     */
    override fun onRenderProcessGone(webView: WebView) {
        if (webView !== this.webView) {
            destroyWebView(webView)
            return
        }

        // Always remove and destroy the terminated WebView first. Android requires this even when
        // we skip recreation (e.g. fragment view already gone). See:
        // https://developer.android.com/develop/ui/views/layout/webapps/handle-termination
        removeView(webView)
        webView.destroy()

        val fragment =
            try {
                findFragment<Fragment>()
            } catch (e: IllegalStateException) {
                Timber.w(e, "skipping WebView recreation; layout is not attached to a Fragment")
                webViewState = WebViewState.DESTROYED_RECOVERABLE
                return
            }
        if (fragment.view == null) {
            Timber.w("skipping WebView recreation; fragment view is gone")
            webViewState = WebViewState.DESTROYED_RECOVERABLE
            return
        }
        if (!isAttachedToWindow) {
            Timber.w("skipping WebView recreation; layout is not attached to a window")
            webViewState = WebViewState.DESTROYED_RECOVERABLE
            return
        }

        recreateInnerWebView(fragment)
    }

    private fun recreateInnerWebView(fragment: Fragment) {
        val previousWebView = this.webView
        if (previousWebView.parent == this) {
            removeView(previousWebView)
        }
        this.webView = createWebView()
        webViewState = WebViewState.ACTIVE
        addView(this.webView, webViewLayoutParams)
        (fragment as? OnWebViewRecreatedListener)?.onWebViewRecreated(this.webView)
    }

    private fun tryRecoverDestroyedWebViewIfNeeded(fragment: Fragment) {
        if (webViewState != WebViewState.DESTROYED_RECOVERABLE) return
        val fragmentView = fragment.view ?: return
        if (this === fragmentView || ancestors.any { it === fragmentView }) {
            recreateInnerWebView(fragment)
        } else {
            Timber.w("skipping WebView recovery; layout is not in the fragment's current view hierarchy")
        }
    }

    private fun warnIfNotActive(methodName: String): Boolean {
        if (webViewState != WebViewState.ACTIVE) {
            Timber.w("$methodName called after WebView was destroyed")
            return true
        }
        return false
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
                if (webViewState == WebViewState.DESTROYED_RECOVERABLE) {
                    Timber.w(e, "SafeWebViewLayout not attached to a Fragment; skipping WebView recovery")
                    return
                }
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
            return
        }

        tryRecoverDestroyedWebViewIfNeeded(fragment)
    }

    /**
     * Destroys the internal state of the wrapped [webView]
     *
     * No other methods may be called on this WebView after destroy
     */
    @MainThread
    fun safeDestroy() {
        when (webViewState) {
            WebViewState.ACTIVE -> {
                destroyWebView(webView, this)
                // Mark destroyed even if [destroyWebView] partially failed; using a partially torn-down
                // WebView is unsafe.
            }
            // Crash cleanup already destroyed the native WebView; promote to terminal so reattach
            // does not recover.
            WebViewState.DESTROYED_RECOVERABLE -> Unit
            WebViewState.DESTROYED_TERMINAL -> {
                Timber.w("safeDestroy called after WebView was destroyed")
                return
            }
        }
        webViewState = WebViewState.DESTROYED_TERMINAL
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
