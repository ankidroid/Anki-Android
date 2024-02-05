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

package com.ichi2.anki

import android.app.DownloadManager
import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE
import com.ichi2.anki.databinding.ActivitySharedDecksBinding
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber
import java.io.Serializable

/**
 * Browse AnkiWeb shared decks with the functionality to download and import them.
 *
 * @see SharedDecksDownloadFragment
 */
class SharedDecksActivity : AnkiActivity() {

    private lateinit var webView: WebView
    lateinit var downloadManager: DownloadManager

    private var shouldHistoryBeCleared = false

    private lateinit var binding: ActivitySharedDecksBinding

    /**
     * Handle condition when page finishes loading and history needs to be cleared.
     * Currently, this condition arises when user presses the home button on the toolbar.
     *
     * History should not be cleared before the page finishes loading otherwise there would be
     * an extra entry in the history since the previous page would not get cleared.
     */
    private val webViewClient = object : WebViewClient() {
        private var redirectTimes = 0

        override fun onPageFinished(view: WebView?, url: String?) {
            // Clear history if mShouldHistoryBeCleared is true and set it to false
            if (shouldHistoryBeCleared) {
                webView.clearHistory()
                shouldHistoryBeCleared = false
            }
            super.onPageFinished(view, url)
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)

            if (errorResponse?.statusCode != 429) return
            // "Please log in to download more decks." - on clicking "Download"
            // "Please log in to perform more searches" - on searching

            // TODO: the result of login is typically redirecting the user to their decks
            // this should be improved

            showSnackbar(R.string.shared_decks_login_required, LENGTH_INDEFINITE) {
                if (isLoggedIn()) return@showSnackbar
                setAction(R.string.sign_up) {
                    webView.loadUrl(getString(R.string.shared_decks_sign_up_url))
                }
            }
            if (redirectTimes++ < 3) {
                Timber.i("HTTP 429, redirecting to login")
                webView.loadUrl(getString(R.string.shared_decks_login_url))
            } else {
                Timber.w("HTTP 429 redirect limit exceeded, only displaying message")
            }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // Set mShouldHistoryBeCleared to false if error occurs since it might have been true
            shouldHistoryBeCleared = false
            super.onReceivedError(view, request, error)
        }
    }

    companion object {
        const val SHARED_DECKS_DOWNLOAD_FRAGMENT = "SharedDecksDownloadFragment"
        const val DOWNLOAD_FILE = "DownloadFile"
    }

    // Show WebView with AnkiWeb shared decks with the functionality to capture downloads and import decks.
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        binding = ActivitySharedDecksBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setTitle(R.string.download_deck)

        val webviewToolbar = binding.webviewToolbar
        webviewToolbar.setTitleTextColor(getColor(R.color.white))

        setSupportActionBar(webviewToolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        webviewToolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.close_icon)

        webView = binding.webView

        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        webView.settings.javaScriptEnabled = true
        webView.loadUrl(resources.getString(R.string.shared_decks_url))
        webView.webViewClient = WebViewClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val sharedDecksDownloadFragment = SharedDecksDownloadFragment()
            sharedDecksDownloadFragment.arguments = bundleOf(DOWNLOAD_FILE to DownloadFile(url, userAgent, contentDisposition, mimetype))

            supportFragmentManager.commit {
                add(R.id.shared_decks_fragment_container, sharedDecksDownloadFragment, SHARED_DECKS_DOWNLOAD_FRAGMENT).addToBackStack(null)
            }
        }

        webView.webViewClient = webViewClient
    }

    /**
     * If download screen is open:
     *      If download is in progress: Show download cancellation dialog
     *      If download is not in progress: Close the download screen
     * If user can go back in WebView, navigate to previous webpage.
     * Otherwise, close the WebView.
     */
    @Deprecated("Deprecated in Java")
    @Suppress("deprecation") // onBackPressed
    override fun onBackPressed() {
        when {
            sharedDecksDownloadFragmentExists() -> {
                supportFragmentManager.findFragmentByTag(SHARED_DECKS_DOWNLOAD_FRAGMENT)?.let {
                    if ((it as SharedDecksDownloadFragment).isDownloadInProgress) {
                        Timber.i("Back pressed when download is in progress, show cancellation confirmation dialog")
                        // Show cancel confirmation dialog if download is in progress
                        it.showCancelConfirmationDialog()
                    } else {
                        Timber.i("Back pressed when download is not in progress but download screen is open, close fragment")
                        // Remove fragment
                        supportFragmentManager.commit {
                            remove(it)
                        }
                    }
                }
                supportFragmentManager.popBackStackImmediate()
            }
            webView.canGoBack() -> {
                Timber.i("Back pressed when user can navigate back to other webpages inside WebView")
                webView.goBack()
            }
            else -> {
                Timber.i("Back pressed which would lead to closing of the WebView")
                super.onBackPressed()
            }
        }
    }

    private fun sharedDecksDownloadFragmentExists(): Boolean {
        val sharedDecksDownloadFragment = supportFragmentManager.findFragmentByTag(SHARED_DECKS_DOWNLOAD_FRAGMENT)
        return sharedDecksDownloadFragment != null && sharedDecksDownloadFragment.isAdded
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.download_shared_decks_menu, menu)

        val searchView = menu.findItem(R.id.search)?.actionView as SearchView
        searchView.queryHint = getString(R.string.search_using_deck_name)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                webView.loadUrl(resources.getString(R.string.shared_decks_url) + query)
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
            shouldHistoryBeCleared = true
            webView.loadUrl(resources.getString(R.string.shared_decks_url))
        }
        return super.onOptionsItemSelected(item)
    }
}

/**
 * Used for sending URL, user agent, content disposition and mime type to SharedDecksDownloadFragment.
 */
data class DownloadFile(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String
) : Serializable
