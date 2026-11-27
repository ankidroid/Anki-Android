// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.SingleFragmentScreenshotTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Screenshot tests for [CongratsPage]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.CongratsPageScreenshotTest"`
 */
class CongratsPageScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = CongratsPage.getIntent(targetContext)

    @Test
    fun `previous card info in overflow menu`() {
        val card = addBasicNote().firstCard()
        col.sched.answerCard(card, Rating.EASY)
        val intent = CongratsPage.getIntent(targetContext, previousCardId = card.id)
        val activity = startRegularActivity<SingleFragmentActivity>(intent)
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
        // Wait for the asynchronous card lookup before opening the native overflow popup.
        advanceRobolectricLooperUntil { toolbar.menu.findItem(R.id.action_previous_card_info).isVisible }
        toolbar.showOverflowMenu()
        advanceRobolectricLooper()
        assertTrue(toolbar.isOverflowMenuShowing)

        captureScreen("previousCardInfo")
    }

    @Test
    fun `standard deck`() =
        runTest {
            addBasicNote()
            withCol { sched.answerCard(checkNotNull(sched.card), Rating.EASY) }

            withScreen {
                advanceUntilIdle()
                advanceRobolectricLooper()
                captureScreen("standardDeck")
            }
        }

    @Test
    fun `filtered deck`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")
            withCol { sched.answerCard(checkNotNull(sched.card), Rating.EASY) }

            withScreen {
                advanceUntilIdle()
                advanceRobolectricLooper()
                captureScreen("filteredDeck")
            }
        }
}
