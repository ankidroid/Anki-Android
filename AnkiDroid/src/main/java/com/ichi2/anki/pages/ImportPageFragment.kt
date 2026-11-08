// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.ichi2.anki.ImportSession
import com.ichi2.anki.ImportSessionId
import com.ichi2.anki.ImportSessionViewModel
import com.ichi2.anki.R

/** Holds the import through options, progress and results, including WebView recreation. */
abstract class ImportPageFragment : PageFragment() {
    private val session: ImportSessionViewModel by viewModels()
    private var admitted = false

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
}
