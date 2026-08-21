// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.dp
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for the legacy [Reviewer]
 *
 * The card itself is a WebView, which Robolectric renders as a blank area: a placeholder fills
 * the card container so the captures show the card's bounds. The tests cover the native chrome
 * around it (toolbar, counts bar and answer buttons)
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.ReviewerScreenshotTest"`
 */
class ReviewerScreenshotTest : ScreenshotTest() {
    // rendering a card requires a media folder
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    /** translucent black, marking where a system bar or camera cutout sits over the content */
    private val overlayColor = Color.argb(0x80, 0x00, 0x00, 0x00)

    @Test
    fun question() =
        withReviewer { reviewer ->
            reviewer.simulateNavigationBar()
            captureScreen("question")
        }

    @Test
    fun answer() =
        withReviewer { reviewer ->
            reviewer.displayCardAnswer()
            advanceRobolectricLooper()
            reviewer.simulateNavigationBar()
            captureScreen("answer")
        }

    @Test
    fun answerButtonsAtTop() {
        targetContext.sharedPrefs().edit { putString("answerButtonPosition", "top") }
        withReviewer { reviewer ->
            reviewer.simulateNavigationBar()
            captureScreen("answer_buttons_top")
        }
    }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side.
     */
    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withReviewer { reviewer ->
            reviewer.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    private fun withReviewer(block: (Reviewer) -> Unit) {
        addBasicNote("Hello", "World")
        val reviewer = ReviewerTest.startReviewer(this)
        advanceRobolectricLooper()
        reviewer.showCardPlaceholder()
        block(reviewer)
    }

    /**
     * Robolectric renders the card's WebView as a blank area: fill the card container with a
     * labelled placeholder, so the captures show the card's bounds.
     */
    private fun Reviewer.showCardPlaceholder() {
        findViewById<FrameLayout>(R.id.flashcard).addView(
            TextView(this).apply {
                text = "WebView (not rendered)"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(0x21, 0x21, 0x21))
                setBackgroundColor(Color.rgb(0xFF, 0xF8, 0xE1))
            },
        )
    }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun Reviewer.simulateNavigationBar() {
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
                setBackgroundColor(overlayColor)
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

    /**
     * As [simulateNavigationBar], but for landscape with 3-button navigation: the navigation bar
     * is a side inset, with the camera cutout on the opposite side.
     */
    private fun Reviewer.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(left = 32.dp))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(overlayColor)
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                navBarWidth.toPx(targetContext),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END,
            ),
        )
    }
}
