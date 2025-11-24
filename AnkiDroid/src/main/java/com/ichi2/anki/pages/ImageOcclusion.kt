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
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import com.ichi2.anki.pages.viewmodel.ImageOcclusionViewModel
import com.ichi2.anki.pages.viewmodel.ImageOcclusionViewModel.Companion.IO_ARGS_KEY
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.selectedDeckIfNotFiltered
import com.ichi2.anki.startDeckSelection
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Page provided by the backend, for a user to add or edit an image occlusion (IO) note
 *
 * IO: Like an image-based cloze: hide parts of an image, revealed on the back
 * ([docs](https://docs.ankiweb.net/editing.html#image-occlusion) and
 * [source](https://github.com/ankitects/anki/blob/main/proto/anki/image_occlusion.proto)).
 *
 * **Paths**
 * `/image-occlusion/$PATH`
 * `/image-occlusion/$NOTE_ID`
 *
 * @see ImageOcclusionViewModel
 * @see ImageOcclusion.getIntent
 */
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
                webViewLayout.evaluateJavascript("anki.imageOcclusion.save()")
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
                viewModel.args.toImageOcclusionMode().let { options ->
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

    // HACK: detect a successful save; #19443 will provide a better method
    // backend calls are only made on success; .save() does not notify on failure
    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray =
        super.handlePostRequest(uri, bytes).also {
            when (uri.backendMethodName) {
                "addImageOcclusionNote", "updateImageOcclusionNote" -> viewModel.onSaveOperationCompleted()
            }
        }

    companion object {
        /**
         * @param args arguments for either adding or editing a note
         */
        fun getIntent(
            context: Context,
            args: ImageOcclusionArgs,
        ): Intent {
            val suffix =
                when (args) {
                    is ImageOcclusionArgs.Add -> Uri.encode(args.imagePath)
                    is ImageOcclusionArgs.Edit -> args.noteId
                }

            val arguments =
                bundleOf(
                    IO_ARGS_KEY to args,
                    PATH_ARG_KEY to "image-occlusion/$suffix",
                )

            return SingleFragmentActivity.getIntent(context, ImageOcclusion::class, arguments)
        }
    }
}
