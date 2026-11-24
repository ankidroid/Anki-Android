// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import anki.collection.Progress
import anki.decks.DeckNameId
import anki.decks.SetDeckCollapsedRequest
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerCollapseDuringDownloadTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a collapse after the download reopened keeps its corruption`() {
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

                override fun setDeckCollapsed(
                    deckId: Long,
                    collapsed: Boolean,
                    scope: SetDeckCollapsedRequest.Scope,
                ): OpChanges {
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.setDeckCollapsed(deckId, collapsed, scope)
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
                    val collapse = picker.viewModel.toggleDeckExpand(1L)
                    advanceRobolectricLooperUntil { collapse.isCompleted }
                    finishPoll.countDown()
                    advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }

                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The collapse found corruption after the replacement collection reopened",
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

    @Test
    fun `a collapse behind the download keeps its corruption`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failCollapse = AtomicBoolean(false)
        val queueHeld = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {}

                override fun setDeckCollapsed(
                    deckId: Long,
                    collapsed: Boolean,
                    scope: SetDeckCollapsedRequest.Scope,
                ): OpChanges {
                    if (failCollapse.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.setDeckCollapsed(deckId, collapsed, scope)
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
                    val blocker =
                        CoroutineScope(Dispatchers.IO).launch {
                            CollectionManager.withCol {
                                queueHeld.countDown()
                                check(releaseQueue.await(30, TimeUnit.SECONDS))
                            }
                        }
                    assertTrue(queueHeld.await(10, TimeUnit.SECONDS), "The collection queue is held")
                    picker.handleNewSync(ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                    advanceRobolectricLooperUntil { CollectionManager.collectionLease != null }

                    failCollapse.set(true)
                    val collapse = picker.viewModel.toggleDeckExpand(1L)
                    advanceRobolectricLooper()
                    releaseQueue.countDown()
                    advanceRobolectricLooperUntil { collapse.isCompleted && blocker.isCompleted && Prefs.lastSyncTime != 0L }

                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The collapse waited for the download and read the replacement collection",
                    )
                } finally {
                    releaseQueue.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }

    @Test
    fun `deck list read behind a download keeps its corruption`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRead = AtomicBoolean(false)
        val queueHeld = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {}

                override fun getDeckLegacy(did: Long): ByteString {
                    val isDeckListRead = Thread.currentThread().stackTrace.any { it.className.contains("flowOfDeckList") }
                    if (isDeckListRead && failRead.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.getDeckLegacy(did)
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
                    val blocker =
                        CoroutineScope(Dispatchers.IO).launch {
                            CollectionManager.withCol {
                                queueHeld.countDown()
                                check(releaseQueue.await(30, TimeUnit.SECONDS))
                            }
                        }
                    assertTrue(queueHeld.await(10, TimeUnit.SECONDS), "The collection queue is held")
                    picker.handleNewSync(ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                    advanceRobolectricLooperUntil { CollectionManager.collectionLease != null }

                    failRead.set(true)
                    picker.viewModel.updateDeckFilter("filter")
                    advanceRobolectricLooper()
                    releaseQueue.countDown()
                    advanceRobolectricLooperUntil { blocker.isCompleted && Prefs.lastSyncTime != 0L }

                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The deck list read waited for the download and read the replacement collection",
                    )
                } finally {
                    releaseQueue.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }

    @Test
    fun `menu read behind a download keeps its corruption`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRead = AtomicBoolean(false)
        val queueHeld = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {}

                override fun getDeckNames(
                    skipEmptyDefault: Boolean,
                    includeFiltered: Boolean,
                ): List<DeckNameId> {
                    val isMenuRead = Thread.currentThread().stackTrace.any { it.className.contains("refreshMenuState") }
                    if (isMenuRead && failRead.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.getDeckNames(skipEmptyDefault, includeFiltered)
                }
            }
        }
        addBasicNote()
        val previousDetection = DatabaseCorruption.isDetected
        try {
            withDeckPicker(deckCount = 1) { picker ->
                advanceRobolectricLooperUntil {
                    picker.viewModel.loadDeckCounts?.isCompleted == true && picker.createMenuJob?.isCompleted == true
                }
                DatabaseCorruption.isDetected = false
                Prefs.hkey = "test sync key"
                Prefs.lastSyncTime = 0
                throwOnShowError = false
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    val blocker =
                        CoroutineScope(Dispatchers.IO).launch {
                            CollectionManager.withCol {
                                queueHeld.countDown()
                                check(releaseQueue.await(30, TimeUnit.SECONDS))
                            }
                        }
                    assertTrue(queueHeld.await(10, TimeUnit.SECONDS), "The collection queue is held")
                    picker.handleNewSync(ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                    advanceRobolectricLooperUntil { CollectionManager.collectionLease != null }

                    failRead.set(true)
                    val menuRefresh = picker.lifecycleScope.launch { picker.viewModel.refreshMenuState() }
                    advanceRobolectricLooper()
                    releaseQueue.countDown()
                    advanceRobolectricLooperUntil { blocker.isCompleted && menuRefresh.isCompleted && Prefs.lastSyncTime != 0L }

                    advanceRobolectricLooperUntil {
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        }
                    }
                } finally {
                    releaseQueue.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
