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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.ichi2.utils.toRGBHex
import timber.log.Timber

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.let { webView ->
            // 14194: background-color should be set before onPageFinished to stop a white flash
            val bgColor = MaterialColors.getColor(webView, android.R.attr.colorBackground).toRGBHex()
            webView.evaluateJavascript("""document.body.style.setProperty("background-color", "$bgColor", "important")""") {
                Timber.v("backgroundColor set")
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { webView ->
            /** [PageFragment.webView] is invisible by default to avoid flashes while
             * the page is loaded, and can be made visible again after it finishes loading */
            webView.isVisible = true
        }
    }
}
