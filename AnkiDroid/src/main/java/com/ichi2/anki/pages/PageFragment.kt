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

import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import timber.log.Timber

/**
 * Base class for displaying Anki HTML pages
 */
@Suppress("LeakingThis")
abstract class PageFragment : Fragment(R.layout.page_fragment), PostRequestHandler {
    abstract val title: String
    abstract val pageName: String

    lateinit var webView: WebView
    private val server = AnkiServer(this).also { it.start() }

    /**
     * Override this to set a custom [WebViewClient] to the page.
     * This is called in [onViewCreated].
     *
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    protected open fun onCreateWebViewClient(savedInstanceState: Bundle?) = PageWebViewClient()

    /**
     * Override this to set a custom [WebChromeClient] to the page.
     * This is called in [onViewCreated].
     *
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    protected open fun onCreateWebChromeClient(savedInstanceState: Bundle?) = PageChromeClient()

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        webView = view.findViewById<WebView>(R.id.webview).apply {
            with(settings) {
                javaScriptEnabled = true
                displayZoomControls = false
                builtInZoomControls = true
                setSupportZoom(true)
            }
            webViewClient = onCreateWebViewClient(savedInstanceState)
            webChromeClient = onCreateWebChromeClient(savedInstanceState)
        }
        val nightMode = if (Themes.currentTheme.isNightMode) "#night" else ""
        val url = server.baseUrl() + "backend/web/$pageName.html$nightMode"

        Timber.i("Loading $url")
        webView.loadUrl(url)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = this@PageFragment.title
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray {
        val methodName = if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
            uri.substring(AnkiServer.ANKI_PREFIX.length)
        } else {
            throw IllegalArgumentException("unhandled request: $uri")
        }
        return requireActivity().handleUiPostRequest(methodName, bytes)
            ?: handleCollectionPostRequest(methodName, bytes)
            ?: throw IllegalArgumentException("unhandled method: $methodName")
    }
}
