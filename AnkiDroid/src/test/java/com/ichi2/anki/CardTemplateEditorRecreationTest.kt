// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.saveAndDestroy
import com.ichi2.testutils.withCollectionAccessOn
import com.ichi2.utils.getInputField
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Test
    fun `a restored deletion cannot remove the other card after a database reorder`() {
        val noteType = col.notetypes.basicAndReversed
        val note = addBasicAndReversedNote()
        val survivingCard = note.cards().single { it.ord == 1 }
        launch(noteType)
        clickMenuItem(R.id.action_delete)
        val saved = controller.saveAndDestroy()

        // The database now maps the two editor ordinals to different cards.
        val reordered = noteType.deepClone()
        col.notetypes.repositionTemplate(reordered, reordered.templates[0], 1)
        col.notetypes.updateDict(reordered)
        assertEquals(0, note.cards().single { it.id == survivingCard.id }.ord)

        restore(saved)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        if (!editor.isFinishing && shownDialog() == null) clickMenuItem(R.id.action_confirm)
        assertTrue(survivingCard.id in note.cids())
    }

    @Test
    fun `restored reposition preserves a card type added to the database while suspended`() {
        val noteType = col.notetypes.basicAndReversed
        val note = addBasicAndReversedNote()
        markCollectionSynced()
        launch(noteType)
        reposition("2")
        assertIs<ConfirmationDialog>(shownDialog())
        val saved = controller.saveAndDestroy()

        col.notetypes.addTemplate(
            noteType.deepClone(),
            Notetypes.newTemplate("Externally added").apply {
                qfmt = "{{Front}} extra"
                afmt = "{{Back}}"
            },
        )
        val externalCardId = note.cards().single { it.ord == 2 }.id
        restore(saved)
        clickButton(AlertDialog.BUTTON_POSITIVE)

        // If Move took effect, save it as the user normally would.
        if (templateNames().first() == "Card 2") clickMenuItem(R.id.action_confirm)
        assertTrue(externalCardId in note.cids(), "Saving the move must preserve the card added outside the editor")
    }

    @Test
    fun `restored add preserves a card type added to the database while suspended`() {
        val noteType = col.notetypes.basicAndReversed
        val note = addBasicAndReversedNote()
        launch(noteType)
        clickMenuItem(R.id.action_add)
        val saved = controller.saveAndDestroy()

        col.notetypes.addTemplate(
            noteType.deepClone(),
            Notetypes.newTemplate("Externally added").apply {
                qfmt = "{{Front}} extra"
                afmt = "{{Back}}"
            },
        )
        val externalCardId = note.cards().single { it.ord == 2 }.id
        restore(saved)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        if (templateNames().size == 3) {
            advanceRobolectricLooperUntil { editor.currentFragment?.isResumed == true && editor.selectedTabPosition == 2 }
            // Give the new template a distinct front before saving, as required by the backend.
            requireNotNull(editor.currentFragment).binding.editText.setText("{{Front}} added in editor")
            clickMenuItem(R.id.action_confirm)
        }
        assertTrue(externalCardId in note.cids(), "Saving Add must preserve the cards generated by the external type")
    }

    @Test
    fun `a distant delete after a move survives two cold restores`() {
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
        launch(noteType)
        reposition("2")
        val expected = templateNames().dropLast(1)
        editor.selectTab(19)
        advanceRobolectricLooperUntil { editor.currentFragment?.isResumed == true && editor.selectedTabPosition == 19 }
        clickMenuItem(R.id.action_delete)

        lateinit var saved: Bundle
        withColdRestore { releaseLoad ->
            assertNull(editor.mainBinding.cardTemplateEditorPager.adapter)
            clickButton(AlertDialog.BUTTON_POSITIVE)
            saved = controller.saveAndDestroy()
            releaseLoad.countDown()
        }
        restoreWhileCollectionLoads(CardTemplateEditor::class.java, launchIntent, saved) { restored, releaseLoad ->
            controller = restored
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.mainBinding.cardTemplateEditorPager.adapter != null }
        }
        assertEquals(expected, templateNames())
    }

    @Test
    @SuppressLint("CheckResult")
    fun `saving a restored delete preserves fields added to the note type elsewhere`() {
        val noteType = col.notetypes.basicAndReversed
        val note = addBasicAndReversedNote()
        launch(noteType)
        clickMenuItem(R.id.action_delete)
        val saved = controller.saveAndDestroy()

        val externalNoteType = noteType.deepClone()
        col.notetypes.addField(externalNoteType, col.notetypes.newField("Imported"))
        col.notetypes.updateDict(externalNoteType)
        val updatedNote = col.getNote(note.id)
        updatedNote.setField(2, "keep this imported field")
        col.updateNote(updatedNote)

        restore(saved)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        if (templateNames().size == 1) clickMenuItem(R.id.action_confirm)
        assertEquals("keep this imported field", col.getNote(note.id).fields.getOrNull(2))
    }

    @Test
    @SuppressLint("CheckResult")
    fun `delete preserves fields added directly to the backend while the editor is open`() {
        val noteType = col.notetypes.basicAndReversed
        val note = addBasicAndReversedNote()
        launch(noteType)
        clickMenuItem(R.id.action_delete)
        val externalNoteType = noteType.deepClone()
        col.notetypes.addField(externalNoteType, col.notetypes.newField("Imported"))
        // Like package import, write through the backend without refreshing the legacy notetype cache.
        col.backend.updateNotetypeLegacy(BackendUtils.toJsonBytes(externalNoteType), skipChecks = false)
        val updatedNote = col.getNote(note.id)
        updatedNote.setField(2, "keep this imported field")
        col.updateNote(updatedNote)
        assertEquals("keep this imported field", col.getNote(note.id).fields.getOrNull(2))

        clickButton(AlertDialog.BUTTON_POSITIVE)
        if (templateNames().size == 1) clickMenuItem(R.id.action_confirm)
        assertEquals("keep this imported field", col.getNote(note.id).fields.getOrNull(2))
    }

    @Test
    fun `a restored delete with an evicted draft does not delete a replacement card`() {
        val noteType = col.notetypes.basicAndReversed
        val note = addBasicAndReversedNote()
        val removedCardId = note.cards().single { it.ord == 1 }.id
        launch(noteType)
        editor.selectTab(1)
        advanceRobolectricLooperUntil { editor.currentFragment?.isResumed == true && editor.selectedTabPosition == 1 }
        clickMenuItem(R.id.action_delete)
        val saved = controller.saveAndDestroy()

        val externalNoteType = noteType.deepClone()
        externalNoteType.templates[1] =
            Notetypes.newTemplate("Card 2").apply {
                qfmt = "{{Front}} replacement"
                afmt = "{{Back}}"
            }
        col.notetypes.updateDict(externalNoteType)
        val replacementCardId = note.cards().single { it.ord == 1 }.id
        assertTrue(replacementCardId != removedCardId)
        assertTrue(CardTemplateNotetype.clearTempNoteTypeFiles() > 0)

        restore(saved)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        if (templateNames().size == 1) clickMenuItem(R.id.action_confirm)
        assertTrue(replacementCardId in note.cids(), "The old Delete must leave the replacement card alone")
    }

    @Test
    fun `delete after draft eviction keeps the saved card type`() {
        val noteType = col.notetypes.basicAndReversed
        val cardTypeName = TR.cardTemplatesCard(2)
        noteType.templates[1].name = cardTypeName
        col.notetypes.updateDict(noteType)
        val note = addBasicAndReversedNote()
        val originalCardIds = note.cids()
        launch(noteType)

        editor.selectTab(1)
        advanceRobolectricLooperUntil { editor.currentFragment?.isResumed == true && editor.selectedTabPosition == 1 }
        clickMenuItem(R.id.action_delete)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 1"), templateNames())

        clickMenuItem(R.id.action_add)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        advanceRobolectricLooperUntil { editor.currentFragment?.isResumed == true && editor.selectedTabPosition == 1 }
        assertEquals(noteType.templatesNames, templateNames())
        assertNull(editor.tempNoteType!!.getOriginalOrdinal(1))
        clickMenuItem(R.id.action_delete)
        val warning = assertIs<ConfirmationDialog>(shownDialog()).requireDialog() as AlertDialog
        assertEquals(
            editor.resources.getQuantityString(CommonPlurals.card_template_editor_confirm_delete, 0, 0, cardTypeName),
            requireNotNull(warning.findViewById<TextView>(android.R.id.message)).text.toString(),
        )
        val saved = controller.saveAndDestroy()
        assertTrue(CardTemplateNotetype.clearTempNoteTypeFiles() > 0)

        restore(saved)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        if (templateNames().size == 1) clickMenuItem(R.id.action_confirm)
        assertEquals(originalCardIds, note.cids())
    }

    @Test
    fun `delete still works after a note of another type is added`() {
        addBasicAndReversedNote()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        val originalMod = col.mod

        val otherNote = col.newNote(col.notetypes.basic)
        otherNote.setField(0, "Another front")
        otherNote.setField(1, "Another back")
        col.addNote(otherNote, Consts.DEFAULT_DECK_ID)
        assertTrue(col.mod != originalMod)

        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
    }

    @Test
    fun `add creates no extra card after an edit during the count`() {
        val noteType = col.notetypes.basic
        val note = col.newNote(noteType)
        note.setField(0, "Front")
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        val originalCardIds = note.cids()
        launch(noteType)
        val binding = requireNotNull(editor.currentFragment).binding
        binding.bottomNavigation.selectedItemId = R.id.back_edit

        withDelayedCollectionWork {
            assertTrue(shadowOf(editor).clickMenuItem(R.id.action_add))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(shownDialog())
            binding.editText.setSelection(binding.editText.length())
            val input = requireNotNull(binding.editText.onCreateInputConnection(EditorInfo()))
            assertTrue(input.commitText("\n{{Front}}", 1))
            assertTrue(
                editor.tempNoteType!!
                    .getTemplate(0)
                    .afmt
                    .endsWith("{{Front}}"),
            )
        }
        val warning = assertIs<ConfirmationDialog>(shownDialog()).requireDialog() as AlertDialog
        assertEquals(
            editor.resources.getQuantityString(CommonPlurals.card_template_editor_confirm_add, 0, 0),
            requireNotNull(warning.findViewById<TextView>(android.R.id.message)).text.toString(),
        )

        clickButton(AlertDialog.BUTTON_POSITIVE)
        clickMenuItem(R.id.action_confirm)
        advanceRobolectricLooperUntil { editor.isFinishing }
        assertEquals(originalCardIds, note.cids())
    }

    private fun withDelayedCollectionWork(block: () -> Unit) {
        val collection = col
        val dispatcher = StandardTestDispatcher()
        mockkObject(CollectionManager)
        try {
            // A synchronous editor read must not block Robolectric's main thread on the paused queue.
            every { CollectionManager.getColUnsafe() } returns collection
            runBlocking {
                withCollectionAccessOn(dispatcher) {
                    block()
                    dispatcher.scheduler.advanceUntilIdle()
                }
            }
        } finally {
            unmockkObject(CollectionManager)
        }
        advanceRobolectricLooper()
    }

    @Test
    fun `add still works after a note of another type is added`() {
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_add)
        val otherNote = col.newNote(col.notetypes.basic)
        otherNote.setField(0, "Another front")
        otherNote.setField(1, "Another back")
        col.addNote(otherNote, Consts.DEFAULT_DECK_ID)

        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(3, templateNames().size)
    }

    @Test
    fun `synced delete works after a note of another type is added`() {
        addBasicAndReversedNote()
        markCollectionSynced()
        launch(col.notetypes.basicAndReversed)
        clickMenuItem(R.id.action_delete)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertIs<ConfirmationDialog>(shownDialog())
        val otherNote = col.newNote(col.notetypes.basic)
        otherNote.setField(0, "Another front")
        col.addNote(otherNote, Consts.DEFAULT_DECK_ID)

        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("Card 2"), templateNames())
    }

    @Test
    fun `restored add works after a new note that gets no card`() {
        val noteType = col.notetypes.basic
        launch(noteType)
        clickMenuItem(R.id.action_add)
        val note = col.newNote(noteType)
        note.setField(0, "Front")
        col.addNote(note, Consts.DEFAULT_DECK_ID)
        switchNightMode()

        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(2, templateNames().size)
    }

    @Test
    fun `synced add works after a new note that gets no card`() {
        val noteType = col.notetypes.basic
        markCollectionSynced()
        launch(noteType)
        clickMenuItem(R.id.action_add)
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertIs<ConfirmationDialog>(shownDialog())
        val note = col.newNote(noteType)
        note.setField(0, "Front")
        col.addNote(note, Consts.DEFAULT_DECK_ID)

        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(2, templateNames().size)
    }

    @Test
    fun `add copies its card type after a distant tab change`() {
        launch(twentyCardTypes())
        val sourcePage = requireNotNull(editor.currentFragment)

        openWarningDuringTabChange(R.id.action_add, sourcePage)

        advanceRobolectricLooperUntil { !sourcePage.isAdded && editor.selectedTabPosition == 19 }
        clickButton(AlertDialog.BUTTON_POSITIVE)
        val templates = editor.tempNoteType!!.notetype.templates
        assertEquals(21, templates.length())
        assertEquals("{{Front}}", templates.last().qfmt)
    }

    @Test
    fun `delete works after a tab change and night mode`() {
        val noteType = twentyCardTypes()
        launch(noteType)
        val expected = noteType.templatesNames.drop(1)

        openWarningDuringTabChange(R.id.action_delete, requireNotNull(editor.currentFragment))
        switchNightMode()

        advanceRobolectricLooperUntil { editor.selectedTabPosition == 19 }
        clickButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(expected, templateNames())
    }

    private fun twentyCardTypes(): NotetypeJson {
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
        return noteType
    }

    private fun openWarningDuringTabChange(
        menuItem: Int,
        sourcePage: CardTemplateEditor.CardTemplateFragment,
    ) {
        val mainLooper = shadowOf(Looper.getMainLooper())
        val collection = col
        val dispatcher = StandardTestDispatcher()
        mockkObject(CollectionManager)
        try {
            every { CollectionManager.getColUnsafe() } returns collection
            runBlocking {
                withCollectionAccessOn(dispatcher) {
                    assertTrue(shadowOf(editor).clickMenuItem(menuItem))
                    mainLooper.idle()
                    assertNull(shownDialog())
                    editor.selectTab(19)
                    assertTrue(sourcePage.isAdded)
                    dispatcher.scheduler.advanceUntilIdle()
                    mainLooper.idle()
                    assertIs<ConfirmationDialog>(shownDialog())
                }
            }
        } finally {
            unmockkObject(CollectionManager)
        }
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
