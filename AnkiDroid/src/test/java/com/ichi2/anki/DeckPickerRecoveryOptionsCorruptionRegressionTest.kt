// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerRecoveryOptionsCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `corruption found in recovery options does not reopen the completed recovery`() {
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        var failRefresh = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw corruption

                override fun deckTree(now: Long): DeckTreeNode = if (failRefresh) throw corruption else super.deckTree(now)
            }
        }
        val target = addDeck("Target")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val recoveries = { picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>() }
                picker.deleteDeck(target)
                advanceRobolectricLooperUntil { recoveries().any { it.dialog?.isShowing == true } }
                (recoveries().single().requireDialog() as AlertDialog)
                    .getButton(DialogInterface.BUTTON_POSITIVE)
                    .performClick()
                advanceRobolectricLooperUntil {
                    recoveries().singleOrNull()?.requireDialogType() == DatabaseErrorDialogType.DIALOG_ERROR_HANDLING
                }

                failRefresh = true
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                advanceRobolectricLooper()
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                failRefresh = false
                // Repair and backup restoration use this handoff to close the entire recovery stack.
                picker.dismissAllDialogFragments()
                advanceRobolectricLooperUntil {
                    picker.viewModel.pendingErrors.value
                        .isEmpty()
                }
                advanceRobolectricLooper()
                try {
                    assertTrue(
                        recoveries().none { it.dialog?.isShowing == true },
                        "A refresh failure covered by recovery must not reopen it over the recovery handoff",
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
