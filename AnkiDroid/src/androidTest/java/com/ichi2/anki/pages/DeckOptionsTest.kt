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
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.findFragmentById
import com.ichi2.libanki.Consts
import com.ichi2.testutils.common.assertTrueWithTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

    /**
     * Runs [block] with [DeckOptions] as the receiver. Intended for tests
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
