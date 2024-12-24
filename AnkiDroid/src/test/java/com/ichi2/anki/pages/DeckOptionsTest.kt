/*
 *  Copyright (c) 2024 Chen Shanhan <c.shanhan3@gmail.com>
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

import android.content.DialogInterface
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class DeckOptionsTest : RobolectricTest() {
    @Test
    fun `When pressing the back button, the activity should finish`() {
        // Launch deck options
        val intent = DeckOptions.getIntent(targetContext, col.decks.selected())

        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertThat("Activity should not be finishing", !activity.isFinishing)

                // Perform system-level back press
                pressBack()

                // Discard on modal
                clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)

                assertThat("Activity should be finishing", activity.isFinishing)
            }
        }
    }

    @Test
    fun `When pressing the toolbar up button, the activity should finish`() {
        // Launch deck options
        val intent = DeckOptions.getIntent(targetContext, col.decks.selected())

        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertThat("Activity should not be finishing", !activity.isFinishing)
            }

            // Perform toolbar up button press
            try {
                onView(withContentDescription("Navigate up")).perform(click())
            } catch (ex: NoMatchingViewException) {
                Timber.d("Toolbar UP button not found. Is english locale being used?")
                return // Abort
            }

            scenario.onActivity { activity ->
                // Discard on modal
                clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
                assertThat("Activity should be finishing", activity.isFinishing)
            }
        }
    }
}
