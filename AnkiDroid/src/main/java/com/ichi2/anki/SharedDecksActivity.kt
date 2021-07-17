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
import android.webkit.*
import androidx.core.os.bundleOf
import java.io.Serializable
import java.util.*

class SharedDecksActivity : AnkiActivity() {

    private lateinit var mWebView: WebView

    companion object {
        const val SHARED_DECKS_DOWNLOAD_FRAGMENT = "SharedDecksDownloadFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_decks)

        mWebView = findViewById(R.id.web_view)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        mWebView.settings.javaScriptEnabled = true
        mWebView.loadUrl(resources.getString(R.string.shared_decks_url))
        mWebView.webViewClient = WebViewClient()
        mWebView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val sharedDecksDownloadFragment = SharedDecksDownloadFragment()
            sharedDecksDownloadFragment.arguments = bundleOf("DownloadFile" to DownloadFile(url, userAgent, contentDisposition, mimetype, downloadManager))

            supportFragmentManager.beginTransaction()
                .add(R.id.shared_decks_fragment_container, sharedDecksDownloadFragment, SHARED_DECKS_DOWNLOAD_FRAGMENT)
                .commit()
        }
    }

    override fun onBackPressed() {
        when {
            sharedDecksDownloadFragmentExists() -> {
                supportFragmentManager.findFragmentByTag(SHARED_DECKS_DOWNLOAD_FRAGMENT)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                supportFragmentManager.popBackStackImmediate()
            }
            mWebView.canGoBack() -> {
                mWebView.goBack()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    private fun sharedDecksDownloadFragmentExists(): Boolean {
        val sharedDecksDownloadFragment = supportFragmentManager.findFragmentByTag(SHARED_DECKS_DOWNLOAD_FRAGMENT)
        return sharedDecksDownloadFragment != null && sharedDecksDownloadFragment.isAdded
    }
}

data class DownloadFile(
    val mUrl: String,
    val mUserAgent: String,
    val mContentDisposition: String,
    val mMimeType: String,
    val mDownloadManager: DownloadManager
) : Serializable
