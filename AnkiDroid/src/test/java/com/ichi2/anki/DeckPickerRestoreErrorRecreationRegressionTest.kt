// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerRestoreErrorRecreationRegressionTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `failed restore remains visible when Deck Picker is recreated`() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
        addBasicNote()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        // A valid ZIP without a collection produces the localized invalid-backup error.
        val backup = tempFolder.newFile("invalid.colpkg")
        ZipOutputStream(backup.outputStream()).use { }
        val expectedError = TR.importingTheProvidedFileIsNotA()

        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { picker ->
                picker.viewModel.awaitStartup()
                picker.restoreFromBackup(backup.path)
                advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
                assertEquals(expectedError, getAlertDialogText(checkDismissed = false))
            }

            scenario.recreate()

            scenario.onActivity { picker ->
                picker.viewModel.awaitStartup()
                advanceRobolectricLooper()
                val restored = ShadowDialog.getLatestDialog() as? AlertDialog
                assertTrue(
                    restored?.isShowing == true && restored.context.findAnkiActivity() === picker,
                    "The new Deck Picker must still show the unacknowledged restore error",
                )
                assertEquals(expectedError, getAlertDialogText(checkDismissed = false))
            }
        }
    }
}
