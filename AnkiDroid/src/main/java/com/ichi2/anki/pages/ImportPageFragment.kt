// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.ichi2.anki.ImportSession
import com.ichi2.anki.ImportSessionId
import com.ichi2.anki.ImportSessionViewModel
import com.ichi2.anki.R
import kotlinx.coroutines.CancellationException

/** Holds the import through options, progress and successful results, including WebView recreation. */
abstract class ImportPageFragment : PageFragment() {
    private val session: ImportSessionViewModel by viewModels()
    private var admitted = false

    /** Replaced with each WebView so Back follows the state of the page currently on screen. */
    protected var backCallback: ImportBackCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        admitted =
            session.attach(
                requireContext(),
                requireActivity().intent.getStringExtra(ImportSession.EXTRA_SESSION_ID)?.let(::ImportSessionId),
            )
        if (!admitted) requireActivity().finish()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        if (admitted) {
            super.onViewCreated(view, savedInstanceState)
        } else {
            webViewLayout = view.findViewById(R.id.webview_layout)
        }
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        val method = uri.backendMethodName
        check(!session.hasFailed || method == "importDialogRequireClose") { "The import has already failed" }
        backCallback?.onPostRequest(uri)
        try {
            return super.handlePostRequest(uri, bytes)
        } catch (failure: Exception) {
            if (failure !is CancellationException && (method == "importAnkiPackage" || method == "importCsv")) {
                // The page shows a terminal error with a Close button. Releasing the file
                // must also stop the old page from controlling or restarting an import.
                backCallback?.remove()
                backCallback = null
                session.markFailed()
            }
            throw failure
        }
    }

    override fun onWebViewRecreated() {
        if (session.hasFailed) {
            activity?.finish()
        } else {
            super.onWebViewRecreated()
        }
    }
}
