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

import com.ichi2.anki.CardBrowser
import com.ichi2.anki.StudyOptionsFragment
import com.ichi2.anki.TemporaryModel
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.*
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.libanki.Note
import com.ichi2.utils.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.util.*
import java.util.ArrayList
import kotlin.Comparator

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

// TODO: Move the operation where it is actually used, no need for a separate function since it is fairly simple
/**
 * Takes a list of edited notes and saves the change permanently to disk
 * @param col Collection
 * @param notesToUpdate a list of edited notes that is to be saved
 * @return list of updated (in disk) notes
 */
fun updateMultipleNotes(
    col: Collection,
    notesToUpdate: List<Note>,
): List<Note> {
    Timber.d("CollectionOperations: updateMultipleNotes")
    return col.db.executeInTransaction {
        for (note in notesToUpdate) {
            note.flush()
        }
        notesToUpdate
    }
}

/**
 * Takes a list of media file names and removes them from the Collection
 * @param col Collection from which media is to be deleted
 * @param unused List of media names to be deleted
 */
fun deleteMedia(
    col: Collection,
    unused: List<String>
): Int {
    val m = col.media
    if (!BackendFactory.defaultLegacySchema) {
        // FIXME: this provides progress info that is not currently used
        col.newMedia.removeFiles(unused)
    } else {
        for (fname in unused) {
            m.removeFile(fname)
        }
    }
    return unused.size
}

// TODO: Once [com.ichi2.async.CollectionTask.RebuildCram] and [com.ichi2.async.CollectionTask.EmptyCram]
// are migrated to Coroutines, move this function to [com.ichi2.anki.StudyOptionsFragment]
fun updateValuesFromDeck(
    col: Collection,
    reset: Boolean
): StudyOptionsFragment.DeckStudyData? {
    Timber.d("doInBackgroundUpdateValuesFromDeck")
    return try {
        val sched = col.sched
        if (reset) {
            // reset actually required because of counts, which is used in getCollectionTaskListener
            sched.resetCounts()
        }
        val counts = sched.counts()
        val totalNewCount = sched.totalNewForCurrentDeck()
        val totalCount = sched.cardCount()
        StudyOptionsFragment.DeckStudyData(
            counts.new, counts.lrn, counts.rev, totalNewCount,
            totalCount, sched.eta(counts)
        )
    } catch (e: RuntimeException) {
        Timber.e(e, "doInBackgroundUpdateValuesFromDeck - an error occurred")
        null
    }
}

/**
 * Returns an ArrayList of all models alphabetically ordered and the number of notes
 * associated with each model.
 *
 * @return {ArrayList<JSONObject> models, ArrayList<Integer> cardCount}
 */
fun getAllModelsAndNotesCount(col: Collection,): Pair<List<Model>, List<Int>> {
    Timber.d("doInBackgroundLoadModels")
    val models = col.models.all()
    Collections.sort(models, Comparator { a: JSONObject, b: JSONObject -> a.getString("name").compareTo(b.getString("name")) } as java.util.Comparator<JSONObject>)
    val cardCount = models.map { col.models.useCount(it) }
    return Pair(models, cardCount)
}

fun changeDeckConfiguration(
    deck: Deck,
    conf: DeckConfig,
    col: Collection
) {
    val newConfId = conf.getLong("id")
    // If new config has a different sorting order, reorder the cards
    val oldOrder = col.decks.getConf(deck.getLong("conf"))!!.getJSONObject("new").getInt("order")
    val newOrder = col.decks.getConf(newConfId)!!.getJSONObject("new").getInt("order")
    if (oldOrder != newOrder) {
        when (newOrder) {
            0 -> col.sched.randomizeCards(deck.getLong("id"))
            1 -> col.sched.orderCards(deck.getLong("id"))
        }
    }
    col.decks.setConf(deck, newConfId)
    col.save()
}

/**
 * Goes through selected cards and checks selected and marked attribute
 * @return If there are unselected cards, if there are unmarked cards
 */
suspend fun checkCardSelection(checkedCards: Set<CardBrowser.CardCache>): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
    var hasUnsuspended = false
    var hasUnmarked = false
    for (c in checkedCards) {
        ensureActive() // check if job is not cancelled
        val card = c.card
        hasUnsuspended = hasUnsuspended || card.queue != Consts.QUEUE_TYPE_SUSPENDED
        hasUnmarked = hasUnmarked || !NoteService.isMarked(card.note())
        if (hasUnsuspended && hasUnmarked) break
    }
    Pair(hasUnsuspended, hasUnmarked)
}

/**
 * Handles everything for a model change at once - template add / deletes as well as content updates
 * @return Pair<Boolean, String> : (true, null) when success, (false, exceptionMessage) when failure
 */
fun saveModel(
    col: Collection,
    model: Model,
    templateChanges: ArrayList<Array<Any>>
) {
    Timber.d("doInBackgroundSaveModel")
    val oldModel = col.models.get(model.getLong("id"))

    // TODO need to save all the cards that will go away, for undo
    //  (do I need to remove them from graves during undo also?)
    //    - undo (except for cards) could just be Models.update(model) / Models.flush() / Collection.reset() (that was prior "undo")
    val newTemplates = model.getJSONArray("tmpls")
    col.db.database.beginTransaction()
    try {
        for (change in templateChanges) {
            val oldTemplates = oldModel!!.getJSONArray("tmpls")
            when (change[1] as TemporaryModel.ChangeType) {
                TemporaryModel.ChangeType.ADD -> {
                    Timber.d("doInBackgroundSaveModel() adding template %s", change[0])
                    col.models.addTemplate(oldModel, newTemplates.getJSONObject(change[0] as Int))
                }
                TemporaryModel.ChangeType.DELETE -> {
                    Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0])
                    col.models.remTemplate(oldModel, oldTemplates.getJSONObject(change[0] as Int))
                }
            }
        }

        // required for Rust: the modified time can't go backwards, and we updated the model by adding fields
        // This could be done better
        model.put("mod", oldModel!!.getLong("mod"))
        col.models.save(model, true)
        col.models.update(model)
        col.reset()
        col.save()
        if (col.db.database.inTransaction()) {
            col.db.database.setTransactionSuccessful()
        } else {
            Timber.i("CollectionTask::SaveModel was not in a transaction? Cannot mark transaction successful.")
        }
    } finally {
        DB.safeEndInTransaction(col.db)
    }
}
