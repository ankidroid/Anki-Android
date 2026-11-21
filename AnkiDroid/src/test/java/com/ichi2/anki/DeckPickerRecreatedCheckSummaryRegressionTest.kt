// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.Progress
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.BackupManagerTestUtilities
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class DeckPickerRecreatedCheckSummaryRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `recreating a database check summary keeps queued recovery behind it`() {
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
        addBasicNote()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        val previousDetection = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    scenario.onActivity { picker ->
                        picker.viewModel.awaitStartup()
                        advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                        picker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                        advanceRobolectricLooper()
                        assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()

                        advanceRobolectricLooperUntil {
                            corruptionRead.count == 0L &&
                                picker.viewModel.pendingErrors.value
                                    .isNotEmpty()
                        }
                        releasePoll.countDown()
                        advanceRobolectricLooperUntil { picker.visibleCheckSummary() != null }
                        assertFalse(DatabaseCorruption.isDetected, "Recovery should be waiting behind the check summary")
                    }

                    scenario.recreate()

                    scenario.onActivity { picker ->
                        picker.viewModel.awaitStartup()
                        advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                        advanceRobolectricLooper()
                        assertFalse(DatabaseCorruption.isDetected, "Recovery overtook the unacknowledged check summary after recreation")
                        val summary = assertNotNull(picker.visibleCheckSummary(), "The check summary should still be visible")
                        summary.performPositiveClick()
                        advanceRobolectricLooperUntil { DatabaseCorruption.isDetected }
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

    private fun DeckPicker.visibleCheckSummary(): AlertDialog? =
        supportFragmentManager.fragments
            .filterIsInstance<SimpleMessageDialog>()
            .mapNotNull { it.dialog as? AlertDialog }
            .firstOrNull { it.isShowing }
}
