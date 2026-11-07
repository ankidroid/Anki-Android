// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitForPageCondition
import com.ichi2.anki.testutil.waitUntil
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the real page and the device's docked keyboard on a landscape phone.
 * Geometry assertions detect regressions; screenshots are saved for visual inspection in the
 * target app's external files directory under `screenshots/DeckOptionsKeyboardScreenshotTest`.
 * Unlike the Robolectric screenshots, these include Chromium's rendered content and the IME.
 */
class DeckOptionsKeyboardScreenshotTest : InstrumentedTest() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun landscapeKeyboardKeepsFocusedFieldAndSaveVisible() {
        ensureWebViewIsSupported()
        val intent = DeckOptions.getIntent(testContext, Consts.DEFAULT_DECK_ID)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            lateinit var page: DeckOptions
            waitUntil(message = { "landscape deck options did not become ready" }) {
                var ready = false
                scenario.onActivity {
                    page = it.fragment as DeckOptions
                    ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && page.webViewLayout.isVisible
                }
                ready
            }
            scenario.onActivity {
                assumeTrue("requires a compact phone window", it.resources.configuration.screenHeightDp < 480)
            }
            page.waitForPageCondition("document.querySelector('input[type=number]') !== null", "daily limit field did not load")
            capture("landscape_keyboard_closed")

            // A real tap reproduces Chromium's scrolling when the IME changes the viewport.
            // Do not scroll or focus the field with JavaScript: that could mask the regression.
            val position =
                JSONObject(
                    page.evaluate(
                        """
                        (() => {
                            const rect = document.querySelector('input[type=number]').getBoundingClientRect();
                            return { x: (rect.left + rect.right) / 2 * devicePixelRatio,
                                     y: (rect.top + rect.bottom) / 2 * devicePixelRatio };
                        })()
                        """.trimIndent(),
                    ),
                )
            val origin = IntArray(2)
            scenario.onActivity { page.webViewLayout.getLocationOnScreen(origin) }
            device.click(origin[0] + position.getInt("x"), origin[1] + position.getInt("y"))

            try {
                waitForKeyboard(scenario, visible = true)
                device.waitForIdle()
                scenario.onActivity { assertFalse(it.findViewById<View>(R.id.toolbar).isVisible) }
                page.waitForPageCondition(
                    """
                    (() => {
                        const input = document.activeElement;
                        const toolbar = document.querySelector('.sticky-container').getBoundingClientRect();
                        const field = input.getBoundingClientRect();
                        return input.matches('input[type=number]') && toolbar.top >= 0 &&
                            toolbar.bottom < innerHeight && field.top >= toolbar.bottom &&
                            field.bottom <= innerHeight;
                    })()
                    """.trimIndent(),
                    "focused field must be visible between the preset/Save bar and the keyboard",
                )
                // DOM bounds alone would miss the whole Android window panning off screen.
                scenario.onActivity {
                    page.webViewLayout.getLocationOnScreen(origin)
                    val insets = checkNotNull(ViewCompat.getRootWindowInsets(it.window.decorView))
                    assertTrue(origin[1] >= insets.getInsets(systemBars()).top, "page toolbar was panned off screen")
                    assertTrue(
                        origin[1] + page.webViewLayout.height <= it.window.decorView.height - insets.getInsets(ime()).bottom,
                        "page extends below the keyboard",
                    )
                }
            } finally {
                capture("landscape_keyboard_open")
            }

            device.pressBack()
            waitForKeyboard(scenario, visible = false)
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.toolbar).isVisible) }
            capture("landscape_keyboard_dismissed")
        }
    }

    private fun waitForKeyboard(
        scenario: ActivityScenario<SingleFragmentActivity>,
        visible: Boolean,
    ) {
        waitUntil(message = { "keyboard visibility did not become $visible" }) {
            var matches = false
            scenario.onActivity {
                matches = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(ime()) == visible
            }
            matches
        }
    }

    private fun DeckOptions.evaluate(script: String): String {
        val result = CompletableFuture<String>()
        instrumentation.runOnMainSync { webViewLayout.evaluateJavascript(script) { result.complete(it) } }
        return result.get(10, TimeUnit.SECONDS)
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val directory = File(testContext.getExternalFilesDir(null), "screenshots/DeckOptionsKeyboardScreenshotTest")
        directory.mkdirs()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
    }
}
