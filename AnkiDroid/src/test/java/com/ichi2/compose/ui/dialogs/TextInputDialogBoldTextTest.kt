// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.graphics.Typeface
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Config.TARGET_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextInputDialogBoldTextTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun `bold text setting makes the typed text bold`() {
        // Settings > Accessibility > Bold text
        compose.activity.resources.configuration.fontWeightAdjustment = 300
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog("Rename profile", "Profile name", "Rename", {}, {})
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("Work")
        val layouts = mutableListOf<TextLayoutResult>()
        field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val input = layouts.first().layoutInput
        assertEquals("Work", input.text.text)
        val style = input.style
        val typeface =
            input.fontFamilyResolver
                .resolve(
                    style.fontFamily,
                    style.fontWeight ?: FontWeight.Normal,
                    style.fontStyle ?: FontStyle.Normal,
                    style.fontSynthesis ?: FontSynthesis.All,
                ).value as Typeface
        assertEquals(FontWeight.Bold.weight, typeface.weight)
    }
}
