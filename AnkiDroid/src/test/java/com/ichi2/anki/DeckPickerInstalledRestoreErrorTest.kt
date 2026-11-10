// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerInstalledRestoreErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a failed but installed restore shows its decks`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val installed = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun importCollectionPackage(
                    colPath: String,
                    backupPath: String,
                    mediaFolder: String,
                    mediaDb: String,
                ) {
                    super.importCollectionPackage(colPath, backupPath, mediaFolder, mediaDb)
                    installed.set(true)
                    throw BackendIoException(backendError { message = "Could not sync the restored collection directory" })
                }
            }
        }
        addBasicNote()
        val directory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(directory.path, force = true, waitForCompletion = true))
        val localOnly = addDeck("Local copy only")
        withDeckPicker(deckCount = 0) { picker ->
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            throwOnShowError = false
            assertTrue(shadowOf(picker).clickMenuItem(R.id.action_restore_backup))
            advanceRobolectricLooper()
            assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
            val chooser = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
            assertTrue(chooser.listView.performItemClick(chooser.listView.getChildAt(0), 0, chooser.listView.adapter.getItemId(0)))
            advanceRobolectricLooperUntil {
                val dialog = ShadowDialog.getLatestDialog()
                installed.get() && dialog is AlertDialog && dialog !== chooser && dialog.isShowing
            }
            assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }

            assertNull(picker.viewModel.dueTree?.find(localOnly), "The deck list shows the replacement collection")
        }
    }
}
