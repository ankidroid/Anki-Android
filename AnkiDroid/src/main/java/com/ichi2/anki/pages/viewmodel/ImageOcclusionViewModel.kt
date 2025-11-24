/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.pages.viewmodel

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.pages.ImageOcclusion
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import timber.log.Timber

@Parcelize
data class ImageOcclusionArgs(
    val kind: String,
    val id: Long,
    val imagePath: String?,
    val editorDeckId: Long,
) : Parcelable

/**
 * ViewModel for the Image Occlusion fragment.
 */
class ImageOcclusionViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var selectedDeckId: Long

    /**
     * The ID of the deck that was originally selected when the editor was opened.
     * This is used to restore the deck after saving a note to prevent unexpected deck changes.
     */
    val oldDeckId: Long

    /**
     * A [JSONObject] containing options for loading the [image occlusion page][ImageOcclusion].
     * This includes the type of operation ("add" or "edit"), and relevant IDs and paths.
     *
     * Defined in https://github.com/ankitects/anki/blob/main/ts/routes/image-occlusion/lib.ts
     */
    val webViewOptions: JSONObject

    init {
        val args: ImageOcclusionArgs = checkNotNull(savedStateHandle[ImageOcclusion.IO_ARGS_KEY])

        selectedDeckId = args.editorDeckId
        oldDeckId = args.editorDeckId

        webViewOptions =
            JSONObject().apply {
                put("kind", args.kind)
                if (args.kind == "add") {
                    put("imagePath", args.imagePath)
                    put("notetypeId", args.id)
                } else {
                    put("noteId", args.id)
                }
            }
    }

    /**
     * Handles the selection of a new deck.
     *
     * @param deckId The [DeckId] object representing the selected deck. Can be null if no deck is selected.
     */
    fun handleDeckSelection(deckId: DeckId): Boolean {
        if (deckId == selectedDeckId) return false
        selectedDeckId = deckId
        return true
    }

    /**
     * Executed when the 'save' operation is completed, before the UI receives the response
     */
    fun onSaveOperationCompleted() {
        Timber.i("save operation completed")
        if (oldDeckId == selectedDeckId) return
        // reset to the previous deck that the backend "saw" as selected, this
        // avoids other screens unexpectedly having their working decks modified(
        // most important being the Reviewer where the user would find itself
        // studying another deck after editing a note with changing the deck)
        viewModelScope.launch {
            CollectionManager.withCol { backend.setCurrentDeck(oldDeckId) }
        }
    }
}
