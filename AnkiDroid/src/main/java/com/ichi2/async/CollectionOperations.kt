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
import com.ichi2.anki.CardTemplateNotetype
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.libanki.Collection
import com.ichi2.libanki.NotetypeJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import timber.log.Timber

/**
 * Takes a list of media file names and removes them from the [Collection]
 * @param unused List of media names to be deleted
 */
fun deleteMedia(col: Collection, unused: List<String>): Int {
    // FIXME: this provides progress info that is not currently used
    col.media.removeFiles(unused)
    return unused.size
}

suspend fun renderBrowserQA(
    cards: List<CardBrowser.CardCache>,
    startPos: Int,
    n: Int,
    column1: CardBrowserColumn,
    column2: CardBrowserColumn,
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
        card.load(false, column1, column2)
        val progress = i.toFloat() / n * 100
        withContext(Dispatchers.Main) { onProgressUpdate(progress.toInt()) }
    }
    Pair(cards, invalidCardIds)
}

/**
 * Handles everything for a model change at once - template add / deletes as well as content updates
 * @return Pair<Boolean, String> : (true, null) when success, (false, exceptionMessage) when failure
 */
fun saveModel(
    col: Collection,
    notetype: NotetypeJson,
    templateChanges: ArrayList<Array<Any>>
) {
    Timber.d("doInBackgroundSaveModel")
    val oldModel = col.notetypes.get(notetype.getLong("id"))

    // TODO: make undoable
    val newTemplates = notetype.getJSONArray("tmpls")
    for (change in templateChanges) {
        val oldTemplates = oldModel!!.getJSONArray("tmpls")
        when (change[1] as CardTemplateNotetype.ChangeType) {
            CardTemplateNotetype.ChangeType.ADD -> {
                Timber.d("doInBackgroundSaveModel() adding template %s", change[0])
                col.notetypes.addTemplate(oldModel, newTemplates.getJSONObject(change[0] as Int))
            }
            CardTemplateNotetype.ChangeType.DELETE -> {
                Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0])
                col.notetypes.remTemplate(oldModel, oldTemplates.getJSONObject(change[0] as Int))
            }
        }
    }

    // required for Rust: the modified time can't go backwards, and we updated the model by adding fields
    // This could be done better
    notetype.put("mod", oldModel!!.getLong("mod"))
    col.notetypes.save(notetype)
    col.notetypes.update(notetype)
}
