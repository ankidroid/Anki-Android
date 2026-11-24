// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerPostInstallRestoreReviewTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `an installed restore drops old corruption`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRefresh = AtomicBoolean(false)
        val installed = AtomicBoolean(false)
        val message = "Could not sync the restored collection directory to storage"
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { this.message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }

                override fun importCollectionPackage(
                    colPath: String,
                    backupPath: String,
                    mediaFolder: String,
                    mediaDb: String,
                ) {
                    super.importCollectionPackage(colPath, backupPath, mediaFolder, mediaDb)
                    installed.set(true)
                    throw BackendIoException(backendError { this.message = message })
                }
            }
        }
        addBasicNote()
        val directory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(directory.path, force = true, waitForCompletion = true))
        val localOnly = "Local copy only"
        addNoteToDeck(addDeck(localOnly))
        val previous = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                assertTrue(shadowOf(picker).clickMenuItem(R.id.action_restore_backup))
                advanceRobolectricLooper()
                assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                val chooser = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                failRefresh.set(true)
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                assertEquals(1, chooser.listView.count)
                assertTrue(chooser.listView.performItemClick(chooser.listView.getChildAt(0), 0, chooser.listView.adapter.getItemId(0)))
                advanceRobolectricLooperUntil {
                    val dialog = ShadowDialog.getLatestDialog()
                    installed.get() && dialog is AlertDialog && dialog !== chooser && dialog.isShowing
                }
                assertNull(col.decks.byName(localOnly), "The replacement collection was installed")
                assertEquals(message, getAlertDialogText(checkDismissed = false))
                assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                advanceRobolectricLooper()
                assertFalse(
                    picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                        it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                    },
                    "The late restore error did not undo the installed replacement",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previous
        }
    }
}
