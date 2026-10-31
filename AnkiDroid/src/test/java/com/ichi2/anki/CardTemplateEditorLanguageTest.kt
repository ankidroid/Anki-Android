// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.ext.reopenWithLanguage
import com.ichi2.testutils.saveAndDestroy
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorLanguageTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a restored add still works after a language change`() {
        val noteType = col.notetypes.basicAndReversed
        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        var controller: ActivityController<CardTemplateEditor> =
            startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        advanceRobolectricLooper()
        assertTrue(shadowOf(controller.get()).clickMenuItem(R.id.action_add))
        advanceRobolectricLooper()
        assertIs<ConfirmationDialog>(controller.get().shownDialog())
        val saved = controller.saveAndDestroy()

        runBlocking { col.reopenWithLanguage("de") }
        controller = restoreActivityController(CardTemplateEditor::class.java, intent, saved)
        advanceRobolectricLooper()
        val dialog = assertIs<ConfirmationDialog>(controller.get().shownDialog()).dialog as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        val names =
            controller
                .get()
                .tempNoteType!!
                .notetype.templatesNames
        assertEquals(3, names.size)
        assertEquals("Karte 3", names.last().filter { it.isLetterOrDigit() || it == ' ' })
    }

    private fun CardTemplateEditor.shownDialog() = supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment?
}
