/****************************************************************************************
 * Copyright (c) 2022 Divyansh Kushwaha <kushwaha.divyansh.dxn@gmail.com>               *
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
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Note
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber

/**
 * This file contains functions that have been migrated from [CollectionTask]
 * Remove this comment when migration has been completed
 * TODO: All functions associated to Collection can be converted to extension function to avoid redundant parameter [col] in each.
 */

/**
 * Saves the newly updated card [editCard] to disk
 * @return updated card
 */
fun updateCard(
    col: Collection,
    editCard: Card,
    isFromReviewer: Boolean,
    canAccessScheduler: Boolean,
): Card {
    Timber.d("doInBackgroundUpdateNote")
    // Save the note
    val editNote = editCard.note()
    if (BackendFactory.defaultLegacySchema) {
        col.db.executeInTransaction {
            // TODO: undo integration
            editNote.flush()
            // flush card too, in case, did has been changed
            editCard.flush()
        }
    } else {
        // TODO: the proper way to do this would be to call this in undoableOp() in a coroutine
        col.newBackend.updateNote(editNote)
        // no need to flush card in new path
    }
    return if (isFromReviewer) {
        if (col.decks.active().contains(editCard.did) || !canAccessScheduler) {
            editCard.apply {
                load()
                q(true) // reload qa-cache
            }
        } else {
            col.sched.card!! // check: are there deleted too?
        }
    } else {
        editCard
    }
}

// TODO: Rename to saveEditedNotesToDisk or something more descriptive
/**
 * Takes a list of edited notes and saves them permanently to disk
 * @param col Collection
 * @param notesToUpdate a list of edited notes that is to be saved
 * @param shouldUpdateCards whether each card inside the notes should be updated
 * @return list of updated (in disk) notes
 */
fun doInBackgroundUpdateMultipleNotes(
    col: Collection,
    notesToUpdate: List<Note>,
    shouldUpdateCards: Boolean = false // Do we intend to use it somewhere?
): List<Note> {
    Timber.d("doInBackgroundUpdateMultipleNotes")
    return col.db.executeInTransaction {
        for (note in notesToUpdate) {
            note.flush()
            if (shouldUpdateCards) {
                for (card in note.cards()) {
                    card.flush()
                }
            }
        }
        notesToUpdate
    }
}
