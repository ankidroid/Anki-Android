// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.utils.getInputField
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test
    fun `restored delete confirmation waits for the card types to load`() {
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        val intent = Intent(editor.intent)
        val saved = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(saved)
            .destroy()

        val realCollection = col
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        mockkObject(CollectionManager)
        every { CollectionManager.isOpenUnsafe() } returns false
        every { CollectionManager.getColUnsafe() } answers {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                loadStarted.countDown()
                check(releaseLoad.await(15, TimeUnit.SECONDS)) { "Background load was not released" }
            }
            realCollection
        }
        try {
            controller = Robolectric.buildActivity(CardTemplateEditor::class.java, intent)
            saveControllerForCleanup(controller)
            controller.setup(saved)
            check(loadStarted.await(5, TimeUnit.SECONDS)) { "Collection load did not begin" }
            clickButton(AlertDialog.BUTTON_POSITIVE)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.tempNoteType?.notetype?.templatesNames == listOf("Card 2") }
        } finally {
            releaseLoad.countDown()
            unmockkObject(CollectionManager)
            advanceRobolectricLooper()
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
    fun `delete confirmed while loading opens the full sync warning after another recreation`() {
        markCollectionSynced()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        val saved = Bundle()
        lateinit var intent: Intent
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            intent = Intent(editor.intent)
            controller
                .pause()
                .stop()
                .saveInstanceState(saved)
                .destroy()
            releaseLoad.countDown()
        }
        controller = Robolectric.buildActivity(CardTemplateEditor::class.java, intent)
        saveControllerForCleanup(controller)
        controller.setup(saved)
        advanceRobolectricLooper()
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
    fun `a consumed add dialog cannot add a second card type after recreation`() {
        launch(col.notetypes.basic)
        clickMenuItem(R.id.action_add)
        confirmAndRecreateBeforeDismissal()
        if (shownDialog() is ConfirmationDialog) clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 1", TR.cardTemplatesCard(2)), templateNames())
        assertNull(shownDialog())
    }

    @Test
    fun `delete confirmed during cold restore after an unsaved reposition completes safely`() {
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_reposition)
        (ShadowDialog.getLatestDialog() as AlertDialog).apply {
            getInputField().setText("2")
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        assertEquals(listOf("Card 2", "Card 1"), templateNames())
        clickMenuItem(R.id.action_delete)
        withColdRestore { releaseLoad ->
            clickButton(AlertDialog.BUTTON_POSITIVE)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.tempNoteType?.notetype?.templatesNames == listOf("Card 2") }
        }
        assertNull(shownDialog())
    }

    private fun withColdRestore(block: (releaseLoad: CountDownLatch) -> Unit) {
        val intent = Intent(editor.intent)
        val saved = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(saved)
            .destroy()

        val realCollection = col
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        mockkObject(CollectionManager)
        every { CollectionManager.isOpenUnsafe() } returns false
        every { CollectionManager.getColUnsafe() } answers {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                loadStarted.countDown()
                check(releaseLoad.await(15, TimeUnit.SECONDS)) { "Background load was not released" }
            }
            realCollection
        }
        try {
            controller = Robolectric.buildActivity(CardTemplateEditor::class.java, intent)
            saveControllerForCleanup(controller)
            controller.setup(saved)
            check(loadStarted.await(5, TimeUnit.SECONDS)) { "Collection load did not begin" }
            block(releaseLoad)
        } finally {
            releaseLoad.countDown()
            unmockkObject(CollectionManager)
            advanceRobolectricLooper()
        }
    }

    @Test
    fun `a consumed delete dialog cannot delete a surviving card after recreation`() {
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        val dialog = assertIs<ConfirmationDialog>(shownDialog()).requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        // Deliver the confirmation, leaving AlertController's dismissal queued.
        shadowOf(Looper.getMainLooper()).runOneTask()

        val intent = Intent(editor.intent)
        val saved = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(saved)
            .destroy()
        controller = Robolectric.buildActivity(CardTemplateEditor::class.java, intent)
        saveControllerForCleanup(controller)
        controller.setup(saved)
        advanceRobolectricLooper()

        // A consumed dialog must not repeat its deletion against the surviving template.
        if (shownDialog() is ConfirmationDialog) clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
        assertNull(shownDialog())
    }

    private fun launch(noteType: NotetypeJson) {
        val intent = CardTemplateEditor.getIntent(targetContext, noteType.id)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        advanceRobolectricLooper()
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
        shadowOf(Looper.getMainLooper()).runOneTask()
        val intent = Intent(editor.intent)
        val saved = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(saved)
            .destroy()
        controller = Robolectric.buildActivity(CardTemplateEditor::class.java, intent)
        saveControllerForCleanup(controller)
        controller.setup(saved)
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
