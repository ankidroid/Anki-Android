// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.Manifest.permission.RECORD_AUDIO
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.testutils.simulateSystemBars
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class InlineAudioRecorderScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val mediaRecorderStub = MediaRecorderStub()

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
    fun inlineRecorderReady() =
        withInlineRecorder { activity ->
            activity.simulateSystemBars()
            captureRecorder("inline_recorder_ready")
        }

    @Test
    fun inlineRecorderRecording() =
        withInlineRecorder { activity ->
            recordButton.performClick()
            activity.simulateSystemBars()
            captureRecorder("inline_recorder_recording")
        }

    @Test
    fun inlineRecorderRecorded() =
        withInlineRecorder { activity ->
            record()
            activity.simulateSystemBars()
            captureRecorder("inline_recorder_recorded")
        }

    @Test
    fun inlineRecorderKeyboard() =
        withInlineRecorder { activity ->
            record()
            activity.simulateKeyboard()
            captureRecorder("inline_recorder_keyboard")
        }

    @Test
    fun inlineRecorderWithoutToolbar() {
        getPreferences().edit { putBoolean("noteEditorShowToolbar", false) }
        withInlineRecorder { activity ->
            record()
            activity.simulateSystemBars()
            captureRecorder("inline_recorder_no_toolbar")
        }
    }

    @Test
    fun inlineRecorderLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withInlineRecorder { activity ->
            record()
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)
            advanceRobolectricLooper()
            captureRecorder("inline_recorder_landscape")
        }
    }

    private fun withInlineRecorder(block: (NoteEditorActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.AddNote().toIntent(targetContext),
            )
        advanceRobolectricLooper()
        val editor = activity.getNoteEditorFragment()
        editor.multimediaController.handleActions(1)
        advanceRobolectricLooper()
        editor.multimediaViewModel.setMultimediaAction(MultimediaAction.SELECT_AUDIO_RECORDING)
        advanceRobolectricLooper()
        recordButton.assertExists()
        block(activity)
    }

    private fun captureRecorder(name: String) {
        composeRule.waitForIdle()
        captureScreen(name)
    }

    private fun record() {
        recordButton.performClick()
        saveButton.performClick()
    }

    private val recordButton
        get() = composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.start_recording))

    private val saveButton
        get() = composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.save_voice))
}
