// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
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
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerRecoveryDownloadClockErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a recovery download shows its actionable clock error before queued corruption`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRefresh = AtomicBoolean(false)
        val downloadAttempted = AtomicBoolean(false)
        val downloadError = CollectionManager.TR.syncClockOff()
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    downloadAttempted.set(true)
                    throw BackendSyncException(backendError { message = downloadError })
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
                failRefresh.set(true)
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    confirmation.performPositiveClick()
                    advanceRobolectricLooperUntil { downloadAttempted.get() && Prefs.lastSyncTime != 0L }
                    val syncError =
                        picker.supportFragmentManager.fragments
                            .filterIsInstance<SyncErrorDialog>()
                            .mapNotNull { it.dialog as? AlertDialog }
                            .firstOrNull { it.isShowing }
                    assertEquals(
                        picker.getString(CommonString.open_settings),
                        syncError?.getButton(DialogInterface.BUTTON_POSITIVE)?.text,
                        "The clock error and its Settings action should be presented before the older corruption",
                    )
                    requireNotNull(syncError).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
                    advanceRobolectricLooper()
                    assertTrue(
                        picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The failed download left the original collection in place, so its corruption should still be shown",
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
