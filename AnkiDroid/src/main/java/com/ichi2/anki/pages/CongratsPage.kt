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
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.launchCatching
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class CongratsPage : PageFragment() {
    override val title: String = ""
    override val pageName = "congrats"

    private val viewModel by viewModels<CongratsViewModel>()

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        return super.onCreateWebViewClient(savedInstanceState).also { client ->
            client.onPageFinishedCallback = OnPageFinishedCallback { webView ->
                webView.evaluateJavascript(
                    "bridgeCommand = function(request){ ankidroid.bridgeCommand(request); };"
                ) {}
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }
            .launchIn(lifecycleScope)

        viewModel.openStudyOptions
            .onEach { openStudyOptionsAndFinish() }
            .launchIn(lifecycleScope)

        webView.addJavascriptInterface(BridgeCommand(), "ankidroid")
    }

    inner class BridgeCommand {
        @JavascriptInterface
        fun bridgeCommand(request: String) {
            when (request) {
                "unbury" -> viewModel.onUnbury()
            }
        }
    }

    private fun openStudyOptionsAndFinish() {
        val intent = Intent(requireContext(), StudyOptionsActivity::class.java).apply {
            putExtra("withDeckOptions", false)
        }
        startActivity(intent, null)
        requireActivity().finish()
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return SingleFragmentActivity.getIntent(context, CongratsPage::class)
        }
    }
}

class CongratsViewModel : ViewModel(), OnErrorListener {
    override val onError = MutableSharedFlow<String>()
    val openStudyOptions = MutableSharedFlow<Boolean>()

    fun onUnbury() {
        launchCatching {
            undoableOp {
                sched.unburyDeck(decks.getCurrentId())
            }
            openStudyOptions.emit(true)
        }
    }
}
