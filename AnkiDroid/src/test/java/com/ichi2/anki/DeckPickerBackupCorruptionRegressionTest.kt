// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerBackupCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `cancelling backup browsing still reports newly discovered corruption`() = checkCorruption(hasBackup = true)

    @Test
    fun `leaving options after finding no backups still reports newly discovered corruption`() = checkCorruption(hasBackup = false)

    private fun checkCorruption(hasBackup: Boolean) {
        var failRefresh = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }
            }
        }
        addDeck("Target")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val backups = BackupManager.getBackupDirectory(CollectionHelper.getCurrentAnkiDroidDirectory(targetContext))
                if (hasBackup) File(backups, "backup-2026-09-29-12.00.00.colpkg").writeText("backup")
                assertTrue(shadowOf(picker).clickMenuItem(R.id.action_restore_backup))
                val confirmation = picker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DatabaseErrorDialog
                (confirmation.requireDialog() as AlertDialog).performPositiveClick()
                advanceRobolectricLooper()
                val backupDialog =
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                if (!hasBackup) (backupDialog.requireDialog() as AlertDialog).performPositiveClick()
                failRefresh = true
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                advanceRobolectricLooper()
                failRefresh = false
                if (hasBackup) {
                    (backupDialog.requireDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                } else {
                    val options = picker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DatabaseErrorDialog
                    (options.requireDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                    advanceRobolectricLooper()
                    // Recovery options return to the no-backups dialog; Back closes that last window.
                    picker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .forEach { it.dismiss() }
                }
                advanceRobolectricLooper()
                try {
                    assertTrue(DatabaseCorruption.isDetected, "Cancelling backup browsing must not discard a new corruption error")
                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.dialog?.isShowing == true
                        },
                        "A corruption error found outside recovery must still open recovery",
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
