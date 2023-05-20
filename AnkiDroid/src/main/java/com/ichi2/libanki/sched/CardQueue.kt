/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.sched

import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.Collection
import com.ichi2.utils.KotlinCleanup
import java.util.*

abstract class CardQueue<T : Card.Cache>(
    // We need to store mSched and not queue, because during initialization of sched, when CardQueues are initialized
    // sched.getCol is null.
    private val sched: AbstractSched
) {
    protected val queue = LinkedList<T>()

    fun loadFirstCard() {
        if (!queue.isEmpty()) {
            // No need to reload. If the card was changed, reset would have been called and emptied the queue
            queue[0].loadQA(false, false)
        }
    }

    @Throws(NoSuchElementException::class)
    fun removeFirstCard(): Card {
        return queue.remove()!!.card
    }

    // CardCache and LrnCache with the same id will be considered as equal so it's a valid implementation.
    fun remove(cid: CardId) =
        queue.removeIf { card -> card.id == cid }

    fun add(elt: T) {
        queue.add(elt)
    }

    open fun clear() {
        queue.clear()
    }

    val isEmpty: Boolean
        get() = queue.isEmpty()

    fun size(): Int {
        return queue.size
    }

    fun shuffle(r: Random) {
        Collections.shuffle(queue, r)
    }

    fun listIterator(): MutableListIterator<T> {
        return queue.listIterator()
    }

    @KotlinCleanup("make non-null")
    protected val col: Collection
        get() = sched.col
}
