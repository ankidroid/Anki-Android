// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import anki.decks.SetDeckCollapsedRequest
import anki.sync.FullUploadOrDownloadRequest
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
class DeckPickerOldCollapseAfterDownloadReviewTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `old collapse error is dropped after a download`() = checkOldOperationError(select = false)

    @Test
    fun `old selection error is dropped after a download`() = checkOldOperationError(select = true)

    private fun checkOldOperationError(select: Boolean) {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failOperation = AtomicBoolean(false)
        val operationStarted = CountDownLatch(1)
        val releaseOperation = CountDownLatch(1)
        val downloaded = CountDownLatch(1)
        val queueDrained = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                private fun failIfRequested() {
                    if (failOperation.getAndSet(false)) {
                        operationStarted.countDown()
                        check(releaseOperation.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                }

                override fun setCurrentDeck(did: Long): OpChanges {
                    if (select) failIfRequested()
                    return super.setCurrentDeck(did)
                }

                override fun setDeckCollapsed(
                    deckId: Long,
                    collapsed: Boolean,
                    scope: SetDeckCollapsedRequest.Scope,
                ): OpChanges {
                    if (!select) failIfRequested()
                    return super.setDeckCollapsed(deckId, collapsed, scope)
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
                    failOperation.set(true)
                    val operation = if (select) picker.viewModel.selectDeck(1L) else picker.viewModel.toggleDeckExpand(1L)
                    advanceRobolectricLooperUntil { operationStarted.count == 0L }
                    picker.handleNewSync(ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                    advanceRobolectricLooperUntil { CollectionManager.collectionLease != null }
                    val sentinel =
                        CoroutineScope(Dispatchers.IO).launch {
                            CollectionManager.withCol { queueDrained.countDown() }
                        }
                    releaseOperation.countDown()
                    assertTrue(downloaded.await(20, TimeUnit.SECONDS), "The download did not reach the backend")
                    assertTrue(queueDrained.await(20, TimeUnit.SECONDS), "The replacement did not reopen")
                    advanceRobolectricLooperUntil { operation.isCompleted && sentinel.isCompleted && Prefs.lastSyncTime != 0L }
                    assertFalse(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The failure came from the old collection before the successful download",
                    )
                } finally {
                    releaseOperation.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
