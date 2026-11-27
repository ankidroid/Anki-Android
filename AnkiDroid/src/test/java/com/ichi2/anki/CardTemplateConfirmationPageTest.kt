// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.withCollectionAccessOn
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateConfirmationPageTest : RobolectricTest() {
    @Test
    fun `delete still works after a distant tab change`() {
        val noteType = col.notetypes.basic
        for (number in 2..20) {
            col.notetypes.addTemplate(
                noteType,
                Notetypes.newTemplate("Card $number").apply {
                    qfmt = "{{Front}} $number"
                    afmt = "{{Back}}"
                },
            )
        }
        col.setSchemaModified()
        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        val editor = controller.get()
        advanceRobolectricLooper()
        val sourcePage = requireNotNull(editor.currentFragment)
        val expectedNames = noteType.templatesNames.drop(1)
        val mainLooper = shadowOf(Looper.getMainLooper())

        val collection = col
        val dispatcher = StandardTestDispatcher()
        mockkObject(CollectionManager)
        try {
            every { CollectionManager.getColUnsafe() } returns collection
            runBlocking {
                withCollectionAccessOn(dispatcher) {
                    assertTrue(shadowOf(editor).clickMenuItem(R.id.action_delete))
                    mainLooper.idle()
                    assertNull(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

                    // Start the tab change but let the count complete before the pager settles.
                    editor.selectTab(19)
                    assertTrue(sourcePage.isAdded, "The source page must still be attached when the warning opens")
                    dispatcher.scheduler.advanceUntilIdle()
                    mainLooper.idle()
                    assertIs<ConfirmationDialog>(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                }
            }
        } finally {
            unmockkObject(CollectionManager)
        }

        advanceRobolectricLooperUntil { !sourcePage.isAdded && editor.selectedTabPosition == 19 }
        val confirmation = assertIs<ConfirmationDialog>(editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        assertIs<AlertDialog>(confirmation.requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertEquals(expectedNames, editor.tempNoteType!!.notetype.templatesNames)
    }
}
