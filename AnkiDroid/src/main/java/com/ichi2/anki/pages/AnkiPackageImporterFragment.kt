// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.hideShowButtonCss
import com.ichi2.utils.OLDEST_WORKING_WEBVIEW_VERSION
import com.ichi2.utils.WebViewVersion

class AnkiPackageImporterFragment : PageFragment() {
    override val pagePath: String by lazy {
        val filePath = requireArguments().getString(ARG_FILE_PATH)
        "import-anki-package$filePath"
    }

    override val minimumWebViewVersion: WebViewVersion = OLDEST_WORKING_WEBVIEW_VERSION

    private val backCallback = ImportBackCallback()

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient = AnkiPackageImporterWebViewClient()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.title = TR.actionsImport()
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        backCallback.onPostRequest(uri)
        return super.handlePostRequest(uri, bytes)
    }

    class AnkiPackageImporterWebViewClient : PageWebViewClient() {
        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            view!!.evaluateJavascript(hideShowButtonCss) {
                super.onPageFinished(view, url)
            }
        }
    }

    companion object {
        private const val ARG_FILE_PATH = "filePath"

        fun getIntent(
            context: Context,
            filePath: String,
        ): Intent {
            val arguments = Bundle().apply { putString(ARG_FILE_PATH, filePath) }
            return SingleFragmentActivity.getIntent(context, AnkiPackageImporterFragment::class, arguments)
        }
    }
}
