/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.anki.servicelayer.SchedulerService.NextCard
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.withProgress
import com.ichi2.libanki.*
import com.ichi2.utils.Computation
import timber.log.Timber

typealias NextCardAnd<T> = Computation<NextCard<T>>
typealias ComputeResult = NextCardAnd<Any?>
typealias ActionAndNextCard = AnkiMethod<ComputeResult>

class SchedulerService {

    /**
     * A pair of the next card from the scheduler, and an optional method result
     */
    class NextCard<out T>(private val card: Card?, val result: T) {
        fun hasNoMoreCards(): Boolean = card == null

        /** Returns the next scheduled card
         * Only call if noMoreCards returns false */
        fun nextScheduledCard(): Card = card!!
        companion object {
            fun withNoResult(card: Card?): NextCard<Unit> =
                NextCard(card, Unit)
        }
    }

    class GetCard : ActionAndNextCard() {
        override fun execute(): ComputeResult {
            return getCard(this)
        }

        companion object {
            fun getCard(getCard: ActionAndNextCard): ComputeResult {
                val sched = getCard.col.sched
                Timber.i("Obtaining card")
                val newCard = sched.card
                newCard?.render_output(true)
                return Computation.ok(NextCard.withNoResult(newCard))
            }
        }
    }
}

suspend fun FragmentActivity.rescheduleCards(cardIds: List<CardId>, newDays: Int) {
    withProgress {
        undoableOp {
            col.sched.reschedCards(cardIds, newDays, newDays)
        }
    }
    val count = cardIds.size
    showSnackbar(
        resources.getQuantityString(
            R.plurals.reschedule_cards_dialog_acknowledge,
            count,
            count
        ),
        Snackbar.LENGTH_SHORT
    )
}

suspend fun FragmentActivity.resetCards(cardIds: List<CardId>) {
    withProgress {
        undoableOp {
            col.sched.forgetCards(cardIds)
        }
    }
    val count = cardIds.size
    showSnackbar(
        resources.getQuantityString(
            R.plurals.reset_cards_dialog_acknowledge,
            count,
            count
        ),
        Snackbar.LENGTH_SHORT
    )
}
