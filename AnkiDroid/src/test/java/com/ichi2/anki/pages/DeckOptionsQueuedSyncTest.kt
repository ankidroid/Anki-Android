// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.updateDeckConfigsRequest
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import com.ichi2.testutils.ext.defaultDeckNewCardsPerDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class DeckOptionsQueuedSyncTest : RobolectricTest() {
    @Test
    fun `a save submitted before a newer preset arrives does not overwrite it on resume`() =
        runTest {
            // Keep activity lifecycle observers on the test thread when a real IO save returns.
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val targetDeck = Consts.DEFAULT_DECK_ID
            val intent = DeckOptions.getIntent(targetContext, targetDeck)
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(SingleFragmentActivity::class.java, intent)
            val page = controller.get().fragment as DeckOptions
            val config =
                col.backend
                    .getDeckConfigsForUpdate(targetDeck)
                    .allConfigList
                    .single()
                    .config
                    .toBuilder()
            config.setConfig(config.config.toBuilder().setNewPerDay(42))
            val body =
                updateDeckConfigsRequest {
                    targetDeckId = targetDeck
                    mode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS
                    fsrs = true
                    configs += config.build()
                }.toByteArray()

            controller.pause().stop()
            val save = async { page.handlePostRequest(PostRequestUri("/_anki/updateDeckConfigs"), body) }
            // The save runs on the real IO dispatcher, so wait in real time for it to finish while stopped.
            withContext(Dispatchers.Default) { withTimeoutOrNull(5.seconds) { save.join() } }
            // Model background sync applying a newer preset through the collection queue.
            withCol { updateDeckConfig(targetDeck) { new.perDay = 73 } }
            controller.restart().start().resume()
            save.await()

            assertEquals(73, col.defaultDeckNewCardsPerDay, "The older save must not replace the newer preset after resume")
        }
}
