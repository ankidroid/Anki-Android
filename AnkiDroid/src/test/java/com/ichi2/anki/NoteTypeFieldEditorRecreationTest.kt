// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.Delete
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

@RunWith(AndroidJUnit4::class)
class NoteTypeFieldEditorRecreationTest : RobolectricTest() {
    private lateinit var controller: ActivityController<NoteTypeFieldEditor>

    @Test
    fun `context menu acts on the chosen field after a night mode switch`() {
        launch()
        openContextMenu(BACK)
        switchNightMode()
        chooseAction(Delete)
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    private fun launch() {
        val intent =
            Intent()
                .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, "Basic")
                .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, col.notetypes.idForName("Basic")!!)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(NoteTypeFieldEditor::class.java, intent)
        advanceRobolectricLooper()
    }

    private fun openContextMenu(position: Int) {
        controller.get().findViewById<ListView>(R.id.fields).performItemClick(null, position, 0)
        advanceRobolectricLooper()
        assertIs<NoteTypeFieldEditorContextMenu>(shownDialog())
    }

    private fun chooseAction(action: NoteTypeFieldEditorContextMenuAction) {
        val menu = assertIs<NoteTypeFieldEditorContextMenu>(shownDialog()).dialog as AlertDialog
        val index = NoteTypeFieldEditorContextMenuAction.entries.sortedBy { it.order }.indexOf(action)
        menu.listView.performItemClick(null, index, 0)
        advanceRobolectricLooper()
    }

    private fun switchNightMode() {
        val activity = controller.get()
        RuntimeEnvironment.setQualifiers("+night")
        controller.configurationChange()
        advanceRobolectricLooper()
        assertNotSame(activity, controller.get())
    }

    private fun confirm() {
        val dialog = assertIs<ConfirmationDialog>(shownDialog()).dialog as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
    }

    private fun shownDialog() = controller.get().supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment?

    private fun fieldNames() = getCurrentDatabaseNoteTypeCopy("Basic").fieldsNames

    companion object {
        private const val BACK = 1
    }
}
