/*
 *  Copyright (c) 2023 Abdo <abdo@abdnh.net>
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
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.DeckId
import com.ichi2.themes.setTransparentStatusBar
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class ImageOcclusion : PageFragment(R.layout.image_occlusion) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().setTransparentStatusBar()
        with(requireActivity()) {
            onBackPressedDispatcher.addCallback(this) {
                DiscardChangesDialog.showDialog(this@with) {
                    finish()
                }
            }
        }

        @NeedsTest("#17393 verify that the added image occlusion cards are put in the correct deck")
        view.findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            val editorWorkingDeckId = requireArguments().getLong(ARG_KEY_EDITOR_DECK_ID)
            if (it.itemId == R.id.action_save) {
                Timber.i("save item selected")
                // TODO desktop code doesn't allow a deck change from the reviewer, if we would do
                //  the same then NoteEditor could simply set the deck as selected and this hack
                //  could be removed
                // because NoteEditor doesn't update the selected deck in Collection.decks when
                // there's a deck change and keeps its own deckId reference, we need to use that
                // deck id reference as the target deck in this fragment(backend code simply uses
                // the current selected deck it sees as the target deck for adding)
                lifecycleScope.launch {
                    val previousDeckId = withCol {
                        val current = backend.getCurrentDeck().id
                        backend.setCurrentDeck(editorWorkingDeckId)
                        current
                    }
                    webView.evaluateJavascript("anki.imageOcclusion.save()") {
                        // reset to the previous deck that the backend "saw" as selected, this
                        // avoids other screens unexpectedly having their working decks modified(
                        // most important being the Reviewer where the user would find itself
                        // studying another deck after editing a note with changing the deck)
                        lifecycleScope.launch {
                            withCol { backend.setCurrentDeck(previousDeckId) }
                        }
                    }
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        return object : PageWebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                val kind = requireArguments().getString(ARG_KEY_KIND)
                val noteOrNotetypeId = requireArguments().getLong(ARG_KEY_ID)
                val imagePath = requireArguments().getString(ARG_KEY_PATH)

                val options = JSONObject()
                options.put("kind", kind)
                if (kind == "add") {
                    options.put("imagePath", imagePath)
                    options.put("notetypeId", noteOrNotetypeId)
                } else {
                    options.put("noteId", noteOrNotetypeId)
                }

                view?.evaluateJavascript("globalThis.anki.imageOcclusion.mode = $options") {
                    super.onPageFinished(view, url)
                }
            }
        }
    }

    companion object {
        private const val ARG_KEY_KIND = "kind"
        private const val ARG_KEY_ID = "id"
        private const val ARG_KEY_PATH = "imagePath"
        private const val ARG_KEY_EDITOR_DECK_ID = "arg_key_editor_deck_id"

        /**
         * @param editorWorkingDeckId the current deck id that [com.ichi2.anki.NoteEditor] is using
         */
        fun getIntent(
            context: Context,
            kind: String,
            noteOrNotetypeId: Long,
            imagePath: String?,
            editorWorkingDeckId: DeckId
        ): Intent {
            val suffix = if (kind == "edit") {
                "/$noteOrNotetypeId"
            } else {
                imagePath
            }
            val arguments = bundleOf(
                ARG_KEY_KIND to kind,
                ARG_KEY_ID to noteOrNotetypeId,
                ARG_KEY_PATH to imagePath,
                PATH_ARG_KEY to "image-occlusion$suffix",
                ARG_KEY_EDITOR_DECK_ID to editorWorkingDeckId
            )
            return SingleFragmentActivity.getIntent(context, ImageOcclusion::class, arguments)
        }
    }
}
