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
 * Represents the state of an [AddEditReminderDialog]'s UI. Does not represent the [ReviewReminder] object itself.
 * For example, instead of storing the card trigger threshold as a [ReviewReminderCardTriggerThreshold], we store an Int, since that's
 * the input type the user is using to enter the threshold into the app. In other words, this class reflects the concrete
 * EditText fields in the dialog, not abstract backend data representations.
 */
class AddEditReminderDialogViewModel(
    initialTime: ReviewReminderTime,
    initialDeckSelected: DeckId,
    initialCardTriggerThreshold: Int,
    initialAdvancedSettingsOpen: Boolean,
) : ViewModel() {
    private val _time = MutableLiveData(initialTime)
    val time: LiveData<ReviewReminderTime> = _time

    private val _deckSelected = MutableLiveData(initialDeckSelected)

    /**
     * [com.ichi2.anki.DeckSpinnerSelection.ALL_DECKS_ID] is used to represent All Decks
     * (i.e. [ReviewReminderScope.Global]) being selected.
     */
    val deckSelected: LiveData<DeckId> = _deckSelected

    private val _cardTriggerThreshold = MutableLiveData(initialCardTriggerThreshold)
    val cardTriggerThreshold: LiveData<Int> = _cardTriggerThreshold

    private val _advancedSettingsOpen = MutableLiveData(initialAdvancedSettingsOpen)
    val advancedSettingsOpen: LiveData<Boolean> = _advancedSettingsOpen

    fun setTime(time: ReviewReminderTime) {
        Timber.d("Updated time to %s", time)
        _time.value = time
    }

    fun setDeckSelected(deckId: DeckId) {
        Timber.d("Updated deck selected to %s", deckId)
        _deckSelected.value = deckId
    }

    fun setCardTriggerThreshold(threshold: Int) {
        Timber.d("Updated card trigger threshold to %s", threshold)
        _cardTriggerThreshold.value = threshold
    }

    fun toggleAdvancedSettingsOpen() {
        Timber.d("Toggled advanced settings open from %s", _advancedSettingsOpen.value)
        _advancedSettingsOpen.value = !(_advancedSettingsOpen.value ?: false)
    }
}
