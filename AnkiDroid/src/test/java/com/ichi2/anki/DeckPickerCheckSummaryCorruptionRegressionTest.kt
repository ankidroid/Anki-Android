// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.Progress
import anki.decks.DeckTreeNode
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
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class DeckPickerCheckSummaryCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `acknowledging a database check result preserves corruption found by its refresh`() {
        val pollStarted = CountDownLatch(1)
        val releasePoll = CountDownLatch(1)
        val corruptionRead = CountDownLatch(1)
        val failRefresh = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun latestProgress(): Progress {
                    pollStarted.countDown()
                    check(releasePoll.await(30, TimeUnit.SECONDS))
                    return Progress.getDefaultInstance()
                }

                override fun checkDatabase(): List<String> {
                    check(pollStarted.await(30, TimeUnit.SECONDS))
                    failRefresh.set(true)
                    return listOf("Database check complete")
                }

                override fun deckTree(now: Long): DeckTreeNode {
                    if (failRefresh.getAndSet(false)) {
                        corruptionRead.countDown()
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        val previousDetection = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    picker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                    advanceRobolectricLooper()
                    assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()

                    advanceRobolectricLooperUntil(lazyMessage = { "The post-check refresh did not read the collection" }) {
                        corruptionRead.count == 0L && picker.viewModel.loadDeckCounts?.isCompleted == true
                    }
                    releasePoll.countDown()

                    var summary: AlertDialog? = null
                    advanceRobolectricLooperUntil(lazyMessage = { "The database check summary did not appear" }) {
                        summary =
                            picker.supportFragmentManager.fragments
                                .filterIsInstance<SimpleMessageDialog>()
                                .mapNotNull { it.dialog as? AlertDialog }
                                .firstOrNull { it.isShowing }
                        summary != null
                    }
                    requireNotNull(summary).performPositiveClick()

                    advanceRobolectricLooperUntil(lazyMessage = { "The successful check summary hid fresh corruption" }) {
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        }
                    }
                } finally {
                    releasePoll.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
