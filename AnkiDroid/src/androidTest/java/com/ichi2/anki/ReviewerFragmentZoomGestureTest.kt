// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** Tests tap gestures of [ReviewerFragment] while the card is zoomed in. */
@RunWith(AndroidJUnit4::class)
class ReviewerFragmentZoomGestureTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    private var testDeckId: DeckId = 0
    private val newReviewerOptionsKey get() = testContext.getString(R.string.new_reviewer_options_key)
    private val changedKeys = listOf(newReviewerOptionsKey, ViewerAction.SHOW_ANSWER.preferenceKey)
    private var originalValues: Map<String, Any?> = emptyMap()

    @Before
    fun setUp() {
        val preferences = testContext.sharedPrefs()
        originalValues = changedKeys.associateWith { preferences.all[it] }
        preferences.edit {
            putBoolean(newReviewerOptionsKey, true)
            putString(
                ViewerAction.SHOW_ANSWER.preferenceKey,
                listOf(ReviewerBinding.fromGesture(Gesture.TAP_TOP)).toPreferenceString(),
            )
        }
        testDeckId = col.decks.addNormalDeckWithName("ReviewerFragmentZoomGestureTest-${UUID.randomUUID()}").id
        col.decks.select(testDeckId)
    }

    @After
    fun tearDown() {
        testContext.sharedPrefs().edit {
            for ((key, value) in originalValues) {
                when (value) {
                    null -> remove(key)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                }
            }
        }
        col.decks.remove(listOf(testDeckId))
    }

    @Test
    fun tapTopWhileZoomedAndScrolledToBottomRight() {
        addNoteUsingBasicNoteType(TALL_CONTENT, "answer").firstCard(col).update { did = testDeckId }

        ActivityScenario.launch<CardViewerActivity>(ReviewerFragment.getIntent(testContext)).use { scenario ->
            lateinit var fragment: ReviewerFragment
            scenario.onActivity { activity -> fragment = activity.fragment as ReviewerFragment }
            val webView = fragment.binding.webViewLayout.getChildAt(0) as WebView

            waitUntil(timeout = 30.seconds, message = { "Question did not load" }) {
                webView.evaluateScript("document.body.textContent.includes('Tall card content')") == "true"
            }
            webView.pinchOut()
            // Pan towards the bottom right corner
            repeat(8) { webView.drag(fromX = 0.8f, fromY = 0.8f, toX = 0.2f, toY = 0.2f) }
            // ReviewerFragment ignores gestures for 300ms after scrolling
            Thread.sleep(1000)

            // Tap the top centre of the visible WebView
            webView.tapAt(xFraction = 0.5f, yFraction = 0.1f)
            waitUntil(message = { "TAP_TOP did not show the answer" }) { fragment.viewModel.showingAnswer.value }
        }
    }

    private fun onMainSync(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** Zooms in with a two-finger pinch, like a user would. */
    private fun WebView.pinchOut() {
        waitUntil(message = { "WebView did not become ready to zoom" }) {
            var canZoom = false
            @Suppress("DEPRECATION")
            onMainSync { canZoom = canZoomIn() }
            canZoom
        }
        val location = IntArray(2)
        onMainSync { getLocationOnScreen(location) }
        val cx = location[0] + width / 2f
        val cy = location[1] + height / 2f
        val steps = 20
        val start = 50f
        val end = width / 2.2f
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()

        fun event(
            action: Int,
            distance: Float,
            pointerCount: Int,
        ): MotionEvent {
            val props =
                Array(pointerCount) { i ->
                    MotionEvent.PointerProperties().apply {
                        id = i
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }
                }
            val coords =
                Array(pointerCount) { i ->
                    MotionEvent.PointerCoords().apply {
                        x = cx + if (i == 0) -distance else distance
                        y = cy + if (i == 0) -distance else distance
                        pressure = 1f
                        size = 1f
                    }
                }
            return MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, pointerCount, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        }
        val pointer1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        instrumentation.sendPointerSync(event(MotionEvent.ACTION_DOWN, start, 1))
        instrumentation.sendPointerSync(event(MotionEvent.ACTION_POINTER_DOWN or pointer1, start, 2))
        for (i in 1..steps) {
            instrumentation.sendPointerSync(event(MotionEvent.ACTION_MOVE, start + (end - start) * i / steps, 2))
            SystemClock.sleep(10)
        }
        instrumentation.sendPointerSync(event(MotionEvent.ACTION_POINTER_UP or pointer1, end, 2))
        instrumentation.sendPointerSync(event(MotionEvent.ACTION_UP, end, 1))
        waitUntil(message = { "WebView did not zoom in" }) {
            evaluateScript("window.visualViewport.scale").toDouble() > 1.5
        }
    }

    private fun WebView.drag(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
    ) {
        val location = IntArray(2)
        onMainSync { getLocationOnScreen(location) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val steps = 20

        fun point(fraction: Float) =
            Pair(
                location[0] + width * (fromX + (toX - fromX) * fraction),
                location[1] + height * (fromY + (toY - fromY) * fraction),
            )

        fun send(
            action: Int,
            fraction: Float,
        ) {
            val (x, y) = point(fraction)
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        for (i in 1..steps) {
            send(MotionEvent.ACTION_MOVE, i / steps.toFloat())
            SystemClock.sleep(10)
        }
        // hold still before lifting to avoid a fling
        SystemClock.sleep(200)
        send(MotionEvent.ACTION_MOVE, 1f)
        send(MotionEvent.ACTION_UP, 1f)
        SystemClock.sleep(200)
    }

    private fun WebView.tapAt(
        xFraction: Float,
        yFraction: Float,
    ) {
        val location = IntArray(2)
        onMainSync { getLocationOnScreen(location) }
        val x = location[0] + width * xFraction
        val y = location[1] + height * yFraction
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        instrumentation.sendPointerSync(down)
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        instrumentation.sendPointerSync(up)
        down.recycle()
        up.recycle()
    }

    /** Evaluates [script] on the UI thread and returns its JSON-encoded result. */
    private fun WebView.evaluateScript(script: String): String {
        val result = CompletableDeferred<String>()
        onMainSync { evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }

    companion object {
        private const val TALL_CONTENT = "<div style='height: 3000px'>Tall card content</div>"

        @JvmStatic
        @BeforeClass
        fun checkWebView() = ensureWebViewIsSupported()
    }
}
