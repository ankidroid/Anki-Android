// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.DurationUnit

sealed interface LearnAheadUiState {
    data object Loading : LearnAheadUiState

    data object Error : LearnAheadUiState

    data class Loaded(
        val isEnabled: Boolean,
        val limit: LearnAheadLimit,
    ) : LearnAheadUiState
}

/**
 * State holder for [LearnAheadSettingsFragment] and [LearnAheadSettingsScreen].
 */
class LearnAheadSettingsViewModel : ViewModel() {
    val uiState: StateFlow<LearnAheadUiState>
        field = MutableStateFlow<LearnAheadUiState>(LearnAheadUiState.Loading)

    private val _errorEvents = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
    val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()

    private var persistedLimit: LearnAheadLimit = LearnAheadLimit.DEFAULT
    private var persistedEnabled: Boolean = false
    private var persistedCustomLimit: Int = Prefs.customLearnAheadLimit
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
                val limitMinutes = CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES)
                val isEnabled = limitMinutes > 0
                val limit =
                    if (isEnabled) {
                        val parsed = LearnAheadLimit.fromMinutes(limitMinutes) ?: LearnAheadLimit.DEFAULT
                        Prefs.customLearnAheadLimit = parsed.minutes
                        parsed
                    } else {
                        // When disabled (0m in collection), restore the user's previously chosen custom limit.
                        LearnAheadLimit.fromMinutes(Prefs.customLearnAheadLimit) ?: LearnAheadLimit.DEFAULT
                    }
                persistedLimit = limit
                persistedEnabled = isEnabled
                persistedCustomLimit = Prefs.customLearnAheadLimit
                uiState.value =
                    LearnAheadUiState.Loaded(
                        isEnabled = isEnabled,
                        limit = limit,
                    )
            } catch (exc: Exception) {
                if (exc is CancellationException) throw exc
                Timber.e(exc, "Failed to load learn ahead settings")
                _errorEvents.tryEmit(CommonString.something_wrong)
                uiState.value = LearnAheadUiState.Error
            }
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        Timber.d("toggleEnabled: %b", enabled)
        val current = uiState.value as? LearnAheadUiState.Loaded ?: return
        uiState.value = current.copy(isEnabled = enabled)
        val limitToSave = if (enabled) current.limit else null
        saveLimit(
            limitToSave = limitToSave,
            onSuccess = {
                persistedEnabled = enabled
                if (limitToSave != null) {
                    persistedLimit = limitToSave
                    persistedCustomLimit = limitToSave.minutes
                }
            },
            onFailure = {
                val loaded = uiState.value as? LearnAheadUiState.Loaded
                if (loaded != null) {
                    uiState.value = loaded.copy(isEnabled = persistedEnabled)
                }
            },
        )
    }

    fun setLearnAheadLimit(limit: LearnAheadLimit) {
        Timber.d("setLearnAheadLimit: %d mins", limit.minutes)
        val current = uiState.value as? LearnAheadUiState.Loaded ?: return
        Prefs.customLearnAheadLimit = limit.minutes
        uiState.value = current.copy(limit = limit)
        if (current.isEnabled) {
            saveLimit(
                limitToSave = limit,
                onSuccess = {
                    persistedLimit = limit
                    persistedCustomLimit = limit.minutes
                },
                onFailure = {
                    Prefs.customLearnAheadLimit = persistedCustomLimit
                    val loaded = uiState.value as? LearnAheadUiState.Loaded
                    if (loaded != null) {
                        uiState.value = loaded.copy(limit = persistedLimit)
                    }
                },
            )
        } else {
            persistedLimit = limit
            persistedCustomLimit = limit.minutes
        }
    }

    /**
     * Sets the learn ahead limit in minutes, ignoring values outside [LearnAheadLimit.INPUT_RANGE].
     */
    fun setLearnAheadLimit(minutes: Int) {
        val parsed = LearnAheadLimit.fromMinutes(minutes) ?: return
        setLearnAheadLimit(parsed)
    }

    private fun saveLimit(
        limitToSave: LearnAheadLimit?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val operationId = ++saveOperationCount
        pendingSaveJob =
            applicationScope.launch {
                try {
                    CollectionPreferences.setLearnAhead(limitToSave)
                    onSuccess()
                } catch (exc: Exception) {
                    if (exc is CancellationException) throw exc
                    Timber.e(exc, "Failed to save learn ahead limit")
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
