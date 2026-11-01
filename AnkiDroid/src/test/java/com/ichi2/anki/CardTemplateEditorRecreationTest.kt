// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.utils.getInputField
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorRecreationTest : RobolectricTest() {
    private lateinit var controller: ActivityController<CardTemplateEditor>

    private val editor: CardTemplateEditor
        get() = controller.get()

    @Test
    fun `delete confirmation works after a night mode switch`() {
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        switchNightMode()
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
    }

    @Test
    fun `add confirmation works after a night mode switch`() {
        launch(col.notetypes.basic)
        clickMenuItem(R.id.action_add)
        switchNightMode()
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 1", TR.cardTemplatesCard(2)), templateNames())
    }

    @Test
    fun `full sync confirmation for deleting works after a night mode switch`() {
        markCollectionSynced()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        switchNightMode()
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
    }

    @Test
    fun `full sync confirmation for adding works after a night mode switch`() {
        markCollectionSynced()
        launch(col.notetypes.basic)
        clickMenuItem(R.id.action_add)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        switchNightMode()
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 1", TR.cardTemplatesCard(2)), templateNames())
    }

    @Test
    fun `full sync confirmation for repositioning works after a night mode switch`() {
        markCollectionSynced()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_reposition)
        (ShadowDialog.getLatestDialog() as AlertDialog).apply {
            getInputField().setText("2")
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        switchNightMode()
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2", "Card 1"), templateNames())
    }

    @Test
    fun `cancelling the full sync confirmation after a night mode switch closes it`() {
        markCollectionSynced()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        switchNightMode()
        clickButton(AlertDialog.BUTTON_NEGATIVE)
        assertNull(shownDialog())
        assertEquals(listOf("Card 1", "Card 2"), templateNames())
    }

    private fun launch(noteType: NotetypeJson) {
        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        advanceRobolectricLooper()
    }

    private fun markCollectionSynced() {
        col.setSchemaModified()
        col.db.execute("update col set ls = scm")
        assertFalse(col.schemaChanged())
    }

    private fun clickMenuItem(id: Int) {
        assertTrue(shadowOf(editor).clickMenuItem(id))
        advanceRobolectricLooper()
    }

    private fun switchNightMode() {
        val activity = editor
        RuntimeEnvironment.setQualifiers("+night")
        controller.configurationChange().visible()
        advanceRobolectricLooper()
        assertNotSame(activity, editor)
    }

    private fun clickButton(which: Int) {
        val dialog = assertIs<ConfirmationDialog>(shownDialog()).dialog as AlertDialog
        dialog.getButton(which).performClick()
        advanceRobolectricLooper()
    }

    private fun shownDialog() = editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment?

    private fun templateNames() = editor.tempNoteType!!.notetype.templatesNames
}
