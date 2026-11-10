// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerMaintenanceDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.ShadowStatFs
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerLowSpaceWarningRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `queued corruption waits while the storage warning is visible`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount =
                    throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
            }
        }
        val target = addDeck("Target")
        addNoteToDeck(target)
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                throwOnShowError = false
                picker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                advanceRobolectricLooper()
                val confirmation = ShadowDialog.getLatestDialog() as AlertDialog
                val deletion = picker.deleteDeck(target)
                advanceRobolectricLooperUntil { deletion.isCompleted }
                assertEquals(1, picker.viewModel.pendingErrors.value.size)

                val directory = CollectionHelper.getCurrentAnkiDroidDirectory(picker)
                ShadowStatFs.registerStats(directory, 100, 0, 0)
                try {
                    assertTrue(CollectionIntegrityStorageCheck.createInstance(picker).shouldWarnOnIntegrityCheck())
                    confirmation.performPositiveClick()
                    advanceRobolectricLooper()
                    val warning = assertNotNull(picker.supportFragmentManager.findFragmentByTag(DeckPickerMaintenanceDialog.TAG))
                    assertTrue(warning is DeckPickerMaintenanceDialog)
                    assertSame(
                        warning.requireDialog(),
                        ShadowDialog.getLatestDialog(),
                        "The queued error covered the Continue Anyway prompt",
                    )
                } finally {
                    ShadowStatFs.markAsNonEmpty(directory)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
