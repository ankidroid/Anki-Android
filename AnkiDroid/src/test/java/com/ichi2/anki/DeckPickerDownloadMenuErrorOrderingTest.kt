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
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerDownloadMenuErrorOrderingTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `download error is shown before menu corruption`() {
        val directory = tempFolder.newFolder("collection")
        getPreferences().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, directory.absolutePath) }
        val files = FolderBasedCollection(directory)
        lateinit var replacement: File
        val blockRefresh = AtomicBoolean(false)
        val failMenuRead = AtomicBoolean(false)
        val releaseRefresh = CountDownLatch(1)
        val downloadError = "Could not sync the downloaded collection directory to storage"
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (blockRefresh.getAndSet(false)) check(releaseRefresh.await(30, TimeUnit.SECONDS))
                    return super.deckTree(now)
                }

                override fun getDeckNames(
                    skipEmptyDefault: Boolean,
                    includeFiltered: Boolean,
                ) = if (
                    Thread.currentThread().stackTrace.any {
                        it.className.contains("refreshMenuState") ||
                            it.methodName.contains("refreshMenuState")
                    } &&
                    failMenuRead.getAndSet(false)
                ) {
                    throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                } else {
                    super.getDeckNames(skipEmptyDefault = skipEmptyDefault, includeFiltered = includeFiltered)
                }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    // The native client can install the replacement before syncing the parent directory fails.
                    closeCollection(false)
                    importCollectionPackage(files.colDb.path, replacement.path, files.mediaFolder.path, files.mediaDb.path)
                    openCollection(files.colDb.path, files.mediaFolder.path, files.mediaDb.path)
                    blockRefresh.set(true)
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
                advanceRobolectricLooperUntil {
                    picker.viewModel.loadDeckCounts?.isCompleted == true &&
                        picker.createMenuJob?.isCompleted == true &&
                        picker.viewModel.optionsMenuState?.undoAvailable == true
                }
                Prefs.hkey = "test sync key"
                Prefs.lastSyncTime = 0
                throwOnShowError = false
                val oldRefresh = picker.viewModel.loadDeckCounts
                val oldMenu = picker.createMenuJob

                fun recoveryIsShowing() =
                    picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                        it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                    }

                picker.handleNewSync(conflict = ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                advanceRobolectricLooperUntil {
                    Prefs.lastSyncTime != 0L && (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true
                }
                assertEquals(downloadError, getAlertDialogText(checkDismissed = false))
                failMenuRead.set(true)
                releaseRefresh.countDown()
                val replacementRefresh = picker.viewModel.loadDeckCounts
                assertNotSame(oldRefresh, replacementRefresh, "The installed replacement must be refreshed")
                advanceRobolectricLooperUntil {
                    replacementRefresh?.isCompleted == true && picker.createMenuJob !== oldMenu && picker.createMenuJob?.isCompleted == true
                }

                assertNull(col.decks.byName(localOnly), "The replacement collection was installed")
                assertFalse(recoveryIsShowing(), "Menu corruption covered the unacknowledged download error")
                assertEquals(downloadError, getAlertDialogText(checkDismissed = false))
                assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                advanceRobolectricLooperUntil { recoveryIsShowing() }
            }
        } finally {
            releaseRefresh.countDown()
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
