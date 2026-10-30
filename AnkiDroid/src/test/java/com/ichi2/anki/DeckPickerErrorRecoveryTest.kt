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
import com.ichi2.anki.widgets.DeckAdapter
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class DeckPickerErrorRecoveryTest : RobolectricTest() {
    private val failure = BackendIoException(backendError { message = "Backend operation failed" })

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `failed replacement selection reconciles the deck list and tablet selection`() {
        var failSelection = false
        useBackend {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges {
                    if (failSelection) {
                        failSelection = false
                        throw failure
                    }
                    return super.setCurrentDeck(did)
                }
            }
        }
        addNoteToDeck(1L)
        val deleted = addDeck("Deleted", setAsSelected = true)
        val survivor = addDeck("Survivor")
        addNoteToDeck(deleted)

        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(1L, deleted, survivor))
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.viewModel?.selectedDeckId == deleted
            }
            throwOnShowError = false

            // Removing the selected deck commits before selecting its replacement can fail.
            failSelection = true
            val deletion = picker.deleteDeck(deleted)
            advanceRobolectricLooperUntil {
                deletion.isCompleted && (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true
            }
            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            ShadowDialog.getLatestDialog().dismiss()

            // Shift+Delete acts on the backend's selected deck. A stale tablet pane can make
            // retrying deletion erase Default's cards while still displaying the deleted deck.
            // Permit either reconciliation of the committed deletion or an atomic rollback.
            val adapter = picker.deckPickerBinding.decks.adapter as DeckAdapter
            advanceRobolectricLooperUntil(
                lazyMessage = {
                    "After failed replacement selection, the deck list and tablet pane must match the collection: " +
                        "visible=${adapter.currentList.map { it.did }}, " +
                        "displayed=${picker.fragment?.viewModel?.selectedDeckId}, selected=${col.decks.selected()}"
                },
            ) {
                adapter.currentList.map { it.did } == col.decks.allNamesAndIds(skipEmptyDefault = true).map { it.id } &&
                    picker.fragment?.viewModel?.selectedDeckId == col.decks.selected()
            }
        }
    }

    @Test
    fun `queued error waits until database recovery is dismissed`() {
        var corrupt = true
        useBackend {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount {
                    if (corrupt) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
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
                    // ViewModel operations can finish while the screen is stopped.
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
                assertSame(
                    recoveryDialog,
                    ShadowDialog.getLatestDialog(),
                    "The next queued error must not cover database recovery before it is dismissed",
                )

                recovery.dismiss()
                advanceRobolectricLooperUntil {
                    val latest = ShadowDialog.getLatestDialog()
                    latest !== recoveryDialog && latest?.isShowing == true
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
