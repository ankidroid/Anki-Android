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
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Test
import java.util.*

@KotlinCleanup("IDE lint")
@KotlinCleanup("`is` -> equalTo")
class CardBrowser_CardCollectionTest {
    @Test
    fun reverseFixesPosition() {
        val cardCollection = createCollection(Positioned(0), Positioned(1))
        assertThat(cardCollection[0].position, `is`(0))
        assertThat(cardCollection[0].initialValue, `is`(0))
        cardCollection.reverse()
        assertThat(cardCollection[0].position, `is`(0))
        assertThat(cardCollection[0].initialValue, `is`(1))
    }

    private fun createCollection(vararg toInsert: Positioned?): CardCollection<Positioned> {
        val cardCollection = CardCollection<Positioned>()
        cardCollection.replaceWith(Arrays.asList(*toInsert))
        return cardCollection
    }

    @KotlinCleanup("See if we can have the variable override")
    class Positioned(override var position: Int) : PositionAware {
        val initialValue: Int = position
        @JvmName("getPosition1")
        fun getPosition(): Int {
            return position
        }

        @JvmName("setPosition1")
        fun setPosition(value: Int) {
            position = value
        }
    }
}
