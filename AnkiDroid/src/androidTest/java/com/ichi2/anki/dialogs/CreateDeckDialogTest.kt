/****************************************************************************************
 * Copyright (c) 2024 neeldoshii <neeldoshi147@gmail.com.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.dialogs

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import org.junit.Rule
import org.junit.Test

class CreateDeckDialogTest : InstrumentedTest() {

    @get:Rule
    val activityRule = ActivityScenarioRule(DeckPicker::class.java)

    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission, notificationPermission)

    @Test
    fun createDeckShowSnackbar() {
        // Creates new Deck
        onView(withId(R.id.fab_main)).perform(click())
        onView(withId(R.id.add_deck_action)).perform(click())
        onView(withId(R.id.dialog_text_input))
            .perform(ViewActions.typeText("TestDeck"))
        onView(withText(R.string.dialog_ok)).perform(click())
        closeSoftKeyboard()

        // Check Snackbar is displayed after successful action
        onView(withText(R.string.deck_created))
            .check(matches(isDisplayed()))
    }
}
