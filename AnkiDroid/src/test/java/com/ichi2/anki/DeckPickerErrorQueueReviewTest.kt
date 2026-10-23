// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerErrorQueueReviewTest : RobolectricTest() {
    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Review probe: backend write failed"
            },
        )

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `failed context selection does not open a context menu`() {
        var failSelection = false
        useBackend {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = if (failSelection) throw failure else super.setCurrentDeck(did)
            }
        }
        val deckId = addDeck("Target")
        withDeckPicker(deckCount = 0) { picker ->
            throwOnShowError = false
            val previousSelection = col.decks.selected()
            failSelection = true
            val request = picker.viewModel.requestContextMenu(deckId)
            advanceRobolectricLooperUntil { request.isCompleted }
            advanceRobolectricLooper()
            assertEquals(previousSelection, col.decks.selected())
            assertEquals(1, picker.viewModel.pendingErrors.value.size)
            assertFalse(
                picker.supportFragmentManager.fragments.any { it is DeckPickerContextMenu },
                "The context menu must not cover the selection error and use the previous deck's state",
            )
        }
    }

    @Test
    fun `failed context selection preserves corruption recovery`() {
        var failSelection = false
        useBackend {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges =
                    if (failSelection) {
                        throw BackendDbCorruptException(
                            backendError {
                                message = "database disk image is malformed"
                            },
                        )
                    } else {
                        super.setCurrentDeck(did)
                    }
            }
        }
        val deckId = addDeck("Target")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                throwOnShowError = false
                failSelection = true
                val request = picker.viewModel.requestContextMenu(deckId)
                advanceRobolectricLooperUntil { request.isCompleted }
                advanceRobolectricLooper()
                assertTrue(DatabaseCorruption.isDetected)
                assertTrue(
                    picker.supportFragmentManager.fragments.any { it is DatabaseErrorDialog },
                    "A failed selection must not replace the corruption recovery dialog with a context menu",
                )
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
