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
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Test

class CardBrowserCardCollectionTest {
    @Test
    fun reverseFixesPosition() {
        val cardCollection = createCollection(Positioned(0), Positioned(1))
        assertThat(cardCollection[0].position, equalTo(0))
        assertThat(cardCollection[0].initialValue, equalTo(0))
        cardCollection.reverse()
        assertThat(cardCollection[0].position, equalTo(0))
        assertThat(cardCollection[0].initialValue, equalTo(1))
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
