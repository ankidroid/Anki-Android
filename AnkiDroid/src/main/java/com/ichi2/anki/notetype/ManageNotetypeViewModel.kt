/****************************************************************************************
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
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.NoteTypeId
import com.ichi2.libanki.getNotetype
import com.ichi2.libanki.getNotetypeNameIdUseCount
import com.ichi2.libanki.removeNotetype
import com.ichi2.libanki.updateNotetype
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @see ManageNotetypes
 */
class ManageNotetypeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NotetypesUiState())
    val uiState: StateFlow<NotetypesUiState> = _uiState.asStateFlow()

    /**
     * Reference to the coroutine started in response to a filter request. As every change in the
     * query makes a request, this job can be used to cancel previous coroutines to avoid useless
     * work.
     */
    private var filterJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isProcessing = true) }
            refreshNotetypes()
        }
    }

    /**
     * Refreshes the list of notetypes, additionally filtering the list. Also calls to emit the new
     * state.
     * @param query the option query string to use to filter the list of notetypes
     */
    private suspend fun refreshNotetypes(query: String = "") {
        val notetypes =
            withCol {
                getNotetypeNameIdUseCount()
                    .filter { backendNotetype ->
                        if (query.isEmpty()) {
                            true
                        } else {
                            backendNotetype.name.lowercase().contains(query.lowercase())
                        }
                    }.map { backendNotetype ->
                        NotetypeItemUiState(
                            id = backendNotetype.id,
                            name = backendNotetype.name,
                            useCount = backendNotetype.useCount,
                            onNavigateTo = { destination ->
                                _uiState.update { state ->
                                    state.copy(destination = destination)
                                }
                            },
                        )
                    }
            }
        _uiState.update { state ->
            state.copy(
                notetypes = notetypes,
                isProcessing = false,
            )
        }
    }

    fun rename(
        noteTypeId: NoteTypeId,
        newName: String,
    ) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isProcessing = true) }
            withCol {
                val initialNotetype = getNotetype(noteTypeId)
                val renamedNotetype = initialNotetype.copy { this.name = newName }
                updateNotetype(renamedNotetype)
            }
            refreshNotetypes()
        }
    }

    fun delete(noteTypeId: NoteTypeId) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isProcessing = true) }
            withCol {
                removeNotetype(noteTypeId)
            }
            refreshNotetypes()
        }
    }

    fun filter(query: String) {
        filterJob?.cancel()
        filterJob =
            viewModelScope.launch {
                refreshNotetypes(query)
            }
    }

    /**
     * Mark the previously sent [NotetypesDestination] as consumed by the ui. Done to prevent
     * situations when we would come back to the screen and the navigation requests is again seen
     * and we would enter in a loop.
     */
    fun markNavigationRequestAsDone() {
        _uiState.update { state -> state.copy(destination = null) }
    }
}
