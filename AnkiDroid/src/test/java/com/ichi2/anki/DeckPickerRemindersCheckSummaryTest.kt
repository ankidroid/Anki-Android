// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.decks.DeckTreeNode
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerRemindersCheckSummaryTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `repairing the selected missing deck preserves the check summary while reminders are open`() {
        val holdRefresh = AtomicBoolean(false)
        val finishRefresh = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun checkDatabase(): List<String> = super.checkDatabase().also { holdRefresh.set(true) }

                override fun deckTree(now: Long): DeckTreeNode {
                    if (holdRefresh.getAndSet(false)) check(finishRefresh.await(30, TimeUnit.SECONDS))
                    return super.deckTree(now)
                }
            }
        }
        val moved = addBasicNote().firstCard()
        addBasicNote(front = "Another card")
        Prefs.newReviewRemindersEnabled = true
        withDeckPicker(deckCount = 1) { picker ->
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            picker.selectContextMenuOptionAndAwait(DeckPickerContextMenuOption.SCHEDULE_REMINDERS, 1L) {
                picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) is ScheduleRemindersFragment
            }

            val missingDeck = 4001L
            col.db.execute("update cards set did = ? where id = ?", missingDeck, moved.id)
            col.config.set("curDeck", missingDeck)
            assertEquals(1L, col.decks.current().id)

            val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
            try {
                picker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                advanceRobolectricLooper()
                assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                advanceRobolectricLooperUntil {
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<SimpleMessageDialog>()
                        .any { it.dialog?.isShowing == true }
                }

                finishRefresh.countDown()
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                assertEquals(missingDeck, col.decks.current().id, "Check Database restored the missing selected deck")
                assertTrue(
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<SimpleMessageDialog>()
                        .any { it.dialog?.isShowing == true },
                    "Refreshing the repaired selection dismissed the unread check summary",
                )
            } finally {
                finishRefresh.countDown()
                CollectionManager.setTestDispatcher(previousDispatcher)
            }
        }
    }
}
