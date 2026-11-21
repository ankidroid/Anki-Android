// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import anki.collection.UndoStatus
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerBusyRecoveryRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `corruption covered by recovery is discarded while an earlier alert blocks the queue`() {
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        var failRefresh = false
        val failMenuRead = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount =
                    throw BackendIoException(backendError { message = "Cannot delete the deck" })

                override fun deckTree(now: Long): DeckTreeNode = if (failRefresh) throw corruption else super.deckTree(now)

                override fun getUndoStatus(): UndoStatus = if (failMenuRead.getAndSet(false)) throw corruption else super.getUndoStatus()
            }
        }
        val target = addDeck("Target")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val deletion = picker.deleteDeck(target)
                advanceRobolectricLooperUntil {
                    deletion.isCompleted &&
                        picker.viewModel.loadDeckCounts?.isCompleted == true &&
                        ShadowDialog.getLatestDialog()?.isShowing == true
                }
                val earlierError = ShadowDialog.getLatestDialog() as AlertDialog
                val recoveries = { picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>() }
                // The menu refresh reports activity errors independently of the ViewModel queue.
                failMenuRead.set(true)
                picker.onCreateOptionsMenu(MenuBuilder(picker))
                advanceRobolectricLooperUntil { recoveries().any { it.dialog?.isShowing == true } }
                failRefresh = true
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                advanceRobolectricLooper()
                assertEquals(2, picker.viewModel.pendingErrors.value.size)
                failRefresh = false
                // Repair and restore close the recovery stack through this handoff.
                picker.dismissAllDialogFragments()
                advanceRobolectricLooperUntil { recoveries().isEmpty() }
                earlierError.dismiss()
                advanceRobolectricLooperUntil {
                    picker.viewModel.pendingErrors.value
                        .isEmpty()
                }
                advanceRobolectricLooper()
                try {
                    assertTrue(
                        recoveries().none { it.dialog?.isShowing == true },
                        "Corruption already covered by recovery must not reopen it after an earlier alert is dismissed",
                    )
                } finally {
                    recoveries().forEach { it.dismiss() }
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
