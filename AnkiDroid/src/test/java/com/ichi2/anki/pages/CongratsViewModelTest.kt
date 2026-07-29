// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

// https://github.com/ankidroid/Anki-Android/issues/21393
@RunWith(AndroidJUnit4::class)
class CongratsViewModelTest : RobolectricTest() {
    private val viewModel = CongratsViewModel()

    @Test
    fun `onStudyQueuesChanged - navigates away once rebuild refills an emptied filtered deck`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            withCol { sched.rebuildFilteredDeck(deckId) }

            viewModel.congratsRefreshState.test {
                viewModel.onStudyQueuesChanged()
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
}
