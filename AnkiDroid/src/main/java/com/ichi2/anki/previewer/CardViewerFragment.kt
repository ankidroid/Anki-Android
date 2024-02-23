/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.TtsVoicesDialogFragment
import com.ichi2.anki.getViewerAssetLoader
import com.ichi2.anki.localizedErrorMessage
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.themes.Themes
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

abstract class CardViewerFragment(@LayoutRes layout: Int) : Fragment(layout) {
    protected abstract val viewModel: CardViewerViewModel
    protected abstract val webView: WebView

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupWebView(savedInstanceState)
        setupErrorListeners()
    }

    override fun onStart() {
        super.onStart()
        viewModel.setSoundPlayerEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.setSoundPlayerEnabled(false)
        }
    }

    private fun setupWebView(savedInstanceState: Bundle?) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        with(webView) {
            webViewClient = onCreateWebViewClient(savedInstanceState)
            webChromeClient = onCreateWebChromeClient()
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            with(settings) {
                javaScriptEnabled = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                domStorageEnabled = true
            }
            loadDataWithBaseURL(
                viewModel.baseUrl(),
                stdHtml(requireContext(), Themes.currentTheme.isNightMode),
                "text/html",
                null,
                null
            )
        }
        viewModel.eval
            .flowWithLifecycle(lifecycle)
            .onEach { eval ->
                webView.evaluateJavascript(eval, null)
            }
            .launchIn(lifecycleScope)
    }

    private fun setupErrorListeners() {
        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }
            .launchIn(lifecycleScope)

        viewModel.onMediaError
            .onEach { showMediaErrorSnackbar(it) }
            .launchIn(lifecycleScope)

        viewModel.onTtsError
            .onEach { showSnackbar(it.localizedErrorMessage(requireContext())) }
            .launchIn(lifecycleScope)
    }

    private fun onCreateWebViewClient(savedInstanceState: Bundle?): WebViewClient {
        val assetLoader = requireContext().getViewerAssetLoader(viewModel.assetLoaderDomain())
        return object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return if (request.method == "GET") {
                    assetLoader.shouldInterceptRequest(request.url)
                } else {
                    null
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                viewModel.onPageFinished(isAfterRecreation = savedInstanceState != null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val urlString = request.url.toString()
                if (urlString.startsWith("playsound:")) {
                    viewModel.playSoundFromUrl(urlString)
                    return true
                }
                if (urlString.startsWith("tts-voices:")) {
                    TtsVoicesDialogFragment().show(childFragmentManager, null)
                    return true
                }
                try {
                    openUrl(request.url)
                    return true
                } catch (_: Exception) {
                    Timber.w("Could not open url")
                }
                return false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                viewModel.mediaErrorHandler.processFailure(request) { filename: String ->
                    showMediaErrorSnackbar(filename)
                }
            }
        }
    }

    private fun onCreateWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            private lateinit var customView: View

            // used for displaying `<video>` in fullscreen.
            // This implementation requires configChanges="orientation" in the manifest
            // to avoid destroying the View if the device is rotated
            override fun onShowCustomView(
                paramView: View,
                paramCustomViewCallback: CustomViewCallback?
            ) {
                customView = paramView
                val window = requireActivity().window
                (window.decorView as FrameLayout).addView(
                    customView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                // hide system bars
                with(WindowInsetsControllerCompat(window, window.decorView)) {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }

            override fun onHideCustomView() {
                val window = requireActivity().window
                (window.decorView as FrameLayout).removeView(customView)
                // show system bars back
                with(WindowInsetsControllerCompat(window, window.decorView)) {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    private fun showMediaErrorSnackbar(filename: String) {
        showSnackbar(getString(R.string.card_viewer_could_not_find_image, filename)) {
            setAction(R.string.help) { openUrl(Uri.parse(getString(R.string.link_faq_missing_media))) }
        }
    }

    private fun openUrl(uri: Uri) = startActivity(Intent(Intent.ACTION_VIEW, uri))
}
