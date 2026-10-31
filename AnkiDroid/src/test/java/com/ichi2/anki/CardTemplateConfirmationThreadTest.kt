// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
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
class CardTemplateConfirmationThreadTest : RobolectricTest() {
    @Test
    @SuppressLint("CheckResult")
    fun `add does not read notes on the UI thread`() {
        val noteType = col.notetypes.basic
        val note = col.newNote(noteType)
        note.setField(0, "Front")
        note.setField(1, "Back")
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        col.setSchemaModified()

        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        val editor = controller.get()
        advanceRobolectricLooper()
        assertTrue(shadowOf(editor).clickMenuItem(R.id.action_add))
        advanceRobolectricLooper()
        val confirmation = assertIs<ConfirmationDialog>(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

        val previousMod = col.mod
        val otherNote = col.newNote(col.notetypes.basicAndReversed)
        otherNote.setField(0, "Unrelated front")
        otherNote.setField(1, "Unrelated back")
        col.addNote(otherNote, Consts.DEFAULT_DECK_ID)
        assertNotEquals(previousMod, col.mod)

        var readNoteOnMainThread = false
        val observedCollection = spyk(col)
        every { observedCollection.getNote(note.id) } answers {
            readNoteOnMainThread = Looper.myLooper() == Looper.getMainLooper()
            callOriginal()
        }
        mockkObject(CollectionManager)
        try {
            every { CollectionManager.getColUnsafe() } returns observedCollection
            val dialog = assertIs<AlertDialog>(confirmation.requireDialog())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooperUntil { editor.tempNoteType!!.templateCount == 2 }
        } finally {
            unmockkObject(CollectionManager)
        }

        assertFalse(readNoteOnMainThread, "Recounting cards for Add must not read each note on the UI thread")
        assertEquals(2, editor.tempNoteType!!.templateCount)
    }
}
