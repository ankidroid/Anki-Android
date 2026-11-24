// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

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
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class DeckPickerFurtherReviewTest : RobolectricTest() {
    private val failure = BackendIoException(backendError { message = "Backend operation failed" })

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `error waiting for recovery is not shown while owner is stopped`() {
        var corrupt = true
        useBackend {
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
                val first = picker.deleteDeck(target)
                advanceRobolectricLooperUntil {
                    first.isCompleted &&
                        picker.supportFragmentManager.fragments
                            .filterIsInstance<DatabaseErrorDialog>()
                            .any { it.dialog?.isShowing == true }
                }
                val recovery =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                val recoveryDialog = recovery.requireDialog()
                corrupt = false
                val second = picker.deleteDeck(target)
                advanceRobolectricLooperUntil { second.isCompleted && picker.viewModel.loadDeckCounts?.isCompleted == true }
                advanceRobolectricLooper()
                assertSame(recoveryDialog, ShadowDialog.getLatestDialog())

                val lifecycle = picker.lifecycle as LifecycleRegistry
                lifecycle.currentState = Lifecycle.State.CREATED
                try {
                    recovery.dismiss()
                    advanceRobolectricLooper()
                    assertSame(
                        recoveryDialog,
                        ShadowDialog.getLatestDialog(),
                        "The next queued error must wait for STARTED after recovery finishes in the background",
                    )
                } finally {
                    lifecycle.currentState = Lifecycle.State.RESUMED
                }
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

    private fun useBackend(backend: () -> Backend) {
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride { backend() }
    }
}
