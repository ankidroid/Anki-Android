// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import android.annotation.SuppressLint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.CommonString
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.themedContext
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IncrementerNumberPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @SuppressLint("NewApi") // Robolectric runs this test on API 36.
    fun `night E-Ink icons contrast with enabled and disabled buttons`() {
        val context = NightTheme.EINK.themedContext(ApplicationProvider.getApplicationContext())
        val state = NumberPickerState(5, 99999, "30")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                AnkiDroidTheme { IncrementerNumberPicker(state) }
            }
        }
        for (text in listOf("30", "5", "99999")) {
            compose.runOnIdle { state.input.edit { replace(0, length, text) } }
            for (label in listOf(CommonString.minus_sign, CommonString.plus_sign)) {
                val pixels =
                    compose
                        .onNodeWithContentDescription(context.getString(label))
                        .captureToImage()
                        .toPixelMap()
                assertEquals(Color.White, pixels[pixels.width / 2, pixels.height / 4], "Button background")
                assertEquals(Color.Black, pixels[pixels.width / 2, pixels.height / 2], "Button icon")
            }
        }
    }
}
