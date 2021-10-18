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

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.UndoAction.revertCardToProvidedState
import com.ichi2.utils.Computation
import timber.log.Timber
import java.util.*

private typealias ActionAndNextCard = AnkiTask<Card?, ComputeResult>
private typealias ComputeResult = Computation<*>

class SchedulerService {
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
                col.markUndo(UndoAction.revertNoteToProvidedState(R.string.menu_bury_note, card))
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
                val allCs = note.cards()
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
                val cards = card.note().cards()
                val cids = LongArray(cards.size)
                for (i in cards.indices) {
                    cids[i] = cards[i].id
                }
                col.markUndo(UndoAction.revertNoteToProvidedState(R.string.menu_suspend_note, card))
                // suspend note
                col.sched.suspendCards(cids)
            }
        }
    }

    private class UndoDeleteNote(
        private val note: Note,
        private val allCs: ArrayList<Card>,
        private val card: Card
    ) : UndoAction(R.string.menu_delete_note) {
        override fun undo(col: Collection): Card {
            Timber.i("Undo: Delete note")
            val ids = ArrayList<Long>(allCs.size + 1)
            note.flush(note.mod, false)
            ids.add(note.id)
            for (c in allCs) {
                c.flush(false)
                ids.add(c.id)
            }
            col.db.execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(ids))
            return card
        }
    }

    companion object {
        fun ActionAndNextCard.computeThenGetNextCardInTransaction(task: (Collection) -> Unit): ComputeResult {
            return try {
                col.db.executeInTransactionReturn {
                    col.sched.deferReset()
                    task(col)
                    // With sHadCardQueue set, getCard() resets the scheduler prior to getting the next card
                    val maybeNextCard: Card? = col.sched.getCard()
                    doProgress(maybeNextCard)
                }
                Computation.OK
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundDismissNote - RuntimeException on dismissing note, dismiss type %s", this.javaClass)
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundDismissNote")
                Computation.ERR
            }
        }
    }
}
