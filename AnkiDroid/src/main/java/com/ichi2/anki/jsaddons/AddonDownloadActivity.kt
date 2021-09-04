/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.anki.jsaddons

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.async.TaskManager
import timber.log.Timber

/**
 * Browse nmpjs.org with the functionality to download and import addon.
 */
class AddonDownloadActivity : AnkiActivity() {

    private lateinit var mWebView: WebView

    private var mShouldHistoryBeCleared = false

    private lateinit var mDownloadButton: Button
    private lateinit var mActivity: Activity

    /**
     * Handle condition when page finishes loading and history needs to be cleared.
     * Currently, this condition arises when user presses the home button on the toolbar.
     *
     * History should not be cleared before the page finishes loading otherwise there would be
     * an extra entry in the history since the previous page would not get cleared.
     */
    private val mWebViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            // Clear history if mShouldHistoryBeCleared is true and set it to false
            if (mShouldHistoryBeCleared) {
                mWebView.clearHistory()
                mShouldHistoryBeCleared = false
            }

            // get addon name from url
            val addonName = NpmUtils.getAddonNameFromUrl(url!!)
            if (addonName == null) {
                mDownloadButton.visibility = View.GONE
            } else {
                // call background task for adding "Install Addon" button at right bottom corner
                TaskManager.launchCollectionTask(
                    NpmPackageDownloader.ShowHideInstallButton(applicationContext, addonName),
                    NpmPackageDownloader.ShowHideInstallButtonListener(mActivity, mDownloadButton, addonName)
                )
            }

            super.onPageFinished(view, url)
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // Set mShouldHistoryBeCleared to false if error occurs since it might have been true
            mShouldHistoryBeCleared = false
            super.onReceivedError(view, request, error)
        }
    }

    // Show WebView with npmjs.org url with the functionality to downloads and import addons.
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_addon)

        val webviewToolbar: Toolbar = findViewById(R.id.webview_toolbar)
        webviewToolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.white))

        setSupportActionBar(webviewToolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        webviewToolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.close_icon)

        mWebView = findViewById(R.id.web_view)
        mDownloadButton = findViewById(R.id.download_button)

        mWebView.settings.javaScriptEnabled = true
        mWebView.loadUrl(resources.getString(R.string.ankidroid_js_addon_npm_search_url))

        mWebView.webViewClient = WebViewClient()
        mWebView.webViewClient = mWebViewClient

        mActivity = this
    }

    override fun onBackPressed() {
        when {
            mWebView.canGoBack() -> {
                Timber.i("Back pressed when user can navigate back to other webpages inside WebView")
                mWebView.goBack()
            }
            else -> {
                Timber.i("Back pressed which would lead to closing of the WebView")
                super.onBackPressed()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.download_shared_decks_menu, menu)

        val searchView = menu?.findItem(R.id.search)?.actionView as SearchView
        searchView.queryHint = getString(R.string.search_using_addon_name)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                mWebView.loadUrl(resources.getString(R.string.ankidroid_js_addon_npm_search_query_url) + query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Nothing to do here
                return false
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) {
            mShouldHistoryBeCleared = true
            mWebView.loadUrl(resources.getString(R.string.ankidroid_js_addon_npm_search_url))
        }
        return super.onOptionsItemSelected(item)
    }
}
