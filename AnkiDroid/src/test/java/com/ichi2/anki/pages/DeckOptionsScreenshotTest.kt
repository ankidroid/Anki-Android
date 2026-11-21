// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.SingleFragmentScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.libanki.Consts
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.windowInsetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Screenshot tests for [DeckOptions]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.DeckOptionsScreenshotTest"`
 */
class DeckOptionsScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = DeckOptions.getIntent(targetContext, Consts.DEFAULT_DECK_ID)

    @Test
    fun keyboard() =
        withPagePlaceholder { activity ->
            activity.simulateSystemBars()
            captureScreen("keyboard_closed")

            val keyboard = activity.simulateKeyboard()
            assertTrue(activity.findViewById<View>(R.id.toolbar).isVisible)
            captureScreen("keyboard_open")

            (activity.window.decorView as ViewGroup).removeView(keyboard)
            activity.dispatchInsets(navBarBottom = 48.dp)
            advanceRobolectricLooper()
            captureScreen("keyboard_dismissed")
        }

    @Test
    fun landscapeKeyboard() {
        // A phone with a tall landscape keyboard must still leave room to edit the page.
        RuntimeEnvironment.setQualifiers("+w952dp-h426dp-land")
        withPagePlaceholder { activity ->
            val keyboard = activity.simulateKeyboard(keyboardHeight = 275.dp, navBarBottom = 0.dp)
            val insets =
                with(activity) {
                    windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp, imeBottom = 275.dp)
                }
            activity.window.simulateSystemBars(insets)
            assertFalse(activity.findViewById<View>(R.id.toolbar).isVisible)
            captureScreen("landscape_keyboard")

            (activity.window.decorView as ViewGroup).removeView(keyboard)
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)
            advanceRobolectricLooper()
            assertTrue(activity.findViewById<View>(R.id.toolbar).isVisible)
            captureScreen("landscape_keyboard_dismissed")
        }
    }

    /** Robolectric does not render WebViews: a labelled placeholder shows the page's bounds. */
    private fun withPagePlaceholder(block: (SingleFragmentActivity) -> Unit) =
        withScreen { activity ->
            val fragment = activity.fragment as DeckOptions
            fragment.onWebViewReady()
            fragment.webViewLayout.addView(
                TextView(activity).apply {
                    text = "WebView (not rendered)"
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(0x21, 0x21, 0x21))
                    setBackgroundColor(Color.rgb(0xFF, 0xF8, 0xE1))
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            advanceRobolectricLooper()
            block(activity)
        }
}
