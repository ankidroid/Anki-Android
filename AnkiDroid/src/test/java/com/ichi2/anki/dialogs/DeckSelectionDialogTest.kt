/*
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
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

package com.ichi2.anki.dialogs

import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test

class DeckSelectionDialogTest {
    @Test
    fun verifyDeckDisplayName() {
        val input = "deck::sub-deck::sub-deck2::sub-deck3"
        val expected = "\t\t\t\t\t\tsub-deck3"

        val deck = SelectableDeck(1234, input)
        val actual: String = deck.displayName

        assertThat(actual, Matchers.equalTo(expected))
    }
}
