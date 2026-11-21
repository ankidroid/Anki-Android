// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerProcessRecoveryRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `restored recovery options consume corruption covered by the recovery action`() {
        val failRefresh = AtomicBoolean(false)
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode = if (failRefresh.getAndSet(false)) throw corruption else super.deckTree(now)
            }
        }
        addDeck("Target")
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val picker = controller.get()
            picker.viewModel.awaitStartup()
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            picker.showException(corruption)
            val loadFailed = recovery(picker, DatabaseErrorDialogType.DIALOG_LOAD_FAILED)!!
            (loadFailed.requireDialog() as AlertDialog).performPositiveClick()
            advanceRobolectricLooperUntil { recovery(picker, DatabaseErrorDialogType.DIALOG_ERROR_HANDLING) != null }
            val originalViewModel = picker.viewModel
            val savedState = Bundle()
            controller
                .pause()
                .stop()
                .saveInstanceState(savedState)
                .destroy()
            DatabaseCorruption.isDetected = false
            val restoredController =
                Robolectric
                    .buildActivity(DeckPicker::class.java, Intent())
                    .create(savedState)
                    .start()
                    .restoreInstanceState(savedState)
                    .postCreate(savedState)
                    .resume()
                    .visible()
            saveControllerForCleanup(restoredController)
            val restored = restoredController.get()
            assertNotSame(originalViewModel, restored.viewModel)
            restored.viewModel.awaitStartup()
            advanceRobolectricLooperUntil { restored.viewModel.loadDeckCounts?.isCompleted == true }
            assertTrue(recovery(restored, DatabaseErrorDialogType.DIALOG_ERROR_HANDLING)?.dialog?.isShowing == true)
            failRefresh.set(true)
            val refresh = restored.viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { refresh.isCompleted }
            assertEquals(1, restored.viewModel.pendingErrors.value.size)
            restored.dismissAllDialogFragments()
            advanceRobolectricLooperUntil {
                restored.viewModel.pendingErrors.value
                    .isEmpty()
            }
            advanceRobolectricLooper()
            assertTrue(
                restored.supportFragmentManager.fragments.none { it is DatabaseErrorDialog && it.dialog?.isShowing == true },
                "A corruption covered by restored recovery must not reopen recovery during its completion handoff",
            )
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    private fun recovery(
        picker: DeckPicker,
        type: DatabaseErrorDialogType,
    ) = picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().singleOrNull {
        BundleCompat.getParcelable(it.requireArguments(), "dialog", DatabaseErrorDialogType::class.java) == type
    }
}
