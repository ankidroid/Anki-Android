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

import androidx.annotation.StringRes
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.servicelayer.SchedulerService.NextCard
import com.ichi2.libanki.Card
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.UndoAction
import com.ichi2.libanki.UndoAction.Companion.revertCardToProvidedState
import com.ichi2.libanki.UndoAction.UndoNameId
import com.ichi2.libanki.Utils
import com.ichi2.utils.Computation
import timber.log.Timber
import java.util.Optional
import java.util.concurrent.CancellationException
import com.ichi2.libanki.Collection as AnkiCollection

typealias NextCardAnd<T> = Computation<NextCard<T>>
typealias ComputeResult = NextCardAnd<Any?>
typealias ActionAndNextCard = AnkiMethod<ComputeResult>
typealias ActionAndNextCardV<T> = AnkiMethod<NextCardAnd<T>>
private typealias RepositionResetResult = NextCardAnd<Array<Card>>
private typealias RepositionOrReset = AnkiMethod<RepositionResetResult>

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

    class BuryCard(val card: Card) : ActionAndNextCard() {
        override fun execute(): ComputeResult {
            return computeThenGetNextCardInTransaction {
                // collect undo information
                col.markUndo(revertCardToProvidedState(R.string.menu_bury_card, card))
                // then bury
                col.sched.buryCards(longArrayOf(card.id))
            }
        }
    }

    class BuryNote(val card: Card) : ActionAndNextCard() {
        override fun execute(): ComputeResult {
            return computeThenGetNextCardInTransaction {
                // collect undo information
                col.markUndo(UndoAction.revertNoteToProvidedState(col, R.string.menu_bury_note, card))
                // then bury
                col.sched.buryNote(card.note().id)
            }
        }
    }

    class DeleteNote(val card: Card) : ActionAndNextCard() {
        override fun execute(): ComputeResult {
            return computeThenGetNextCardInTransaction {
                val note: Note = card.note()
                // collect undo information
                val allCs = note.cards(col)
                col.markUndo(UndoDeleteNote(note, allCs, card))
                // delete note
                col.remNotes(longArrayOf(note.id))
            }
        }
    }

    class SuspendCard(val card: Card) : ActionAndNextCard() {
        override fun execute(): ComputeResult {
            return computeThenGetNextCardInTransaction {
                // collect undo information
                val suspendedCard: Card = card.clone()
                col.markUndo(revertCardToProvidedState(R.string.menu_suspend_card, suspendedCard))
                // suspend card
                if (card.queue == Consts.QUEUE_TYPE_SUSPENDED) {
                    col.sched.unsuspendCards(longArrayOf(card.id))
                } else {
                    col.sched.suspendCards(longArrayOf(card.id))
                }
            }
        }
    }

    class SuspendNote(val card: Card) : ActionAndNextCard() {
        override fun execute(): ComputeResult {
            return computeThenGetNextCardInTransaction {
                // collect undo information
                val cards = card.note().cards(col)
                val cids = LongArray(cards.size)
                for (i in cards.indices) {
                    cids[i] = cards[i].id
                }
                col.markUndo(UndoAction.revertNoteToProvidedState(col, R.string.menu_suspend_note, card))
                // suspend note
                col.sched.suspendCards(cids)
            }
        }
    }

    class RepositionCards(private val cardIds: List<Long>, private val startPosition: Int) : RepositionOrReset() {
        override fun execute(): RepositionResetResult {
            val inputCards = dismissNotes(cardIds) { cards ->
                return@dismissNotes rescheduleRepositionReset(cards, R.string.card_editor_reposition_card) {
                    col.sched.sortCards(cardIds, startPosition, 1, false, true)
                }
            }
            return inputCards.map { x -> NextCard(x.first.orElse(null), x.second) }
        }
    }

    class RescheduleCards(val cardIds: List<Long>, private val interval: Int) : RepositionOrReset() {
        override fun execute(): RepositionResetResult {
            val inputCards = dismissNotes(cardIds) { cards ->
                return@dismissNotes rescheduleRepositionReset(cards, R.string.card_editor_reschedule_card) {
                    col.sched.reschedCards(cardIds, interval, interval)
                }
            }
            return inputCards.map { x -> NextCard(x.first.orElse(null), x.second) }
        }
    }

    class ResetCards(val cardIds: List<Long>) : RepositionOrReset() {
        override fun execute(): RepositionResetResult {
            val inputCards = dismissNotes(cardIds) { cards ->
                return@dismissNotes rescheduleRepositionReset(cards, R.string.card_editor_reset_card) {
                    col.sched.forgetCards(cardIds)
                }
            }
            return inputCards.map { x -> NextCard(x.first.orElse(null), x.second) }
        }
    }

    private class UndoDeleteNote(
        private val note: Note,
        private val allCs: ArrayList<Card>,
        private val card: Card
    ) : UndoAction(R.string.menu_delete_note) {
        override fun undo(col: AnkiCollection): Card {
            Timber.i("Undo: Delete note")
            val ids = ArrayList<Long>(allCs.size + 1)
            note.flush(col, note.mod, false)
            ids.add(note.id)
            for (c in allCs) {
                c.flush(false)
                ids.add(c.id)
            }
            col.db.execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(ids))
            return card
        }
    }

    class UndoRepositionRescheduleResetCards(
        @StringRes @UndoNameId
        undoNameId: Int,
        private val cardsCopied: Array<Card>
    ) : UndoAction(undoNameId) {
        override fun undo(col: AnkiCollection): Card? {
            Timber.i("Undoing action of type %s on %d cards", javaClass, cardsCopied.size)
            for (card in cardsCopied) {
                card.flush(false)
            }
            // /* card schedule change undone, reset and get
            // new card */
            Timber.d("Single card non-review change undo succeeded")
            col.reset()
            return col.sched.card
        }
    }

    companion object {
        fun <T> ActionAndNextCardV<T>.computeThenGetNextCardInTransaction(task: (AnkiCollection) -> T): Computation<NextCard<T>> {
            return try {
                val maybeNextCard = col.db.executeInTransaction {
                    col.sched.deferReset()
                    val result = task(col)
                    // With sHadCardQueue set, getCard() resets the scheduler prior to getting the next card
                    val maybeNextCard = col.sched.card

                    return@executeInTransaction NextCard(maybeNextCard, result)
                }
                Computation.ok(maybeNextCard)
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundDismissNote - RuntimeException on dismissing note, dismiss type %s", this.javaClass)
                CrashReportService.sendExceptionReport(e, "doInBackgroundDismissNote")
                Computation.err()
            }
        }

        fun AnkiMethod<*>.rescheduleRepositionReset(
            cards: Array<Card>,
            @UndoNameId @StringRes
            undoNameId: Int,
            actualActualTask: () -> Unit
        ): Computation<Optional<Card>> {
            val sched = col.sched
            // collect undo information, sensitive to memory pressure, same for all 3 cases
            try {
                Timber.d("Saving undo information of type %s on %d cards", javaClass, cards.size)
                val cards_copied = Card.deepCopyCardArray(cards, this)
                val repositionRescheduleResetCards: UndoAction = UndoRepositionRescheduleResetCards(undoNameId, cards_copied)
                col.markUndo(repositionRescheduleResetCards)
            } catch (ce: CancellationException) {
                Timber.i(ce, "Cancelled while handling type %s, skipping undo", undoNameId)
            }
            actualActualTask()
            // In all cases schedule a new card so Reviewer doesn't sit on the old one
            col.reset()
            return Computation.ok(Optional.ofNullable(sched.card))
        }
    }
}
