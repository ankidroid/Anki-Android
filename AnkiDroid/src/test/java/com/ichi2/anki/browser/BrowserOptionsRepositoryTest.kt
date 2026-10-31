// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.ext.ignoreAccentsInSearch
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BrowserOptionsRepositoryTest : JvmTest() {
    @Test
    fun `latest ignore accents save wins while earlier save is queued`() =
        runTest {
            col.config.ignoreAccentsInSearch = false
            val repository = BrowserOptionsRepository(SPMockBuilder().createSharedPreferences())
            repository.load()

            withQueuedCollectionAccess {
                val first = launch(start = CoroutineStart.UNDISPATCHED) { repository.setIgnoreAccentsInSearch(true) }
                assertFalse(first.isCompleted, "The first write must still be queued")

                repository.setIgnoreAccentsInSearch(false)
                first.join()

                assertFalse(withCol { config.ignoreAccentsInSearch }, "The latest saved value should be persisted")
                assertFalse(repository.ignoreAccentsInSearch.value, "The flow should match the latest saved value")
            }
        }

    @Test
    fun `latest mode save wins while earlier save is queued`() =
        runTest {
            CardsOrNotes.CARDS.saveToCollection(col)
            val repository = BrowserOptionsRepository(SPMockBuilder().createSharedPreferences())
            repository.load()

            withQueuedCollectionAccess {
                val first = launch(start = CoroutineStart.UNDISPATCHED) { repository.setCardsOrNotes(CardsOrNotes.NOTES) }
                assertFalse(first.isCompleted, "The first write must still be queued")

                repository.setCardsOrNotes(CardsOrNotes.CARDS)
                first.join()

                assertEquals(
                    CardsOrNotes.CARDS,
                    withCol { CardsOrNotes.fromCollection(this) },
                    "The latest saved value should be persisted",
                )
                assertEquals(CardsOrNotes.CARDS, repository.cardsOrNotes.value, "The flow should match the latest saved value")
            }
        }

    /** Queue collection work on the test scheduler to exercise suspended, competing writes. */
    private suspend fun TestScope.withQueuedCollectionAccess(block: suspend TestScope.() -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }
}
