// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerReplaceConfirmationCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `queued corruption lets the user confirm a replacement package`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRefresh = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        addBasicNote()
        val backups = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backups.path, force = true, waitForCompletion = true))
        val backup = backups.listFiles()!!.single { it.extension == "colpkg" }
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                var imported = false
                picker.importColpkgListener = ImportColpkgListener { imported = true }
                val fragment = ImportDialog.newInstance(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, backup.path)
                picker.showDialogFragment(fragment)
                val confirmation = fragment.requireDialog() as AlertDialog

                failRefresh.set(true)
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }

                assertSame(confirmation, ShadowDialog.getLatestDialog(), "Corruption covered the replacement confirmation")
                assertTrue(confirmation.isShowing)
                confirmation.performPositiveClick()
                advanceRobolectricLooperUntil(lazyMessage = { "The confirmed replacement did not finish" }) { imported }
                assertFalse(DatabaseCorruption.isDetected, "Corruption from the original collection must not surface after replacement")
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
