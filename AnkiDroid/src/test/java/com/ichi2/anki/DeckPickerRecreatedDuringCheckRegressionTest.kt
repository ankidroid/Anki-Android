// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerRecreatedDuringCheckRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `recreating during a successful database check does not reopen old corruption`() {
        val checkStarted = CountDownLatch(1)
        val finishCheck = CountDownLatch(1)
        val checkCompleted = AtomicBoolean(false)
        val failRefresh = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun checkDatabase(): List<String> {
                    checkStarted.countDown()
                    check(finishCheck.await(30, TimeUnit.SECONDS))
                    checkCompleted.set(true)
                    return emptyList()
                }

                override fun deckTree(now: Long): DeckTreeNode {
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        addNoteToDeck(addDeck("History"))
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        val previouslyDetected = DatabaseCorruption.isDetected
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
                        val confirmation = ShadowDialog.getLatestDialog() as AlertDialog
                        failRefresh.set(true)
                        val refresh = picker.viewModel.reloadDeckCounts()
                        advanceRobolectricLooperUntil { refresh.isCompleted }
                        assertEquals(1, picker.viewModel.pendingErrors.value.size)
                        confirmation.performPositiveClick()
                        advanceRobolectricLooperUntil { checkStarted.count == 0L }
                    }
                    scenario.recreate()
                    finishCheck.countDown()
                    scenario.onActivity { picker ->
                        picker.viewModel.awaitStartup()
                        advanceRobolectricLooperUntil {
                            checkCompleted.get() && !picker.viewModel.recoveringCollection.value &&
                                picker.viewModel.loadDeckCounts?.isCompleted == true
                        }
                        assertTrue(
                            picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().none {
                                it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                            },
                            "A successful check must clear the old diagnosis after the Deck Picker is recreated",
                        )
                    }
                } finally {
                    finishCheck.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
