// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.model.SelectableDeck
import com.ichi2.testutils.withQueuedCollectionAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.test.assertEquals

class CardBrowserLatestDeckSelectionTest : JvmCardBrowserViewModelTestBase() {
    @Test
    fun `reselecting the original deck supersedes a queued all-decks selection`() =
        runTest {
            val browser = viewModel()
            val originalDeck =
                browser.searchRequestFlow.value.filters.decks
                    .single()

            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            withQueuedCollectionAccess {
                browser.setSelectedDeck(SelectableDeck.AllDecks)
                browser.setSelectedDeck(SelectableDeck.Deck(originalDeck.id, originalDeck.name))
                advanceUntilIdle()
            }

            assertEquals(originalDeck.id, browser.lastDeckId)
            assertEquals(originalDeck.id, browser.deckId, "The last explicit deck selection must win")
        }
}
