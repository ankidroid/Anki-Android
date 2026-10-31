// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateDeleteConfirmationThreadTest : RobolectricTest() {
    @Test
    fun `delete does not read cards on the UI thread`() {
        val noteType = col.notetypes.basicAndReversed
        val note =
            col.newNote(noteType).apply {
                setField(0, "Front")
                setField(1, "Back")
            }
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        col.setSchemaModified()
        assertTrue(col.schemaChanged())
        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        val editor = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent).get()
        advanceRobolectricLooper()
        assertTrue(shadowOf(editor).clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        val confirmation = assertIs<ConfirmationDialog>(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

        val previousMod = col.mod
        val otherNote = col.newNote(col.notetypes.basic).apply { setField(0, "Another note") }
        col.addNote(otherNote, Consts.DEFAULT_DECK_ID)
        assertNotEquals(previousMod, col.mod)

        var checkedOnMainThread = false
        val observedNotetypes = spyk(col.notetypes)
        every { observedNotetypes.getCardIdsForNoteType(noteType.id, any()) } answers {
            checkedOnMainThread = checkedOnMainThread || Looper.myLooper() == Looper.getMainLooper()
            callOriginal()
        }
        val observedCollection = spyk(col)
        every { observedCollection.notetypes } returns observedNotetypes
        mockkObject(CollectionManager)
        try {
            every { CollectionManager.getColUnsafe() } returns observedCollection
            val dialog = assertIs<AlertDialog>(confirmation.requireDialog())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooperUntil { editor.tempNoteType!!.notetype.templatesNames == listOf("Card 2") }
        } finally {
            unmockkObject(CollectionManager)
        }

        assertEquals(listOf("Card 2"), editor.tempNoteType!!.notetype.templatesNames)
        assertFalse(checkedOnMainThread, "Confirming Delete must keep the collection-wide orphan check off the UI thread")
    }
}
