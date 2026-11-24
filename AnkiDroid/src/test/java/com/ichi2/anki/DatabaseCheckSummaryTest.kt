// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DatabaseCheckSummaryTest : RobolectricTest() {
    @Test
    fun `confirming a database check keeps its summary on screen`() =
        assertSummaryShownAfterStartingCheck {
            showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
        }

    @Test
    fun `checking the database from a sync check error keeps its summary on screen`() =
        assertSummaryShownAfterStartingCheck {
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_SYNC_BASIC_CHECK_ERROR, "")
        }

    @Test
    fun `checking the database from a collection too large to sync keeps its summary on screen`() =
        assertSummaryShownAfterStartingCheck {
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_COLLECTION_TOO_LARGE, "")
        }

    private fun assertSummaryShownAfterStartingCheck(showDialog: DeckPicker.() -> Unit) {
        withDeckPicker(deckCount = 1) { picker ->
            picker.showDialog()
            advanceRobolectricLooper()
            assertIs<AlertDialog>(ShadowDialog.getLatestDialog()).performPositiveClick()
            advanceRobolectricLooper()

            assertTrue(
                picker.supportFragmentManager.fragments
                    .filterIsInstance<SimpleMessageDialog>()
                    .any { it.dialog?.isShowing == true },
                "The database check summary was dismissed with the dialog that started the check",
            )
        }
    }
}
