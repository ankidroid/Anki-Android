// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerLateCheckSummaryCorruptionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `fresh corruption waits for an already visible database check summary`() {
        val failPostCheckRefresh = AtomicBoolean(false)
        val refreshStarted = CountDownLatch(1)
        val finishRefresh = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failPostCheckRefresh.getAndSet(false)) {
                        refreshStarted.countDown()
                        check(finishRefresh.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }

                override fun checkDatabase(): List<String> {
                    failPostCheckRefresh.set(true)
                    return listOf("Repaired an invalid note")
                }
            }
        }
        val previous = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                throwOnShowError = false
                picker.handleDatabaseCheck()
                var result: AlertDialog? = null
                advanceRobolectricLooperUntil {
                    result =
                        picker.supportFragmentManager.fragments
                            .filterIsInstance<SimpleMessageDialog>()
                            .mapNotNull { it.dialog as? AlertDialog }
                            .firstOrNull { it.isShowing }
                    result != null && refreshStarted.count == 0L
                }
                val summary = requireNotNull(result)

                finishRefresh.countDown()
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                assertTrue(summary.isShowing, "Fresh corruption replaced the unacknowledged check result")

                summary.performPositiveClick()
                advanceRobolectricLooperUntil(lazyMessage = { "Fresh corruption was lost after the summary closed" }) {
                    picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                        it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                    }
                }
            }
        } finally {
            finishRefresh.countDown()
            DatabaseCorruption.isDetected = previous
        }
    }
}
