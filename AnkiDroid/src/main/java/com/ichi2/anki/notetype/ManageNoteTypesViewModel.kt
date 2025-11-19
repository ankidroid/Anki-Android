/****************************************************************************************
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.notetype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.notetypes.Notetype
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.getNotetypeNameIdUseCount
import com.ichi2.anki.libanki.removeNotetype
import com.ichi2.anki.libanki.updateNotetype
import com.ichi2.anki.notetype.ManageNoteTypesState.CardEditor
import com.ichi2.anki.notetype.ManageNoteTypesState.FieldsEditor
import com.ichi2.anki.notetype.ManageNoteTypesState.ReportableException
import com.ichi2.anki.notetype.ManageNoteTypesState.UserMessage
import com.ichi2.anki.notetype.NoteTypeItemState.Companion.asModel
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException

class ManageNoteTypesViewModel : ViewModel() {
    private val _state = MutableStateFlow(ManageNoteTypesState())
    val state: StateFlow<ManageNoteTypesState> = _state.asStateFlow()
    private lateinit var initialNoteTypes: List<NoteTypeItemState>

    init {
        refreshNoteTypes()
    }

    fun refreshNoteTypes() {
        _state.update { oldState -> oldState.copy(isLoading = true) }
        viewModelScope.launch {
            withCol { safeGetNotetypeNameIdUseCount() }
                .onFailure {
                    _state.update { oldState ->
                        oldState.copy(isLoading = false, error = ReportableException(it))
                    }
                }.onSuccess {
                    initialNoteTypes = it
                    _state.update { oldState ->
                        oldState.copy(isLoading = false, noteTypes = it)
                    }
                }
        }
    }

    fun filter(query: String) {
        val matchedNoteTypes =
            initialNoteTypes.filter { entry ->
                entry.name.contains(query)
            }
        _state.update { oldState ->
            oldState.copy(isLoading = false, noteTypes = matchedNoteTypes, searchQuery = query)
        }
    }

    fun rename(
        nid: NoteTypeId,
        name: String,
    ) {
        _state.update { oldState -> oldState.copy(isLoading = true) }
        viewModelScope.launch {
            undoableOp<OpChanges> {
                safeRenameNoteType(nid, name)
                    .onSuccess { changes ->
                        _state.update { oldState ->
                            val updatedNoteTypes =
                                oldState.noteTypes
                                    .map { noteTypeState ->
                                        if (noteTypeState.id == nid) {
                                            noteTypeState.copy(name = name)
                                        } else {
                                            noteTypeState
                                        }
                                    }.also { initialNoteTypes = it }
                            oldState.copy(isLoading = false, noteTypes = updatedNoteTypes)
                        }
                        return@undoableOp changes
                    }.onFailure {
                        // TODO: Change to CardTypeException: https://github.com/ankidroid/Anki-Android-Backend/issues/537
                        // Card template 1 in note type 'character' has a problem.
                        // Expected to find a field replacement on the front of the card template.
                        _state.update { oldState ->
                            oldState.copy(
                                isLoading = false,
                                error = ReportableException(it, it !is BackendException),
                            )
                        }
                        OpChanges.getDefaultInstance()
                    }
                OpChanges.getDefaultInstance()
            }
        }
    }

    fun delete(nid: NoteTypeId) {
        _state.update { oldState -> oldState.copy(isLoading = true) }
        val noteTypesCount = _state.value.noteTypes.size
        viewModelScope.launch {
            undoableOp<OpChanges> {
                if (noteTypesCount <= 1) {
                    _state.update { oldState ->
                        oldState.copy(isLoading = false, message = UserMessage.DeletingLastModel)
                    }
                    return@undoableOp OpChanges.getDefaultInstance()
                }
                safeRemoveNoteType(nid)
                    .onSuccess { changes ->
                        _state.update { oldState ->
                            val updatedNoteTypes =
                                oldState.noteTypes
                                    .filter { it.id != nid }
                                    .also { initialNoteTypes = it }
                            oldState.copy(isLoading = false, noteTypes = updatedNoteTypes)
                        }
                        return@undoableOp changes
                    }.onFailure {
                        _state.update { oldState ->
                            oldState.copy(isLoading = false, error = ReportableException(it))
                        }
                        return@undoableOp OpChanges.getDefaultInstance()
                    }
                OpChanges.getDefaultInstance()
            }
        }
    }

    fun onItemClick(entry: NoteTypeItemState) {
        _state.update { oldState ->
            oldState.copy(destination = FieldsEditor(entry.id, entry.name))
        }
    }

    fun onCardEditorRequested(entry: NoteTypeItemState) {
        _state.update { oldState ->
            oldState.copy(destination = CardEditor(entry.id))
        }
    }

    fun clearMessage() {
        _state.update { oldState -> oldState.copy(message = null) }
    }

    fun clearError() {
        _state.update { oldState -> oldState.copy(error = null) }
    }

    fun clearDestination() {
        _state.update { oldState -> oldState.copy(destination = null) }
    }
}

private fun Collection.safeRenameNoteType(
    nid: NoteTypeId,
    newName: String,
): Result<OpChanges> =
    try {
        val currentNoteType: Notetype = getNotetype(nid)
        val renamedNotetype = currentNoteType.copy { this.name = newName }
        Result.success(updateNotetype(renamedNotetype))
    } catch (exception: Exception) {
        Result.failure(exception)
    }

private fun Collection.safeRemoveNoteType(nid: NoteTypeId): Result<OpChanges> =
    try {
        Result.success(removeNotetype(nid))
    } catch (exception: Exception) {
        Result.failure(exception)
    }

private fun Collection.safeGetNotetypeNameIdUseCount(): Result<List<NoteTypeItemState>> =
    try {
        Result.success(getNotetypeNameIdUseCount().map(::asModel))
    } catch (exception: Exception) {
        Result.failure(exception)
    }
