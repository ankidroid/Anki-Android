// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
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

    @Test
    fun `a created deck remains visible when selecting it fails`() {
        var failSelection = false
        useBackend {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = if (failSelection) throw failure else super.setCurrentDeck(did)
            }
        }
        val existing = addDeck("Existing")
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(existing))
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            throwOnShowError = false
            failSelection = true
            picker.showCreateDeckDialog()
            (ShadowDialog.getLatestDialog() as AlertDialog).apply {
                input = "New deck"
                performPositiveClick()
            }
            advanceRobolectricLooperUntil {
                picker.viewModel.pendingErrors.value
                    .isNotEmpty()
            }
            ShadowDialog.getLatestDialog().dismiss()
            advanceRobolectricLooperUntil {
                picker.viewModel.pendingErrors.value
                    .isEmpty()
            }
            val created = col.decks.byName("New deck")!!.id
            picker.awaitVisibleDecks(listOf(existing, created))
        }
    }

    private fun useBackend(backend: () -> Backend) {
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride { backend() }
    }
}
