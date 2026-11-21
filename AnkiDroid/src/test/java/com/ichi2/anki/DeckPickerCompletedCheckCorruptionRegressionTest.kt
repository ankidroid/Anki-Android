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
class DeckPickerCompletedCheckCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `successful database check does not report an old in-flight refresh failure`() {
        val failNext = AtomicBoolean(false)
        val checked = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failNext.compareAndSet(true, false)) {
                        entered.countDown()
                        check(release.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }

                override fun checkDatabase(): List<String> {
                    checked.set(true)
                    return listOf("Database check complete")
                }
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
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    failNext.set(true)
                    val refresh = picker.viewModel.reloadDeckCounts()
                    assertTrue(entered.await(10, TimeUnit.SECONDS), "Old refresh did not enter the backend")
                    (confirmation.requireDialog() as AlertDialog).performPositiveClick()
                    release.countDown()
                    advanceRobolectricLooperUntil { checked.get() && refresh.isCompleted }
                    advanceRobolectricLooper()
                    assertTrue(
                        !DatabaseCorruption.isDetected &&
                            picker.supportFragmentManager.fragments.none {
                                it is DatabaseErrorDialog && it.dialog?.isShowing == true
                            },
                        "An error from before the completed database check must not open recovery",
                    )
                } finally {
                    release.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            release.countDown()
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
