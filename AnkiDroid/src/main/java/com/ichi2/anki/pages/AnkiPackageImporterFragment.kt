// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.hideShowButtonCss
import com.ichi2.utils.OLDEST_WORKING_WEBVIEW_VERSION
import com.ichi2.utils.WebViewVersion

class AnkiPackageImporterFragment : PageFragment() {
    override val pagePath: String by lazy {
        val filePath = requireArguments().getString(KEY_FILE_PATH)
        "import-anki-package$filePath"
    }

    override val minimumWebViewVersion: WebViewVersion = OLDEST_WORKING_WEBVIEW_VERSION

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        // the back callback is only enabled when import is running and showing progress
        val backCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    CollectionManager.getBackend().setWantsAbort()
                    // once triggered the callback is not needed as the import process can't be resumed
                    remove()
                }
            }
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
        return AnkiPackageImporterWebViewClient(backCallback)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.title = TR.actionsImport()
    }

    class AnkiPackageImporterWebViewClient(
        private val backCallback: OnBackPressedCallback,
    ) : PageWebViewClient() {
        /**
         * Ideally, to handle the state of the back callback, we would just need to check for
         * `/latestProgress` calls followed by one `/importDone` call. However there are some extra
         * calls to `/latestProgress` AFTER `/importDone` and this property keeps track of this.
         */
        private var isDone = false

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            view!!.evaluateJavascript(hideShowButtonCss) {
                super.onPageFinished(view, url)
            }
        }

        override fun onLoadResource(
            view: WebView?,
            url: String?,
        ) {
            super.onLoadResource(view, url)
            backCallback.isEnabled =
                when {
                    url == null -> false
                    url.endsWith("latestProgress") && !isDone -> true
                    url.endsWith("importDone") -> {
                        isDone = true // import was done so disable any back callback changes after this call
                        false
                    }
                    else -> false
                }
        }
    }

    companion object {
        private const val KEY_FILE_PATH = "filePath"

        fun getIntent(
            context: Context,
            filePath: String,
        ): Intent {
            val arguments = Bundle().apply { putString(KEY_FILE_PATH, filePath) }
            return SingleFragmentActivity.getIntent(context, AnkiPackageImporterFragment::class, arguments)
        }
    }
}
