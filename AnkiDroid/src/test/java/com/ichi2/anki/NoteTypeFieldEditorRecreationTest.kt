// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.REQUEST_HINT_LOCALE_SELECTION
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.RESULT_SELECTED_LOCALE
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.AddLanguageHint
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.Delete
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class NoteTypeFieldEditorRecreationTest : RobolectricTest() {
    private lateinit var controller: ActivityController<NoteTypeFieldEditor>
    private lateinit var launchIntent: Intent

    @Test
    fun `context menu acts on the chosen field after a night mode switch`() {
        launch()
        openContextMenu(BACK)
        switchNightMode()
        chooseAction(Delete)
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    @Test
    fun `restored context menu acts on Back after schema reorder`() {
        launch()
        openContextMenu(BACK)
        val saved = saveAndDestroy()
        moveBackToTheFront()
        restore(roundTrip(saved))
        chooseAction(Delete)
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    @Test
    fun `language hint after schema reorder goes to the chosen field`() {
        launch()
        openContextMenu(BACK)
        chooseAction(AddLanguageHint)
        val saved = saveAndDestroy()
        moveBackToTheFront()
        restore(roundTrip(saved))
        pickLanguageHint(Locale.JAPANESE)
        assertEquals(Locale.JAPANESE, field("Back").languageHint)
        assertNull(field("Front").languageHint)
    }

    private fun launch() {
        val intent =
            Intent()
                .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, "Basic")
                .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, col.notetypes.idForName("Basic")!!)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(NoteTypeFieldEditor::class.java, intent)
        advanceRobolectricLooper()
    }

    private fun saveAndDestroy(): Bundle {
        launchIntent = Intent(controller.get().intent)
        return Bundle().also {
            controller
                .pause()
                .stop()
                .saveInstanceState(it)
                .destroy()
        }
    }

    private fun restore(saved: Bundle) {
        controller = Robolectric.buildActivity(NoteTypeFieldEditor::class.java, launchIntent)
        saveControllerForCleanup(controller)
        controller.setup(saved)
        advanceRobolectricLooper()
    }

    private fun roundTrip(saved: Bundle): Bundle {
        val out = android.os.Parcel.obtain()
        val input = android.os.Parcel.obtain()
        try {
            out.writeBundle(saved)
            val bytes = out.marshall()
            input.unmarshall(bytes, 0, bytes.size)
            input.setDataPosition(0)
            return input.readBundle(NoteTypeFieldEditor::class.java.classLoader)!!
        } finally {
            out.recycle()
            input.recycle()
        }
    }

    private fun moveBackToTheFront() {
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        col.notetypes.moveFieldLegacy(notetype, notetype.fields[BACK], 0)
        assertEquals(listOf("Back", "Front"), fieldNames())
    }

    private fun pickLanguageHint(locale: Locale) {
        assertIs<LocaleSelectionDialog>(shownDialog()).parentFragmentManager.setFragmentResult(
            REQUEST_HINT_LOCALE_SELECTION,
            bundleOf(RESULT_SELECTED_LOCALE to locale),
        )
        advanceRobolectricLooper()
    }

    private fun field(name: String) = getCurrentDatabaseNoteTypeCopy("Basic").fields.first { it.name == name }

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
