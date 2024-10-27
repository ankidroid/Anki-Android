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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.updateDeckConfigsRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@NeedsTest("15130: pressing back: icon + button should return to options if the manual is open")
class DeckOptions : PageFragment() {

    // handle going back from the manual
    private val onBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Timber.v("webView: navigating back")
            webView.goBack()
        }
    }

    // HACK: this is enabled unconditionally as we currently cannot get the 'changed' status
    private val onBackSaveCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Timber.v("DeckOptions: showing 'discard changes'")
            DiscardChangesDialog.showDialog(requireContext()) {
                Timber.i("OK button pressed to confirm discard changes")
                this.isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    @NeedsTest("disabled by default")
    @NeedsTest("enabled if a modal is displayed")
    @NeedsTest("disabled if a modal is hidden")
    @NeedsTest("disabled if back button is pressed: no error")
    @NeedsTest("disabled if back button is pressed: with error closing modal")
    private val onCloseBootstrapModalCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Timber.i("back button: closing displayed modal")
            try {
                webView.evaluateJavascript(
                    """
                document.getElementsByClassName("modal show")[0]
                .getElementsByClassName("btn-close")[0].click()
                    """.trimIndent(),
                    {}
                )
            } catch (e: Exception) {
                CrashReportService.sendExceptionReport(e, "DeckOptions:onCloseBootstrapModalCallback")
            } finally {
                // Even if we fail, disable the callback so the next call succeeds
                this.isEnabled = false
            }
        }
    }

    /**
     * Listens to bootstrap open and close events
     */
    inner class ModalJavaScriptInterfaceListener {
        @JavascriptInterface
        fun onEvent(request: String) {
            when (request) {
                "open" -> {
                    Timber.d("WebVew modal opened")
                    onCloseBootstrapModalCallback.isEnabled = true
                }
                "close" -> {
                    Timber.d("WebView modal closed")
                    onCloseBootstrapModalCallback.isEnabled = false
                }
                else -> Timber.w("Unknown command: $request")
            }
        }
    }

    override fun onWebViewCreated(webView: WebView) {
        // addJavascriptInterface needs to happen before loadUrl
        webView.addJavascriptInterface(ModalJavaScriptInterfaceListener(), "ankidroid")
        Timber.d("Added JS Interface: 'ankidroid")
    }

    @NeedsTest("going back on a manual page takes priority over closing a modal")
    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackSaveCallback)
        requireActivity().onBackPressedDispatcher.addCallback(this, onCloseBootstrapModalCallback)
        // going back on a manual page takes priority over closing a modal
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackCallback)

        return object : PageWebViewClient() {
            private val ankiManualHostRegex = Regex("^docs\\.ankiweb\\.net\$")

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // #16715: ensure that the fragment can't be used for general web browsing
                val host = request?.url?.host ?: return shouldOverrideUrlLoading(view, request)
                return if (ankiManualHostRegex.matches(host)) {
                    super.shouldOverrideUrlLoading(view, request)
                } else {
                    openUrl(request.url)
                    true
                }
            }
        }.apply {
            onPageFinishedCallback = OnPageFinishedCallback { view ->
                Timber.v("canGoBack: %b", view.canGoBack())
                onBackCallback.isEnabled = view.canGoBack()
                // reset the modal state on page load
                // clicking a link to the online manual closes the modal and reloads the page
                onCloseBootstrapModalCallback.isEnabled = false
                listenToModalShowHideEvents()
            }
        }
    }

    /**
     * Passes bootstrap modal show/hide events to [ModalJavaScriptInterfaceListener]
     */
    private fun listenToModalShowHideEvents() {
        // this function is called multiple times on one document, only register the listener once
        // we use the command name as this is a valid identifier
        fun getListenerJs(event: String, command: String): String =
            """
            if (!document.added$command) {
                console.log("listening to '$command'");
                document.added$command = true
                document.addEventListener("$event", () => { ankidroid.onEvent("$command"); })
            }"""

        // event names:
        // https://github.com/ankitects/anki/blob/85f034b144ea17f90319b76d2c7d0feaa491eaa5/ts/lib/components/HelpModal.svelte
        val openJs = getListenerJs("shown.bs.modal", "open")
        val closeJs = getListenerJs("hidden.bs.modal", "close")

        webView.evaluateJavascript(openJs, {})
        webView.evaluateJavascript(closeJs, {})
    }

    companion object {
        fun getIntent(context: Context, deckId: Long): Intent {
            val title = context.getString(R.string.menu__deck_options)
            return getIntent(context, "deck-options/$deckId", title, DeckOptions::class)
        }
    }
}

suspend fun FragmentActivity.updateDeckConfigsRaw(input: ByteArray): ByteArray {
    val output = withContext(Dispatchers.Main) {
        withProgress(
            extractProgress = {
                text = if (progress.hasComputeParams()) {
                    val tr = CollectionManager.TR
                    val value = progress.computeParams
                    val label = tr.deckConfigOptimizingPreset(
                        currentCount = value.currentPreset,
                        totalCount = value.totalPresets
                    )
                    val pct = if (value.total > 0) (value.current / value.total * 100) else 0
                    val reviewsLabel = tr.deckConfigPercentOfReviews(pct = pct.toString(), reviews = value.reviews)
                    label + "\n" + reviewsLabel
                } else {
                    getString(R.string.dialog_processing)
                }
            }
        ) {
            withContext(Dispatchers.IO) {
                CollectionManager.withCol { updateDeckConfigsRaw(input) }
            }
        }
    }
    undoableOp { OpChanges.parseFrom(output) }
    withContext(Dispatchers.Main) { finish() }
    return output
}
