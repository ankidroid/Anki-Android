/****************************************************************************************
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki

import android.content.res.Resources
import androidx.annotation.StringRes
import com.ichi2.utils.LanguageUtil.getLocaleCompat
import timber.log.Timber
import java.util.*

abstract class UndoAction
/**
 * For all descendants, we assume that a card/note/object passed as argument is never going to be changed again.
 * It's the caller responsibility to clone the object if necessary. */(
    @field:UndoNameId @field:StringRes @param:StringRes @param:UndoNameId
    val undoNameId: Int
) {
    @Retention(AnnotationRetention.SOURCE)
    annotation class UndoNameId

    private fun getLocale(resources: Resources): Locale? {
        return getLocaleCompat(resources)
    }

    fun name(res: Resources): String? {
        return getLocale(res)?.let { res.getString(undoNameId).lowercase(it) }
    }

    /**
     * Return MULTI_CARD when no other action is needed, e.g. for multi card action
     * Return NO_REVIEW when we just need to reset the collection
     * Returned positive integers are card id. Those ids is the card that was discarded and that may be sent back to the reviewer. */
    abstract fun undo(col: Collection): Card?

    companion object {
        /**
         * Create an UndoAction that set back `card` and its siblings to the current states.
         * @param undoNameId The id of the string representing an action that could be undone
         * @param card the card currently in the reviewer
         * @return An UndoAction which, if executed, put back the `card` in the state given here
         */
        fun revertNoteToProvidedState(
            @StringRes @UndoNameId
            undoNameId: Int,
            card: Card
        ): UndoAction {
            return revertToProvidedState(undoNameId, card, card.note().cards())
        }

        /**
         * Create an UndoAction that set back `card` and its siblings to the current states.
         * @param undoNameId The id of the string representing an action that could be undone
         * @param card the card currently in the reviewer
         * @return An UndoAction which, if executed, put back the `card` in the state given here
         */
        fun revertCardToProvidedState(
            @StringRes @UndoNameId
            undoNameId: Int,
            card: Card
        ): UndoAction {
            return revertToProvidedState(undoNameId, card, mutableListOf(card.clone()))
        }

        /**
         * Create an UndoAction that set back `card` and its siblings to the current states.
         * @param undoNameId The id of the string representing an action that could be undone
         * @param card the card currently in the reviewer
         * @param cards The cards that must be reverted
         * @return An UndoAction which, if executed, put back the `card` in the state given here
         */
        private fun revertToProvidedState(
            @StringRes @UndoNameId
            undoNameId: Int,
            card: Card,
            cards: Iterable<Card>
        ): UndoAction {
            return object : UndoAction(undoNameId) {
                override fun undo(col: Collection): Card {
                    Timber.i("Undo: %d", undoNameId)
                    for (cc in cards) {
                        cc.flush(false)
                    }
                    return card
                }
            }
        }
    }
}
