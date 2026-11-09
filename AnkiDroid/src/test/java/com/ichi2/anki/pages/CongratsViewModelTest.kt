// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertEquals

// https://github.com/ankidroid/Anki-Android/issues/21393
class CongratsViewModelTest : JvmTest() {
    private val viewModel by lazy { CongratsViewModel() }

    @Test
    fun `collection changes request study options after a rebuild refills the deck`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            viewModel.congratsRefreshState.test {
                undoableOp { sched.rebuildFilteredDeck(deckId) }
                assertEquals(CongratsRefreshState.DeckHasCardsToStudy, awaitItem())
            }
        }

    @Test
    fun `onStudyQueuesChanged - reloads the page when rebuild finds no cards`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "deck:NonExistentDeck")

            withCol { sched.rebuildFilteredDeck(deckId) }

            viewModel.congratsRefreshState.test {
                viewModel.onStudyQueuesChanged()
                assertEquals(CongratsRefreshState.ReloadPage, awaitItem())
            }
        }

    @Test
    fun `collection changes from this ViewModel are handled by their caller`() =
        runTest {
            addBasicNote()
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            viewModel.congratsRefreshState.test {
                undoableOp(handler = viewModel) { sched.rebuildFilteredDeck(deckId) }
                expectNoEvents()
            }
        }

    @Test
    fun `clearing the ViewModel unsubscribes from collection changes`() {
        val subscribers = ChangeManager.subscriberCount
        val store = ViewModelStore()
        try {
            store.put("congrats", viewModel)
            assertEquals(subscribers + 1, ChangeManager.subscriberCount)
        } finally {
            store.clear()
        }
        assertEquals(subscribers, ChangeManager.subscriberCount)
    }
}
