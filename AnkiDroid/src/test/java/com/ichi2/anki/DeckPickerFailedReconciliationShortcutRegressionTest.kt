// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import anki.decks.DeckTreeNode
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerFailedReconciliationShortcutRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `Shift Delete after failed reconciliation does not delete the replacement deck`() {
        val failMerge = AtomicBoolean(false)
        val failTree = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun mergeUndoEntries(`val`: Int): OpChanges {
                    if (failMerge.getAndSet(false)) {
                        failTree.set(true)
                        throw BackendIoException(backendError { message = "Cannot finish deletion" })
                    }
                    return super.mergeUndoEntries(`val`)
                }

                override fun deckTree(now: Long): DeckTreeNode =
                    if (failTree.get()) {
                        throw BackendIoException(backendError { message = "Cannot refresh deck list" })
                    } else {
                        super.deckTree(now)
                    }
            }
        }
        addNoteToDeck(1L)
        val deleted = addDeck("Deleted", setAsSelected = true)
        val survivor = addDeck("Survivor")
        addNoteToDeck(deleted)
        addNoteToDeck(survivor)
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(1L, deleted, survivor))
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == deleted
            }
            throwOnShowError = false
            failMerge.set(true)
            val deletion = picker.deleteDeck(deleted)
            advanceRobolectricLooperUntil {
                deletion.isCompleted && picker.viewModel.loadDeckCounts?.isCompleted == true &&
                    ShadowDialog.getLatestDialog()?.isShowing == true
            }
            assertNull(col.decks.getLegacy(deleted), "The first deletion committed")
            assertEquals(survivor, col.decks.selected(), "The backend selected the replacement")
            repeat(3) {
                advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
                ShadowDialog.getLatestDialog().dismiss()
                advanceRobolectricLooper()
            }
            advanceRobolectricLooperUntil {
                picker.viewModel.pendingErrors.value
                    .isEmpty() && picker.viewModel.loadDeckCounts?.isCompleted == true
            }
            assertEquals(deleted, picker.fragment?.shownDeckId, "The side panel still shows the deleted deck")
            failTree.set(false)
            picker.onKeyUp(
                KeyEvent.KEYCODE_DEL,
                KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_SHIFT_ON),
            )
            advanceRobolectricLooperUntil { picker.viewModel.progressManager.progress.value is ViewModelProgress.Idle }
            assertNotNull(col.decks.getLegacy(survivor), "Shift+Delete must not delete the unseen replacement deck")
        }
    }
}
