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
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.updateDeckConfigsRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@NeedsTest("pressing back: icon + button should go to the previous screen")
@NeedsTest("15130: pressing back: icon + button should return to options if the manual is open")
@NeedsTest("saveAndExit closes screen")
class DeckOptions : PageFragment() {
    override val title: String
        get() = resources.getString(R.string.menu__deck_options)
    override val pageName = "deck-options"

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
            DiscardChangesDialog.showDialog(requireContext(), getString(R.string.discard), CollectionManager.TR.addingKeepEditing(), CollectionManager.TR.addingDiscardCurrentInput()) {
                Timber.i("OK button pressed to confirm discard changes")
                this.isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        val deckId = arguments?.getLong(ARG_DECK_ID)
            ?: throw Exception("missing deck ID")

        requireActivity().onBackPressedDispatcher.addCallback(this, onBackSaveCallback)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackCallback)
        return DeckOptionsWebClient(deckId).apply {
            onPageFinishedCallback = OnPageFinishedCallback { view ->
                Timber.v("canGoBack: %b", view.canGoBack())
                onBackCallback.isEnabled = view.canGoBack()
            }
        }
    }

    @Suppress("unused")
    fun saveAndExit() {
        // dispatch Ctrl+Enter
        val downEvent = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_CTRL_ON)
        val upEvent = KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_CTRL_ON)
        webView.dispatchKeyEvent(downEvent)
        webView.dispatchKeyEvent(upEvent)
    }

    class DeckOptionsWebClient(val deckId: Long) : PageWebViewClient() {
        override val promiseToWaitFor: String
            get() = "\$deckOptions"

        override fun onPageFinished(view: WebView?, url: String?) {
            // from upstream: https://github.com/ankitects/anki/blob/678c354fed4d98c0a8ef84fb7981ee085bd744a7/qt/aqt/deckoptions.py#L55
            view!!.evaluateJavascript("const \$deckOptions = anki.setupDeckOptions($deckId);") {
                super.onPageFinished(view, url)
            }
        }
    }

    companion object {
        const val ARG_DECK_ID = "deckId"

        fun getIntent(context: Context, deckId: Long): Intent {
            val arguments = Bundle().apply {
                putLong(ARG_DECK_ID, deckId)
            }
            return SingleFragmentActivity.getIntent(context, DeckOptions::class, arguments)
        }
    }
}

suspend fun FragmentActivity.updateDeckConfigsRaw(input: ByteArray): ByteArray {
    val output = CollectionManager.withCol { updateDeckConfigsRaw(input) }
    undoableOp { OpChanges.parseFrom(output) }
    withContext(Dispatchers.Main) { finish() }
    return output
}
