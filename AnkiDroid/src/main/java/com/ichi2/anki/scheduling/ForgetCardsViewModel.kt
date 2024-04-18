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

package com.ichi2.anki.scheduling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.pages.CardInfo
import com.ichi2.libanki.CardId
import kotlinx.coroutines.async
import timber.log.Timber

/**
 * [ViewModel] for [ForgetCardsDialog]
 *
 * Moves cards back to the [new queue](https://docs.ankiweb.net/getting-started.html#types-of-cards)
 *
 * docs:
 * * https://docs.ankiweb.net/studying.html#editing-and-more
 * * https://docs.ankiweb.net/browsing.html#cards
 */
class ForgetCardsViewModel : ViewModel() {
    private lateinit var cardIds: List<CardId>

    /**
     * Resets cards back to their original positions in the new queue
     *
     * This only works if the card was first studied using SchedV3 with backend >= 2.1.50+
     *
     * If `false`, cards are moved to the end of the new queue
     */
    var restoreOriginalPositionIfPossible = true
        set(value) {
            Timber.i("restoreOriginalPositionIfPossible: %b", value)
            field = value
        }

    /**
     * Set the review and failure counters back to zero.
     *
     * This does not affect [CardInfo]/review history
     */
    var resetRepetitionAndLapseCounts = false
        set(value) {
            Timber.i("resetRepetitionAndLapseCounts: %b", value)
            field = value
        }

    fun init(cardIds: List<CardId>) {
        this.cardIds = cardIds
    }

    fun resetCardsAsync() = viewModelScope.async {
        Timber.i(
            "forgetting %d cards, restorePosition = %b, resetCounts = %b",
            cardIds.size,
            restoreOriginalPositionIfPossible,
            resetRepetitionAndLapseCounts
        )
        withCol {
            sched.forgetCards(
                cardIds,
                restorePosition = restoreOriginalPositionIfPossible,
                resetCounts = resetRepetitionAndLapseCounts
            )
        }
        return@async cardIds.size
    }
}
