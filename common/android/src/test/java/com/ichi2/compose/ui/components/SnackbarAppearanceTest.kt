// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.settings.enums.DayTheme
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
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnackbarAppearanceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @SuppressLint("NewApi") // Robolectric's @Config runs this test on API 36.
    fun `snackbar shadow stays close to its surface`() {
        show(DayTheme.LIGHT)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // A one-line snackbar is 48dp tall, centered in a 100dp white container.
        // Its shadow should not darken content 8dp below the surface.
        assertEquals(Color.White, pixels[160, 82])
    }

    @Test
    @SuppressLint("NewApi") // Robolectric's @Config runs this test on API 36.
    fun `e-ink snackbar has a black outline around its white surface`() {
        show(DayTheme.EINK)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // The snackbar has a 12dp outer margin and a 2dp theme stroke.
        assertEquals(Color.Black, pixels[13, 50])
        assertEquals(Color.White, pixels[16, 50])
    }

    private fun show(theme: DayTheme) {
        val context = theme.themedContext(ApplicationProvider.getApplicationContext())
        val host = SnackbarHostState()
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                AnkiDroidTheme {
                    Box(Modifier.size(320.dp, 100.dp).background(Color.White), contentAlignment = Alignment.Center) {
                        SnackbarHost(host)
                    }
                }
            }
            LaunchedEffect(host) { host.showSnackbar("Message", duration = SnackbarDuration.Indefinite) }
        }
        compose.waitForIdle()
    }
}
