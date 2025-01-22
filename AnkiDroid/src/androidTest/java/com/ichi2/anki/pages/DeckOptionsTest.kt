/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.pages

import androidx.lifecycle.Lifecycle.State
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.findFragmentById
import com.ichi2.libanki.Consts
import com.ichi2.testutils.common.assertTrueWithTimeout
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.test.assertEquals

/** Tests [DeckOptions] */
@RunWith(AndroidJUnit4::class)
class DeckOptionsTest : InstrumentedTest() {
    @get:Rule
    val activityRule =
        ActivityScenarioRule<SingleFragmentActivity>(
            DeckOptions.getIntent(testContext, Consts.DEFAULT_DECK_ID),
        )

    @Test
    fun discardChangesIsNotShownIfNoChanges() = testDeckOptions { assertBackPressClosesOptions() }

    @Test
    fun discardChangesIsShownIfChangeMade() {
        testDeckOptions { makeChange() }

        Espresso.pressBack()

        onView(withText("Discard current input?"))
            .inRoot(isDialog())
            .check(matches(isEnabled()))
    }

    @Test
    @Ignore("broken on main")
    fun discardChangesIsNotShownIfChangeIsReversed() =
        testDeckOptions {
            makeChangeAndUndo()
            assertBackPressClosesOptions()
        }

    /**
     * Runs [block] with [DeckOptions] as the receiver. Intended for test setup
     *
     * `onView` should not be called inside this method, instead call it afterwards
     */
    private fun testDeckOptions(block: (DeckOptions).() -> Unit) {
        assertEquals(State.RESUMED, activityRule.scenario.state)
        activityRule.scenario.onActivity { activity ->
            block(activity.findFragmentById<DeckOptions>(R.id.fragment_container))
        }
    }
}

private fun DeckOptions.assertBackPressClosesOptions() {
    // we can't use Espresso.pressBackUnconditionally() as this may be called inside onActivity
    requireActivity().onBackPressedDispatcher.onBackPressed()
    // we assert this as [actuallyClose] launches a task
    assertTrueWithTimeout("fragment closing") { isClosingFragment }
}

/** Changes an option, so there are unsaved changes */
private fun DeckOptions.makeChange() {
    toggleFsrs()
}

/** Changes an option, and undoes the change so there are no unsaved changes */
private fun DeckOptions.makeChangeAndUndo() {
    toggleFsrs()
    toggleFsrs()
}

private fun DeckOptions.toggleFsrs() {
    val js =
        """
        // Find the FSRS heading. This is not translated. Exclude the FSRS modal. 
        const element = Array.from(document.getElementsByTagName("h1")).filter(x => x.innerText == "FSRS" && !x.classList.contains("modal-title"))[0];
        // Find the 'FSRS' container
        const container = element.closest(".container");
        // Find the only checkbox, and click it
        container.querySelectorAll('input[type="checkbox"]')[0].click()
        """.trimIndent()

    fun execToggleFsrsJs() = this.webView.evaluateJavascript(js) {}

    if (pageWebViewClient.callbacksExecuted) {
        Timber.v("scheduling JS callback: 'toggleFsrs'")
        execToggleFsrsJs()
    } else {
        Timber.v("scheduling JS callback: 'toggleFsrs'")
        pageWebViewClient.onPageFinishedCallbacks.add { execToggleFsrsJs() }
    }
}
