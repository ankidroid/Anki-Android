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

import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.utils.Computation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.*

/**
 * This file contains functions that have been migrated from [CollectionTask]
 * Remove this comment when migration has been completed
 * TODO: All functions associated to Collection can be converted to extension function to avoid redundant parameter [col] in each.
 */

// TODO: Move the operation where it is actually used, no need for a separate function since it is fairly simple
/**
 * Takes a list of edited notes and saves the change permanently to disk
 * @param col Collection
 * @param notesToUpdate a list of edited notes that is to be saved
 * @return list of updated (in disk) notes
 */
fun updateMultipleNotes(
    col: Collection,
    notesToUpdate: List<Note>
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
    // FIXME: this provides progress info that is not currently used
    col.media.removeFiles(unused)
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
            counts.new,
            counts.lrn,
            counts.rev,
            totalNewCount,
            totalCount,
            sched.eta(counts)
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
suspend fun getAllModelsAndNotesCount(): Pair<List<Model>, List<Int>> = withContext(Dispatchers.IO) {
    Timber.d("doInBackgroundLoadModels")
    val models = withCol { notetypes.all() }
    Collections.sort(models, Comparator { a: JSONObject, b: JSONObject -> a.getString("name").compareTo(b.getString("name")) } as java.util.Comparator<JSONObject>)
    val cardCount = models.map {
        ensureActive()
        withCol { this.notetypes.useCount(it) }
    }
    Pair(models, cardCount)
}

fun changeDeckConfiguration(
    deck: Deck,
    conf: DeckConfig,
    col: Collection
) {
    val newConfId = conf.getLong("id")
    // If new config has a different sorting order, reorder the cards
    val oldOrder = col.decks.getConf(deck.getLong("conf")).getJSONObject("new").getInt("order")
    val newOrder = col.decks.getConf(newConfId).getJSONObject("new").getInt("order")
    if (oldOrder != newOrder) {
        when (newOrder) {
            0 -> col.sched.randomizeCards(deck.getLong("id"))
            1 -> col.sched.orderCards(deck.getLong("id"))
        }
    }
    col.decks.setConf(deck, newConfId)
}

suspend fun renderBrowserQA(
    cards: CardBrowser.CardCollection<CardBrowser.CardCache>,
    startPos: Int,
    n: Int,
    column1Index: Int,
    column2Index: Int,
    onProgressUpdate: (Int) -> Unit
): Pair<CardBrowser.CardCollection<CardBrowser.CardCache>, MutableList<Long>> = withContext(Dispatchers.IO) {
    Timber.d("doInBackgroundRenderBrowserQA")
    val invalidCardIds: MutableList<Long> = ArrayList()
    // for each specified card in the browser list
    for (i in startPos until startPos + n) {
        // Stop if cancelled, throw cancellationException
        ensureActive()

        if (i < 0 || i >= cards.size()) {
            continue
        }
        var card: CardBrowser.CardCache
        card = try {
            cards[i]
        } catch (e: IndexOutOfBoundsException) {
            // even though we test against card.size() above, there's still a race condition
            // We might be able to optimise this to return here. Logically if we're past the end of the collection,
            // we won't reach any more cards.
            continue
        }
        if (card.isLoaded) {
            // We've already rendered the answer, we don't need to do it again.
            continue
        }
        // Extract card item
        try {
            // Ensure that card still exists.
            card.card
        } catch (e: WrongId) {
            // #5891 - card can be inconsistent between the deck browser screen and the collection.
            // Realistically, we can skip any exception as it's a rendering task which should not kill the
            // process
            val cardId = card.id
            Timber.e(e, "Could not process card '%d' - skipping and removing from sight", cardId)
            invalidCardIds.add(cardId)
            continue
        }
        // Update item
        card.load(false, column1Index, column2Index)
        val progress = i.toFloat() / n * 100
        withContext(Dispatchers.Main) { onProgressUpdate(progress.toInt()) }
    }
    Pair(cards, invalidCardIds)
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
    val oldModel = col.notetypes.get(model.getLong("id"))

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
                    col.notetypes.addTemplate(oldModel, newTemplates.getJSONObject(change[0] as Int))
                }
                TemporaryModel.ChangeType.DELETE -> {
                    Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0])
                    col.notetypes.remTemplate(oldModel, oldTemplates.getJSONObject(change[0] as Int))
                }
            }
        }

        // required for Rust: the modified time can't go backwards, and we updated the model by adding fields
        // This could be done better
        model.put("mod", oldModel!!.getLong("mod"))
        col.notetypes.save(model, true)
        col.notetypes.update(model)
        col.reset()

        if (col.db.database.inTransaction()) {
            col.db.database.setTransactionSuccessful()
        } else {
            Timber.i("CollectionTask::SaveModel was not in a transaction? Cannot mark transaction successful.")
        }
    } finally {
        col.db.safeEndInTransaction()
    }
}

fun suspendCardMulti(col: Collection, cardIds: List<Long>): Array<Card> {
    val cards = cardIds.map { col.getCard(it) }.toTypedArray()
    return col.db.executeInTransaction {
        val sched = col.sched
        // collect undo information
        val cids = LongArray(cards.size)
        val originalSuspended = BooleanArray(cards.size)
        var hasUnsuspended = false
        for (i in cards.indices) {
            val card = cards[i]
            cids[i] = card.id
            if (card.queue != Consts.QUEUE_TYPE_SUSPENDED) {
                hasUnsuspended = true
                originalSuspended[i] = false
            } else {
                originalSuspended[i] = true
            }
        }

        // if at least one card is unsuspended -> suspend all
        // otherwise unsuspend all
        if (hasUnsuspended) {
            sched.suspendCards(cids)
        } else {
            sched.unsuspendCards(cids)
        }

        // mark undo for all at once
        col.markUndo(UndoSuspendCardMulti(cards, originalSuspended, hasUnsuspended))

        // reload cards because they'll be passed back to caller
        for (c in cards) {
            c.load()
        }
        sched.deferReset()
        // pass cards back so more actions can be performed by the caller
        // (querying the cards again is unnecessarily expensive)
        cards
    }
}

// TODO: Instead of returning Computation.err() can throw an exception with the exact message what went wrong
//      Or can add a message parameter to the Computation.err() so that message can be propagated upwards, currently
//      there is no way for user to know why the operation failed, was it due to same deck id, dynamic deck or something else?
fun changeDeckMulti(
    col: Collection,
    cardIds: List<Long>,
    newDid: DeckId
): Computation<Array<Card>> {
    val cards = cardIds.map { col.getCard(it) }.toTypedArray()
    Timber.i("Changing %d cards to deck: '%d'", cards.size, newDid)
    return col.db.executeInTransaction {
        val deckData = col.decks.get(newDid)
        if (Decks.isDynamic(deckData)) {
            // #5932 - can't change to a dynamic deck. Use "Rebuild"
            Timber.w("Attempted to move to dynamic deck. Cancelling task.")
            return@executeInTransaction Computation.err()
        }

        // Confirm that the deck exists (and is not the default)
        try {
            val actualId = deckData.getLong("id")
            if (actualId != newDid) {
                Timber.w("Attempted to move to deck %d, but got %d", newDid, actualId)
                return@executeInTransaction Computation.err()
            }
        } catch (e: Exception) {
            Timber.e(e, "failed to check deck")
            return@executeInTransaction Computation.err()
        }
        val changedCardIds = LongArray(cards.size)
        for (i in cards.indices) {
            changedCardIds[i] = cards[i].id
        }
        col.sched.remFromDyn(changedCardIds)
        val originalDids = LongArray(cards.size)
        for (i in cards.indices) {
            val card = cards[i]
            card.load()
            // save original did for undo
            originalDids[i] = card.did
            // then set the card ID to the new deck
            card.did = newDid
            val note = card.note()
            note.flush()
            // flush card too, in case, did has been changed
            card.flush()
        }
        val changeDeckMulti: UndoAction = UndoChangeDeckMulti(cards, originalDids)
        // mark undo for all at once
        col.markUndo(changeDeckMulti)
        // pass cards back so more actions can be performed by the caller
        // (querying the cards again is unnecessarily expensive)
        return@executeInTransaction Computation.ok(cards)
    }
}
