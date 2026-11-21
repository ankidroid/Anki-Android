// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer
import anki.scheduler.customStudyRequest
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_FORGOT
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.utils.positiveButton
import net.ankiweb.rsdroid.BackendException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ForgottenCardsEligibilityTest : RobolectricTest() {
    @Test
    fun `suspended forgotten cards do not enable Create`() =
        runTest {
            val card = forgottenCard()
            col.sched.suspendCards(listOf(card.id))

            assertNoCardsCanBeStudied()
            assertCreateAvailability(false)
        }

    @Test
    fun `buried forgotten cards do not enable Create`() =
        runTest {
            val card = forgottenCard()
            col.sched.buryCards(listOf(card.id))

            assertNoCardsCanBeStudied()
            assertCreateAvailability(false)
        }

    @Test
    fun `cards borrowed by another filtered deck do not enable Create`() =
        runTest {
            val card = forgottenCard()
            val filtered = col.decks.newFiltered("Other filtered deck")
            col.sched.rebuildFilteredDeck(filtered)
            assertEquals(filtered, col.getCard(card.id).did)
            // Keep the source deck visible while its forgotten card is borrowed.
            col.decks.select(Consts.DEFAULT_DECK_ID)
            addBasicNote("Unreviewed", "Card")

            assertNoCardsCanBeStudied()
            assertCreateAvailability(false)
        }

    @Test
    fun `eligible forgotten cards enable Create`() =
        runTest {
            forgottenCard()

            assertCreateAvailability(true)
            buildForgottenSession()
            assertEquals(1, col.decks.cardCount(assertNotNull(col.decks.customStudySession).id))
        }

    @Test
    fun `cards in the reusable custom study session still enable Create`() =
        runTest {
            forgottenCard()
            buildForgottenSession()
            col.decks.select(Consts.DEFAULT_DECK_ID)
            addBasicNote("Unreviewed", "Card")

            assertCreateAvailability(true)
            buildForgottenSession()
            assertEquals(1, col.decks.cardCount(assertNotNull(col.decks.customStudySession).id))
        }

    @Test
    fun `reusable session cards from a different source deck do not enable Create`() =
        runTest {
            val card = forgottenCard()
            val other = addDeck("Other deck")
            col.setDeck(listOf(card.id), other)
            buildForgottenSession(deckId = other)
            col.decks.select(Consts.DEFAULT_DECK_ID)
            addBasicNote("Unreviewed", "Card")

            assertNoCardsCanBeStudied()
            assertCreateAvailability(false)
        }

    @Test
    fun `reusable session cards do not enable Create for a deck named filtered`() =
        runTest {
            forgottenCard()
            buildForgottenSession()
            val deckId = addDeck("filtered")
            col.setDeck(listOf(addBasicNote("Unreviewed", "Card").firstCard().id), deckId)
            col.decks.select(deckId)

            assertNoCardsCanBeStudied(deckId)
            assertCreateAvailability(false, deckId = deckId)
        }

    // Detects when the bundled backend includes the fix for https://github.com/ankitects/anki/issues/6004
    @Test
    fun `backend still rejects forgotten cards from a deck named filtered`() =
        runTest {
            val card = forgottenCard()
            val deckId = addDeck("filtered")
            col.setDeck(listOf(card.id), deckId)
            assertEquals(listOf(card.id), col.findCards("deck:Filtered rated:1:1"))

            assertNoCardsCanBeStudied(
                deckId,
                message = "Custom Study now accepts filtered. Remove FullDeckName.toStoredNameSearchNode and this test",
            )
        }

    @Test
    fun `changing the day range updates forgotten card eligibility`() =
        runTest {
            forgottenCard()
            col.db.execute("update revlog set id = id - ?", TimeUnit.DAYS.toMillis(2))

            assertNoCardsCanBeStudied()
            assertCreateAvailability(false)
            assertCreateAvailability(true, days = "3")
            buildForgottenSession(days = 3)
            assertEquals(1, col.decks.cardCount(assertNotNull(col.decks.customStudySession).id))
        }

    private fun forgottenCard(): Card {
        TimeManager.reset()
        val card = addBasicNote().firstCard()
        col.sched.answerCard(card, CardAnswer.Rating.AGAIN)
        assertEquals(listOf(card.id), col.findCards("deck:Default rated:1:1"))
        return col.getCard(card.id)
    }

    private fun buildForgottenSession(
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        days: Int = 1,
    ) {
        val changes = col.sched.customStudy(forgottenRequest(deckId, days))
        assertTrue(changes.card, "Custom Study should move matching cards into the session")
    }

    private fun forgottenRequest(
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        days: Int = 1,
    ) = customStudyRequest {
        this.deckId = deckId
        forgotDays = days
    }

    private fun assertNoCardsCanBeStudied(
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        message: String? = null,
    ) {
        val error = assertFailsWith<BackendException>(message) { col.sched.customStudy(forgottenRequest(deckId = deckId)) }
        assertEquals(TR.customStudyNoCardsMatchedTheCriteriaYou(), error.message)
    }

    private fun assertCreateAvailability(
        expected: Boolean,
        days: String? = null,
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
    ) {
        Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup().use { controller ->
            val fragment = CustomStudyDialog.createSubDialog(deckId, STUDY_FORGOT)
            controller.get().showDialogFragment(fragment)
            days?.let { fragment.binding.detailsEditText2.setText(it) }
            shadowOf(Looper.getMainLooper()).idle()

            val dialog = fragment.requireDialog() as AlertDialog
            assertEquals(expected, dialog.positiveButton.isEnabled, "Create must match filtered-deck eligibility")
            assertEquals(
                if (expected) null else TR.customStudyNoCardsMatchedTheCriteriaYou(),
                fragment.binding.detailsEditText2Layout.error
                    ?.toString(),
            )
        }
    }
}
