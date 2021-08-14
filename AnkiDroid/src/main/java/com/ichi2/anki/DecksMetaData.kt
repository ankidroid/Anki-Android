/*
 * Copyright (c) 2021 Prateek Singh <prateeksingh3212@gmail.com>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.util.Pair
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Decks
import com.ichi2.libanki.sched.Counts
import timber.log.Timber

data class DecksMetaData(val context: Context) {
    private val mDecks: Decks?

    // Lambda to check the collection is not null.
    private val mCollection: (context: Context) -> Collection? = {
        val collection = CollectionHelper.getInstance()
        if (collection.getColSafe(it) != null) {
            collection.getCol(it)
        } else {
            null
        }
    }

    init {
        mDecks = mCollection(context)?.decks
    }

    /**
     * Total number of due cards and their expected time to compete.
     * @return Pair of Total Due Cards and Expected time to complete. <br></br>
     * **first** is due cards. <br></br>
     * **second** is Expected time.
     */
    fun getTotalDueCards(): Pair<Int, Int> {

        val collection = mCollection(context) ?: return Pair(0, 0)

        val total = Counts()
        // Ensure queues are reset if we cross over to the next day.
        collection.sched._checkDay()

        // Only count the top-level decks in the total
        val nodes = collection.sched.deckDueTree()
        for (node in nodes) {
            total.addNew(node.newCount)
            total.addLrn(node.lrnCount)
            total.addRev(node.revCount)
        }
        val eta = collection.sched.eta(total, false)
        Timber.d("getTotalDueCards: count -> ${total.count()} eta -> $eta")

        return Pair(total.count(), eta)
    }
}
