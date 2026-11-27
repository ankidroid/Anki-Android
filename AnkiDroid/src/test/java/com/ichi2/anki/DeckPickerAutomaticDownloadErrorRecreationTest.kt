// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.sync.FullUploadOrDownloadRequest
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.syncCollectionResponse
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.utils.positiveButton
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerAutomaticDownloadErrorRecreationTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `an automatic download error survives recreation until dismissed`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
            putBoolean("backupPromptDisabled", true)
        }
        val downloadError = "The connection was lost during download"
        backendOverride.replaceWith {
            object : Backend() {
                override fun syncCollection(
                    auth: SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse = syncCollectionResponse { required = SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest): Unit =
                    throw BackendNetworkException(backendError { message = downloadError })
            }
        }
        addBasicNote()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        throwOnShowError = false
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        advanceRobolectricLooper()
        Prefs.hkey = "test sync key"
        Prefs.lastSyncTime = 0

        controller.get().handleNewSync(conflict = null, syncMedia = false)
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }
        val original = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
        assertTrue(original.isShowing)
        assertEquals(downloadError, getAlertDialogText(checkDismissed = true))

        controller.recreate()
        advanceRobolectricLooper()

        val restored = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
        assertTrue(restored.isShowing, "The download error was lost when the Deck Picker was recreated")
        assertNotSame(original, restored)
        assertEquals(downloadError, getAlertDialogText(checkDismissed = true))

        clickAlertDialogButton { positiveButton }
        controller.recreate()
        advanceRobolectricLooper()

        assertFalse(ShadowDialog.getLatestDialog().isShowing, "The dismissed error returned after recreation")
    }
}
