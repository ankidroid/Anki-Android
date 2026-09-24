// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.testutils.ext.defaultDeckNewCardsPerDay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DeckOptionsTest : InstrumentedTest() {
    private val doubleTapIntervalKey get() = testContext.getString(R.string.double_tap_timeout_pref_key)
    private var fsrsWasEnabled = false

    @Before
    fun enableFsrs() {
        // the parameters are only displayed when FSRS is enabled
        fsrsWasEnabled = col.config.get<Boolean>("fsrs") ?: false
        col.config.set("fsrs", true)
    }

    @After
    fun restoreState() {
        col.config.set("fsrs", fsrsWasEnabled)
        testContext.sharedPrefs().edit { remove(doubleTapIntervalKey) }
    }

    @Test
    fun pageExposesParameterUnlockClickTimeout() {
        withDeckOptions {
            assertEquals("\"function\"", evaluateJavascript("typeof anki.setParameterUnlockClickTimeoutMs"))
            assertEquals("\"number\"", evaluateJavascript("typeof anki.defaultParameterUnlockClickTimeoutMs"))
        }
    }

    @Test
    fun raisedDoubleTapIntervalUnlocksParametersWithSlowerTaps() {
        testContext.sharedPrefs().edit { putInt(doubleTapIntervalKey, 1000) }
        withDeckOptions {
            assertTrue(tapParametersThreeTimes(gapMs = 750), "parameters unlocked")
        }
    }

    @Test
    fun defaultDoubleTapIntervalKeepsPageTimeout() {
        withDeckOptions {
            assertFalse(tapParametersThreeTimes(gapMs = 750), "parameters remain locked")
        }
    }

    @Test
    fun optimizingAllPresetsSavesAndReloadsOptions() {
        withDeckOptions {
            saveAndOptimize(newPerDay = 43)

            waitUntil(timeout = 30.seconds, message = { "optimization did not save the changed limit" }) {
                col.defaultDeckNewCardsPerDay == 43
            }
            waitUntil(timeout = 30.seconds, message = { "options did not reload after optimization" }) {
                evaluateJavascript(
                    "globalThis.beforeOptimization === undefined && Array.from(document.querySelectorAll('input[type=number]')).find(input => input.offsetParent !== null)?.value === '43'",
                ) ==
                    "true"
            }
            assertFalse(requireActivity().isFinishing)
        }
    }

    private fun DeckOptions.saveAndOptimize(newPerDay: Int) {
        val script =
            """
            globalThis.beforeOptimization = true;
            window.confirm = () => true;
            const input = Array.from(document.querySelectorAll('input[type="number"]')).find(input => input.offsetParent !== null);
            input.focus();
            input.value = '$newPerDay';
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
            Array.from(document.querySelectorAll('button'))
                .find(button => button.textContent.trim() === ${JSONObject.quote(TR.deckConfigSaveAndOptimize())}).click();
            """.trimIndent()
        // The action navigates away from its JavaScript context, which can discard the
        // evaluation callback. Observe the persisted settings and the new page instead.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webViewLayout.evaluateJavascript(script)
        }
    }

    private fun withDeckOptions(block: DeckOptions.() -> Unit) {
        val intent = DeckOptions.getIntent(testContext, Consts.DEFAULT_DECK_ID)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            lateinit var fragment: DeckOptions
            scenario.onActivity { fragment = it.fragment as DeckOptions }
            // the WebView is shown once the page reports it is ready, which also sets the timeout
            waitUntil(timeout = 30.seconds, message = { "deck options did not become ready" }) {
                var ready = false
                scenario.onActivity { ready = fragment.webViewLayout.isVisible }
                ready
            }
            block(fragment)
        }
    }

    /**
     * Taps the FSRS parameters three times, [gapMs] apart
     *
     * @return whether the parameters were unlocked
     */
    private fun DeckOptions.tapParametersThreeTimes(gapMs: Int): Boolean {
        evaluateJavascript(
            """
            globalThis.ankidroidTest = undefined;
            (async () => {
                try {
                    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
                    const parameters = document.querySelector('[aria-label="FSRS Parameters"]');
                    const textarea = parameters.querySelector("textarea");
                    for (let tap = 0; tap < 3; tap++) {
                        if (tap > 0) await sleep($gapMs);
                        parameters.click();
                    }
                    await sleep(100); // allow the DOM to update
                    globalThis.ankidroidTest = { unlocked: !textarea.disabled };
                } catch (e) {
                    globalThis.ankidroidTest = { error: String(e) };
                }
            })();
            """.trimIndent(),
        )
        waitUntil(message = { "tap sequence did not complete" }) {
            evaluateJavascript("globalThis.ankidroidTest !== undefined") == "true"
        }
        assertEquals("null", evaluateJavascript("globalThis.ankidroidTest.error ?? null"), "tap sequence failed")
        return evaluateJavascript("globalThis.ankidroidTest.unlocked") == "true"
    }

    /** Evaluates [script] in the WebView, returning the JSON-encoded result */
    private fun DeckOptions.evaluateJavascript(script: String): String {
        val result = CompletableDeferred<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webViewLayout.evaluateJavascript(script) { result.complete(it) }
        }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }
}
