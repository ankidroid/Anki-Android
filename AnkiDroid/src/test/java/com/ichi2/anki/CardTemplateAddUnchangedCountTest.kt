// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateAddUnchangedCountTest : RobolectricTest() {
    @Test
    fun `add works after a new note that gets no card`() {
        val noteType = col.notetypes.basic
        col.setSchemaModified()
        assertTrue(col.schemaChanged())
        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        val editor = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent).get()
        advanceRobolectricLooper()
        assertTrue(shadowOf(editor).clickMenuItem(R.id.action_add))
        advanceRobolectricLooper()
        val confirmation = assertIs<ConfirmationDialog>(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        val warning = assertIs<AlertDialog>(confirmation.requireDialog())
        assertEquals(
            editor.resources.getQuantityString(CommonPlurals.card_template_editor_confirm_add, 0, 0),
            warning.findViewById<TextView>(android.R.id.message)?.text.toString(),
        )

        val originalMod = col.mod
        // This note has an empty Back, so it would not get the new reverse card.
        val note = col.newNote(noteType).apply { setField(0, "Front") }
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        assertNotEquals(originalMod, col.mod)
        assertEquals(1, note.cids().size)

        warning.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertEquals(2, editor.tempNoteType!!.templateCount, "Adding the reverse template still creates zero cards")
        assertTrue(shadowOf(editor).clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperUntil { editor.isFinishing }
        assertEquals(1, note.cids().size)
    }
}
