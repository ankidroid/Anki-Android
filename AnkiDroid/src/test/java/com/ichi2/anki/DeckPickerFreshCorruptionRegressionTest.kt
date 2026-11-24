// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerFreshCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `refresh queued during a database check reports corruption found afterward`() {
        val checkStarted = CountDownLatch(1)
        val finishCheck = CountDownLatch(1)
        val finishRefresh = CountDownLatch(1)
        val failRefresh = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun checkDatabase(): List<String> {
                    checkStarted.countDown()
                    check(finishCheck.await(30, TimeUnit.SECONDS))
                    return listOf("Database check complete")
                }

                override fun deckTree(now: Long): DeckTreeNode {
                    if (failRefresh.getAndSet(false)) {
                        check(finishRefresh.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                picker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                advanceRobolectricLooper()
                val confirmation = ShadowDialog.getLatestDialog() as AlertDialog
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    confirmation.performPositiveClick()
                    advanceRobolectricLooperUntil { checkStarted.count == 0L }
                    failRefresh.set(true)
                    val refresh = picker.viewModel.reloadDeckCounts()
                    finishCheck.countDown()
                    advanceRobolectricLooperUntil {
                        val dialog = ShadowDialog.getLatestDialog()
                        dialog is AlertDialog && dialog !== confirmation && dialog.isShowing
                    }
                    assertEquals("Database check complete", getAlertDialogText(checkDismissed = false))
                    finishRefresh.countDown()
                    advanceRobolectricLooperUntil { refresh.isCompleted }
                    (ShadowDialog.getLatestDialog() as AlertDialog).performPositiveClick()
                    advanceRobolectricLooper()
                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The refresh first accessed the database after the check and must report fresh corruption",
                    )
                } finally {
                    finishCheck.countDown()
                    finishRefresh.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
