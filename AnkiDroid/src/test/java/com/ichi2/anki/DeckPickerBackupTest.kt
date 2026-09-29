// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.ext.menu
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerBackupTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `create backup reports when collection is unchanged`() {
        withDeckPicker(deckCount = 0, withCards = true) { deckPicker ->
            assertTrue(
                col.createBackup(
                    BackupManager.getBackupDirectoryFromCollection(col.colDb),
                    force = true,
                    waitForCompletion = true,
                ),
            )

            deckPicker.createBackupFromMenu()

            assertEquals(TR.profilesBackupUnchanged(), ShadowToast.getTextOfLatestToast())
            assertEquals(1, BackupManager.getBackups(col.colDb).size)
        }
    }

    @Test
    fun `create backup reports success after writing backup`() {
        withDeckPicker(deckCount = 0, withCards = true) { deckPicker ->
            deckPicker.createBackupFromMenu()

            assertEquals(TR.profilesBackupCreated(), ShadowToast.getTextOfLatestToast())
            assertEquals(1, BackupManager.getBackups(col.colDb).size)
        }
    }

    private fun DeckPicker.createBackupFromMenu() {
        ShadowToast.reset()
        assertTrue(onOptionsItemSelected(menu().findItem(R.id.action_create_backup)))
        advanceRobolectricLooperUntil { ShadowToast.getTextOfLatestToast() != null }
    }
}
