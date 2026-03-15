/*
 * Copyright (c) 2024 Neel Doshi <neeldoshi147@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype.fieldeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.getColUnsafe
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.notetype.fieldeditor.NoteTypeFieldEditor.Companion.EXTRA_NOTETYPE_ID
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class NoteTypeFieldEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state by lazy {
        val noteTypeID = savedStateHandle.get<Long>(EXTRA_NOTETYPE_ID) ?: 0
        val notetype = getColUnsafe().notetypes.get(noteTypeID)!!
        MutableStateFlow(
            NoteTypeFieldEditorState(
                notetype = notetype,
                noteTypeId = noteTypeID,
            ),
        )
    }
    val state: StateFlow<NoteTypeFieldEditorState> = _state.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            refreshNoteTypes()
        }
    }

    fun updateCurrentPosition(position: Int) {
        _state.update { oldState -> oldState.copy(currentPos = position) }
    }

    suspend fun add(name: String) {
        Timber.d("doInBackgroundAddField")
        _state.update { oldState -> oldState.copy(isLoading = true) }
        runCatching {
            withCol {
                val notetype = notetypes.get(state.value.noteTypeId)!!
                notetypes.addField(notetype, notetypes.newField(name))
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    suspend fun rename(
        field: Field,
        name: String,
    ) {
        Timber.d("doInBackgroundRenameField")
        _state.update { oldState -> oldState.copy(isLoading = true) }
        runCatching {
            withCol {
                val notetype = notetypes.get(state.value.noteTypeId)!!
                notetypes.renameField(notetype, field, name)
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    suspend fun delete(field: Field) {
        Timber.d("doInBackGroundDeleteField")
        _state.update { oldState -> oldState.copy(isLoading = true) }
        runCatching {
            withCol {
                val notetype = notetypes.get(state.value.noteTypeId)!!
                notetypes.removeField(notetype, field)
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    suspend fun changeSort(position: Int) {
        Timber.d("doInBackgroundChangeSortField")
        runCatching {
            withCol {
                val notetype = notetypes.get(state.value.noteTypeId)!!
                notetypes.setSortIndex(notetype, position)
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    suspend fun reposition(
        field: Field,
        position: Int,
    ) {
        Timber.d("doInBackgroundRepositionField")
        runCatching {
            withCol {
                val notetype = notetypes.get(state.value.noteTypeId)!!
                notetypes.repositionField(notetype, field, position)
            }
        }
        initialize()
    }

    suspend fun languageHint(locale: LanguageHint) {
        runCatching {
            withCol {
                setLanguageHintForField(
                    notetypes,
                    state.value.notetype,
                    state.value.currentPos,
                    locale,
                )
            }
        }
        initialize()
    }

    fun undo() {
    }

    suspend fun refreshNoteTypes() {
        val notetype = withCol { notetypes.get(state.value.noteTypeId)!! }
        _state.update { oldState -> oldState.copy(isLoading = false, notetype = notetype) }
    }
}
