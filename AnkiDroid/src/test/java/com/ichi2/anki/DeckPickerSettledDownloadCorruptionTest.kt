// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import anki.sync.FullUploadOrDownloadRequest
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerSettledDownloadCorruptionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a successful one-way download does not show a late error from the old collection`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val corrupt = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        val failRefresh = AtomicBoolean(false)
        val pauseNextReport = AtomicBoolean(true)
        val downloaded = AtomicBoolean(false)
        val reportingOldError = CountDownLatch(1)
        val releaseOldError = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failRefresh.getAndSet(false)) throw corrupt
                    return super.deckTree(now)
                }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    downloaded.set(true)
                }
            }
        }
        // Hold the refresh after its backend read has thrown and released the collection queue.
        val pauseError =
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    if (t is BackendDbCorruptException && t.message == corrupt.message && pauseNextReport.getAndSet(false)) {
                        reportingOldError.countDown()
                        check(releaseOldError.await(30, TimeUnit.SECONDS))
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

                Timber.plant(pauseError)
                try {
                    failRefresh.set(true)
                    val refresh = picker.viewModel.reloadDeckCounts()
                    assertTrue(reportingOldError.await(10, TimeUnit.SECONDS), "The old refresh did not encounter corruption")

                    picker.handleNewSync(ConflictResolution.FULL_DOWNLOAD, syncMedia = false)
                    advanceRobolectricLooperUntil { downloaded.get() && Prefs.lastSyncTime != 0L }

                    releaseOldError.countDown()
                    advanceRobolectricLooperUntil { refresh.isCompleted }

                    assertFalse(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The download succeeded before the old refresh reported corruption",
                    )
                } finally {
                    releaseOldError.countDown()
                    Timber.uproot(pauseError)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
