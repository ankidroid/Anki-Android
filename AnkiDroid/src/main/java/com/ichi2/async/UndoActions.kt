/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2022 Divyansh Kushwaha <norbert.nagold@gmail.com>                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.async

import com.ichi2.anki.CardUtils
import com.ichi2.anki.R
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Note
import com.ichi2.libanki.UndoAction
import com.ichi2.libanki.Utils
import timber.log.Timber
import java.util.ArrayList

/** @param hasUnsuspended  whether there were any unsuspended card (in which card the action was "Suspend",
 * otherwise the action was "Unsuspend")
 */
class UndoSuspendCardMulti(
    private val cards: Array<Card>,
    private val originalSuspended: BooleanArray,
    hasUnsuspended: Boolean
) : UndoAction(if (hasUnsuspended) R.string.menu_suspend_card else R.string.card_browser_unsuspend_card) {
    override fun undo(col: Collection): Card? {
        Timber.i("Undo: Suspend multiple cards")
        val nbOfCards = cards.size
        val toSuspendIds: MutableList<Long> = ArrayList(nbOfCards)
        val toUnsuspendIds: MutableList<Long> = ArrayList(nbOfCards)
        for (i in 0 until nbOfCards) {
            val card = cards[i]
            if (originalSuspended[i]) {
                toSuspendIds.add(card.id)
            } else {
                toUnsuspendIds.add(card.id)
            }
        }

        // unboxing
        val toSuspendIdsArray = LongArray(toSuspendIds.size)
        val toUnsuspendIdsArray = LongArray(toUnsuspendIds.size)
        for (i in toSuspendIds.indices) {
            toSuspendIdsArray[i] = toSuspendIds[i]
        }
        for (i in toUnsuspendIds.indices) {
            toUnsuspendIdsArray[i] = toUnsuspendIds[i]
        }
        col.sched.suspendCards(toSuspendIdsArray)
        col.sched.unsuspendCards(toUnsuspendIdsArray)
        return null // don't fetch new card
    }
}

class UndoDeleteNoteMulti(private val notesArr: Array<Note>, private val allCards: List<Card>) : UndoAction(
    R.string.card_browser_delete_card
) {
    override fun undo(col: Collection): Card? {
        Timber.i("Undo: Delete notes")
        // undo all of these at once instead of one-by-one
        val ids = ArrayList<Long>(notesArr.size + allCards.size)
        for (n in notesArr) {
            n.flush(n.mod, false)
            ids.add(n.id)
        }
        for (c in allCards) {
            c.flush(false)
            ids.add(c.id)
        }
        col.db.execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(ids))
        return null // don't fetch new card
    }
}

class UndoChangeDeckMulti(private val cards: Array<Card>, private val originalDids: LongArray) : UndoAction(
    R.string.undo_action_change_deck_multi
) {
    override fun undo(col: Collection): Card? {
        Timber.i("Undo: Change Decks")
        // move cards to original deck
        for (i in cards.indices) {
            val card = cards[i]
            card.load(col)
            card.did = originalDids[i]
            val note = card.note()
            note.flush()
            card.flush()
        }
        return null // don't fetch new card
    }
}

/** @param hasUnmarked whether there were any unmarked card (in which card the action was "mark",
 * otherwise the action was "Unmark")
 */
class UndoMarkNoteMulti
(private val originalMarked: List<Note>, private val originalUnmarked: List<Note>, hasUnmarked: Boolean) : UndoAction(if (hasUnmarked) R.string.card_browser_mark_card else R.string.card_browser_unmark_card) {
    override fun undo(col: Collection): Card? {
        Timber.i("Undo: Mark notes")
        CardUtils.markAll(originalMarked, true)
        CardUtils.markAll(originalUnmarked, false)
        return null // don't fetch new card
    }
}
