// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerPartialDeletionDeleteTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `Delete keeps Default when the shown deck is gone`() = verifyDefaultCardsPreserved(openReminders = false)

    @Test
    fun `Delete keeps Default when reminders show a gone deck`() {
        val previouslyEnabled = Prefs.newReviewRemindersEnabled
        Prefs.newReviewRemindersEnabled = true
        try {
            verifyDefaultCardsPreserved(openReminders = true)
        } finally {
            Prefs.newReviewRemindersEnabled = previouslyEnabled
        }
    }

    private fun verifyDefaultCardsPreserved(openReminders: Boolean) {
        val failSelection = AtomicBoolean(false)
        val failure = BackendIoException(backendError { message = "Cannot select the replacement deck" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges {
                    if (failSelection.getAndSet(false)) throw failure
                    return super.setCurrentDeck(did)
                }
            }
        }
        addNoteToDeck(DEFAULT_DECK_ID)
        val removed = addDeck("Removed", setAsSelected = true)
        val survivor = addDeck("Survivor")
        addNoteToDeck(removed)
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(DEFAULT_DECK_ID, removed, survivor))
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true &&
                    picker.createMenuJob?.isCompleted == true && picker.fragment?.shownDeckId == removed
            }

            val pane =
                if (openReminders) {
                    picker.supportFragmentManager.setDeckPickerContextMenuResult(
                        DeckPickerContextMenuResult(removed, DeckPickerContextMenuOption.SCHEDULE_REMINDERS),
                    )
                    advanceRobolectricLooperUntil {
                        picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) is ScheduleRemindersFragment
                    }
                    picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)
                } else {
                    picker.fragment
                }

            throwOnShowError = false
            failSelection.set(true)
            val deletion = picker.deleteDeck(removed)
            advanceRobolectricLooperUntil {
                deletion.isCompleted && (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true
            }
            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            ShadowDialog.getLatestDialog().dismiss()
            advanceRobolectricLooper()

            assertNull(col.decks.getLegacy(removed))
            val currentPane = picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)
            // If the UI has already switched to the actual selected deck, Delete may act on it.
            if (currentPane !== pane || (!openReminders && picker.fragment?.shownDeckId != removed)) return@withDeckPicker
            val priorDispatcher = CollectionManager.setTestDispatcher(UnconfinedTestDispatcher())
            try {
                assertTrue(shadowOf(picker).clickMenuItem(R.id.action_deck_delete))
                advanceRobolectricLooper()
                assertEquals(
                    1,
                    col.decks.cardCount(DEFAULT_DECK_ID, includeSubdecks = false),
                    "Delete must preserve the cards in the deck the pane does not show",
                )
            } finally {
                CollectionManager.setTestDispatcher(priorDispatcher)
            }
        }
    }
}
