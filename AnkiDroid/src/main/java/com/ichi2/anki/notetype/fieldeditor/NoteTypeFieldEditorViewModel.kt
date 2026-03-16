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
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.notetype.fieldeditor.NoteTypeFieldEditor.Companion.EXTRA_NOTETYPE_ID
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class NoteTypeFieldEditorViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val noteTypeId = savedStateHandle.get<Long>(EXTRA_NOTETYPE_ID) ?: 0
    val state: StateFlow<NoteTypeFieldEditorState>
        field = MutableStateFlow(NoteTypeFieldEditorState(fieldsLabels = emptyList()))

    init {
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            refreshNoteTypes()
        }
    }

    fun updateCurrentPosition(position: Int) {
        state.update { oldState -> oldState.copy(currentPos = position) }
    }

    suspend fun add(name: String) {
        Timber.d("doInBackgroundAddField")
        runCatching {
            withCol {
                val notetype = notetypes.get(noteTypeId)!!
                notetypes.addField(notetype, notetypes.newField(name))
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    suspend fun rename(
        position: Int,
        name: String,
    ) {
        Timber.d("doInBackgroundRenameField")
        runCatching {
            withCol {
                val notetype = notetypes.get(noteTypeId)!!
                val field = notetype.getField(position)
                notetypes.renameField(notetype, field, name)
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    suspend fun delete(position: Int) {
        Timber.d("doInBackGroundDeleteField")
        runCatching {
            withCol {
                val notetype = notetypes.get(noteTypeId)!!
                val field = notetype.getField(position)
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
                val notetype = notetypes.get(noteTypeId)!!
                notetypes.setSortIndex(notetype, position)
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    suspend fun reposition(
        oldPosition: Int,
        newPosition: Int,
    ) {
        Timber.d("doInBackgroundRepositionField")
        runCatching {
            withCol {
                val notetype = notetypes.get(noteTypeId)!!
                val field = notetype.getField(oldPosition)
                notetypes.repositionField(notetype, field, newPosition)
            }
        }
        initialize()
    }

    suspend fun languageHint(
        position: Int,
        locale: LanguageHint,
    ) {
        runCatching {
            withCol {
                val notetype = notetypes.get(noteTypeId)!!
                setLanguageHintForField(
                    notetypes,
                    notetype,
                    position,
                    locale,
                )
            }
        }
        initialize()
    }

    suspend fun refreshNoteTypes() {
        val notetype = withCol { notetypes.get(noteTypeId)!! }
        state.value =
            state.value.copy(
                currentPos = 0,
                sortf = notetype.sortf,
                fieldsLabels = notetype.fieldsNames,
            )
    }
}
