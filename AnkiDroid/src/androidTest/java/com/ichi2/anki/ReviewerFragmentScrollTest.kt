// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.workarounds.SafeWebViewLayout
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
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Tests scroll behaviour of [ReviewerFragment]. */
@RunWith(AndroidJUnit4::class)
class ReviewerFragmentScrollTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    private var testDeckId: DeckId = 0
    private val newReviewerOptionsKey get() = testContext.getString(R.string.new_reviewer_options_key)
    private var originalNewReviewerOptions: Boolean? = null

    @Before
    fun setUp() {
        val preferences = testContext.sharedPrefs()
        originalNewReviewerOptions = preferences.all[newReviewerOptionsKey] as Boolean?
        preferences.edit { putBoolean(newReviewerOptionsKey, true) }
        testDeckId = col.decks.addNormalDeckWithName("ReviewerFragmentScrollTest-${UUID.randomUUID()}").id
        col.decks.select(testDeckId)
    }

    @After
    fun tearDown() {
        testContext.sharedPrefs().edit {
            val original = originalNewReviewerOptions
            if (original == null) remove(newReviewerOptionsKey) else putBoolean(newReviewerOptionsKey, original)
        }
        col.decks.remove(listOf(testDeckId))
    }

    /** Issue 5182: revealing a cloze without an answer anchor must preserve the viewport. */
    @Test
    fun showingAnswerPreservesScrollPosition() {
        addTallClozeCard("answer")

        withReviewer {
            waitForCloze("[...]")
            scrollDown()
            clickShowAnswer()
            waitForCloze("answer")
            awaitAnimationFrames()

            assertEquals(SCROLL_POSITION.toDouble(), pageScrollY, "scroll position after revealing the cloze")
        }
    }

    @Test
    fun showingAnswerResetsZoomAndPreservesScrollPosition() {
        addTallClozeCard("answer")

        withReviewer {
            waitForCloze("[...]")
            val originalScale = pageScale
            zoomIn()
            scrollDown()
            clickShowAnswer()
            waitForCloze("answer")
            waitForScale(originalScale)
            awaitAnimationFrames()

            assertEquals(SCROLL_POSITION.toDouble(), pageScrollY, "scroll position after resetting zoom on card flip")
        }
    }

    @Test
    fun answerAnchorTakesPrecedenceAfterResettingZoom() {
        addNoteUsingBasicNoteType("${SPACER}question", "answer$SPACER").firstCard(col).update { did = testDeckId }

        withReviewer {
            waitUntil(timeout = 30.seconds, message = { "Question did not load" }) {
                evaluateScript("document.body.textContent.includes('question')") == "true"
            }
            val originalScale = pageScale
            zoomIn()
            scrollDown()
            clickShowAnswer()
            waitForScale(originalScale)
            awaitAnimationFrames()

            waitUntil(message = { "Answer anchor was not scrolled into view after resetting zoom" }) {
                evaluateScript("Math.abs(document.getElementById('answer')?.getBoundingClientRect().top) <= 1") == "true"
            }
        }
    }

    @Test
    fun answerAnchorTakesPrecedenceOverScrollPosition() {
        addNoteUsingBasicNoteType("${SPACER}question", "answer$SPACER").firstCard(col).update { did = testDeckId }

        withReviewer {
            waitUntil(timeout = 30.seconds, message = { "Question did not load" }) {
                evaluateScript("document.body.textContent.includes('question')") == "true"
            }
            scrollDown()
            clickShowAnswer()

            waitUntil(message = { "Answer anchor was not scrolled into view" }) {
                evaluateScript("Math.abs(document.getElementById('answer')?.getBoundingClientRect().top) <= 1") == "true"
            }
        }
    }

    @Test
    fun nextQuestionStartsAtTop() = checkNextQuestionStartsAtTop(zoom = false)

    @Test
    fun nextQuestionResetsZoomAndStartsAtTop() = checkNextQuestionStartsAtTop(zoom = true)

    private fun checkNextQuestionStartsAtTop(zoom: Boolean) {
        addTallClozeCard("first")
        addTallClozeCard("second")

        withReviewer {
            waitForCloze("[...]")
            clickShowAnswer()
            waitUntil(message = { "Answer did not load" }) {
                evaluateScript("['first', 'second'].includes(document.querySelector('.cloze')?.textContent)") == "true"
            }
            val originalScale = pageScale
            if (zoom) zoomIn()
            scrollDown()
            onView(withId(R.id.good_button)).perform(click())
            waitForCloze("[...]")
            waitForScale(originalScale)
            awaitAnimationFrames()

            assertEquals(0.0, pageScrollY, "scroll position on the next question")
        }
    }

    private fun addTallClozeCard(answer: String) {
        addClozeNote("$SPACER{{c1::$answer}}$SPACER").firstCard(col).update { did = testDeckId }
    }

    private fun withReviewer(block: SafeWebViewLayout.() -> Unit) {
        ActivityScenario.launch<CardViewerActivity>(ReviewerFragment.getIntent(testContext)).use { scenario ->
            lateinit var webViewLayout: SafeWebViewLayout
            scenario.onActivity { activity ->
                val fragment = activity.fragment as ReviewerFragment
                webViewLayout = fragment.binding.webViewLayout
            }
            webViewLayout.block()
        }
    }

    private fun clickShowAnswer() {
        onView(withId(R.id.show_answer_button)).perform(click())
    }

    private fun SafeWebViewLayout.waitForCloze(text: String) {
        waitUntil(timeout = 30.seconds, message = { "Cloze did not display '$text'" }) {
            evaluateScript("document.querySelector('.cloze')?.textContent") == "\"$text\""
        }
    }

    private fun SafeWebViewLayout.scrollDown() {
        evaluateScript("window.scrollTo(0, $SCROLL_POSITION)")
        waitUntil(message = { "Card did not scroll to $SCROLL_POSITION" }) {
            pageScrollY == SCROLL_POSITION.toDouble()
        }
    }

    private val SafeWebViewLayout.pageScrollY: Double
        get() = evaluateScript("window.scrollY").toDouble()

    private val SafeWebViewLayout.pageScale: Double
        get() = evaluateScript("window.visualViewport.scale").toDouble()

    private fun SafeWebViewLayout.zoomIn() {
        val originalScale = pageScale
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            zoomBy(2f)
        }
        waitForScale(originalScale * 2)
    }

    private fun SafeWebViewLayout.waitForScale(expectedScale: Double) {
        waitUntil(message = { "Expected page scale $expectedScale, got $pageScale" }) {
            abs(pageScale - expectedScale) < 0.01
        }
    }

    /** Allow queued layout and scroll changes to finish before asserting the viewport position. */
    private fun SafeWebViewLayout.awaitAnimationFrames() {
        evaluateScript(
            """
            window.scrollTestReady = false;
            requestAnimationFrame(() => requestAnimationFrame(() => { window.scrollTestReady = true; }));
            """.trimIndent(),
        )
        waitUntil(message = { "WebView did not render" }) {
            evaluateScript("window.scrollTestReady") == "true"
        }
    }

    /** Evaluates [script] on the UI thread and returns its JSON-encoded result. */
    private fun SafeWebViewLayout.evaluateScript(script: String): String {
        val result = CompletableDeferred<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            evaluateJavascript(script) { result.complete(it) }
        }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }

    companion object {
        private const val SCROLL_POSITION = 800

        /** Filler which makes a card taller than the viewport, so that it can be scrolled. */
        private const val SPACER = "<div style='height: 1500px'>Tall card content</div>"

        @JvmStatic
        @BeforeClass
        fun checkWebView() = ensureWebViewIsSupported()
    }
}
