// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.lifecycle.ViewModel
import com.ichi2.anki.CommonString
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
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
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import timber.log.Timber
import kotlin.coroutines.coroutineContext
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
    private var hasLoaded = false
    private var activeLoadJob: Job? = null
    private var userEditedLimit: LearnAheadLimit? = null
    private var userEditedToggle: Boolean? = null

    fun consumeError() {
        _errorEvents.resetReplayCache()
    }

    fun refresh() {
        loadSettings()
    }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        activeLoadJob?.cancel()
        val loadJob =
            applicationScope.launch {
                pendingSaveJob?.join()
                try {
                    val limitMinutes = CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES)
                    val collectionIsEnabled = limitMinutes > 0
                    val collectionLimit =
                        if (collectionIsEnabled) {
                            val parsed = LearnAheadLimit.fromMinutes(limitMinutes) ?: LearnAheadLimit.DEFAULT
                            if (userEditedLimit == null) {
                                Prefs.customLearnAheadLimit = parsed.minutes
                            }
                            parsed
                        } else {
                            // When disabled (0m in collection), restore the user's previously chosen custom limit.
                            LearnAheadLimit.fromMinutes(Prefs.customLearnAheadLimit) ?: LearnAheadLimit.DEFAULT
                        }

                    persistedLimit = collectionLimit
                    persistedEnabled = collectionIsEnabled
                    persistedCustomLimit = Prefs.customLearnAheadLimit
                    hasLoaded = true

                    val finalEnabled = userEditedToggle ?: collectionIsEnabled
                    val finalLimit = userEditedLimit ?: collectionLimit

                    uiState.value =
                        LearnAheadUiState.Loaded(
                            isEnabled = finalEnabled,
                            limit = finalLimit,
                        )

                    val userChangedToggle = userEditedToggle != null && userEditedToggle != collectionIsEnabled
                    val userChangedLimit = userEditedLimit != null && userEditedLimit != collectionLimit

                    if (finalEnabled) {
                        if (userChangedLimit || (userChangedToggle && collectionLimit != finalLimit)) {
                            saveLimit(
                                limitToSave = finalLimit,
                                onSuccess = { isLatest ->
                                    persistedLimit = finalLimit
                                    persistedCustomLimit = finalLimit.minutes
                                    persistedEnabled = true
                                    if (isLatest && userEditedLimit == null) {
                                        Prefs.customLearnAheadLimit = finalLimit.minutes
                                    }
                                },
                                onFailure = ::rollbackToPersistedState,
                            )
                        } else if (userChangedToggle) {
                            saveLimit(
                                limitToSave = finalLimit,
                                onSuccess = { isLatest ->
                                    persistedLimit = finalLimit
                                    persistedCustomLimit = finalLimit.minutes
                                    persistedEnabled = true
                                    if (isLatest && userEditedLimit == null) {
                                        Prefs.customLearnAheadLimit = finalLimit.minutes
                                    }
                                },
                                onFailure = ::rollbackToPersistedState,
                            )
                        }
                    } else {
                        if (userChangedToggle) {
                            saveLimit(
                                limitToSave = null,
                                onSuccess = {
                                    persistedEnabled = false
                                },
                                onFailure = ::rollbackToPersistedState,
                            )
                        }
                    }
                } catch (exc: Exception) {
                    if (exc is CancellationException) throw exc
                    Timber.e(exc, "Failed to load learn ahead settings")
                    if (isDatabaseCorruptException(exc)) {
                        DatabaseCorruption.isDetected = true
                    }
                    _errorEvents.tryEmit(CommonString.something_wrong)
                    uiState.value = LearnAheadUiState.Error
                } finally {
                    if (coroutineContext[Job]?.isCancelled != true) {
                        userEditedLimit = null
                        userEditedToggle = null
                    }
                    if (activeLoadJob === coroutineContext[Job]) {
                        activeLoadJob = null
                    }
                }
            }
        activeLoadJob = loadJob
        pendingSaveJob = loadJob
    }

    fun toggleEnabled(enabled: Boolean) {
        Timber.d("toggleEnabled: %b", enabled)
        if (!hasLoaded) {
            userEditedToggle = enabled
            loadSettings()
            return
        }
        val current = uiState.value as? LearnAheadUiState.Loaded ?: return
        uiState.value = current.copy(isEnabled = enabled)
        if (activeLoadJob?.isActive == true) {
            userEditedToggle = enabled
            ++saveOperationCount
            return
        }
        val limitToSave = if (enabled) current.limit else null
        saveLimit(
            limitToSave = limitToSave,
            onSuccess = { isLatest ->
                persistedEnabled = enabled
                if (limitToSave != null) {
                    persistedLimit = limitToSave
                    persistedCustomLimit = limitToSave.minutes
                    if (isLatest && userEditedLimit == null) {
                        Prefs.customLearnAheadLimit = limitToSave.minutes
                    }
                }
            },
            onFailure = ::rollbackToPersistedState,
        )
    }

    fun setLearnAheadLimit(limit: LearnAheadLimit) {
        Timber.d("setLearnAheadLimit: %d mins", limit.minutes)
        val current = uiState.value as? LearnAheadUiState.Loaded ?: return
        Prefs.customLearnAheadLimit = limit.minutes
        uiState.value = current.copy(limit = limit)
        if (activeLoadJob?.isActive == true) {
            userEditedLimit = limit
            ++saveOperationCount
            return
        }
        if (current.isEnabled) {
            saveLimit(
                limitToSave = limit,
                onSuccess = { isLatest ->
                    persistedLimit = limit
                    persistedCustomLimit = limit.minutes
                    persistedEnabled = true
                    if (isLatest && userEditedLimit == null) {
                        Prefs.customLearnAheadLimit = limit.minutes
                    }
                },
                onFailure = ::rollbackToPersistedState,
            )
        } else {
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

    private fun rollbackToPersistedState() {
        Prefs.customLearnAheadLimit = persistedCustomLimit
        val loaded = uiState.value as? LearnAheadUiState.Loaded ?: return
        uiState.value =
            loaded.copy(
                isEnabled = persistedEnabled,
                limit = persistedLimit,
            )
    }

    private fun saveLimit(
        limitToSave: LearnAheadLimit?,
        onSuccess: (isLatest: Boolean) -> Unit,
        onFailure: () -> Unit,
    ) {
        val previousJob = pendingSaveJob
        val operationId = ++saveOperationCount
        val saveJob =
            applicationScope.launch {
                try {
                    CollectionPreferences.setLearnAhead(limitToSave)
                    Analytics.send(AnalyticsEvent.SettingChanged("learnCutoff", limitToSave?.minutes ?: 0))
                    val isLatest = (operationId == saveOperationCount)
                    onSuccess(isLatest)
                    if (isLatest) {
                        if (limitToSave != null) {
                            val current = uiState.value as? LearnAheadUiState.Loaded
                            if (current != null && current.limit != limitToSave) {
                                uiState.value = current.copy(limit = limitToSave)
                            }
                        }
                    }
                } catch (exc: Exception) {
                    if (exc is CancellationException) throw exc
                    Timber.e(exc, "Failed to save learn ahead limit")
                    if (isDatabaseCorruptException(exc)) {
                        DatabaseCorruption.isDetected = true
                    }
                    _errorEvents.tryEmit(CommonString.something_wrong)
                    previousJob?.join()
                    if (operationId == saveOperationCount) {
                        onFailure()
                    }
                }
            }
        pendingSaveJob = saveJob
    }

    private fun isDatabaseCorruptException(exc: Throwable?): Boolean =
        exc is SQLiteDatabaseCorruptException ||
            exc is BackendDbCorruptException ||
            (exc?.cause != null && isDatabaseCorruptException(exc.cause))

    companion object {
        var pendingSaveJob: Job? = null
    }
}
