// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit

data class LearnAheadUiState(
    val isLoading: Boolean = true,
    val isEnabled: Boolean = false,
    val limit: LearnAheadLimit = LearnAheadLimit.DEFAULT,
)

/**
 * State holder for [LearnAheadSettingsFragment] and [LearnAheadSettingsScreen].
 */
class LearnAheadSettingsViewModel : ViewModel() {
    val uiState: StateFlow<LearnAheadUiState>
        field = MutableStateFlow(LearnAheadUiState())

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val limitMinutes = CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES)
            val isEnabled = limitMinutes > 0
            val custom = LearnAheadLimit.fromMinutes(Prefs.customLearnAheadLimit) ?: LearnAheadLimit.DEFAULT
            val limit =
                if (isEnabled) {
                    val parsed = LearnAheadLimit.fromMinutes(limitMinutes) ?: LearnAheadLimit.DEFAULT
                    Prefs.customLearnAheadLimit = parsed.minutes
                    parsed
                } else {
                    custom
                }
            uiState.value =
                LearnAheadUiState(
                    isLoading = false,
                    isEnabled = isEnabled,
                    limit = limit,
                )
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        uiState.update { it.copy(isEnabled = enabled) }
        viewModelScope.launch {
            val limitToSave = if (enabled) uiState.value.limit else null
            CollectionPreferences.setLearnAhead(limitToSave)
        }
    }

    fun setMinutes(newMinutes: Int) {
        val parsed = LearnAheadLimit.fromMinutes(newMinutes) ?: return
        Prefs.customLearnAheadLimit = parsed.minutes
        uiState.update { it.copy(limit = parsed) }
        if (uiState.value.isEnabled) {
            viewModelScope.launch {
                CollectionPreferences.setLearnAhead(parsed)
            }
        }
    }
}
