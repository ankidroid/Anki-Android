// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateConfirmationSyncTest : RobolectricTest() {
    @Test
    fun `delete survives a sync of the note type`() {
        val noteType = col.notetypes.basicAndReversed
        col.setSchemaModified()
        col.db.execute("update col set ls = scm")
        assertFalse(col.schemaChanged())
        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        val editor = controller.get()
        advanceRobolectricLooper()
        assertTrue(shadowOf(editor).clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        val confirmation = assertIs<ConfirmationDialog>(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

        // Uploading this note type in a normal sync changes its USN without editing its contents.
        val before = col.getNotetype(noteType.id)
        val syncedUsn = before.usn + 1
        col.db.execute("update notetypes set usn = ? where id = ?", syncedUsn, noteType.id)
        assertEquals(before.toBuilder().setUsn(syncedUsn).build(), col.getNotetype(noteType.id))

        val dialog = assertIs<AlertDialog>(confirmation.requireDialog())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        val fullSync = assertIs<ConfirmationDialog>(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        assertNotSame(confirmation, fullSync)
        assertIs<AlertDialog>(fullSync.requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertEquals(listOf("Card 2"), editor.tempNoteType!!.notetype.templatesNames)
    }
}
