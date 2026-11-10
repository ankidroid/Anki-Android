// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
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
class DeckPickerCanceledRefreshCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `a superseded refresh still reports corruption found by its running read`() {
        val failNextRead = AtomicBoolean(false)
        val failingReadStarted = CountDownLatch(1)
        val finishFailingRead = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failNextRead.getAndSet(false)) {
                        failingReadStarted.countDown()
                        check(finishFailingRead.await(30, TimeUnit.SECONDS))
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
                val dispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    failNextRead.set(true)
                    val superseded = picker.viewModel.reloadDeckCounts()
                    assertTrue(failingReadStarted.await(10, TimeUnit.SECONDS), "The first refresh never entered its backend read")
                    val latest = picker.viewModel.reloadDeckCounts()
                    assertTrue(superseded.isCancelled)
                    finishFailingRead.countDown()
                    advanceRobolectricLooperUntil { superseded.isCompleted && latest.isCompleted }

                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "Replacing a refresh must not suppress corruption from the native read already in progress",
                    )
                } finally {
                    finishFailingRead.countDown()
                    CollectionManager.setTestDispatcher(dispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
