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
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import com.ichi2.anki.databinding.InfoBinding
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.themes.Themes
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
class Info :
    AnkiActivity(),
    BaseSnackbarBuilderProvider {
    private lateinit var binding: InfoBinding

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = binding.buttons
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        binding = InfoBinding.inflate(layoutInflater)
        val res = resources
        val type = intent.getIntExtra(TYPE_EXTRA, TYPE_NEW_VERSION)
        // If the page crashes, we do not want to display it again (#7135 maybe)
        if (type == TYPE_NEW_VERSION) {
            val prefs = this.baseContext.sharedPrefs()
            InitialActivity.setUpgradedToLatestVersion(prefs)
        }
        setViewBinding(binding)
        val mainView = findViewById<View>(android.R.id.content)
        enableToolbar(mainView)
        binding.donate.setOnClickListener { openUrl(R.string.link_opencollective_donate) }
        title = "$appName v$pkgVersionName"
        binding.webView.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(
                    view: WebView,
                    progress: Int,
                ) {
                    // Hide the progress indicator when the page has finished loaded
                    if (progress == 100) {
                        mainView.findViewById<View>(R.id.progress_bar).visibility = View.GONE
                    }
                }
            }
        binding.leftButton.run {
            if (canOpenMarketUri()) {
                setText(R.string.info_rate)
                setOnClickListener {
                    tryOpenIntent(
                        this@Info,
                        AnkiDroidApp.getMarketIntent(this@Info),
                    )
                }
            } else {
                visibility = View.GONE
            }
        }
        val onBackPressedCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (binding.webView.canGoBack()) binding.webView.goBack()
                }
            }
        // Apply Theme colors
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground, android.R.attr.textColor))
        val backgroundColor = typedArray.getColor(0, -1)
        val textColor = typedArray.getColor(1, -1).toRGBHex()

        val anchorTextThemeColor = Themes.getColorFromAttr(this, android.R.attr.colorAccent)
        val anchorTextColor = anchorTextThemeColor.toRGBHex()

        binding.webView.setBackgroundColor(backgroundColor)
        binding.webView.settings.allowFileAccess = true
        binding.webView.settings.allowContentAccess = true
        setRenderWorkaround(this)
        when (type) {
            TYPE_NEW_VERSION -> {
                binding.rightButton.run {
                    text = res.getString(R.string.dialog_continue)
                    setOnClickListener { close() }
                }
                val background = backgroundColor.toRGBHex()
                binding.webView.loadUrl("/android_asset/changelog.html")
                binding.webView.settings.javaScriptEnabled = true
                binding.webView.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String,
                        ) {
                        /* The order of below javascript code must not change (this order works both in debug and release mode)
                         *  or else it will break in any one mode.
                         */
                            @Suppress("ktlint:standard:max-line-length")
                            binding.webView.loadUrl(
                                """javascript:document.body.style.setProperty("color", "$textColor");
                                    x=document.getElementsByTagName("a");
                                    for(i=0; i<x.length; i++){
                                      x[i].style.color="$anchorTextColor";
                                    }
                                    document.getElementsByTagName("h1")[0].style.color="$textColor";
                                    x=document.getElementsByTagName("h2");
                                    for(i=0; i<x.length; i++){
                                      x[i].style.color="#E37068";
                                    }
                                    document.body.style.setProperty("background", "$background");""",
                            )
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            // Excludes the url that are opened inside the changelog.html
                            // and redirect the user to the browser
                            val url = request?.url?.toString() ?: return false
                            if (url == CHANGE_LOG_URL) {
                                return false
                            }
                            this@Info.openUrl(url)
                            return true
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean,
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            onBackPressedCallback.isEnabled = view != null && view.canGoBack()
                        }
                    }
            }
            else -> finish()
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun close() {
        setResult(RESULT_OK)
        finishWithAnimation()
    }

    private fun canOpenMarketUri(): Boolean =
        try {
            canOpenIntent(this, AnkiDroidApp.getMarketIntent(this))
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    private fun finishWithAnimation() {
        finish()
    }

    companion object {
        const val TYPE_EXTRA = "infoType"
        const val TYPE_NEW_VERSION = 2
    }
}
