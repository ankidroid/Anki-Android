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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.ichi2.anki.R
import com.ichi2.anki.pages.PagesActivity.Companion.HOST_NAME
import com.ichi2.themes.Themes
import timber.log.Timber

/**
 * Base class for displaying Anki HTML pages
 */
abstract class PageFragment : Fragment() {
    @get:StringRes
    /** Title string resource of the page */
    abstract val title: Int
    abstract val pageName: String
    abstract var webViewClient: PageWebViewClient
    abstract var webChromeClient: PageChromeClient

    lateinit var webView: WebView

    val port
        get() = (requireActivity() as PagesActivity).port

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.page_fragment, container, false)

        webView = view.findViewById<WebView>(R.id.pagesWebview).apply {
            settings.javaScriptEnabled = true
            webViewClient = this@PageFragment.webViewClient
            webChromeClient = this@PageFragment.webChromeClient
        }
        val nightMode = if (Themes.currentTheme.isNightMode) "#night" else ""
        val url = "http://$HOST_NAME:$port/$pageName.html$nightMode"

        Timber.i("Loading $url")
        webView.loadUrl(url)

        return view
    }
}
