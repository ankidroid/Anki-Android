// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.DeckConfigsForUpdate
import anki.deck_config.updateDeckConfigsRequest
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class DeckOptionsTodayLimitTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a Today only limit saved before rollover does not apply to the next day`() =
        runTest {
            // Keep activity lifecycle observers on the test thread when a real IO save returns.
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val deck = Consts.DEFAULT_DECK_ID
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    SingleFragmentActivity::class.java,
                    DeckOptions.getIntent(targetContext, deck),
                )
            val page = assertIs<DeckOptions>(controller.get().fragment)
            val config = withCol { backend.getDeckConfigsForUpdate(deck) }
            val input =
                updateDeckConfigsRequest {
                    targetDeckId = deck
                    fsrs = config.fsrs
                    configs.add(config.allConfigList.single().config)
                    limits =
                        DeckConfigsForUpdate.CurrentDeck.Limits
                            .newBuilder()
                            .setNewToday(0)
                            .setNewTodayActive(true)
                            .build()
                }.toByteArray()
            val day = withCol { sched.today }

            controller.pause().stop()
            val save = async { page.handlePostRequest(PostRequestUri("/_anki/updateDeckConfigs"), input) }
            // Give an ungated save time to finish on the real IO dispatcher.
            withContext(Dispatchers.Default) { withTimeoutOrNull(5.seconds) { save.join() } }
            // Move the collection one day back so Anki's elapsed study day advances without changing the system clock.
            withCol { db.execute("update col set crt = crt - 86400") }
            CollectionManager.ensureClosed()
            assertEquals(day + 1, withCol { sched.today })

            controller.restart().start().resume()
            save.await()
            assertFalse(
                withCol {
                    backend
                        .getDeckConfigsForUpdate(deck)
                        .currentDeck.limits.newTodayActive
                },
                "The override belonged to the study day when the WebView submitted the save",
            )
        }
}
