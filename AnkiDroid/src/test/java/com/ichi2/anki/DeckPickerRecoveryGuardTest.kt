// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerRecoveryGuardTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private val failDeckTree = AtomicBoolean(false)
    private var previouslyDetected = false

    @Before
    fun failDeckTreeOnRequest() {
        previouslyDetected = DatabaseCorruption.isDetected
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failDeckTree.get()) throw BackendDbCorruptException(backendError {}) else super.deckTree(now)
            }
        }
    }

    @After
    fun restoreCorruptionFlag() {
        DatabaseCorruption.isDetected = previouslyDetected
    }

    @Test
    fun `check database prompt gives way to corruption`() {
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
            advanceRobolectricLooper()

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, deckPicker.shownDatabaseErrorDialog)
        }
    }

    @Test
    fun `restore backup prompt gives way to corruption`() {
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
            advanceRobolectricLooper()

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, deckPicker.shownDatabaseErrorDialog)
        }
    }

    @Test
    fun `backup list from the menu gives way to corruption`() {
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
            advanceRobolectricLooper()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooperUntil { deckPicker.shownDatabaseErrorDialog == DatabaseErrorDialogType.DIALOG_RESTORE_BACKUP }

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, deckPicker.shownDatabaseErrorDialog)
        }
    }

    @Test
    fun `backup list in recovery stays open`() {
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_ERROR_HANDLING)
            advanceRobolectricLooper()
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_RESTORE_BACKUP)
            advanceRobolectricLooper()

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_RESTORE_BACKUP, deckPicker.shownDatabaseErrorDialog)
        }
    }

    @Test
    fun `import prompt gives way to corruption`() {
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "/cache/backup.colpkg")
            advanceRobolectricLooper()

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, deckPicker.shownDatabaseErrorDialog)
        }
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `import prompt gives way with the reminders pane open`() {
        Prefs.newReviewRemindersEnabled = true
        withDeckPicker(deckCount = 0, withCards = true) { deckPicker ->
            throwOnShowError = false
            deckPicker.selectContextMenuOptionAndAwait(DeckPickerContextMenuOption.SCHEDULE_REMINDERS, col.decks.selected()) {
                deckPicker.supportFragmentManager.backStackEntryCount == 1
            }
            deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "/cache/backup.colpkg")
            advanceRobolectricLooper()

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, deckPicker.shownDatabaseErrorDialog)
        }
    }

    @Test
    fun `repair prompt stays open on a corrupt refresh`() {
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_REPAIR_COLLECTION)
            advanceRobolectricLooper()

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_REPAIR_COLLECTION, deckPicker.shownDatabaseErrorDialog)
        }
    }

    @Test
    fun `corruption is recorded while recovery is open`() {
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_ERROR_HANDLING)
            advanceRobolectricLooper()
            DatabaseCorruption.isDetected = false

            deckPicker.refreshWithCorruption()

            assertEquals(DatabaseErrorDialogType.DIALOG_ERROR_HANDLING, deckPicker.shownDatabaseErrorDialog)
            assertTrue(DatabaseCorruption.isDetected)
        }
    }

    @Test
    fun `recovery survives recreation and a corrupt refresh`() {
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        val controller =
            Robolectric
                .buildActivity(DeckPicker::class.java, Intent())
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        advanceRobolectricLooper()
        controller.get().viewModel.awaitStartup()
        throwOnShowError = false

        controller.get().refreshWithCorruption()
        advanceRobolectricLooperUntil { controller.get().shownDatabaseErrorDialog == DatabaseErrorDialogType.DIALOG_LOAD_FAILED }
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooperUntil { controller.get().shownDatabaseErrorDialog == DatabaseErrorDialogType.DIALOG_ERROR_HANDLING }

        controller.recreate()
        advanceRobolectricLooper()
        advanceRobolectricLooperUntil { controller.get().shownDatabaseErrorDialog != null }

        controller.get().refreshWithCorruption()

        assertEquals(DatabaseErrorDialogType.DIALOG_ERROR_HANDLING, controller.get().shownDatabaseErrorDialog)
    }

    private fun DeckPicker.refreshWithCorruption() {
        failDeckTree.set(true)
        val previous = viewModel.loadDeckCounts
        viewModel.updateDeckList()
        advanceRobolectricLooperUntil { viewModel.loadDeckCounts.let { it !== previous && it?.isCompleted == true } }
        advanceRobolectricLooper()
    }

    private val DeckPicker.shownDatabaseErrorDialog: DatabaseErrorDialogType?
        get() = (supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? DatabaseErrorDialog)?.requireDialogType()
}
