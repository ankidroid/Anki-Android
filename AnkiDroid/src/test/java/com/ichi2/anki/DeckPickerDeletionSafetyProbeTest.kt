// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import anki.decks.DeckTreeNode
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerDeletionSafetyProbeTest : RobolectricTest() {
    private val failure = BackendIoException(backendError { message = "Backend operation failed" })

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `missing study options pane does not authorize deleting the fallback deck`() {
        withPartlyAppliedDeletion(reminders = true) { picker, _, _ ->
            assertIs<ScheduleRemindersFragment>(picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment))
            assertNull(picker.fragment)

            pressDelete(picker)
            advanceRobolectricLooperUntil {
                picker.viewModel.progressManager.progress.value is com.ichi2.anki.progress.ViewModelProgress.Idle
            }
            assertEquals(
                1,
                col.decks.cardCount(DEFAULT_DECK_ID, includeSubdecks = true),
                "Without a visible study pane, Shift+Delete must not delete the fallback deck",
            )
        }
    }

    @Test
    fun `deletion checks the rendered deck rather than a newer model selection`() {
        withPartlyAppliedDeletion { picker, _, allowReads ->
            val pane = assertNotNull(picker.fragment)
            val deckName = pane.requireView().findViewById<TextView>(R.id.studyoptions_deck_name)
            assertEquals("Deleted", deckName.text.toString())
            // Complete a recovery refresh before the main thread renders its new StateFlow value.
            allowReads()
            runBlocking { withContext(Dispatchers.Default) { pane.viewModel.refreshData() } }
            assertEquals(DEFAULT_DECK_ID, pane.selectedDeckId)
            assertEquals("Deleted", deckName.text.toString())

            pressDelete(picker)
            advanceRobolectricLooperUntil {
                picker.viewModel.progressManager.progress.value is com.ichi2.anki.progress.ViewModelProgress.Idle
            }
            assertEquals(
                1,
                col.decks.cardCount(DEFAULT_DECK_ID, includeSubdecks = true),
                "Shift+Delete must not delete Default while the pane still displays the deleted deck",
            )
        }
    }

    private fun pressDelete(picker: DeckPicker) {
        picker.onKeyUp(
            KeyEvent.KEYCODE_DEL,
            KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_SHIFT_ON),
        )
    }

    private fun withPartlyAppliedDeletion(
        reminders: Boolean = false,
        block: (DeckPicker, Long, () -> Unit) -> Unit,
    ) {
        var failSelection = false
        var failReload = false
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges {
                    if (failSelection) {
                        failSelection = false
                        failReload = true
                        throw failure
                    }
                    return super.setCurrentDeck(did)
                }

                override fun deckTree(now: Long): DeckTreeNode = if (failReload) throw failure else super.deckTree(now)
            }
        }
        addNoteToDeck(DEFAULT_DECK_ID)
        val deleted = addDeck("Deleted", setAsSelected = true)
        val survivor = addDeck("Survivor")
        addNoteToDeck(deleted)
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(DEFAULT_DECK_ID, deleted, survivor))
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.selectedDeckId == deleted
            }
            if (reminders) {
                Prefs.newReviewRemindersEnabled = true
                picker.supportFragmentManager.setDeckPickerContextMenuResult(
                    DeckPickerContextMenuResult(deleted, DeckPickerContextMenuOption.SCHEDULE_REMINDERS),
                )
                advanceRobolectricLooper()
                assertIs<ScheduleRemindersFragment>(picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment))
            }
            throwOnShowError = false
            failSelection = true
            val deletion = picker.deleteDeck(deleted)
            advanceRobolectricLooperUntil { deletion.isCompleted && picker.viewModel.loadDeckCounts?.isCompleted == true }
            while (picker.viewModel.pendingErrors.value
                    .isNotEmpty()
            ) {
                val id =
                    picker.viewModel.pendingErrors.value
                        .first()
                        .id
                advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
                ShadowDialog.getLatestDialog().dismiss()
                advanceRobolectricLooperUntil {
                    picker.viewModel.pendingErrors.value
                        .firstOrNull()
                        ?.id != id
                }
            }
            assertEquals(DEFAULT_DECK_ID, col.decks.selected())
            block(picker, deleted) { failReload = false }
        }
    }
}
