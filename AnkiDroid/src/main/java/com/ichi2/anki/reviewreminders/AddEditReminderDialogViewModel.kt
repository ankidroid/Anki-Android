/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ichi2.anki.libanki.DeckId
import timber.log.Timber

/**
 * Represents the state of an [AddEditReminderDialog]'s UI. Does not represent the ReviewReminder object itself.
 * For example, instead of tracking an entire [ReviewReminderSnoozeAmount] object, we hold the fields of the object
 * (i.e. interval, max amount of snoozes) in pieces as Ints. This is because the EditText fields in the dialog have their inputType set to "number".
 */
class AddEditReminderDialogViewModel(
    initialTime: ReviewReminderTime,
    initialDeckRadioSelected: DeckRadioOptions,
    initialDeckSelected: DeckId,
    initialSnoozeRadioSelected: SnoozeRadioOptions,
    initialSnoozeInterval: Int,
    initialMaxSnoozes: Int,
    initialCardTriggerThreshold: Int,
    initialAdvancedSettingsOpen: Boolean,
) : ViewModel() {
    /**
     * The radio buttons available in the dialog when picking a deck to associate a review reminder with.
     */
    enum class DeckRadioOptions {
        GLOBAL,
        DECK_SPECIFIC,
    }

    /**
     * The radio buttons available in the dialog when picking a snooze amount for a review reminder.
     */
    enum class SnoozeRadioOptions {
        DISABLED,
        INFINITE,
        SET_AMOUNT,
    }

    private val _time = MutableLiveData(initialTime)
    val time: LiveData<ReviewReminderTime> = _time

    private val _deckRadioSelected = MutableLiveData(initialDeckRadioSelected)
    val deckRadioSelected: LiveData<DeckRadioOptions> = _deckRadioSelected

    private val _deckSelected = MutableLiveData(initialDeckSelected)
    val deckSelected: LiveData<DeckId> = _deckSelected

    private val _snoozeRadioSelected = MutableLiveData(initialSnoozeRadioSelected)
    val snoozeRadioSelected: LiveData<SnoozeRadioOptions> = _snoozeRadioSelected

    private val _snoozeInterval = MutableLiveData(initialSnoozeInterval)
    val snoozeInterval: LiveData<Int> = _snoozeInterval

    private val _maxSnoozes = MutableLiveData(initialMaxSnoozes)
    val maxSnoozes: LiveData<Int> = _maxSnoozes

    private val _cardTriggerThreshold = MutableLiveData(initialCardTriggerThreshold)
    val cardTriggerThreshold: LiveData<Int> = _cardTriggerThreshold

    private val _advancedSettingsOpen = MutableLiveData(initialAdvancedSettingsOpen)
    val advancedSettingsOpen: LiveData<Boolean> = _advancedSettingsOpen

    fun setTime(time: ReviewReminderTime) {
        Timber.d("Updated time to $time")
        _time.value = time
    }

    fun setDeckRadioSelected(option: DeckRadioOptions) {
        Timber.d("Updated deck radio selected to $option")
        _deckRadioSelected.value = option
    }

    fun setDeckSelected(deckId: DeckId) {
        Timber.d("Updated deck selected to $deckId")
        _deckSelected.value = deckId
    }

    fun setSnoozeRadioSelected(option: SnoozeRadioOptions) {
        Timber.d("Updated snooze radio selected to $option")
        _snoozeRadioSelected.value = option
    }

    fun setSnoozeInterval(interval: Int?) {
        Timber.d("Updated snooze interval to $interval")
        _snoozeInterval.value = interval
    }

    fun setMaxSnoozes(max: Int?) {
        Timber.d("Updated max snoozes to $max")
        _maxSnoozes.value = max
    }

    fun setCardTriggerThreshold(threshold: Int?) {
        Timber.d("Updated card trigger threshold to $threshold")
        _cardTriggerThreshold.value = threshold
    }

    fun toggleAdvancedSettingsOpen() {
        Timber.d("Toggled advanced settings open")
        _advancedSettingsOpen.value = !(_advancedSettingsOpen.value ?: false)
    }
}
