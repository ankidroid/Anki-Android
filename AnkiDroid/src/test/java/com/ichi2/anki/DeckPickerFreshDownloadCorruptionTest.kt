// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.Progress
import anki.decks.DeckTreeNode
import anki.sync.FullUploadOrDownloadRequest
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerFreshDownloadCorruptionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a successful one-way download preserves corruption found in the replacement collection`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRefresh = AtomicBoolean(false)
        val pollStarted = CountDownLatch(1)
        val finishPoll = CountDownLatch(1)
        val downloaded = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun latestProgress(): Progress {
                    pollStarted.countDown()
                    check(finishPoll.await(30, TimeUnit.SECONDS))
                    return Progress.getDefaultInstance()
                }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    check(pollStarted.await(30, TimeUnit.SECONDS))
                    downloaded.set(true)
                }

                override fun deckTree(now: Long): DeckTreeNode {
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        addBasicNote()
        val previousDetection = DatabaseCorruption.isDetected
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                DatabaseCorruption.isDetected = false
                Prefs.hkey = "test sync key"
                Prefs.lastSyncTime = 0
                throwOnShowError = false
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    picker.handleNewSync(ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                    advanceRobolectricLooperUntil { downloaded.get() && CollectionManager.collectionLease == null }
                    assertTrue(CollectionManager.isOpenUnsafe(), "The replacement collection has reopened")
                    assertTrue(Prefs.lastSyncTime == 0L, "The progress poll still holds the download wrapper open")

                    failRefresh.set(true)
                    val refresh = picker.viewModel.reloadDeckCounts()
                    advanceRobolectricLooperUntil { refresh.isCompleted }
                    finishPoll.countDown()
                    advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }

                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "Corruption was found after the replacement collection reopened",
                    )
                } finally {
                    finishPoll.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
