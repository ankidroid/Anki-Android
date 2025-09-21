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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs.KEY_EDITOR_DECK_ID
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs.KEY_ID
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs.KEY_KIND
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs.KEY_PATH
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Argument keys used for passing data to the [com.ichi2.anki.pages.ImageOcclusion] fragment.*/
object ImageOcclusionArgs {
    const val KEY_KIND = "kind"
    const val KEY_ID = "id"
    const val KEY_PATH = "imagePath"
    const val KEY_EDITOR_DECK_ID = "arg_key_editor_deck_id"
}

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
    val oldDeckID: Long

    /**
     * A [JSONObject] containing options for initializing the WebView. This includes
     * the type of operation ("add" or "edit"), and relevant IDs and paths.
     */
    val webViewOptions: JSONObject

    init {
        val deckId: Long = checkNotNull(savedStateHandle[KEY_EDITOR_DECK_ID])
        val kind: String = checkNotNull(savedStateHandle[KEY_KIND])
        val noteOrNotetypeId: Long = checkNotNull(savedStateHandle[KEY_ID])
        val imagePath: String? = savedStateHandle[KEY_PATH]

        selectedDeckId = deckId
        oldDeckID = deckId

        webViewOptions =
            JSONObject().apply {
                put("kind", kind)
                if (kind == "add") {
                    put("imagePath", imagePath)
                    put("notetypeId", noteOrNotetypeId)
                } else {
                    put("noteId", noteOrNotetypeId)
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

    fun onSaveOperationCompleted() {
        if (oldDeckID == selectedDeckId) return
        viewModelScope.launch {
            CollectionManager.withCol { backend.setCurrentDeck(oldDeckID) }
        }
    }
}
