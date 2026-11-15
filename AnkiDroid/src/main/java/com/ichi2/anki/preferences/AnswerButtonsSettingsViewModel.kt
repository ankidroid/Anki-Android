// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.launchCatching
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class AnswerButtonsSettingsState(
    val showAnswerButtons: Boolean,
    /** `null` while it is loaded from the collection */
    val showNextTimes: Boolean?,
    val hideHardAndEasyButtons: Boolean,
    val buttonSize: Int,
)

class AnswerButtonsSettingsViewModel(
    private val prefs: PrefsRepository = Prefs,
) : ViewModel(),
    OnErrorListener {
    override val onError = Channel<Exception>(Channel.BUFFERED)

    val uiState: StateFlow<AnswerButtonsSettingsState>
        field = MutableStateFlow(
            AnswerButtonsSettingsState(
                showAnswerButtons = prefs.showAnswerButtons,
                showNextTimes = null,
                hideHardAndEasyButtons = prefs.hideHardAndEasyButtons,
                buttonSize = prefs.newStudyScreenAnswerButtonSize,
            ),
        )

    init {
        launchCatching {
            val showNextTimes = CollectionPreferences.getShowIntervalOnButtons()
            uiState.update { it.copy(showNextTimes = showNextTimes) }
        }
    }

    fun setShowAnswerButtons(value: Boolean) {
        prefs.showAnswerButtons = value
        uiState.update { it.copy(showAnswerButtons = value) }
    }

    fun setShowNextTimes(value: Boolean) {
        uiState.update { it.copy(showNextTimes = value) }
        launchCatching { CollectionPreferences.setShowIntervalsOnButtons(value) }
    }

    fun setHideHardAndEasyButtons(value: Boolean) {
        prefs.hideHardAndEasyButtons = value
        uiState.update { it.copy(hideHardAndEasyButtons = value) }
    }

    fun setButtonSize(value: Int) {
        prefs.newStudyScreenAnswerButtonSize = value
        uiState.update { it.copy(buttonSize = value) }
    }
}
