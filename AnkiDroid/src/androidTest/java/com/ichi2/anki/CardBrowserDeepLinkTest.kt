// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.waitUntil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sending the `anki://x-callback-url/browser?search=` deep link opens a search in [CardBrowser].
 */
@RunWith(AndroidJUnit4::class)
class CardBrowserDeepLinkTest : InstrumentedTest() {
    @get:Rule
    val storagePermission = GrantStoragePermission.instance

    @Test
    fun deepLinkOpensCardBrowserWithSearch() {
        addNoteUsingBasicNoteType("dog", "barks")
        addNoteUsingBasicNoteType("cat", "meows")

        // implicit VIEW intent, constrained to AnkiDroid so the OS resolves it without a chooser
        val deepLink =
            Intent(Intent.ACTION_VIEW, "anki://x-callback-url/browser?search=dog".toUri()).apply {
                setPackage(testContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        withCardBrowser(deepLink) { browser ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertThat(
                    "the deep link opens the browser on its search",
                    browser.viewModel.searchTerms,
                    equalTo("dog"),
                )
            }
        }
    }

    /**
     * The deep link opens [CardBrowser] standalone, so pressing 'back' closes it.
     */
    @Test
    fun backFromBrowserClosesTheActivity() {
        // TODO: make the 'back' experience consistent once we move to the M3 SearchBar
        addNoteUsingBasicNoteType("dog", "barks")

        val deepLink =
            Intent(Intent.ACTION_VIEW, "anki://x-callback-url/browser?search=dog".toUri()).apply {
                setPackage(testContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        withCardBrowser(deepLink) { browser ->
            // The SearchBar & keyboard may need to be collapsed before 'back' finishes the activity
            val deadline = SystemClock.uptimeMillis() + 3.seconds.inWholeMilliseconds
            while (!browser.isFinishing && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { browser.onBackPressedDispatcher.onBackPressed() }
                instrumentation.waitForIdleSync()
            }

            assertThat("'back' closes the browser", browser.isFinishing, equalTo(true))
            assertThat(
                "'back' does not surface DeckPicker beneath the browser",
                TestUtils.activityInstance,
                not(instanceOf(DeckPicker::class.java)),
            )
        }
    }

    /** Launches [deepLink] and runs [block] on the [CardBrowser] it launched. */
    private inline fun withCardBrowser(
        deepLink: Intent,
        block: (CardBrowser) -> Unit,
    ) {
        testContext.startActivity(deepLink)
        val browser = awaitResumed<CardBrowser>()
        try {
            block(browser)
        } finally {
            browser.finishAndAwaitDestruction()
        }
    }

    /** Keep the collection open until the activity can no longer access it. */
    private fun Activity.finishAndAwaitDestruction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { finishAndRemoveTask() }
        waitUntil(message = { "Timed out waiting for ${javaClass.simpleName} to be destroyed" }) {
            var destroyed = false
            instrumentation.runOnMainSync { destroyed = isDestroyed }
            destroyed
        }
    }

    /**
     * Polls until an activity of type [T] is resumed and returns it. Fails if [T] does not resume
     * within [timeout].
     */
    private inline fun <reified T : Activity> awaitResumed(timeout: Duration = 10.seconds): T {
        val deadline = SystemClock.uptimeMillis() + timeout.inWholeMilliseconds
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var activity = TestUtils.activityInstance as? T

        while (activity == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(50)
            activity = TestUtils.activityInstance as? T
        }
        return activity
            ?: fail("Timed out waiting for ${T::class.java.simpleName}; resumed = ${TestUtils.activityInstance}")
    }
}
