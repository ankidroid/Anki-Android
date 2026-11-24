// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DeckPickerStaleOpenAfterRestoreTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a failed open of the original collection does not show recovery after restoring a backup`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val oldOpenFailed = CountDownLatch(1)
        val failNextOpen = AtomicBoolean(false)
        val corrupt = OldCollectionCorruptException()
        backendOverride.replaceWith {
            object : Backend() {
                override fun openCollection(
                    collectionPath: String,
                    mediaFolderPath: String,
                    mediaDbPath: String,
                ) {
                    if (failNextOpen.getAndSet(false)) {
                        oldOpenFailed.countDown()
                        throw corrupt
                    }
                    super.openCollection(collectionPath, mediaFolderPath, mediaDbPath)
                }
            }
        }
        addBasicNote()
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val backup = backupDirectory.listFiles()!!.single { it.extension == "colpkg" }
        val previousDetection = DatabaseCorruption.isDetected
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                DatabaseCorruption.isDetected = false
                throwOnShowError = false
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    runBlocking { CollectionManager.ensureClosed() }
                    failNextOpen.set(true)
                    // withCol attaches the caller via Throwable.addSuppressed after leaving the
                    // collection queue. Keep that attachment paused until the backup is restored.
                    val refresh =
                        synchronized(corrupt) {
                            val oldRefresh = picker.viewModel.reloadDeckCounts()
                            assertTrue(oldOpenFailed.await(10, TimeUnit.SECONDS), "The old collection open did not fail")
                            var restored = false
                            picker.importColpkgListener = ImportColpkgListener { restored = true }
                            picker.restoreFromBackup(backup.path)
                            advanceRobolectricLooperUntil { restored }
                            oldRefresh
                        }
                    advanceRobolectricLooperUntil { refresh.isCompleted }

                    assertFalse(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The open failed on the old collection, before the restore succeeded",
                    )
                } finally {
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }

    private class OldCollectionCorruptException :
        SQLiteDatabaseCorruptException("old collection is corrupt"),
        CopyableThrowable<OldCollectionCorruptException> {
        override val cause: Throwable?
            get() = null

        override fun createCopy(): OldCollectionCorruptException? = null
    }
}
