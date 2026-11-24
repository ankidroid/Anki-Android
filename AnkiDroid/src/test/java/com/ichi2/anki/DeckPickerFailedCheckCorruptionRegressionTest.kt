// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerFailedCheckCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `failed database check retains corruption detected before it`() {
        var failRefresh = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }

                override fun checkDatabase(): List<String> = throw BackendIoException(backendError { message = "Cannot check database" })
            }
        }
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                picker.showDatabaseErrorDialog(DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                advanceRobolectricLooper()
                val confirmation =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                failRefresh = true
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                failRefresh = false
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                (confirmation.requireDialog() as AlertDialog).performPositiveClick()
                advanceRobolectricLooperUntil {
                    val dialog = ShadowDialog.getLatestDialog()
                    dialog is AlertDialog && dialog !== confirmation.dialog && dialog.isShowing
                }
                ShadowDialog.getLatestDialog().dismiss()
                advanceRobolectricLooper()
                assertTrue(
                    DatabaseCorruption.isDetected &&
                        picker.supportFragmentManager.fragments.any {
                            it is DatabaseErrorDialog && it.dialog?.isShowing == true
                        },
                    "A failed database check must still report previously detected corruption",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
