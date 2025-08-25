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
import android.os.Parcelable
import android.view.View
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import timber.log.Timber

class ImageOcclusion : PageFragment(R.layout.image_occlusion) {
    /**
     * Options to decide how to start the image occlusion.
     */
    @Parcelize
    sealed class Options(
        val kind: String,
    ) : Parcelable {
        /**
         * Returns JSON that can be set to imageOcclusion.mode
         */
        open fun json() =
            JSONObject().apply {
                put("kind", kind)
            }

        @Parcelize
        data class NewOcclusionNote(
            val id: NoteTypeId,
            val imagePath: String?,
        ) : Options("add") {
            override fun json() =
                super.json().apply {
                    put("imagePath", imagePath)
                    put("notetypeId", id)
                }
        }

        @Parcelize
        data class EditOcclusionNote(
            val id: NoteId,
        ) : Options("edit") {
            override fun json() =
                super.json().apply {
                    put("noteId", id)
                }
        }
    }

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
                    val previousDeckId =
                        withCol {
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

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient =
        object : PageWebViewClient() {
            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)

                val options = BundleCompat.getParcelable(requireArguments(), ARG_OPTIONS, Options::class.java)!!

                view?.evaluateJavascript("globalThis.anki.imageOcclusion.mode = ${options.json()}") {
                    super.onPageFinished(view, url)
                }
            }
        }

    companion object {
        private const val NOTE_ID_KEY = "noteId"
        private const val ARG_KEY_EDITOR_DECK_ID = "arg_key_editor_deck_id"
        private const val ARG_OPTIONS = "arg_options"

        /**
         * An intent to open Image occlusion for an existing card to edit.
         * @param editorWorkingDeckId the current deck id that [com.ichi2.anki.NoteEditorFragment] is using
         */
        fun getEditIntent(
            context: Context,
            noteId: Long,
            editorWorkingDeckId: DeckId,
        ): Intent {
            val options = Options.EditOcclusionNote(noteId)
            val arguments =
                bundleOf(
                    ARG_OPTIONS to options,
                    NOTE_ID_KEY to noteId,
                    PATH_ARG_KEY to "image-occlusion/$noteId",
                    ARG_KEY_EDITOR_DECK_ID to editorWorkingDeckId,
                )
            return SingleFragmentActivity.getIntent(context, ImageOcclusion::class, arguments)
        }

        /**
         * An intent to open Image occlusion for a new card.
         * @param editorWorkingDeckId the current deck id that [com.ichi2.anki.NoteEditorFragment] is using
         */
        fun getAddIntent(
            context: Context,
            noteTypeId: Long,
            imagePath: String?,
            editorWorkingDeckId: DeckId,
        ): Intent {
            val suffix = Uri.encode(imagePath)
            val options = Options.NewOcclusionNote(noteTypeId, imagePath)
            val arguments =
                bundleOf(
                    ARG_OPTIONS to options,
                    PATH_ARG_KEY to "image-occlusion/$suffix",
                    ARG_KEY_EDITOR_DECK_ID to editorWorkingDeckId,
                )
            return SingleFragmentActivity.getIntent(context, ImageOcclusion::class, arguments)
        }
    }
}
