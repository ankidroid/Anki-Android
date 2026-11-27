// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertIs
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class DeckPickerRecoveryImportRefreshTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    @SuppressLint("UseKtx")
    fun `a corrupt refresh keeps the chosen backup confirmation`() {
        val failTree = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failTree.get()) throw BackendDbCorruptException(backendError {}) else super.deckTree(now)
            }
        }
        val previousCorruption = DatabaseCorruption.isDetected
        try {
            withDeckPicker(deckCount = 0) { picker ->
                throwOnShowError = false
                failTree.set(true)
                picker.viewModel.updateDeckList()
                advanceRobolectricLooperUntil {
                    picker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) is DatabaseErrorDialog
                }

                // The user selected another collection backup in the recovery file picker.
                val source = ImportResolution.Source(Uri.parse("content://test/backup.colpkg"), "backup.colpkg")
                picker.openImport(ImportResult.Success(ImportResolution.CollectionPackage(source), "/cache/backup.colpkg"))
                advanceRobolectricLooperUntil {
                    picker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) is ImportDialog
                }
                val confirmation = assertIs<ImportDialog>(picker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                val previousRefresh = picker.viewModel.loadDeckCounts
                picker.refreshState()
                advanceRobolectricLooperUntil {
                    picker.viewModel.loadDeckCounts.let { it !== previousRefresh && it?.isCompleted == true }
                }
                advanceRobolectricLooper()
                assertSame(
                    confirmation,
                    picker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG),
                    "The refresh should not replace the selected backup confirmation",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previousCorruption
        }
    }
}
