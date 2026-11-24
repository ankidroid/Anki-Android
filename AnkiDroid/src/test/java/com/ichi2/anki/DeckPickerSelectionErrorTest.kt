// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerSelectionErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Operation not permitted (os error 1)"
            },
        )

    @Test
    fun `failed selection of a new deck shows an error`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = throw failure
            }
        }

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showCreateDeckDialog()
            (ShadowDialog.getLatestDialog() as AlertDialog).apply {
                input = "Deck"
                performPositiveClick()
            }

            assertErrorShown()
        }
    }

    @Test
    fun `a corrupt selection opens recovery instead of the menu`() {
        var failSelection = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = if (failSelection) throw corruption() else super.setCurrentDeck(did)
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
                advanceRobolectricLooperUntil { picker.supportFragmentManager.fragments.any { it is DatabaseErrorDialog } }

                assertTrue(DatabaseCorruption.isDetected)
                assertFalse(picker.supportFragmentManager.fragments.any { it is DeckPickerContextMenu })
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    private fun corruption() = BackendDbCorruptException(backendError { message = "database disk image is malformed" })

    private fun assertErrorShown() {
        advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
    }
}
