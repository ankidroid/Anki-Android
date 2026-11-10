// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
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
class DeckPickerCheckFlattenErrorReviewTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `deck list corruption waits for the check summary`() {
        val afterCheck = AtomicBoolean(false)
        val failOnce = AtomicBoolean(true)
        val flattenRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun checkDatabase(): List<String> {
                    afterCheck.set(true)
                    return listOf("Repaired an invalid note")
                }

                override fun getDeckLegacy(did: Long): ByteString {
                    // The deck-list flow has its own read, separate from the main refresh.
                    val isFlattenRead = Thread.currentThread().stackTrace.any { it.className.contains("flowOfDeckList") }
                    if (afterCheck.get() && isFlattenRead && failOnce.getAndSet(false)) {
                        flattenRead.countDown()
                        check(releaseRead.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.getDeckLegacy(did)
                }
            }
        }
        val previous = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                throwOnShowError = false
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    picker.handleDatabaseCheck()
                    var summary: AlertDialog? = null
                    advanceRobolectricLooperUntil(lazyMessage = { "The post-check deck-list read did not start" }) {
                        summary =
                            picker.supportFragmentManager.fragments
                                .filterIsInstance<SimpleMessageDialog>()
                                .mapNotNull { it.dialog as? AlertDialog }
                                .firstOrNull { it.isShowing }
                        summary != null && flattenRead.count == 0L
                    }
                    releaseRead.countDown()
                    advanceRobolectricLooperUntil {
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        } ||
                            picker.viewModel.pendingErrors.value
                                .isNotEmpty()
                    }
                    assertTrue(requireNotNull(summary).isShowing, "The deck list error replaced the unacknowledged check summary")
                    requireNotNull(summary).performPositiveClick()
                    advanceRobolectricLooperUntil {
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        }
                    }
                } finally {
                    releaseRead.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            releaseRead.countDown()
            DatabaseCorruption.isDetected = previous
        }
    }
}
