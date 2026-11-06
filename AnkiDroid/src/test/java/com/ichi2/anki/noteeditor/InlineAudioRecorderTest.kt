// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Spinner
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.compat.Compat
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog
import com.ichi2.anki.getBrowserWithNotes
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.anki.setCardBrowserFragmented
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.withSplitPaneUi
import io.mockk.every
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItemInArray
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class InlineAudioRecorderTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val mediaRecorderStub = MediaRecorderStub()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun installRecorder() {
        mediaRecorderStub.install()
        grantPermissions(RECORD_AUDIO)
        Prefs.devInlineAudioRecorderEnabled = true
    }

    @After
    fun uninstallRecorder() {
        mediaRecorderStub.uninstall()
    }

    @Test
    fun `record audio opens the recording screen while the option is off`() {
        Prefs.devInlineAudioRecorderEnabled = false
        val editor = startNoteEditor().noteEditor

        editor.chooseRecordAudio(fieldIndex = 0)

        val started = shadowOf(editor.requireActivity()).nextStartedActivity
        assertThat("the recording screen opens", started?.component?.className, equalTo(MultimediaActivity::class.java.name))
        assertThat("no inline recorder", editor.inlineRecorder, nullValue())
    }

    @Test
    fun `record audio opens the inline recorder for the field`() {
        val editor = startNoteEditor().noteEditor

        editor.chooseRecordAudio(fieldIndex = 1)

        assertThat("no screen opens", shadowOf(editor.requireActivity()).nextStartedActivity, nullValue())
        composeRule.onNodeWithText("Back").assertExists()
        recordButton.assertExists()
        saveButton.assertDoesNotExist()
    }

    @Test
    fun `a tap records, pauses and resumes until the recording is saved`() {
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()
        pauseButton.performClick()
        verify { mediaRecorderStub.mediaRecorder.pause() }
        resumeButton.performClick()
        verify { mediaRecorderStub.mediaRecorder.resume() }
        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }

        saveButton.performClick()
        verify { mediaRecorderStub.mediaRecorder.stop() }
        insertButton.assertExists()
        discardButton.assertExists()
    }

    @Test
    fun `holding record records until it is released`() {
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performTouchInput { longClick() }

        verify { mediaRecorderStub.mediaRecorder.start() }
        verify { mediaRecorderStub.mediaRecorder.stop() }
        insertButton.assertExists()
    }

    @Test
    fun `screen readers and keyboards can record without holding`() {
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performSemanticsAction(SemanticsActions.OnClick)
        pauseButton.performSemanticsAction(SemanticsActions.OnClick)
        verify { mediaRecorderStub.mediaRecorder.pause() }
        saveButton.performSemanticsAction(SemanticsActions.OnClick)

        insertButton.assertExists()
    }

    @Test
    fun `recording asks for the microphone permission first`() {
        denyPermissions(RECORD_AUDIO)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()

        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.start() }
        val request = shadowOf(editor.requireActivity()).lastRequestedPermission
        assertThat("the microphone permission is requested", request.requestedPermissions, hasItemInArray(RECORD_AUDIO))
    }

    @Test
    fun `a denied microphone permission explains how to grant it`() {
        denyPermissions(RECORD_AUDIO)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()

        val activity = editor.requireActivity()
        val request = shadowOf(activity).lastRequestedPermission
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PERMISSION_DENIED))

        composeRule.onNodeWithText(targetContext.getString(CommonString.microphone_permission_denied_message)).assertExists()
        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.start() }
    }

    @Test
    fun `insert adds the recording to its field and closes the recorder`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        insertButton.performClick()
        advanceRobolectricLooper()

        val back = editor.fieldText(1)
        assertThat("the sound tag is added", back, startsWith("[sound:ankidroid_audiorec"))
        assertThat("the recording is in the media folder", col.media.have(back.removeSurrounding("[sound:", "]")), equalTo(true))
        assertThat("the other field is unchanged", editor.fieldText(0), equalTo(""))
        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
    }

    @Test
    fun `record audio on another field moves the recording there`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()

        editor.chooseRecordAudio(fieldIndex = 1)
        insertButton.performClick()
        advanceRobolectricLooper()

        assertThat("Front", editor.fieldText(0), equalTo(""))
        assertThat("Back", editor.fieldText(1), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `a recording is an unsaved change until it is deleted`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        assertThat("before recording", editor.hasUnsavedChanges(), equalTo(false))

        recordButton.performClick()
        assertThat("while recording", editor.hasUnsavedChanges(), equalTo(true))
        saveButton.performClick()
        assertThat("once saved", editor.hasUnsavedChanges(), equalTo(true))

        discardButton.performClick()
        assertThat("after deleting", editor.hasUnsavedChanges(), equalTo(false))
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        recordButton.assertExists()
    }

    @Test
    fun `close discards the recording and closes the recorder`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()

        closeButton.performClick()
        advanceRobolectricLooper()

        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        assertThat("nothing is inserted", editor.fieldText(0), equalTo(""))
    }

    @Test
    fun `back closes an empty recorder and keeps the note editor open`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)

        editor.pressBack()

        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the note editor stays", editor.requireActivity().isFinishing, equalTo(false))
    }

    @Test
    fun `back with a recording asks first and keeps the note editor open`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        record()

        editor.pressBack()
        latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        assertThat("the note editor stays", editor.requireActivity().isFinishing, equalTo(false))
    }

    @Test
    fun `back while recording asks and keep editing carries on recording`() {
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()

        editor.pressBack()
        latestDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()

        pauseButton.assertExists()
        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }
        assertThat("the note editor stays", editor.requireActivity().isFinishing, equalTo(false))
    }

    @Test
    fun `pause, save and delete vibrate like the recording screen`() {
        val vibrations = mutableListOf<Duration>()
        val compat = CompatHelper.compat
        every { CompatHelper.compat } returns
            object : Compat by compat {
                override fun vibrate(
                    context: Context,
                    duration: Duration,
                    usage: Int,
                ) {
                    vibrations += duration
                }
            }
        startNoteEditor().noteEditor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()
        assertThat("start", vibrations.size, equalTo(0))
        pauseButton.performClick()
        assertThat("pause", vibrations.size, equalTo(1))
        resumeButton.performClick()
        assertThat("resume", vibrations.size, equalTo(1))
        saveButton.performClick()
        assertThat("save", vibrations.size, equalTo(2))
        discardButton.performClick()
        assertThat("delete", vibrations, equalTo(List(3) { 20.milliseconds }))
    }

    @Test
    fun `the screen stays on only while recording`() {
        val controller = startNoteEditor()
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 0)

        recordButton.performClick()
        assertThat("recording", editor.recorderKeepsScreenOn, equalTo(true))
        pauseButton.performClick()
        assertThat("paused", editor.recorderKeepsScreenOn, equalTo(false))
        resumeButton.performClick()
        assertThat("recording again", editor.recorderKeepsScreenOn, equalTo(true))

        controller.pause().stop()
        assertThat("in the background", editor.recorderKeepsScreenOn, equalTo(false))
    }

    @Test
    fun `leaving the note editor finishes the recording`() {
        val controller = startNoteEditor()
        controller.noteEditor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()

        controller.pause().stop()
        verify { mediaRecorderStub.mediaRecorder.stop() }
        controller.start().resume()

        insertButton.assertExists()
    }

    @Test
    fun `a recording continues when the note editor is recreated`() {
        val controller = startNoteEditor()
        controller.noteEditor.chooseRecordAudio(fieldIndex = 1)
        recordButton.performClick()

        controller.recreate()
        advanceRobolectricLooper()

        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }
        saveButton.performClick()
        insertButton.performClick()
        advanceRobolectricLooper()
        assertThat("the recording goes to its field", controller.noteEditor.fieldText(1), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `the waveform survives the note editor being recreated`() {
        every { mediaRecorderStub.mediaRecorder.maxAmplitude } returns 16384
        val controller = startNoteEditor()
        controller.noteEditor.chooseRecordAudio(fieldIndex = 0)
        recordButton.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        val levels = controller.noteEditor.recorderViewModel.waveform.size
        assertThat("levels before recreating", levels > 0, equalTo(true))

        controller.recreate()
        advanceRobolectricLooper()

        assertThat("levels after recreating", controller.noteEditor.recorderViewModel.waveform.size >= levels, equalTo(true))
    }

    @Test
    fun `a note type change keeps the recording on the same field position`() {
        addStandardNoteType("Question and answer", arrayOf("Question", "Answer"), "{{Question}}", "{{Answer}}")
        col.notetypes.setCurrent(col.notetypes.byName("Basic")!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        editor.selectNoteType("Question and answer")

        composeRule.onNodeWithText("Answer").assertExists()
        insertButton.performClick()
        advanceRobolectricLooper()
        assertThat("Answer", editor.fieldText(1), startsWith("[sound:ankidroid_audiorec"))
    }

    @Test
    fun `a note type without the field deletes the recording`() {
        val threeFields = addStandardNoteType("Three fields", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}")
        col.notetypes.setCurrent(col.notetypes.byName(threeFields)!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 2)
        record()

        editor.selectNoteType("Basic")

        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
    }

    @Test
    fun `saving an edit preserves a pending recording`() {
        val note = addBasicNote("Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
        advanceRobolectricLooper()

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            assertThat("closing saves the recording", col.getNote(note.id).fields[1], containsString("[sound:"))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(1), containsString("[sound:"))
        }
    }

    @Test
    fun `saving a new note preserves its recording`() {
        val editor = startNoteEditor().noteEditor
        editor.setFieldValueFromUi(0, "Front")
        editor.chooseRecordAudio(fieldIndex = 1)
        record()
        val count = col.noteCount()

        editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
        advanceRobolectricLooper()

        if (col.noteCount() > count) {
            val savedNote = col.getNote(col.findNotes("").single())
            assertThat("the saved note includes its recording", savedNote.fields[1], containsString("[sound:"))
        } else {
            assertThat("the original note remains", editor.fieldText(0), equalTo("Front"))
            insertButton.performClick()
            advanceRobolectricLooper()
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooper()
            assertThat("the completed note can be saved", col.noteCount(), equalTo(count + 1))
        }
    }

    @Test
    fun `browser save preserves pending audio`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            advanceRobolectricLooper()
            val editor = browser.fragment!!
            val noteId = editor.editorNote!!.id
            val otherNote = col.findNotes("").first { it != noteId }
            editor.chooseRecordAudio(fieldIndex = 1)
            record()

            browser.viewModel.setQuery("nid:$otherNote", forceRefresh = true)
            advanceRobolectricLooper()
            latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()

            if (browser.fragment !== editor) {
                assertThat("navigation saves the recording", col.getNote(noteId).fields[1], containsString("[sound:"))
            } else {
                insertButton.assertExists()
                assertThat("the recording is retained", mediaRecorderStub.outputFiles.single().exists(), equalTo(true))
            }
        }

    @Test
    fun `hiding the browser pane stops its recording`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(1)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            advanceRobolectricLooper()
            val editor = browser.fragment!!
            editor.chooseRecordAudio(fieldIndex = 1)
            recordButton.performClick()

            try {
                browser.viewModel.setQuery("no-such-recorded-note", forceRefresh = true)
                advanceRobolectricLooper()

                val pane = browser.findViewById<android.view.View>(R.id.note_editor_frame)
                assertThat("the editor pane is hidden", pane.isVisible, equalTo(false))
                verify(exactly = 1) { mediaRecorderStub.mediaRecorder.stop() }
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                advanceRobolectricLooper()
                insertButton.assertExists()
            } finally {
                editor.recorderViewModel.interrupt()
            }
        }

    @Test
    fun `changing an existing note type preserves pending audio`() {
        val note = addBasicNote("Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        val touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        try {
            editor.noteTypeSpinner!!.dispatchTouchEvent(touch)
        } finally {
            touch.recycle()
        }
        advanceRobolectricLooper()
        val dialog =
            editor.parentFragmentManager.fragments
                .filterIsInstance<ChangeNoteTypeDialog>()
                .singleOrNull()
        if (dialog != null) {
            val destination = col.notetypes.byName("Basic (and reversed card)")!!.id
            val spinner = dialog.requireView().findViewById<Spinner>(R.id.dest_note_type_spinner)
            spinner.setSelection((0 until spinner.count).first { spinner.adapter.getItemId(it) == destination })
            advanceRobolectricLooper()
            val save = dialog.requireView().findViewById<android.view.View>(R.id.btn_save)
            assertThat("conversion is available", save.isEnabled, equalTo(true))
            save.performClick()
            advanceRobolectricLooper()
            assertThat("the conversion completed", col.getNote(note.id).noteTypeId, equalTo(destination))
        }

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            assertThat("closing saves the recording", col.getNote(note.id).fields[1], containsString("[sound:"))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(1), containsString("[sound:"))
        }
    }

    @Test
    fun `editing occlusions preserves pending audio`() {
        val note =
            col.newNote(col.notetypes.byName("Image Occlusion")!!).apply {
                setField(0, "{{c1::x}}")
                col.addNote(this, col.decks.selected())
            }
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 3)
        record()

        editor.requireView().findViewById<android.view.View>(R.id.EditOcclusionsButton).performClick()
        advanceRobolectricLooper()
        val launched = shadowOf(controller.get()).nextStartedActivityForResult
        if (launched != null) {
            shadowOf(controller.get()).receiveResult(launched.intent, android.app.Activity.RESULT_OK, android.content.Intent())
            advanceRobolectricLooper()
        }

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            assertThat("closing saves the recording", col.getNote(note.id).fields[3], containsString("[sound:"))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(3), containsString("[sound:"))
        }
    }

    @Test
    fun `saving with a recording asks to insert it first`() {
        val editor = startNoteEditor().noteEditor
        editor.setFieldValueFromUi(0, "Front")
        editor.chooseRecordAudio(fieldIndex = 1)
        record()
        val count = col.noteCount()

        editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
        advanceRobolectricLooper()

        val message = targetContext.getString(CommonString.insert_or_delete_recording)
        assertThat("message", editor.requireActivity().snackbarText, equalTo(message))
        assertThat("nothing is saved", col.noteCount(), equalTo(count))
    }

    @Test
    fun `discarding the recording changes the note type`() {
        val note = addBasicNote("Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val editor =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        val touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        editor.noteTypeSpinner!!.dispatchTouchEvent(touch)
        touch.recycle()
        advanceRobolectricLooper()
        latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        val dialogs = editor.parentFragmentManager.fragments.filterIsInstance<ChangeNoteTypeDialog>()
        assertThat("the note type dialog opens", dialogs.size, equalTo(1))
    }

    @Test
    fun `deleting the current template preserves pending audio`() {
        val note = addNoteUsingNoteTypeName("Basic (and reversed card)", "Front", "Back")
        val card = note.cards().first { it.ord == 0 }
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(card.id).toIntent(targetContext),
            )
        val editor = controller.noteEditor
        editor.chooseRecordAudio(fieldIndex = 1)
        record()

        editor.requireView().findViewById<android.view.View>(R.id.CardEditorCardsButton).performClick()
        advanceRobolectricLooper()
        val launched = shadowOf(controller.get()).nextStartedActivityForResult
        if (launched != null) {
            val notetype = col.notetypes.get(note.noteTypeId)!!
            col.notetypes.removeTemplate(notetype, notetype.templates[card.ord])
            col.notetypes.save(notetype)
            assertThat("the other card survives", col.getNote(note.id).cardIds(col).size, equalTo(1))
            shadowOf(controller.get()).receiveResult(launched.intent, android.app.Activity.RESULT_OK, android.content.Intent())
            advanceRobolectricLooper()
        }

        if (controller.get().isFinishing) {
            controller.pause().stop().destroy()
            val attached = col.getNote(note.id).fields[1].contains("[sound:")
            assertThat("the audio is retained or attached", mediaRecorderStub.outputFiles.single().exists() || attached, equalTo(true))
        } else {
            insertButton.performClick()
            advanceRobolectricLooper()
            assertThat("the recording remains available", editor.fieldText(1), containsString("[sound:"))
        }
    }

    @Test
    fun `discarding the recording opens the card templates`() {
        val note = addNoteUsingNoteTypeName("Basic (and reversed card)", "Front", "Back")
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        controller.noteEditor.chooseRecordAudio(fieldIndex = 1)
        record()

        val ctrlL = KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON)
        controller.get().dispatchKeyEvent(ctrlL)
        advanceRobolectricLooper()
        assertThat("nothing opens yet", shadowOf(controller.get()).peekNextStartedActivityForResult(), nullValue())
        latestDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
        val launched = shadowOf(controller.get()).nextStartedActivityForResult.intent
        assertThat("the card templates open", launched.component?.className, equalTo(CardTemplateEditor::class.java.name))
    }

    private fun startNoteEditor(): ActivityController<NoteEditorActivity> {
        ensureCollectionLoadIsSynchronous()
        return startActivityControllerNormallyOpenCollectionWithIntent(
            NoteEditorActivity::class.java,
            NoteEditorDestination.AddNote().toIntent(targetContext),
        )
    }

    private val ActivityController<NoteEditorActivity>.noteEditor: NoteEditorFragment
        get() = get().getNoteEditorFragment()

    private val NoteEditorFragment.inlineRecorder
        get() = childFragmentManager.findFragmentById(R.id.inline_audio_recorder_container)

    private val NoteEditorFragment.recorderViewModel: AudioRecordingViewModel
        get() = ViewModelProvider(inlineRecorder!!)[AudioRecordingViewModel::class.java]

    private val NoteEditorFragment.recorderKeepsScreenOn: Boolean
        get() {
            composeRule.waitForIdle()
            return inlineRecorder!!.requireView().allViews.any { it.keepScreenOn }
        }

    private fun NoteEditorFragment.pressBack() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()
    }

    private val latestDialog: AlertDialog
        get() = ShadowDialog.getLatestDialog() as AlertDialog

    private fun NoteEditorFragment.chooseRecordAudio(fieldIndex: Int) {
        multimediaController.handleActions(fieldIndex)
        advanceRobolectricLooper()
        multimediaViewModel.setMultimediaAction(MultimediaAction.SELECT_AUDIO_RECORDING)
        advanceRobolectricLooper()
    }

    private fun NoteEditorFragment.selectNoteType(name: String) {
        val spinner: Spinner = noteTypeSpinner!!
        val position = (0 until spinner.count).first { spinner.getItemAtPosition(it) == name }
        spinner.setSelection(position)
        advanceRobolectricLooper()
    }

    private fun NoteEditorFragment.fieldText(index: Int) = editFieldAt(index)!!.text.toString()

    private fun record() {
        recordButton.performClick()
        saveButton.performClick()
    }

    private fun button(
        @StringRes description: Int,
    ): SemanticsNodeInteraction = composeRule.onNodeWithContentDescription(targetContext.getString(description))

    private val recordButton get() = button(CommonString.start_recording)
    private val pauseButton get() = button(CommonString.pause_recording)
    private val resumeButton get() = button(CommonString.resume_recording)
    private val saveButton get() = button(CommonString.save_voice)
    private val insertButton get() = button(CommonString.insert_recording)
    private val discardButton get() = button(CommonString.delete_audio_button_description)
    private val closeButton get() = button(CommonString.close)
}
