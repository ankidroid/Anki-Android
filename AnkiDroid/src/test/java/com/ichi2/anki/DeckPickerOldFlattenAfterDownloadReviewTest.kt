// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.sync.FullUploadOrDownloadRequest
import com.google.protobuf.ByteString
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerOldFlattenAfterDownloadReviewTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `old deck list error is dropped after a download`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRead = AtomicBoolean(false)
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val downloaded = CountDownLatch(1)
        val queueDrained = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString {
                    val isFlattenRead = Thread.currentThread().stackTrace.any { it.className.contains("flowOfDeckList") }
                    if (isFlattenRead && failRead.getAndSet(false)) {
                        readStarted.countDown()
                        check(releaseRead.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.getDeckLegacy(did)
                }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    downloaded.countDown()
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
                    failRead.set(true)
                    picker.viewModel.updateDeckFilter("filter")
                    advanceRobolectricLooperUntil { readStarted.count == 0L }
                    picker.handleNewSync(ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                    advanceRobolectricLooperUntil { CollectionManager.collectionLease != null }
                    val sentinel = CoroutineScope(Dispatchers.IO).launch { CollectionManager.withCol { queueDrained.countDown() } }
                    releaseRead.countDown()
                    assertTrue(downloaded.await(20, TimeUnit.SECONDS), "The download did not reach the backend")
                    assertTrue(queueDrained.await(20, TimeUnit.SECONDS), "The replacement did not reopen")
                    advanceRobolectricLooperUntil { sentinel.isCompleted && Prefs.lastSyncTime != 0L }
                    assertFalse(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The failed deck-list read used the old collection before the successful download",
                    )
                } finally {
                    releaseRead.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
