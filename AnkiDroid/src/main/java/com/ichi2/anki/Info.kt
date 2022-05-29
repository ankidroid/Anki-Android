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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.annotation.VisibleForTesting
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.servicelayer.DebugInfoService.getDebugInfo
import com.ichi2.utils.IntentUtil.canOpenIntent
import com.ichi2.utils.IntentUtil.tryOpenIntent
import com.ichi2.utils.VersionUtils.appName
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import org.intellij.lang.annotations.Language
import timber.log.Timber

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
        Timber.d("onCreate()")
        super.onCreate(savedInstanceState)
        val res = resources
        val type = intent.getIntExtra(TYPE_EXTRA, TYPE_ABOUT)
        // If the page crashes, we do not want to display it again (#7135 maybe)
        if (type == TYPE_NEW_VERSION) {
            val prefs = AnkiDroidApp.getSharedPrefs(this.baseContext)
            InitialActivity.setUpgradedToLatestVersion(prefs)
        }
        setContentView(R.layout.info)
        val mainView = findViewById<View>(android.R.id.content)
        enableToolbar(mainView)
        findViewById<View>(R.id.info_donate).setOnClickListener { openUrl(Uri.parse(getString(R.string.link_opencollective_donate))) }
        title = String.format("%s v%s", appName, pkgVersionName)
        mWebView = findViewById(R.id.info)
        mWebView!!.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                // Hide the progress indicator when the page has finished loaded
                if (progress == 100) {
                    mainView.findViewById<View>(R.id.progress_bar).visibility = View.GONE
                }
            }
        }
        val marketButton = findViewById<Button>(R.id.left_button)
        if (canOpenMarketUri()) {
            marketButton.setText(R.string.info_rate)
            marketButton.setOnClickListener { tryOpenIntent(this, AnkiDroidApp.getMarketIntent(this)) }
        } else {
            marketButton.visibility = View.GONE
        }

        // Apply Theme colors
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground, android.R.attr.textColor))
        val backgroundColor = typedArray.getColor(0, -1)
        val textColor = String.format("#%06X", 0xFFFFFF and typedArray.getColor(1, -1)) // Color to hex string
        mWebView!!.setBackgroundColor(backgroundColor)
        setRenderWorkaround(this)
        when (type) {
            TYPE_ABOUT -> {
                val htmlContent = getAboutAnkiDroidHtml(res, textColor)
                mWebView!!.loadDataWithBaseURL("", htmlContent, "text/html", "utf-8", null)
                val debugCopy = findViewById<Button>(R.id.right_button)
                debugCopy.text = res.getString(R.string.feedback_copy_debug)
                debugCopy.setOnClickListener { copyDebugInfo() }
            }
            TYPE_NEW_VERSION -> {
                val continueButton = findViewById<Button>(R.id.right_button)
                continueButton.text = res.getString(R.string.dialog_continue)
                continueButton.setOnClickListener { close() }
                val background = String.format("#%06X", 0xFFFFFF and backgroundColor)
                mWebView!!.loadUrl("file:///android_asset/changelog.html")
                mWebView!!.settings.javaScriptEnabled = true
                mWebView!!.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {

                        /* The order of below javascript code must not change (this order works both in debug and release mode)
                                 *  or else it will break in any one mode.
                                 */
                        mWebView!!.loadUrl(
                            "javascript:document.body.style.setProperty(\"color\", \"" + textColor + "\");" +
                                "x=document.getElementsByTagName(\"a\"); for(i=0;i<x.length;i++){x[i].style.color=\"#E37068\";}" +
                                "document.getElementsByTagName(\"h1\")[0].style.color=\"" + textColor + "\";" +
                                "x=document.getElementsByTagName(\"h2\"); for(i=0;i<x.length;i++){x[i].style.color=\"#E37068\";}" +
                                "document.body.style.setProperty(\"background\", \"" + background + "\");"
                        )
                    }
                }
            }
            else -> finishWithoutAnimation()
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

    /**
     * Copy debug information about the device to the clipboard
     * @return debugInfo
     */
    private fun copyDebugInfo(): String {
        val debugInfo = getDebugInfo(this) { this.col }
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(this.title, debugInfo))
            showThemedToast(this, getString(R.string.about_ankidroid_successfully_copied_debug), true)
        } else {
            showThemedToast(this, getString(R.string.about_ankidroid_error_copy_debug_info), false)
        }
        return debugInfo
    }

    override fun onBackPressed() {
        if (mWebView!!.canGoBack()) {
            mWebView!!.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val TYPE_EXTRA = "infoType"
        const val TYPE_ABOUT = 0
        const val TYPE_NEW_VERSION = 2
        @JvmStatic
        @VisibleForTesting
        fun getAboutAnkiDroidHtml(res: Resources, textColor: String): String {
            val sb = StringBuilder()
            fun append(@Language("HTML") html: String) = sb.append(html)

            val content = res.getStringArray(R.array.about_content)
            append("<html><style>body {color:")
            append(textColor)
            append(";}</style>")
            append("<body text=\"#000000\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">")
            append(String.format(content[0], res.getString(R.string.app_name), res.getString(R.string.link_anki)))
            append("<br/><br/>")
            append(
                String.format(
                    content[1], res.getString(R.string.link_issue_tracker),
                    res.getString(R.string.link_wiki), res.getString(R.string.link_forum)
                )
            )
            append(
                "<br/><br/>"
            )
            append(
                String.format(
                    content[2], res.getString(R.string.link_wikipedia_open_source),
                    res.getString(R.string.link_contribution)
                )
            )
            append(" ")
            append(String.format(content[3], res.getString(R.string.link_translation)))
            append("<br/><br/>")
            append(
                String.format(
                    content[4], res.getString(R.string.licence_wiki),
                    res.getString(R.string.link_source)
                )
            )
            append("<br/><br/>")
            append("</body></html>")
            return sb.toString()
        }
    }
}
