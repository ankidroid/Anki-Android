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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerOptionsMenuCheckSummaryTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `menu corruption waits for the check summary`() {
        val checkCompleted = AtomicBoolean(false)
        val blockRefresh = AtomicBoolean(true)
        val failMenuRead = AtomicBoolean(false)
        val releaseRefresh = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun checkDatabase(): List<String> = super.checkDatabase().also { checkCompleted.set(true) }

                override fun deckTree(now: Long): DeckTreeNode {
                    if (checkCompleted.get() && blockRefresh.getAndSet(false)) {
                        check(releaseRefresh.await(30, TimeUnit.SECONDS))
                    }
                    return super.deckTree(now)
                }

                override fun getDeckNames(
                    skipEmptyDefault: Boolean,
                    includeFiltered: Boolean,
                ) = if (
                    Thread.currentThread().stackTrace.any {
                        it.className.contains("refreshMenuState") ||
                            it.methodName.contains("refreshMenuState")
                    } &&
                    failMenuRead.getAndSet(false)
                ) {
                    throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                } else {
                    super.getDeckNames(skipEmptyDefault = skipEmptyDefault, includeFiltered = includeFiltered)
                }
            }
        }
        addBasicNote()
        val previousDetection = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil {
                    picker.viewModel.loadDeckCounts?.isCompleted == true &&
                        picker.createMenuJob?.isCompleted == true &&
                        picker.viewModel.optionsMenuState?.undoAvailable == true
                }
                throwOnShowError = false
                picker.handleDatabaseCheck()
                var summary: AlertDialog? = null
                advanceRobolectricLooperUntil {
                    summary =
                        picker.supportFragmentManager.fragments
                            .filterIsInstance<SimpleMessageDialog>()
                            .mapNotNull { it.dialog as? AlertDialog }
                            .firstOrNull { it.isShowing }
                    summary != null
                }

                fun recoveryIsShowing() =
                    picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                        it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                    }

                failMenuRead.set(true)
                releaseRefresh.countDown()
                advanceRobolectricLooperUntil {
                    recoveryIsShowing() ||
                        picker.viewModel.pendingErrors.value
                            .isNotEmpty()
                }
                assertTrue(requireNotNull(summary).isShowing, "The options menu refresh dismissed the unread database check summary")
                assertFalse(recoveryIsShowing(), "Recovery must wait for the database check summary")
                requireNotNull(summary).performPositiveClick()
                advanceRobolectricLooperUntil { recoveryIsShowing() }
            }
        } finally {
            releaseRefresh.countDown()
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
