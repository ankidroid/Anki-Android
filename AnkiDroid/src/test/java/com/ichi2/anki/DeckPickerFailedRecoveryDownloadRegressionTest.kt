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
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerFailedRecoveryDownloadRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a failed one-way recovery download keeps the original corruption diagnosis`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        val failRefresh = AtomicBoolean(false)
        val downloadAttempted = AtomicBoolean(false)
        val downloadError = "Could not download the recovery copy"
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
                    throw BackendIoException(backendError { message = downloadError })
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
                confirmation.performPositiveClick()
                advanceRobolectricLooperUntil {
                    val failure = ShadowDialog.getLatestDialog()
                    downloadAttempted.get() && failure is AlertDialog && failure !== confirmation && failure.isShowing
                }
                assertEquals(downloadError, getAlertDialogText(checkDismissed = false))
                assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
                advanceRobolectricLooper()
                assertTrue(
                    picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                        it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                    },
                    "The failed download left the original collection in place, so its corruption should still be shown",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previousDetection
        }
    }
}
