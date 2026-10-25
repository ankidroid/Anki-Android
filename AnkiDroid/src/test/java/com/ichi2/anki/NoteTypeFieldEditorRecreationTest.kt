// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteTypeFieldEditorRecreationTest : RobolectricTest() {
    @Test
    fun `field deletion confirmation survives activity recreation and deletes correct field`() {
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        val initialFieldCount = notetype.fieldsNames.size
        val fieldToDelete = notetype.fieldsNames[1] // "Back" or second field

        val intent =
            Intent(targetContext, NoteTypeFieldEditor::class.java).apply {
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, notetype.name)
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, notetype.id)
            }

        val controller: ActivityController<NoteTypeFieldEditor> =
            startActivityControllerNormallyOpenCollectionWithIntent(NoteTypeFieldEditor::class.java, intent)
        val activity = controller.get()
        advanceRobolectricLooper()

        // Select second field (index 1) and invoke delete action
        activity.currentPos = 1
        activity.handleAction(NoteTypeFieldEditorContextMenuAction.Delete)
        advanceRobolectricLooper()

        val confirmationDialog = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        assertTrue(confirmationDialog.isShowing)

        // Recreate activity while confirmation dialog is open (e.g. night mode change)
        controller.recreate()
        advanceRobolectricLooper()

        val recreatedActivity = controller.get()
        assertNotSame(activity, recreatedActivity)
        assertEquals(1, recreatedActivity.currentPos)
        val restoredDialog = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        assertNotSame(confirmationDialog, restoredDialog)
        assertTrue(restoredDialog.isShowing)

        // Confirm deletion on the restored dialog
        restoredDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        // Verify dialog result and cleanup
        assertFalse(restoredDialog.isShowing)
        assertEquals(0, recreatedActivity.supportFragmentManager.backStackEntryCount)

        // Verify the correct field was deleted
        val updatedNotetype = getCurrentDatabaseNoteTypeCopy("Basic")
        assertEquals(initialFieldCount - 1, updatedNotetype.fieldsNames.size)
        assertFalse(updatedNotetype.fieldsNames.contains(fieldToDelete))
        // Verify field 0 ("Front") was NOT deleted
        assertTrue(updatedNotetype.fieldsNames.contains("Front"))
    }
}
