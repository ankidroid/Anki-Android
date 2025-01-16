/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.deckpicker

import androidx.annotation.CheckResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** @see [DeckPicker] */
class DeckPickerViewModel : ViewModel() {
    /**
     * @see deleteDeck
     * @see DeckDeletionResult
     */
    val deckDeletedNotification = MutableSharedFlow<DeckDeletionResult>()
    val emptyCardsNotification = MutableSharedFlow<EmptyCardsResult>()

    /**
     * A notification that the study counts have changed
     */
    // TODO: most of the recalculation should be moved inside the ViewModel
    val flowOfDeckCountsChanged = MutableSharedFlow<Unit>()

    /**
     * Keep track of which deck was last given focus in the deck list. If we find that this value
     * has changed between deck list refreshes, we need to recenter the deck list to the new current
     * deck.
     */
    // TODO: This should later be handled as a Flow
    var focusedDeck: DeckId = 0

    /**
     * Deletes the provided deck, child decks. and all cards inside.
     *
     * This is a slow operation and should be inside `withProgress`
     *
     * @param did ID of the deck to delete
     */
    @CheckResult // This is a slow operation and should be inside `withProgress`
    fun deleteDeck(did: DeckId) =
        viewModelScope.launch {
            val deckName = withCol { decks.get(did)!!.name }
            val changes = undoableOp { decks.remove(listOf(did)) }
            // After deletion: decks.current() reverts to Default, necessitating `focusedDeck`
            // to match and avoid unnecessary scrolls in `renderPage()`.
            focusedDeck = Consts.DEFAULT_DECK_ID

            deckDeletedNotification.emit(
                DeckDeletionResult(deckName = deckName, cardsDeleted = changes.count),
            )
        }

    /**
     * Deletes the currently selected deck
     *
     * This is a slow operation and should be inside `withProgress`
     */
    @CheckResult
    fun deleteSelectedDeck() =
        viewModelScope.launch {
            val targetDeckId = withCol { decks.selected() }
            deleteDeck(targetDeckId).join()
        }

    /** Returns a list of cards to be passed to [deleteEmptyCards] (after user confirmation) */
    suspend fun findEmptyCards() = EmptyCards(withCol { emptyCids() })

    /**
     * Removes the provided list of cards from the collection.
     * @param emptyCards Cards to be deleted, result of [findEmptyCards]
     */
    fun deleteEmptyCards(emptyCards: EmptyCards) =
        viewModelScope.launch {
            val result = undoableOp { removeCardsAndOrphanedNotes(emptyCards) }
            emptyCardsNotification.emit(EmptyCardsResult(cardsDeleted = result.count))
        }

    // TODO: move withProgress to the ViewModel, so we don't return 'Job'
    fun emptyFilteredDeck(deckId: DeckId): Job =
        viewModelScope.launch {
            Timber.i("empty filtered deck %s", deckId)
            withCol { decks.select(deckId) }
            undoableOp { sched.emptyDyn(decks.selected()) }
            flowOfDeckCountsChanged.emit(Unit)
        }
}

/** Result of [DeckPickerViewModel.deleteDeck] */
data class DeckDeletionResult(
    val deckName: String,
    val cardsDeleted: Int,
) {
    /**
     * @see GeneratedTranslations.browsingCardsDeletedWithDeckname
     */
    // TODO: Somewhat questionable meaning: {count} cards deleted from {deck_name}.
    @CheckResult
    fun toHumanReadableString() =
        TR.browsingCardsDeletedWithDeckname(
            count = cardsDeleted,
            deckName = deckName,
        )
}

/**
 * Result of [DeckPickerViewModel.findEmptyCards], used in [DeckPickerViewModel.deleteEmptyCards]
 */
@JvmInline
value class EmptyCards(
    val cards: List<CardId>,
) : List<CardId> by cards

/** Result of [DeckPickerViewModel.deleteEmptyCards] */
data class EmptyCardsResult(
    val cardsDeleted: Int,
) {
    /**
     * @see GeneratedTranslations.emptyCardsDeletedCount */
    @CheckResult
    fun toHumanReadableString() = TR.emptyCardsDeletedCount(cardsDeleted)
}
