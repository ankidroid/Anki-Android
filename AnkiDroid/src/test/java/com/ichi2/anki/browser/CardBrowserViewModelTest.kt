/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardBrowserViewModelTest : JvmTest() {
    @Test
    fun `delete search history - Issue 14989`() = runViewModelTest {
        saveSearch("hello", "aa")
        savedSearches().also { searches ->
            assertThat("filters after saving", searches.size, equalTo(1))
            assertThat("filters after saving", searches["hello"], equalTo("aa"))
        }
        removeSavedSearch("hello")
        assertThat("filters should be empty after removing", savedSearches().size, equalTo(0))
    }

    @Test
    fun `change deck in notes mode 15444`() = runViewModelTest {
        val newDeck = addDeck("World")
        selectDefaultDeck()

        for (i in 0 until 5) {
            addNoteUsingBasicAndReversedModel()
        }
        setCardsOrNotes(CardsOrNotes.NOTES)
        waitForSearchResults()

        selectRowsWithPositions(0, 2)

        val allCardIds = queryAllSelectedCardIds()
        assertThat(allCardIds.size, equalTo(4))

        moveSelectedCardsToDeck(newDeck).join()

        for (cardId in allCardIds) {
            assertThat("Deck should be changed", col.getCard(cardId).did, equalTo(newDeck))
        }

        val hasSomeDecksUnchanged = cards.any { row -> row.card.did != newDeck }
        assertThat("some decks are unchanged", hasSomeDecksUnchanged)
    }

    private fun runViewModelTest(testBody: suspend CardBrowserViewModel.() -> Unit) = runTest {
        val viewModel = CardBrowserViewModel(
            lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
            cacheDir = createTransientDirectory(),
            preferences = AnkiDroidApp.sharedPreferencesProvider
        )
        testBody(viewModel)
    }
}

@Suppress("SameParameterValue")
private fun CardBrowserViewModel.selectRowsWithPositions(vararg positions: Int) {
    for (pos in positions) {
        selectRowAtPosition(pos)
    }
}

private suspend fun CardBrowserViewModel.waitForSearchResults() {
    searchJob?.join()
}
