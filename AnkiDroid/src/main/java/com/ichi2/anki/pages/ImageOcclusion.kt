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
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import com.ichi2.anki.pages.viewmodel.ImageOcclusionViewModel
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.selectedDeckIfNotFiltered
import com.ichi2.anki.startDeckSelection
import kotlinx.coroutines.launch
import timber.log.Timber

class ImageOcclusion :
    PageFragment(R.layout.image_occlusion),
    DeckSelectionDialog.DeckSelectionListener {
    private val viewModel: ImageOcclusionViewModel by viewModels()
    private lateinit var deckNameView: TextView

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        with(requireActivity()) {
            onBackPressedDispatcher.addCallback(this) {
                DiscardChangesDialog.showDialog(this@with) {
                    finish()
                }
            }
        }

        deckNameView = view.findViewById(R.id.deck_name)
        deckNameView.setOnClickListener { startDeckSelection(all = false, filtered = false, skipEmptyDefault = false) }

        requireAnkiActivity().launchCatchingTask {
            val selectedDeck = withCol { selectedDeckIfNotFiltered() }
            deckNameView.text = selectedDeck.name
        }

        @NeedsTest("#17393 verify that the added image occlusion cards are put in the correct deck")
        view.findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            if (it.itemId == R.id.action_save) {
                Timber.i("save item selected")
                webViewLayout.evaluateJavascript("anki.imageOcclusion.save()") {
                    // reset to the previous deck that the backend "saw" as selected, this
                    // avoids other screens unexpectedly having their working decks modified(
                    // most important being the Reviewer where the user would find itself
                    // studying another deck after editing a note with changing the deck)
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.onSaveOperationCompleted()
                    }
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient =
        object : PageWebViewClient() {
            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)
                viewModel.webViewOptions.let { options ->
                    view?.evaluateJavascript("globalThis.anki.imageOcclusion.mode = $options") {
                        super.onPageFinished(view, url)
                    }
                }
            }
        }

    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        deckNameView.text = deck.name
        val deckDidChange = viewModel.handleDeckSelection(deck.deckId)
        if (deckDidChange) {
            viewLifecycleOwner.lifecycleScope.launch {
                withCol { decks.select(viewModel.selectedDeckId) }
            }
        }
    }

    companion object {
        const val IO_ARGS_KEY = "IMAGE_OCCLUSION_ARGS"

        /**
         * @param editorWorkingDeckId the current deck id that [com.ichi2.anki.NoteEditorFragment] is using
         */
        fun getIntent(
            context: Context,
            kind: String,
            noteOrNotetypeId: Long,
            imagePath: String?,
            editorWorkingDeckId: DeckId,
        ): Intent {
            val suffix = if (kind == "edit") noteOrNotetypeId else Uri.encode(imagePath)

            val args =
                ImageOcclusionArgs(
                    kind = kind,
                    id = noteOrNotetypeId,
                    imagePath = imagePath,
                    editorDeckId = editorWorkingDeckId,
                )

            val arguments =
                bundleOf(
                    IO_ARGS_KEY to args,
                    PATH_ARG_KEY to "image-occlusion/$suffix",
                )

            return SingleFragmentActivity.getIntent(context, ImageOcclusion::class, arguments)
        }
    }
}
