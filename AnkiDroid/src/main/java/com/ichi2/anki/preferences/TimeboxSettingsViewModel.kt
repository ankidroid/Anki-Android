// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class TimeboxUiState(
    val isLoading: Boolean = true,
    val isEnabled: Boolean = false,
    val limit: TimeboxTimeLimit = TimeboxTimeLimit.DEFAULT,
)

/**
 * State holder for [TimeboxSettingsFragment] and [TimeboxSettingsScreen].
 */
class TimeboxSettingsViewModel(
    private val prefs: PrefsRepository = Prefs,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TimeboxUiState())
    val uiState: StateFlow<TimeboxUiState> = _uiState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
    val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()

    private var persistedLimit: TimeboxTimeLimit = TimeboxTimeLimit.DEFAULT
    private var persistedEnabled: Boolean = false
    private var persistedCustomLimit: TimeboxTimeLimit = prefs.timeboxCustomLimit
    private var saveOperationCount = 0

    fun consumeError() {
        _errorEvents.resetReplayCache()
    }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            pendingSaveJob?.join()
            try {
                val collectionLimit = CollectionPreferences.getTimeboxLimit()
                val isEnabled = collectionLimit.isEnabled
                val limit = collectionLimit.resolveDisplayLimit(prefs)
                persistedLimit = limit
                persistedEnabled = isEnabled
                persistedCustomLimit = prefs.timeboxCustomLimit
                _uiState.value =
                    TimeboxUiState(
                        isLoading = false,
                        isEnabled = isEnabled,
                        limit = limit,
                    )
            } catch (exc: Exception) {
                if (exc is CancellationException) throw exc
                Timber.e(exc, "Failed to load timebox settings")
                _errorEvents.tryEmit(CommonString.something_wrong)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        val limitToSave =
            if (enabled) {
                _uiState.value.limit
            } else {
                TimeboxTimeLimit.DISABLED
            }
        saveLimit(
            limitToSave = limitToSave,
            onSuccess = {
                persistedEnabled = enabled
                if (enabled) {
                    persistedLimit = limitToSave
                    persistedCustomLimit = limitToSave
                }
            },
            onFailure = {
                _uiState.update { it.copy(isEnabled = persistedEnabled) }
            },
        )
    }

    fun setMinutes(newMinutes: Int) {
        val limit = TimeboxTimeLimit.fromMinutes(newMinutes) ?: return
        _uiState.update { it.copy(limit = limit) }
        prefs.timeboxCustomLimit = limit
        if (_uiState.value.isEnabled) {
            saveLimit(
                limitToSave = limit,
                onSuccess = {
                    persistedLimit = limit
                    persistedCustomLimit = limit
                },
                onFailure = {
                    prefs.timeboxCustomLimit = persistedCustomLimit
                    _uiState.update { it.copy(limit = persistedLimit) }
                },
            )
        } else {
            persistedLimit = limit
            persistedCustomLimit = limit
        }
    }

    fun increment() = setMinutes(_uiState.value.limit.minutes + 1)

    fun decrement() = setMinutes(_uiState.value.limit.minutes - 1)

    private fun saveLimit(
        limitToSave: TimeboxTimeLimit,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val operationId = ++saveOperationCount
        pendingSaveJob =
            applicationScope.launch {
                try {
                    CollectionPreferences.setTimeboxLimit(limitToSave)
                    onSuccess()
                } catch (exc: Exception) {
                    if (exc is CancellationException) throw exc
                    Timber.e(exc, "Failed to save timebox limit")
                    if (operationId == saveOperationCount) {
                        onFailure()
                    }
                    _errorEvents.tryEmit(CommonString.something_wrong)
                }
            }
    }

    companion object {
        var pendingSaveJob: Job? = null
    }
}
