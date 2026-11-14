// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class DeckPickerRecoveryHandoffProbeTest : RobolectricTest() {
    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `a queued error rechecks recovery after waiting for STARTED`() {
        var corrupt = true
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount =
                    if (corrupt) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        throw BackendIoException(backendError { message = "operation failed" })
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
                val first = picker.deleteDeck(target)
                advanceRobolectricLooperUntil {
                    first.isCompleted &&
                        picker.supportFragmentManager.fragments
                            .filterIsInstance<DatabaseErrorDialog>()
                            .any { it.dialog?.isShowing == true }
                }
                val firstRecovery =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                corrupt = false
                val second = picker.deleteDeck(target)
                advanceRobolectricLooperUntil { second.isCompleted && picker.viewModel.loadDeckCounts?.isCompleted == true }
                advanceRobolectricLooper()

                val lifecycle = picker.lifecycle as LifecycleRegistry
                lifecycle.currentState = Lifecycle.State.CREATED
                firstRecovery.dismiss()
                advanceRobolectricLooper()

                // A later async check can open another recovery dialog while the queued
                // error is suspended waiting for the activity to start again.
                picker.showDatabaseErrorDialog(DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
                advanceRobolectricLooper()
                val secondRecovery =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                val secondRecoveryDialog = secondRecovery.requireDialog()
                lifecycle.currentState = Lifecycle.State.RESUMED
                advanceRobolectricLooper()
                try {
                    assertSame(
                        secondRecoveryDialog,
                        ShadowDialog.getLatestDialog(),
                        "The queued generic error must not cover the new recovery dialog after resuming",
                    )
                } finally {
                    (ShadowDialog.getLatestDialog() as? AlertDialog)?.dismiss()
                    secondRecovery.dismiss()
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
