// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.DeckConfigsForUpdate
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.updateDeckConfigsRequest
import anki.decks.deckId
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import com.ichi2.testutils.ext.defaultDeckNewCardsPerDay
import com.ichi2.testutils.webView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class DeckOptionsRendererRecoveryTest : RobolectricTest() {
    @Test
    fun `recovered Deck Options shows settings from a successful Save and Optimize`() =
        runTest {
            // Keep activity lifecycle observers on the test thread when a real IO save returns.
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    SingleFragmentActivity::class.java,
                    DeckOptions.getIntent(targetContext, Consts.DEFAULT_DECK_ID),
                )
            val page = assertIs<DeckOptions>(controller.get().fragment)
            page.onWebViewReady()
            val originalWebView = page.webViewLayout.webView
            val saveTracking =
                assertIs<DeckOptions.SaveAndOptimizeReload>(shadowOf(originalWebView).getJavascriptInterface("ankidroidSave"))
            val config =
                col.backend
                    .getDeckConfigsForUpdate(Consts.DEFAULT_DECK_ID)
                    .allConfigList
                    .single()
                    .config
            val newLimit = 42
            val input =
                updateDeckConfigsRequest {
                    targetDeckId = Consts.DEFAULT_DECK_ID
                    mode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS
                    fsrs = true
                    configs.add(config.toBuilder().setConfig(config.config.toBuilder().setNewPerDay(newLimit)).build())
                }.toByteArray()
            saveTracking.started()
            controller.pause().stop()
            val save = async { page.handlePostRequest(PostRequestUri("/_anki/updateDeckConfigs"), input) }

            // Leave time for the real backend operation to finish before the stopped renderer is reclaimed.
            // Use a real clock because the save runs on Dispatchers.IO.
            withContext(Dispatchers.Default) { withTimeoutOrNull(3.seconds) { save.join() } }
            assertTrue(page.webViewLayout.isAttachedToWindow)
            page.webViewLayout.onRenderProcessGone(originalWebView)
            val recoveredWebView = page.webViewLayout.webView
            assertNotSame(originalWebView, recoveredWebView)

            val loadedConfig =
                DeckConfigsForUpdate.parseFrom(
                    page.handlePostRequest(
                        PostRequestUri("/_anki/getDeckConfigsForUpdate"),
                        deckId { did = Consts.DEFAULT_DECK_ID }.toByteArray(),
                    ),
                )
            val loadedLimit =
                loadedConfig.allConfigList
                    .single()
                    .config.config.newPerDay

            controller.restart().start().resume()
            // Only the destroyed document was waiting for the save response.
            save.await()
            assertEquals(newLimit, col.defaultDeckNewCardsPerDay)
            assertTrue(
                loadedLimit == newLimit || shadowOf(recoveredWebView).reloadInvocations > 0,
                "The recovered WebView loaded a stale limit of $loadedLimit and did not reload after saving $newLimit",
            )
        }
}
