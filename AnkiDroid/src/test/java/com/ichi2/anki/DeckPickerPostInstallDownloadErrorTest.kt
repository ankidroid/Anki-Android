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
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerPostInstallDownloadErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `an error after installing a download does not show the old collection corruption`() {
        val directory = tempFolder.newFolder("collection")
        getPreferences().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, directory.absolutePath) }
        val files = FolderBasedCollection(directory)
        lateinit var replacement: File
        val failRefresh = AtomicBoolean(false)
        val downloadAttempted = AtomicBoolean(false)
        val downloadError = "Could not sync the downloaded collection directory to storage"
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    // The native client can install the replacement before syncing the parent directory fails.
                    closeCollection(false)
                    importCollectionPackage(files.colDb.path, replacement.path, files.mediaFolder.path, files.mediaDb.path)
                    openCollection(files.colDb.path, files.mediaFolder.path, files.mediaDb.path)
                    downloadAttempted.set(true)
                    throw BackendIoException(backendError { message = downloadError })
                }
            }
        }
        addBasicNote()
        val backupDirectory = BackupManager.getBackupDirectory(directory)
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        replacement =
            backupDirectory.listFiles()!!.single { it.extension == "colpkg" }.copyTo(
                tempFolder.newFile("replacement.colpkg"),
                overwrite = true,
            )
        val localOnly = "Local copy only"
        addNoteToDeck(addDeck(localOnly))
        val previousDetection = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 2) { picker ->
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
                failRefresh.set(true)
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    confirmation.performPositiveClick()
                    advanceRobolectricLooperUntil { downloadAttempted.get() && Prefs.lastSyncTime != 0L }
                    assertNull(col.decks.byName(localOnly), "The replacement collection was installed")
                    assertEquals(downloadError, getAlertDialogText(checkDismissed = false))
                    assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                    advanceRobolectricLooper()
                    assertFalse(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The late transfer error did not undo the installed replacement",
                    )
                } finally {
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
