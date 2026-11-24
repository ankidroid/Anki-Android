// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import anki.sync.FullUploadOrDownloadRequest
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.libanki.CollectionFiles.FolderBasedCollection
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerDownloadStorageErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a failed download that changed the file checks it again`() {
        val directory = tempFolder.newFolder("collection")
        getPreferences().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, directory.absolutePath) }
        val colDb = FolderBasedCollection(directory).colDb
        val corrupt = AtomicBoolean(false)
        val downloadAttempted = AtomicBoolean(false)
        val downloadError = "Could not write the downloaded collection"
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (corrupt.get()) throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    return super.deckTree(now)
                }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    downloadAttempted.set(true)
                    colDb.setLastModified(colDb.lastModified() + 5_000)
                    throw BackendIoException(backendError { message = downloadError })
                }
            }
        }
        addBasicNote()
        val previousDetection = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                Prefs.hkey = "test sync key"
                Prefs.lastSyncTime = 0
                throwOnShowError = false
                picker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
                advanceRobolectricLooper()
                assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                advanceRobolectricLooper()
                val options = assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).listView
                val download = picker.getString(CommonString.backup_one_way_sync_from_server)
                val option = (0 until options.count).single { options.adapter.getItem(it) == download }
                assertTrue(options.performItemClick(options.getChildAt(option), option, options.adapter.getItemId(option)))
                advanceRobolectricLooper()
                val confirmation = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                corrupt.set(true)
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    confirmation.performPositiveClick()
                    advanceRobolectricLooperUntil { downloadAttempted.get() && Prefs.lastSyncTime != 0L }
                    assertEquals(downloadError, getAlertDialogText(checkDismissed = false))
                    assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                    advanceRobolectricLooperUntil {
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        }
                    }
                } finally {
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
