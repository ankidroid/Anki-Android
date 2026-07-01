// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.dp
import org.junit.Test

/**
 * Screenshot tests for [CardBrowser]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.CardBrowserScreenshotTest"`
 */
class CardBrowserScreenshotTest : ScreenshotTest() {
    init {
        setPhoneQualifiers()
    }

    @Test
    fun cardBrowserWith30Notes() =
        withCardBrowser(noteCount = 50) { browser ->
            // Robolectric reports zero system-bar insets by default. Inject realistic ones
            // so the app's edge-to-edge layout responds as it would on a real device.
            val navBarHeight = 48.dp
            val insets =
                with(targetContext) {
                    WindowInsetsCompat
                        .Builder()
                        .setInsets(statusBars(), insetsOf(top = 24.dp))
                        .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                        .build()
                }
            ViewCompat.dispatchApplyWindowInsets(browser.window.decorView, insets)

            // overlay a translucent band where the nav bar would sit
            // to see if content is drawn underneath it
            val decor = browser.window.decorView as ViewGroup
            val navBarOverlay =
                View(browser).apply {
                    setBackgroundColor(0x80000000.toInt())
                }
            decor.addView(
                navBarOverlay,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    navBarHeight.toPx(targetContext),
                    Gravity.BOTTOM,
                ),
            )

            captureScreen("30_notes")
        }
}
