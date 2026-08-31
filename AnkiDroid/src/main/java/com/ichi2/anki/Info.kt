/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2015 Tim Rae <perceptualchaos2@gmail.com>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.widget.ThemeUtils
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.IntentUtil.canOpenIntent
import com.ichi2.utils.IntentUtil.tryOpenIntent
import com.ichi2.utils.VersionUtils.appName
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.utils.toRGBHex
import timber.log.Timber

private const val CHANGE_LOG_URL = "https://docs.ankidroid.org/changelog.html"

/**
 * Shows an about box, which is a small HTML page.
 */
class Info : AnkiActivity() {
    private var mWebView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        val res = resources
        val type = intent.getIntExtra(TYPE_EXTRA, TYPE_NEW_VERSION)
        // If the page crashes, we do not want to display it again (#7135 maybe)
        if (type == TYPE_NEW_VERSION) {
            val prefs = this.baseContext.sharedPrefs()
            InitialActivity.setUpgradedToLatestVersion(prefs)
        }
        setContentView(R.layout.info)
        val mainView = findViewById<View>(android.R.id.content)
        enableToolbar(mainView)
        if (BuildConfig.SHOW_DONATE_LINKS) {
            findViewById<View>(R.id.info_donate).setOnClickListener {
                openUrl(Uri.parse(getString(R.string.link_opencollective_donate)))
            }
        } else {
            findViewById<View>(R.id.info_donate).visibility = View.GONE
        }
        title = "$appName v$pkgVersionName"
        mWebView = findViewById(R.id.info)
        mWebView!!.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                // Hide the progress indicator when the page has finished loaded
                if (progress == 100) {
                    mainView.findViewById<View>(R.id.progress_bar).visibility = View.GONE
                }
            }
        }
        findViewById<Button>(R.id.left_button).run {
            if (canOpenMarketUri()) {
                setText(R.string.info_rate)
                setOnClickListener {
                    tryOpenIntent(
                        this@Info,
                        AnkiDroidApp.getMarketIntent(this@Info)
                    )
                }
            } else {
                visibility = View.GONE
            }
        }

        // Apply Theme colors
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground, android.R.attr.textColor))
        val backgroundColor = typedArray.getColor(0, -1)
        val textColor = typedArray.getColor(1, -1).toRGBHex()

        val anchorTextThemeColor = ThemeUtils.getThemeAttrColor(this, android.R.attr.colorAccent)
        val anchorTextColor = anchorTextThemeColor.toRGBHex()

        mWebView!!.setBackgroundColor(backgroundColor)
        setRenderWorkaround(this)
        when (type) {
            TYPE_NEW_VERSION -> {
                findViewById<Button>(R.id.right_button).run {
                    text = res.getString(R.string.dialog_continue)
                    setOnClickListener { close() }
                }
                val background = backgroundColor.toRGBHex()
                mWebView!!.loadUrl("file:///android_asset/changelog.html")
                mWebView!!.settings.javaScriptEnabled = true
                mWebView!!.webViewClient = ChangelogWebViewClient(textColor, anchorTextColor, background)
            }
            else -> finishWithoutAnimation()
        }
    }

    /**
     * Changelog navigation for both API 24+ [WebResourceRequest] and API 21–23 String overloads.
     * Returns true to keep the load in-app (blocked or opened externally); false to let WebView load it.
     */
    fun handleChangelogUrl(uri: Uri): Boolean {
        if (shouldBlockChangelogDonateNavigation(BuildConfig.SHOW_DONATE_LINKS, uri.host)) {
            return true
        }
        val url = uri.toString()
        if (url == CHANGE_LOG_URL) {
            return false
        }
        if (!AdaptionUtil.hasWebBrowser(this)) {
            // snackbar can't be used here as it's a webview and lack coordinator layout
            UIUtils.showThemedToast(
                this,
                resources.getString(R.string.no_browser_notification) + url,
                false
            )
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        return true
    }

    inner class ChangelogWebViewClient(
        private val textColor: String = "",
        private val anchorTextColor: String = "",
        private val background: String = ""
    ) : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            /* The order of below javascript code must not change (this order works both in debug and release mode)
                     *  or else it will break in any one mode.
                     */
            // ES5 only: minSdk 21 WebViews may not support forEach/arrows/replaceWith/spread.
            var themeJs = "javascript:document.body.style.setProperty(\"color\", \"" + textColor + "\");" +
                "x=document.getElementsByTagName(\"a\"); for(i=0;i<x.length;i++){x[i].style.color=\"" + anchorTextColor + "\";}" +
                "document.getElementsByTagName(\"h1\")[0].style.color=\"" + textColor + "\";" +
                "x=document.getElementsByTagName(\"h2\"); for(i=0;i<x.length;i++){x[i].style.color=\"#E37068\";}" +
                "document.body.style.setProperty(\"background\", \"" + background + "\");"
            if (!BuildConfig.SHOW_DONATE_LINKS) {
                // Unwrap Open Collective anchors, keeping the text (live NodeList: iterate backwards).
                themeJs += "x=document.getElementsByTagName(\"a\"); for(i=x.length-1;i>=0;i--){" +
                    "if(x[i].href.indexOf(\"opencollective.com\")!==-1){" +
                    "while(x[i].firstChild){x[i].parentNode.insertBefore(x[i].firstChild,x[i]);}" +
                    "x[i].parentNode.removeChild(x[i]);}}"
            }
            mWebView!!.loadUrl(themeJs)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val uri = request?.url ?: return false
            return handleChangelogUrl(uri)
        }

        @Suppress("DEPRECATION") // API 21–23 String overload
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null) {
                return false
            }
            return handleChangelogUrl(Uri.parse(url))
        }
    }

    private fun close() {
        setResult(RESULT_OK)
        finishWithAnimation()
    }

    private fun canOpenMarketUri(): Boolean {
        return try {
            canOpenIntent(this, AnkiDroidApp.getMarketIntent(this))
        } catch (e: Exception) {
            Timber.w(e)
            false
        }
    }

    private fun finishWithAnimation() {
        finishWithAnimation(ActivityTransitionAnimation.Direction.START)
    }

    @Suppress("deprecation") // onBackPressed
    override fun onBackPressed() {
        if (mWebView!!.canGoBack()) {
            mWebView!!.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val TYPE_EXTRA = "infoType"
        const val TYPE_NEW_VERSION = 2

        /**
         * Play hides donate UI; changelog HTML may still contain Open Collective links until JS
         * unwraps them. Swallow those navigations so a JS race cannot open a browser.
         */
        fun shouldBlockChangelogDonateNavigation(showDonateLinks: Boolean, host: String?): Boolean {
            return !showDonateLinks && host?.contains("opencollective.com") == true
        }
    }
}
