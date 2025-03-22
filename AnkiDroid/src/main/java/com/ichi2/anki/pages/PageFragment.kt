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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.themes.Themes
import timber.log.Timber
import kotlin.reflect.KClass

/**
 * Base class for displaying Anki HTML pages
 */
@Suppress("LeakingThis")
open class PageFragment(
    @LayoutRes contentLayoutId: Int = R.layout.page_fragment,
) : Fragment(contentLayoutId),
    PostRequestHandler {
    lateinit var webView: WebView
    private val server = AnkiServer(this).also { it.start() }

    /**
     * A loading indicator for the page. May be shown before the WebView is loaded to
     * stop flickering
     *
     * @exception IllegalStateException if accessed before [onViewCreated]
     */
    val pageLoadingIndicator: CircularProgressIndicator
        get() = requireView().findViewById(R.id.page_loading)

    /**
     * Override this to set a custom [WebViewClient] to the page.
     * This is called in [onViewCreated].
     *
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    protected open fun onCreateWebViewClient(savedInstanceState: Bundle?) = PageWebViewClient()

    protected open fun onWebViewCreated(webView: WebView) { }

    /**
     * When the webview calls `BridgeCommand("foo")`, the PageFragment execute `bridgeCommands["foo"]`.
     * By default, only bridge command is allowed, subclasses must redefine it if they expect bridge commands.
     */
    open val bridgeCommands: Map<String, () -> Unit> = mapOf()

    /**
     * Ensures that [pageWebViewClient] can receive `bridgeCommand` requests and execute the command from [bridgeCommands].
     */
    private fun setupBridgeCommand(pageWebViewClient: PageWebViewClient) {
        if (bridgeCommands.isEmpty()) {
            return
        }
        webView.addJavascriptInterface(
            object : Object() {
                @JavascriptInterface
                fun bridgeCommandImpl(request: String) {
                    bridgeCommands.orEmpty().getOrDefault(request) {
                        Timber.d("Unknown request received %s", request)
                    }()
                }
            },
            "bridgeCommandInterface",
        )
        pageWebViewClient.onPageFinishedCallbacks.add { webView ->
            webView.evaluateJavascript(
                "bridgeCommand = function(request){ bridgeCommandInterface.bridgeCommandImpl(request); };",
            ) {}
        }
    }

    @CallSuper
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        val pageWebViewClient = onCreateWebViewClient(savedInstanceState)
        webView =
            view.findViewById<WebView>(R.id.webview).apply {
                with(settings) {
                    javaScriptEnabled = true
                    displayZoomControls = false
                    builtInZoomControls = true
                    setSupportZoom(true)
                }
                webViewClient = pageWebViewClient
                webChromeClient = PageChromeClient()
            }
        setupBridgeCommand(pageWebViewClient)
        onWebViewCreated(webView)

        val arguments = requireArguments()
        val path = requireNotNull(arguments.getString(PATH_ARG_KEY)) { "'$PATH_ARG_KEY' missing" }
        val title = arguments.getString(TITLE_ARG_KEY)

        val nightMode = if (Themes.currentTheme.isNightMode) "#night" else ""
        val url = "${server.baseUrl()}$path$nightMode".toUri()
        Timber.i("Loading $url")
        webView.loadUrl(url.toString())

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            if (title != null) {
                setTitle(title)
            }
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override suspend fun handlePostRequest(
        uri: String,
        bytes: ByteArray,
    ): ByteArray {
        val methodName =
            if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
                uri.substring(AnkiServer.ANKI_PREFIX.length)
            } else {
                throw IllegalArgumentException("unhandled request: $uri")
            }
        return activity.handleUiPostRequest(methodName, bytes)
            ?: handleCollectionPostRequest(methodName, bytes)
            ?: throw IllegalArgumentException("unhandled method: $methodName")
    }

    companion object {
        const val PATH_ARG_KEY = "path"
        const val TITLE_ARG_KEY = "title"

        fun getIntent(
            context: Context,
            path: String,
            title: String? = null,
            clazz: KClass<out PageFragment> = PageFragment::class,
        ): Intent {
            val arguments = bundleOf(PATH_ARG_KEY to path, TITLE_ARG_KEY to title)
            return SingleFragmentActivity.getIntent(context, clazz, arguments)
        }
    }
}
