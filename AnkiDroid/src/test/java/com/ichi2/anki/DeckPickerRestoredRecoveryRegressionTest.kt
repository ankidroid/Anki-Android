// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerRestoredRecoveryRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `returning from recreated recovery options keeps queued errors behind recovery`() {
        val failRefresh = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount =
                    throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })

                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh.getAndSet(false)) {
                        throw BackendIoException(backendError { message = "Cannot refresh the deck list" })
                    } else {
                        super.deckTree(now)
                    }
            }
        }
        val target = addDeck("Target")
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                scenario.onActivity { picker ->
                    picker.viewModel.awaitStartup()
                    advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                    picker.deleteDeck(target)
                    advanceRobolectricLooperUntil { recovery(picker, DatabaseErrorDialogType.DIALOG_LOAD_FAILED) != null }
                    (recovery(picker, DatabaseErrorDialogType.DIALOG_LOAD_FAILED)!!.requireDialog() as AlertDialog).performPositiveClick()
                    advanceRobolectricLooperUntil { recovery(picker, DatabaseErrorDialogType.DIALOG_ERROR_HANDLING) != null }
                }
                scenario.recreate()
                scenario.onActivity { picker ->
                    picker.viewModel.awaitStartup()
                    advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                    val options = recovery(picker, DatabaseErrorDialogType.DIALOG_ERROR_HANDLING)!!
                    failRefresh.set(true)
                    val refresh = picker.viewModel.reloadDeckCounts()
                    advanceRobolectricLooperUntil { refresh.isCompleted }
                    advanceRobolectricLooper()
                    (options.requireDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                    advanceRobolectricLooperUntil { recovery(picker, DatabaseErrorDialogType.DIALOG_LOAD_FAILED) != null }
                    advanceRobolectricLooper()
                    val restored = recovery(picker, DatabaseErrorDialogType.DIALOG_LOAD_FAILED)!!.requireDialog()
                    assertTrue(restored.isShowing)
                    assertSame(restored, ShadowDialog.getLatestDialog(), "A queued error must not cover the restored recovery dialog")
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    private fun recovery(
        picker: DeckPicker,
        type: DatabaseErrorDialogType,
    ) = picker.supportFragmentManager.fragments
        .filterIsInstance<DatabaseErrorDialog>()
        .singleOrNull { it.requireDialogType() == type }
}
