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

    lateinit var windowTitle: String

    val flowOfDescription = MutableStateFlow(stateHandle.get<String>(STATE_DESCRIPTION) ?: "")

    var description
        get() = flowOfDescription.value
        set(value) {
            stateHandle[STATE_DESCRIPTION] = value
            flowOfDescription.value = value
        }

    // TODO: make this a unit
    val flowOfDismissDialog = MutableStateFlow(false)

    val flowOfShowDiscardChanges = MutableSharedFlow<Unit>()

    val flowOfInitCompleted = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            windowTitle = withCol { decks.getLegacy(deckId)!!.name }
            if (description.isEmpty()) {
                description = getDescription()
            }
            flowOfInitCompleted.emit(true)
        }
    }

    fun onBackRequested() =
        viewModelScope.launch {
            if (getDescription() == description) {
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
            setDescription(description)
            Timber.i("closing deck description dialog")
            flowOfDismissDialog.emit(true)
        }

    private suspend fun getDescription() = withCol { decks.getLegacy(deckId)!!.description }

    private suspend fun setDescription(value: String) {
        Timber.i("updating deck description")
        withCol { decks.update(deckId) { description = value } }
    }

    companion object {
        const val ARG_DECK_ID = "deckId"
        const val STATE_DESCRIPTION = "description"
    }
}
