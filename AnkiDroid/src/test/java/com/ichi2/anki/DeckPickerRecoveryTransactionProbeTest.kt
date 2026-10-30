// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendFactory
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DeckPickerRecoveryTransactionProbeTest : RobolectricTest() {
    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `queued corruption opens recovery after a database confirmation is dismissed`() {
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        var corrupt = false
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount {
                    corrupt = true
                    throw corruption
                }

                override fun deckTree(now: Long): DeckTreeNode = if (corrupt) throw corruption else super.deckTree(now)
            }
        }
        addDeck("Target")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                picker.showDatabaseErrorDialog(DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                val confirmation =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                // A background refresh can discover corruption while the confirmation is open.
                corrupt = true
                picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                advanceRobolectricLooper()
                assertEquals(1, picker.viewModel.pendingErrors.value.size)

                confirmation.dismiss()
                advanceRobolectricLooperUntil {
                    picker.viewModel.pendingErrors.value
                        .isEmpty()
                }
                try {
                    assertEquals(
                        1,
                        picker.supportFragmentManager.fragments
                            .filterIsInstance<DatabaseErrorDialog>()
                            .count { it.dialog?.isShowing == true },
                        "The queued corruption must open recovery now, rather than becoming a background notification",
                    )
                } finally {
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .forEach { it.dismiss() }
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
