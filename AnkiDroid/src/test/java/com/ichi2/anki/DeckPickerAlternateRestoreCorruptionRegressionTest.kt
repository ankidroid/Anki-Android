// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerAlternateRestoreCorruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `saving an alternate restore folder preserves fresh database recovery`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val importStarted = CountDownLatch(1)
        val finishImport = CountDownLatch(1)
        val refreshQueued = CountDownLatch(1)
        val trackRefreshDispatch = AtomicBoolean(false)
        val failNextOpen = AtomicBoolean(false)
        val firstOpenFailed = AtomicBoolean(false)
        val nextOpenStarted = CountDownLatch(1)
        val finishOpen = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun importCollectionPackage(
                    colPath: String,
                    backupPath: String,
                    mediaFolder: String,
                    mediaDb: String,
                ) {
                    importStarted.countDown()
                    check(finishImport.await(30, TimeUnit.SECONDS))
                    super.importCollectionPackage(colPath, backupPath, mediaFolder, mediaDb)
                    failNextOpen.set(true)
                }

                override fun openCollection(
                    collectionPath: String,
                    mediaFolderPath: String,
                    mediaDbPath: String,
                ) {
                    if (failNextOpen.getAndSet(false)) {
                        firstOpenFailed.set(true)
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    if (firstOpenFailed.get()) {
                        nextOpenStarted.countDown()
                        check(finishOpen.await(30, TimeUnit.SECONDS))
                    }
                    super.openCollection(collectionPath, mediaFolderPath, mediaDbPath)
                }
            }
        }
        addBasicNote()
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val backup = backupDirectory.listFiles()!!.single { it.extension == "colpkg" }
        val alternateDirectory = tempFolder.newFolder("alternate-collection")
        val previousDirectoryOverride = CollectionHelper.ankiDroidDirectoryOverride
        val previousDetection = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val serialQueue = Dispatchers.IO.limitedParallelism(1)
                val observedQueue =
                    object : CoroutineDispatcher() {
                        override fun dispatch(
                            context: CoroutineContext,
                            block: Runnable,
                        ) {
                            serialQueue.dispatch(context, block)
                            if (trackRefreshDispatch.getAndSet(false)) refreshQueued.countDown()
                        }
                    }
                val previousDispatcher = CollectionManager.setTestDispatcher(observedQueue, useReentrantLock = false)
                try {
                    CollectionHelper.ankiDroidDirectoryOverride = alternateDirectory
                    picker.importColpkgListener = DatabaseRestorationListener(picker, alternateDirectory)
                    picker.restoreFromBackup(backup.path)
                    assertTrue(importStarted.await(10, TimeUnit.SECONDS), "The restore did not start")
                    trackRefreshDispatch.set(true)
                    val refresh = picker.viewModel.reloadDeckCounts()
                    assertTrue(refreshQueued.await(10, TimeUnit.SECONDS), "The refresh did not queue behind the restore")
                    finishImport.countDown()
                    advanceRobolectricLooperUntil {
                        nextOpenStarted.count == 0L && picker.isRecoveryVisible()
                    }
                    assertTrue(firstOpenFailed.get(), "The first open of the replacement must encounter corruption")
                    finishOpen.countDown()
                    advanceRobolectricLooperUntil {
                        picker.importColpkgListener == null && refresh.isCompleted &&
                            picker.viewModel.loadDeckCounts?.isCompleted == true
                    }
                    assertEquals(alternateDirectory.absolutePath, getPreferences().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
                    assertTrue(picker.isRecoveryVisible(), "Saving the restore folder dismissed fresh database recovery")
                } finally {
                    finishImport.countDown()
                    finishOpen.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            CollectionHelper.ankiDroidDirectoryOverride = previousDirectoryOverride
            DatabaseCorruption.isDetected = previousDetection
        }
    }

    private fun DeckPicker.isRecoveryVisible(): Boolean =
        supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
        }
}
