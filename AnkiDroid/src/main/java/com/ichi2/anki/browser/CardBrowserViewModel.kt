/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import android.content.res.Resources
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ExportDialogParams
import com.ichi2.anki.export.ExportType
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.*
import com.ichi2.anki.model.SortType
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.servicelayer.CardService
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Consts
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import java.util.LinkedHashSet

class CardBrowserViewModel(
    preferences: SharedPreferencesProvider
) : ViewModel(), SharedPreferencesProvider by preferences {
    val cards = CardBrowser.CardCollection<CardBrowser.CardCache>()

    var searchTerms: String = ""
    var restrictOnDeck: String = ""
    var currentFlag = 0

    /**
     * Whether the browser is working in Cards mode or Notes mode.
     * default: [CARDS]
     * */
    val cardsOrNotesFlow = MutableStateFlow(CARDS)
    val cardsOrNotes get() = cardsOrNotesFlow.value

    // card that was clicked (not marked)
    var currentCardId: CardId = 0
    var order = SortType.NO_SORTING
    var orderAsc = false

    val column1IndexFlow = MutableStateFlow(sharedPrefs().getInt(DISPLAY_COLUMN_1_KEY, 0))
    val column2IndexFlow = MutableStateFlow(sharedPrefs().getInt(DISPLAY_COLUMN_2_KEY, 0))
    val column1Index get() = column1IndexFlow.value
    val column2Index get() = column2IndexFlow.value

    /** The query which is currently in the search box, potentially null. Only set when search box was open  */
    var tempSearchQuery: String? = null

    var isInMultiSelectMode = false

    val isTruncatedFlow: MutableStateFlow<Boolean> =
        MutableStateFlow(sharedPrefs().getBoolean("isTruncated", false))
    val isTruncated get() = isTruncatedFlow.value

    val checkedCards: MutableSet<CardBrowser.CardCache> = Collections.synchronizedSet(LinkedHashSet())
    val selectedCardIds: List<Long>
        get() = checkedCards.map { c -> c.id }
    var lastSelectedPosition = 0

    val cardInfoDestination: CardInfoDestination?
        get() {
            val firstSelectedCard = selectedCardIds.firstOrNull() ?: return null
            return CardInfoDestination(firstSelectedCard)
        }

    init {
        column1IndexFlow
            .onEach { index -> sharedPrefs().edit { putInt(DISPLAY_COLUMN_1_KEY, index) } }
            .launchIn(viewModelScope)

        column2IndexFlow
            .onEach { index -> sharedPrefs().edit { putInt(DISPLAY_COLUMN_2_KEY, index) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val cardsOrNotes = withCol { CardsOrNotes.fromCollection(this) }
            cardsOrNotesFlow.update { cardsOrNotes }
        }
    }

    fun hasSelectedCards(): Boolean = checkedCards.isNotEmpty()

    /**
     * All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked
     */
    suspend fun toggleMark(cardIds: List<CardId>) {
        if (!hasSelectedCards()) {
            Timber.i("Not marking cards - nothing selected")
            return
        }
        undoableOp {
            val noteIds = notesOfCards(cardIds)
            // if all notes are marked, remove the mark
            // if no notes are marked, add the mark
            // if there is a mix, enable the mark on all
            val wantMark = !noteIds.all { getNote(it).hasTag("marked") }
            Timber.i("setting mark = %b for %d notes", wantMark, noteIds.size)
            if (wantMark) {
                tags.bulkAdd(noteIds, "marked")
            } else {
                tags.bulkRemove(noteIds, "marked")
            }
        }
    }

    /**
     * Deletes the selected notes,
     * @return the number of deleted notes
     */
    suspend fun deleteSelectedNotes(): Int =
        undoableOp { removeNotes(cids = selectedCardIds) }.count

    fun setCardsOrNotes(newValue: CardsOrNotes) = viewModelScope.launch {
        withCol {
            // Change this to only change the preference on a state change
            newValue.saveToCollection(this)
        }
        cardsOrNotesFlow.update { newValue }
    }

    fun setTruncated(value: Boolean) {
        viewModelScope.launch {
            isTruncatedFlow.emit(value)
        }
        sharedPrefs().edit {
            putBoolean("isTruncated", value)
        }
    }

    fun setColumn1Index(value: Int) = column1IndexFlow.update { value }

    fun setColumn2Index(value: Int) = column2IndexFlow.update { value }
    suspend fun suspendCards() {
        val cardIds = selectedCardIds
        if (cardIds.isEmpty()) {
            return
        }

        undoableOp {
            // if all cards are suspended, unsuspend all
            // if no cards are suspended, suspend all
            // if there is a mix, suspend all
            val wantUnsuspend = cardIds.all { getCard(it).queue == Consts.QUEUE_TYPE_SUSPENDED }
            if (wantUnsuspend) {
                sched.unsuspendCards(cardIds)
            } else {
                sched.suspendCards(cardIds).changes
            }
        }
    }

    /** @return an [ExportDialogParams] based on the current screen state */
    suspend fun getExportDialogParams(resources: Resources): ExportDialogParams? {
        if (!isInMultiSelectMode) return null
        return when (cardsOrNotes) {
            CARDS -> {
                val selectedCardIds = selectedCardIds
                ExportDialogParams(
                    message = resources.getQuantityString(
                        R.plurals.confirm_apkg_export_selected_cards,
                        selectedCardIds.size,
                        selectedCardIds.size
                    ),
                    exportType = ExportType.ExportCards(selectedCardIds)
                )
            }
            NOTES -> {
                val selectedNoteIds = withCol { CardService.selectedNoteIds(selectedCardIds, this) }
                ExportDialogParams(
                    message = resources.getQuantityString(
                        R.plurals.confirm_apkg_export_selected_notes,
                        selectedNoteIds.size,
                        selectedNoteIds.size
                    ),
                    exportType = ExportType.ExportNotes(selectedNoteIds)
                )
            }
        }
    }

    companion object {
        const val DISPLAY_COLUMN_1_KEY = "cardBrowserColumn1"
        const val DISPLAY_COLUMN_2_KEY = "cardBrowserColumn2"
        fun factory(preferencesProvider: SharedPreferencesProvider? = null) = viewModelFactory {
            initializer {
                CardBrowserViewModel(preferencesProvider ?: AnkiDroidApp.sharedPreferencesProvider)
            }
        }
    }
}
