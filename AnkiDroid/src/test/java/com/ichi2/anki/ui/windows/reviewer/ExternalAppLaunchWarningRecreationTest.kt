// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.DeckConfig
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.testutils.mockWebResourceRequest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ExternalAppLaunchWarningRecreationTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `auto advance survives activity recreation while a launch warning is open`() =
        runTest {
            val card = addBasicNote().firstCard()
            val config = col.decks.configDictForDeckId(card.currentDeckId())
            config.secondsToShowQuestion = 0.0
            config.secondsToShowAnswer = 1.0
            config.jsonObject.put("waitForAudio", false)
            config.jsonObject.put(DeckConfig.ANSWER_ACTION, 2)
            col.decks.save(config)
            editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }

            val intent = ReviewerFragment.getIntent(targetContext)
            Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
                controller.setup().windowFocusChanged(true)
                advanceUntilIdle()
                val reviewer = assertIs<ReviewerFragment>(controller.get().fragment)
                reviewer.viewModel.onPageFinished(false)
                advanceUntilIdle()
                reviewer.viewModel.onShowAnswer()
                advanceUntilIdle()
                reviewer.viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
                testScheduler.runCurrent()

                val webView = assertIs<WebView>(reviewer.binding.webViewLayout.getChildAt(0))
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
                    webView,
                    mockWebResourceRequest("intent:#Intent;action=android.intent.action.MAIN;package=com.android.settings;end"),
                )
                advanceRobolectricLooper()
                val originalDialogs = ShadowDialog.getShownDialogs().toSet()
                controller.recreate()
                testScheduler.runCurrent()
                val recreated = assertIs<ReviewerFragment>(controller.get().fragment)
                assertSame(reviewer.viewModel, recreated.viewModel)
                recreated.viewModel.onPageFinished(true)
                testScheduler.runCurrent()
                ShadowDialog
                    .getShownDialogs()
                    .filter { it !in originalDialogs && it.isShowing }
                    .forEach { it.cancel() }
                advanceRobolectricLooper()
                advanceUntilIdle()

                assertTrue(recreated.viewModel.isAutoAdvanceEnabledFlow.value)
                assertEquals(1, col.getCard(card.id).reps)
            }
        }
}
