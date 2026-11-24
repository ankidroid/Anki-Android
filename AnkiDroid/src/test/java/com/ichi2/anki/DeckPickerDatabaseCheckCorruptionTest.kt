// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerDatabaseCheckCorruptionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `corruption is reported over the check database prompt`() =
        checkFirstCorruption(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)

    @Test
    fun `corruption is reported over the restore backup prompt`() =
        checkFirstCorruption(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)

    private fun checkFirstCorruption(dialog: DatabaseErrorDialogType) {
        val failDeckTree = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failDeckTree.get()) throw BackendDbCorruptException(backendError {}) else super.deckTree(now)
            }
        }
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                throwOnShowError = false
                picker.showDatabaseErrorDialog(dialog)
                failDeckTree.set(true)
                val previous = picker.viewModel.loadDeckCounts
                picker.viewModel.updateDeckList()
                advanceRobolectricLooperUntil {
                    picker.viewModel.loadDeckCounts.let { it !== previous && it?.isCompleted == true }
                }
                advanceRobolectricLooper()
                assertTrue(DatabaseCorruption.isDetected, "The first corruption must be reported while $dialog is open")
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
