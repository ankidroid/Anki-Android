// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class TimeboxUiState(
    val isEnabled: Boolean = false,
    val minutes: Int = TimeboxSettingsViewModel.DEFAULT_TIMEBOX_MINUTES,
)

/**
 * State holder for [TimeboxSettingsFragment] and [TimeboxSettingsScreen].
 */
class TimeboxSettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TimeboxUiState())
    val uiState: StateFlow<TimeboxUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val limitMinutes = CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES)
            val isEnabled = limitMinutes > 0
            val minutes = if (isEnabled) limitMinutes else DEFAULT_TIMEBOX_MINUTES
            _uiState.value =
                TimeboxUiState(
                    isEnabled = isEnabled,
                    minutes = minutes,
                )
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        viewModelScope.launch {
            // TODO: Follow-up issue: persist the old non-zero time limit in Android storage when disabled so the value is restored on re-enabling
            val minutesToSave = if (enabled) _uiState.value.minutes else 0
            CollectionPreferences.setTimeboxTimeLimit(minutesToSave.toDuration(DurationUnit.MINUTES))
        }
    }

    fun setMinutes(newMinutes: Int) {
        val clamped = newMinutes.coerceIn(MIN_TIMEBOX_MINUTES, MAX_TIMEBOX_MINUTES)
        _uiState.update { it.copy(minutes = clamped) }
        if (_uiState.value.isEnabled) {
            viewModelScope.launch {
                CollectionPreferences.setTimeboxTimeLimit(clamped.toDuration(DurationUnit.MINUTES))
            }
        }
    }

    fun increment() = setMinutes(_uiState.value.minutes + 1)

    fun decrement() = setMinutes(_uiState.value.minutes - 1)

    companion object {
        const val DEFAULT_TIMEBOX_MINUTES = 20
        const val MIN_TIMEBOX_MINUTES = 1
        const val MAX_TIMEBOX_MINUTES = 999
    }
}
