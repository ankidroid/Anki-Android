// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.Manifest.permission.RECORD_AUDIO
import android.widget.Spinner
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.grantPermissions
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
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
    fun `a note type without the recording's field closes the recorder and deletes the recording`() {
        val threeFields = addStandardNoteType("Three fields", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}")
        col.notetypes.setCurrent(col.notetypes.byName(threeFields)!!)
        val editor = startNoteEditor().noteEditor
        editor.chooseRecordAudio(fieldIndex = 2)
        record()

        editor.selectNoteType("Basic")

        assertThat("the recorder closes", editor.inlineRecorder, nullValue())
        assertThat("the recording is deleted", mediaRecorderStub.outputFiles.single().exists(), equalTo(false))
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

    private val NoteEditorFragment.recorderKeepsScreenOn: Boolean
        get() {
            composeRule.waitForIdle()
            return inlineRecorder!!.requireView().keepScreenOn
        }

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
