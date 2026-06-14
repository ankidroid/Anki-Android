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
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.ui.RecyclerFastScroller
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
            browser.simulateNavigationBar()
            captureScreen("30_notes")
        }

    /**
     * The state described in the 'Handling Corners' plan: when scrolled to the bottom, the last
     * row, the bottom of the scroll track and the bottom of the scroll handle all rest on the
     * same line, above the navigation bar/rounded corners.
     */
    @Test
    fun cardBrowserScrolledToBottom() =
        withCardBrowser(noteCount = 50) { browser ->
            browser.simulateNavigationBar()

            val list = browser.findViewById<RecyclerView>(R.id.card_browser_list)
            list.scrollToPosition(49)
            while (list.canScrollVertically(1)) list.scrollBy(0, 50)
            advanceRobolectricLooper()

            // keep the auto-hiding fast scroller visible for the capture
            browser.findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()

            captureScreen("scrolled_to_bottom")
        }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun CardBrowser.simulateNavigationBar() {
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
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
    }
}
