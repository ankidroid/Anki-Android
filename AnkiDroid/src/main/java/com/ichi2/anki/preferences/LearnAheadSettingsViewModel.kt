// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
            pendingSaveJob?.join()
            try {
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
            } catch (exc: Exception) {
                if (exc is CancellationException) throw exc
                Timber.e(exc, "Failed to load learn ahead settings")
                uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        uiState.update { it.copy(isEnabled = enabled) }
        val limitToSave = if (enabled) uiState.value.limit else null
        saveLimit(limitToSave)
    }

    fun setMinutes(newMinutes: Int) {
        val parsed = LearnAheadLimit.fromMinutes(newMinutes) ?: return
        Prefs.customLearnAheadLimit = parsed.minutes
        uiState.update { it.copy(limit = parsed) }
        if (uiState.value.isEnabled) {
            saveLimit(parsed)
        }
    }

    private fun saveLimit(limitToSave: LearnAheadLimit?) {
        pendingSaveJob =
            applicationScope.launch {
                try {
                    CollectionPreferences.setLearnAhead(limitToSave)
                } catch (exc: Exception) {
                    if (exc is CancellationException) throw exc
                    Timber.e(exc, "Failed to save learn ahead limit")
                }
            }
    }

    companion object {
        var pendingSaveJob: Job? = null
    }
}
