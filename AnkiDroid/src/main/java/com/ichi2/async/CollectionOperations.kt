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
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import timber.log.Timber
import java.util.*

/**
 * This file contains functions that have been migrated from [CollectionTask]
 * Remove this comment when migration has been completed
 * TODO: All functions associated to Collection can be converted to extension function to avoid redundant parameter [col] in each.
 */

/**
 * Takes a list of media file names and removes them from the [Collection]
 * @param unused List of media names to be deleted
 */
context (Collection)
fun deleteMedia(unused: List<String>): Int {
    // FIXME: this provides progress info that is not currently used
    this@Collection.media.removeFiles(unused)
    return unused.size
}

// TODO: Once [com.ichi2.async.CollectionTask.RebuildCram] and [com.ichi2.async.CollectionTask.EmptyCram]
// are migrated to Coroutines, move this function to [com.ichi2.anki.StudyOptionsFragment]
context (Collection)
fun updateValuesFromDeck(): StudyOptionsFragment.DeckStudyData? {
    Timber.d("doInBackgroundUpdateValuesFromDeck")
    return try {
        val sched = this@Collection.sched
        val counts = sched.counts()
        val totalNewCount = sched.totalNewForCurrentDeck()
        val totalCount = sched.cardCount()
        StudyOptionsFragment.DeckStudyData(
            counts.new,
            counts.lrn,
            counts.rev,
            totalNewCount,
            totalCount
        )
    } catch (e: RuntimeException) {
        Timber.e(e, "doInBackgroundUpdateValuesFromDeck - an error occurred")
        null
    }
}

suspend fun renderBrowserQA(
    cards: List<CardBrowser.CardCache>,
    startPos: Int,
    n: Int,
    column1Index: Int,
    column2Index: Int,
    onProgressUpdate: (Int) -> Unit
): Pair<List<CardBrowser.CardCache>, MutableList<Long>> = withContext(Dispatchers.IO) {
    Timber.d("doInBackgroundRenderBrowserQA")
    val invalidCardIds: MutableList<Long> = ArrayList()
    // for each specified card in the browser list
    for (i in startPos until startPos + n) {
        // Stop if cancelled, throw cancellationException
        ensureActive()

        if (i < 0 || i >= cards.size) {
            continue
        }
        val card: CardBrowser.CardCache = try {
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
        } catch (e: BackendNotFoundException) {
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
 * Handles everything for a model change at once - template add / deletes as well as content updates
 * @return Pair<Boolean, String> : (true, null) when success, (false, exceptionMessage) when failure
 */
context (Collection)
fun saveModel(
    notetype: NotetypeJson,
    templateChanges: ArrayList<Array<Any>>
) {
    Timber.d("doInBackgroundSaveModel")
    val oldModel = this@Collection.notetypes.get(notetype.getLong("id"))

    // TODO: make undoable
    val newTemplates = notetype.getJSONArray("tmpls")
    for (change in templateChanges) {
        val oldTemplates = oldModel!!.getJSONArray("tmpls")
        when (change[1] as CardTemplateNotetype.ChangeType) {
            CardTemplateNotetype.ChangeType.ADD -> {
                Timber.d("doInBackgroundSaveModel() adding template %s", change[0])
                this@Collection.notetypes.addTemplate(oldModel, newTemplates.getJSONObject(change[0] as Int))
            }
            CardTemplateNotetype.ChangeType.DELETE -> {
                Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0])
                this@Collection.notetypes.remTemplate(oldModel, oldTemplates.getJSONObject(change[0] as Int))
            }
        }
    }

    // required for Rust: the modified time can't go backwards, and we updated the model by adding fields
    // This could be done better
    notetype.put("mod", oldModel!!.getLong("mod"))
    this@Collection.notetypes.save(notetype)
    this@Collection.notetypes.update(notetype)
}
