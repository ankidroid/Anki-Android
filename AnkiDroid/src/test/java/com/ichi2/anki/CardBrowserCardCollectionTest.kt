/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import com.ichi2.anki.CardBrowser.CardCollection
import com.ichi2.anki.CardBrowser.PositionAware
import org.junit.Test
import kotlin.test.assertEquals

class CardBrowserCardCollectionTest {

    @Test
    fun reverseFixesPosition() {
        val cardCollection = createCollection(Positioned(0), Positioned(1))
        assertEquals(cardCollection[0].position, 0)
        assertEquals(cardCollection[0].initialValue, 0)
        cardCollection.reverse()
        assertEquals(cardCollection[0].position, 0)
        assertEquals(cardCollection[0].initialValue, 1)
    }

    private fun createCollection(vararg toInsert: Positioned): CardCollection<Positioned> {
        val cardCollection = CardCollection<Positioned>()
        cardCollection.replaceWith(toInsert.toMutableList())
        return cardCollection
    }

    private class Positioned(override var position: Int) : PositionAware {
        val initialValue: Int = position
    }
}
