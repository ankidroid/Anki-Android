// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.positiveButton
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteTypeFieldEditorRecreationTest : RobolectricTest() {
    @Test
    fun `delete confirmation action survives activity recreation`() {
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        val initialFields = notetype.fieldsNames
        assertTrue(initialFields.size >= 2, "Basic note type must have at least 2 fields for this test")
        val targetField = initialFields[1]

        val intent =
            Intent(targetContext, NoteTypeFieldEditor::class.java).apply {
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, notetype.name)
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, notetype.id)
            }

        ActivityScenario.launch<NoteTypeFieldEditor>(intent).use { scenario ->
            advanceRobolectricLooper()

            scenario.onActivity { activity ->
                val listView = activity.findViewById<ListView>(R.id.fields)
                listView.performItemClick(listView.getChildAt(1), 1, listView.adapter.getItemId(1))
            }
            advanceRobolectricLooper()

            // NoteTypeFieldEditorContextMenu actions: Reposition(0), Sort(1), Rename(2), Delete(3), AddLanguageHint(4)
            val contextMenuDialog = ShadowDialog.getLatestDialog() as AlertDialog
            contextMenuDialog.listView.performItemClick(null, 3, 3L)
            advanceRobolectricLooper()

            scenario.onActivity { activity ->
                val dialog = activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? ConfirmationDialog
                assertNotNull(dialog, "ConfirmationDialog should be showing")
                assertTrue(dialog.requireDialog().isShowing, "ConfirmationDialog should be displayed")
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { restoredActivity ->
                val restoredDialog = restoredActivity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? ConfirmationDialog
                assertNotNull(restoredDialog, "Restored ConfirmationDialog should exist after recreation")
                assertTrue(restoredDialog.requireDialog().isShowing, "Restored ConfirmationDialog should be showing")

                restoredDialog.positiveButton.performClick()
            }
            advanceRobolectricLooper()

            scenario.onActivity { finalActivity ->
                val finalFields = getCurrentDatabaseNoteTypeCopy("Basic").fieldsNames
                assertFalse(finalFields.contains(targetField), "Target field '$targetField' should be deleted")
                assertTrue(finalFields.contains(initialFields[0]), "First field should remain")
                assertNull(
                    finalActivity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG),
                    "ConfirmationDialog should be dismissed",
                )
                assertEquals(
                    0,
                    finalActivity.supportFragmentManager.backStackEntryCount,
                    "Dialog back stack should be cleared after deleting the field",
                )
            }
        }
    }
}
