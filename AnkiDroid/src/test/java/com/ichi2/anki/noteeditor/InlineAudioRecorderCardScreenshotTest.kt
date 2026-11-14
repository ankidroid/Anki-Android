// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.Theme
import org.junit.Test
import org.robolectric.annotation.Config

class InlineAudioRecorderCardScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun states(
        @TestParameter(valuesProvider = ThemeProvider::class) theme: Theme,
    ) = capture("inline_recorder_states", theme = theme) {
        InlineAudioRecorderStates(animated = theme != DayTheme.EINK)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun largeText() =
        capture("inline_recorder_states_large_text", fontScale = 2f) {
            InlineAudioRecorderStates(animated = true)
        }
}
