/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.ext.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class EditDeckDescriptionDialogViewModel(
    private val stateHandle: SavedStateHandle,
) : ViewModel() {
    val deckId: DeckId
        get() = requireNotNull(stateHandle.get<DeckId>(ARG_DECK_ID))

    @VisibleForTesting
    var userHasMadeChanges: Boolean
        get() = stateHandle.get<Boolean>(STATE_USER_MADE_CHANGES) ?: false
        set(value) {
            stateHandle[STATE_USER_MADE_CHANGES] = value
        }

    lateinit var windowTitle: String

    val flowOfDescription = MutableStateFlow(stateHandle.get<String>(STATE_DESCRIPTION) ?: "")

    val flowOfFormatAsMarkdown = MutableStateFlow(stateHandle.get<Boolean>(STATE_FORMAT_AS_MARKDOWN) ?: false)

    var description
        get() = flowOfDescription.value
        set(value) {
            userHasMadeChanges = true
            stateHandle[STATE_DESCRIPTION] = value
            flowOfDescription.value = value
        }

    var formatAsMarkdown: Boolean
        get() = flowOfFormatAsMarkdown.value
        set(value) {
            userHasMadeChanges = true
            stateHandle[STATE_FORMAT_AS_MARKDOWN] = value
            flowOfFormatAsMarkdown.value = value
        }

    private val dialogState: DeckDescriptionState
        get() =
            DeckDescriptionState(
                description = this.description,
                formatAsMarkdown = this.formatAsMarkdown,
            )

    // TODO: make this a unit
    val flowOfDismissDialog = MutableStateFlow(false)

    val flowOfShowDiscardChanges = MutableSharedFlow<Unit>()

    val flowOfInitCompleted = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            windowTitle = withCol { decks.getLegacy(deckId)!!.name }
            if (!userHasMadeChanges) {
                val state = queryDescriptionState()
                description = state.description
                formatAsMarkdown = state.formatAsMarkdown
                userHasMadeChanges = false
            }
            flowOfInitCompleted.emit(true)
        }
    }

    fun onBackRequested() =
        viewModelScope.launch {
            if (queryDescriptionState() == dialogState) {
                closeWithoutSaving()
                return@launch
            }

            Timber.i("asking if user should discard changes")
            flowOfShowDiscardChanges.emit(Unit)
        }

    fun closeWithoutSaving() =
        viewModelScope.launch {
            Timber.i("Closing dialog without saving")
            flowOfDismissDialog.emit(true)
        }

    fun saveAndExit() =
        viewModelScope.launch {
            save()
            Timber.i("closing deck description dialog")
            flowOfDismissDialog.emit(true)
        }

    private suspend fun queryDescriptionState() =
        withCol {
            decks.getLegacy(deckId)!!.let {
                DeckDescriptionState(
                    description = it.description,
                    formatAsMarkdown = it.descriptionAsMarkdown,
                )
            }
        }

    private suspend fun save() {
        Timber.i("updating deck description")
        withCol {
            decks.update(deckId) {
                this.description = dialogState.description
                this.descriptionAsMarkdown = dialogState.formatAsMarkdown
            }
        }
    }

    /**
     * State for [EditDeckDescriptionDialog].
     *
     * Simplifies detecting user changes
     *
     * @param description see [com.ichi2.anki.libanki.Deck.description]
     * @param formatAsMarkdown see [com.ichi2.anki.libanki.Deck.markdownDescription]
     */
    private data class DeckDescriptionState(
        val description: String,
        val formatAsMarkdown: Boolean,
    )

    companion object {
        const val ARG_DECK_ID = "deckId"
        const val STATE_DESCRIPTION = "description"
        const val STATE_FORMAT_AS_MARKDOWN = "format_as_markdown"
        const val STATE_USER_MADE_CHANGES = "user_made_changes"
    }
}
