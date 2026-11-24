// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.annotation.SuppressLint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.themedContext
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogOutlineTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `message dialog has an e-ink outline`() = assertDialogOutline(input = false)

    @Test
    fun `text input dialog has an e-ink outline`() = assertDialogOutline(input = true)

    @SuppressLint("NewApi") // Robolectric runs this test on the app's target SDK.
    private fun assertDialogOutline(input: Boolean) {
        val context = DayTheme.EINK.themedContext(targetContext)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                AnkiDroidTheme {
                    if (input) {
                        TextInputDialog("Rename tag", "New name", "Rename", {}, {}, initialText = "Science")
                    } else {
                        MessageDialog("Delete tag", "Delete Science from all notes?", "Delete", {}, {})
                    }
                }
            }
        }
        val pixels = compose.onNode(isDialog()).captureToImage().toPixelMap()
        // Dialogs have a 24dp outer margin; sample the straight side of the 2dp frame.
        val x = with(compose.density) { 25.dp.roundToPx() }
        assertEquals(Color.Black, pixels[x, pixels.height / 2])
    }
}
