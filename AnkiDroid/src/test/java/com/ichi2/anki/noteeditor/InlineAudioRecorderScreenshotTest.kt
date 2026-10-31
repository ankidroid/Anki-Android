// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.Dp
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import org.junit.Rule
import org.junit.Test
import org.robolectric.Robolectric
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class InlineAudioRecorderScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun inlineRecorderStates() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { controller ->
            controller.setup().get().setContent {
                AnkiDroidPreview {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = Dp(16f))) {
                        for (state in sheetStates) {
                            InlineAudioRecorder(
                                state = state,
                                elapsedSeconds = if (state == RecorderState.Idle) 0 else 7,
                                levels = if (state == RecorderState.Idle) emptyList() else sampleLevels,
                                fieldName = "Front",
                                onRecordPress = {},
                                onRecordRelease = {},
                                onRecordClick = {},
                                onSave = {},
                                onDiscard = {},
                                onInsert = {},
                                onClose = {},
                            )
                        }
                    }
                }
            }
            captureRecorder("inline_recorder_states")
        }
    }

    private fun captureRecorder(name: String) {
        composeRule.waitForIdle()
        captureScreen(name)
    }

    private val sheetStates =
        listOf(RecorderState.Idle, RecorderState.Recording, RecorderState.Paused, RecorderState.Recorded(File("")))

    private val sampleLevels = List(44) { abs(sin(it * 0.9f) * cos(it * 0.31f)) }
}
