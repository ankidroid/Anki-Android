// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.saveAndDestroy
import com.ichi2.utils.getInputField
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorRecreationTest : RobolectricTest() {
    private lateinit var controller: ActivityController<CardTemplateEditor>
    private lateinit var launchIntent: Intent

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
        reposition("2")
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

    @Test
    fun `restored delete confirmation waits for the card types to load`() {
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.tempNoteType?.notetype?.templatesNames == listOf("Card 2") }
        }
    }

    @Test
    fun `add confirmed while loading opens the full sync warning when returning from background`() {
        markCollectionSynced()
        launch(col.notetypes.basic)
        clickMenuItem(R.id.action_add)
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            controller.pause().stop()
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.mainBinding.cardTemplateEditorPager.adapter != null }
            controller.start().resume().visible()
            advanceRobolectricLooper()
        }
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 1", TR.cardTemplatesCard(2)), templateNames())
    }

    @Test
    fun `pending delete confirmation opens full sync warning when returning from background`() {
        markCollectionSynced()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        withColdRestore { releaseLoad ->
            // Confirm the real restored dialog while the collection is still loading.
            clickButton(AlertDialog.BUTTON_POSITIVE)
            assertNull(shownDialog())
            assertFalse(col.schemaChanged())
            controller.pause().stop()

            // Loading finishes while the receiving fragment is stopped, leaving a pending result.
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.mainBinding.cardTemplateEditorPager.adapter != null }

            // ON_START replays the result. Opening its full-sync warning must not run nested transactions.
            controller.start().resume().visible()
            advanceRobolectricLooper()
        }

        val warning = assertIs<ConfirmationDialog>(shownDialog()).requireDialog() as AlertDialog
        assertEquals(
            editor.getString(CommonString.full_sync_confirmation),
            requireNotNull(warning.findViewById<TextView>(android.R.id.message)).text.toString(),
        )
        assertEquals(listOf("Card 1", "Card 2"), templateNames())
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
    }

    @Test
    fun `delete confirmed while loading opens the full sync warning after another recreation`() {
        markCollectionSynced()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        lateinit var saved: Bundle
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            saved = controller.saveAndDestroy()
            releaseLoad.countDown()
        }
        restore(saved)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
    }

    @Test
    fun `add confirmed during cold restore after an unsaved reposition completes safely`() {
        launch(col.notetypes.basicAndReversed)
        reposition("2")
        clickMenuItem(R.id.action_add)
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.tempNoteType?.templateCount == 3 }
        }
        assertEquals(listOf("Card 2", "Card 1", TR.cardTemplatesCard(3)), templateNames())
        assertNull(shownDialog())
    }

    @Test
    fun `synced delete confirmed during cold restore after an unsaved reposition opens the full sync warning`() {
        launch(col.notetypes.basicAndReversed)
        reposition("2")
        markCollectionSynced()
        clickMenuItem(R.id.action_delete)
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { shownDialog() is ConfirmationDialog }
        }
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
        assertNull(shownDialog())
    }

    @Test
    fun `delete confirmed during cold restore after an unsaved reposition completes safely`() {
        launch(col.notetypes.basicAndReversed)
        reposition("2")
        assertEquals(listOf("Card 2", "Card 1"), templateNames())
        clickMenuItem(R.id.action_delete)
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.tempNoteType?.notetype?.templatesNames == listOf("Card 2") }
        }
        assertNull(shownDialog())
    }

    @Test
    fun `a consumed add dialog cannot add a second card type after recreation`() {
        launch(col.notetypes.basic)
        clickMenuItem(R.id.action_add)
        confirmAndRecreateBeforeDismissal()
        if (shownDialog() is ConfirmationDialog) clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 1", TR.cardTemplatesCard(2)), templateNames())
        assertNull(shownDialog())
    }

    @Test
    fun `a consumed delete dialog cannot delete a surviving card after recreation`() {
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        confirmAndRecreateBeforeDismissal()

        // A consumed dialog must not repeat its deletion against the surviving template.
        if (shownDialog() is ConfirmationDialog) clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
        assertNull(shownDialog())
    }

    private fun launch(noteType: NotetypeJson) {
        launchIntent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, launchIntent)
        advanceRobolectricLooper()
    }

    private fun restore(saved: Bundle) {
        controller = restoreActivityController(CardTemplateEditor::class.java, launchIntent, saved)
        advanceRobolectricLooper()
    }

    private fun withColdRestore(block: (releaseLoad: CountDownLatch) -> Unit) =
        restoreWhileCollectionLoads(CardTemplateEditor::class.java, launchIntent, controller.saveAndDestroy()) { restored, releaseLoad ->
            controller = restored
            block(releaseLoad)
        }

    private fun reposition(position: String) {
        clickMenuItem(R.id.action_reposition)
        (ShadowDialog.getLatestDialog() as AlertDialog).apply {
            getInputField().setText(position)
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
    }

    private fun confirmAndRecreateBeforeDismissal() {
        val dialog = assertIs<ConfirmationDialog>(shownDialog()).requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        // Deliver the confirmation, leaving AlertController's dismissal queued.
        shadowOf(Looper.getMainLooper()).runOneTask()
        restore(controller.saveAndDestroy())
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
