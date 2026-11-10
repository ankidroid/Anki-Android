// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class DeckPickerQueuedRecoveryRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `queued operation error waits for database recovery to close`() {
        var corrupt = true
        val failure = BackendIoException(backendError { message = "Cannot delete the deck" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount =
                    if (corrupt) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        throw failure
                    }
            }
        }
        val target = addDeck("Target")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val lifecycle = picker.lifecycle as LifecycleRegistry
                lifecycle.currentState = Lifecycle.State.CREATED
                try {
                    val first = picker.deleteDeck(target)
                    advanceRobolectricLooperUntil { first.isCompleted }
                    corrupt = false
                    val second = picker.deleteDeck(target)
                    advanceRobolectricLooperUntil { second.isCompleted }
                    assertEquals(2, picker.viewModel.pendingErrors.value.size)
                } finally {
                    lifecycle.currentState = Lifecycle.State.RESUMED
                }
                advanceRobolectricLooperUntil {
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .any { it.dialog?.isShowing == true }
                }
                advanceRobolectricLooper()
                val recovery =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                val recoveryDialog = recovery.requireDialog()
                assertSame(recoveryDialog, ShadowDialog.getLatestDialog(), "An ordinary error must not cover database recovery")
                recovery.dismiss()
                advanceRobolectricLooperUntil {
                    ShadowDialog.getLatestDialog().let { it !== recoveryDialog && it?.isShowing == true }
                }
                assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
                ShadowDialog.getLatestDialog().dismiss()
                advanceRobolectricLooperUntil {
                    picker.viewModel.pendingErrors.value
                        .isEmpty()
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
