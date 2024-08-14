/*
 *  Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
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

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.libanki.CardId
import com.ichi2.libanki.DeckId

/**
 * Defines various configurations for opening the NoteEditor fragment with specific data or actions.
 */
sealed interface NoteEditorLauncher {

    /**
     * Generates an intent to open the NoteEditor fragment with the configured parameters.
     *
     * @param context The context from which the intent is launched.
     * @param action Optional action string for the intent.
     * @return Intent configured to launch the NoteEditor fragment.
     */
    fun getIntent(context: Context, action: String? = null) =
        SingleFragmentActivity.getIntent(context, NoteEditor::class, toBundle(), action)

    /**
     * Converts the configuration into a Bundle to pass arguments to the NoteEditor fragment.
     *
     * @return Bundle containing arguments specific to this configuration.
     */
    fun toBundle(): Bundle

    /**
     * Represents opening the NoteEditor with an image occlusion.
     * @property imageUri The URI of the image to occlude.
     */
    data class ImageOcclusion(val imageUri: Uri?) : NoteEditorLauncher {
        override fun toBundle(): Bundle = bundleOf(
            NoteEditor.EXTRA_CALLER to NoteEditor.CALLER_IMG_OCCLUSION,
            NoteEditor.EXTRA_IMG_OCCLUSION to imageUri
        )
    }

    /**
     * Represents opening the NoteEditor with custom arguments.
     * @property arguments The bundle of arguments to pass.
     */
    data class PassArguments(val arguments: Bundle) : NoteEditorLauncher {
        override fun toBundle(): Bundle {
            return arguments
        }
    }

    /**
     * Represents adding a note to the NoteEditor within a specific deck (Optional).
     * @property deckId The ID of the deck where the note should be added.
     */
    data class AddNote(val deckId: DeckId? = null) : NoteEditorLauncher {
        override fun toBundle(): Bundle = bundleOf(
            NoteEditor.EXTRA_CALLER to NoteEditor.CALLER_DECKPICKER
        ).also { bundle ->
            deckId?.let { deckId -> bundle.putLong(NoteEditor.EXTRA_DID, deckId) }
        }
    }

    /**
     * Represents adding a note to the NoteEditor from the card browser.
     * @property viewModel The view model containing data from the card browser.
     */
    data class AddNoteFromCardBrowser(val viewModel: CardBrowserViewModel) :
        NoteEditorLauncher {
        override fun toBundle(): Bundle {
            val bundle = bundleOf(
                NoteEditor.EXTRA_CALLER to NoteEditor.CALLER_CARDBROWSER_ADD,
                NoteEditor.EXTRA_TEXT_FROM_SEARCH_VIEW to viewModel.searchTerms
            )
            if (viewModel.lastDeckId?.let { id -> id > 0 } == true) {
                bundle.putLong(NoteEditor.EXTRA_DID, viewModel.lastDeckId!!)
            }
            return bundle
        }
    }

    /**
     * Represents adding a note to the NoteEditor from the reviewer.
     * @property animation The animation direction to use when transitioning.
     */
    data class AddNoteFromReviewer(val animation: ActivityTransitionAnimation.Direction? = null) :
        NoteEditorLauncher {
        override fun toBundle(): Bundle = bundleOf(
            NoteEditor.EXTRA_CALLER to NoteEditor.CALLER_REVIEWER_ADD
        ).also { bundle ->
            animation?.let { animation ->
                bundle.putParcelable(
                    AnkiActivity.FINISH_ANIMATION_EXTRA,
                    animation as Parcelable
                )
            }
        }
    }

    /**
     * Allows to move from Instant note editor to standard note editor while keeping the text content
     *
     * @property sharedText The shared text content for the instant note.
     */
    data class AddInstantNote(val sharedText: String) : NoteEditorLauncher {
        override fun toBundle(): Bundle = bundleOf(
            NoteEditor.EXTRA_CALLER to NoteEditor.INSTANT_NOTE_EDITOR,
            Intent.EXTRA_TEXT to sharedText
        )
    }

    /**
     * Represents editing a card in the NoteEditor.
     * @property cardId The ID of the card to edit.
     * @property animation The animation direction.
     */
    data class EditCard(val cardId: CardId, val animation: ActivityTransitionAnimation.Direction) :
        NoteEditorLauncher {
        override fun toBundle(): Bundle = bundleOf(
            NoteEditor.EXTRA_CALLER to NoteEditor.CALLER_EDIT,
            NoteEditor.EXTRA_CARD_ID to cardId,
            AnkiActivity.FINISH_ANIMATION_EXTRA to animation as Parcelable
        )
    }

    /**
     * Represents editing a note in the NoteEditor from the previewer.
     * @property cardId The ID of the card associated with the note to edit.
     */
    data class EditNoteFromPreviewer(val cardId: Long) : NoteEditorLauncher {
        override fun toBundle(): Bundle = bundleOf(
            NoteEditor.EXTRA_CALLER to NoteEditor.CALLER_PREVIEWER_EDIT,
            NoteEditor.EXTRA_EDIT_FROM_CARD_ID to cardId
        )
    }

    /**
     * Represents copying a note to the NoteEditor.
     * @property deckId The ID of the deck where the note should be copied.
     * @property fieldsText The text content of the fields to copy.
     * @property tags Optional list of tags to assign to the copied note.
     */
    data class CopyNote(
        val deckId: DeckId,
        val fieldsText: String,
        val tags: List<String>? = null
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = bundleOf(
            NoteEditor.EXTRA_CALLER to NoteEditor.CALLER_NOTEEDITOR,
            NoteEditor.EXTRA_DID to deckId,
            NoteEditor.EXTRA_CONTENTS to fieldsText
        ).also { bundle ->
            tags?.let { tags -> bundle.putStringArray(NoteEditor.EXTRA_TAGS, tags.toTypedArray()) }
        }
    }
}
