// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
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
            captureScreen("keyboard_open")

            (activity.window.decorView as ViewGroup).removeView(keyboard)
            activity.dispatchInsets(navBarBottom = 48.dp)
            advanceRobolectricLooper()
            captureScreen("keyboard_dismissed")
        }

    @Test
    fun landscapeKeyboard() {
        RuntimeEnvironment.setQualifiers("+land")
        withPagePlaceholder { activity ->
            activity.simulateKeyboard(keyboardHeight = 180.dp, navBarBottom = 0.dp)
            val insets =
                with(activity) {
                    windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp, imeBottom = 180.dp)
                }
            activity.window.simulateSystemBars(insets)
            captureScreen("landscape_keyboard")
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
