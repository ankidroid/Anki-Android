// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.UndoStatus
import com.google.protobuf.ByteString
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
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
class DeckPickerUndoReadAfterCheckRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `an undo status read after a database check reports fresh corruption`() {
        val blockCurrentDeck = AtomicBoolean(false)
        val currentDeckRead = CountDownLatch(1)
        val finishCurrentDeckRead = CountDownLatch(1)
        val failUndoRead = AtomicBoolean(false)
        val undoReadFailed = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString {
                    if (blockCurrentDeck.getAndSet(false)) {
                        currentDeckRead.countDown()
                        check(finishCurrentDeckRead.await(30, TimeUnit.SECONDS))
                    }
                    return super.getDeckLegacy(did)
                }

                override fun checkDatabase(): List<String> {
                    failUndoRead.set(true)
                    return listOf("Database check complete")
                }

                override fun getUndoStatus(): UndoStatus {
                    if (failUndoRead.getAndSet(false)) {
                        undoReadFailed.countDown()
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.getUndoStatus()
                }
            }
        }
        addBasicNote()
        val previousDetection = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    blockCurrentDeck.set(true)
                    val refresh = picker.viewModel.reloadDeckCounts()
                    assertTrue(currentDeckRead.await(10, TimeUnit.SECONDS), "The refresh did not start reading the current deck")

                    picker.handleDatabaseCheck()
                    finishCurrentDeckRead.countDown()
                    assertTrue(undoReadFailed.await(10, TimeUnit.SECONDS), "The refresh did not read undo status after the check")
                    advanceRobolectricLooperUntil { refresh.isCompleted }
                    advanceRobolectricLooperUntil {
                        DatabaseCorruption.isDetected ||
                            picker.supportFragmentManager.fragments.filterIsInstance<SimpleMessageDialog>().any {
                                it.dialog?.isShowing ==
                                    true
                            }
                    }
                    if (!DatabaseCorruption.isDetected) {
                        val summary =
                            picker.supportFragmentManager.fragments
                                .filterIsInstance<SimpleMessageDialog>()
                                .single()
                        (summary.dialog as AlertDialog).performPositiveClick()
                        advanceRobolectricLooper()
                    }
                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The undo status read found fresh corruption after the successful check",
                    )
                } finally {
                    finishCurrentDeckRead.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
