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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerCorruptionBurstRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `one recovery prompt handles three corruption failures queued while stopped`() {
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw corruption
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
                    repeat(3) {
                        val deletion = picker.deleteDeck(target)
                        advanceRobolectricLooperUntil { deletion.isCompleted }
                    }
                    assertEquals(3, picker.viewModel.pendingErrors.value.size)
                } finally {
                    lifecycle.currentState = Lifecycle.State.RESUMED
                }
                val recoveries = { picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>() }
                advanceRobolectricLooperUntil { recoveries().any { it.dialog?.isShowing == true } }
                advanceRobolectricLooper()
                recoveries().single().dismiss()
                advanceRobolectricLooperUntil {
                    picker.viewModel.pendingErrors.value
                        .isEmpty()
                }
                advanceRobolectricLooper()
                try {
                    assertTrue(
                        recoveries().none { it.dialog?.isShowing == true },
                        "Failures already covered by the dismissed recovery prompt must not reopen it",
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
