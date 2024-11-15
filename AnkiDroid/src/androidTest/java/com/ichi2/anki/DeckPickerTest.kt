/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.anki

import android.annotation.SuppressLint
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ichi2.anki.TestUtils.activityInstance
import com.ichi2.anki.TestUtils.isTablet
import com.ichi2.anki.TestUtils.wasBuiltOnCI
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.createDeckWithUniqueName
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.discardPreliminaryViews
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.tapOnCountLayouts
import org.hamcrest.Matchers.instanceOf
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@SuppressLint("DirectSystemCurrentTimeMillisUsage")
class DeckPickerTest : InstrumentedTest() {
    @get:Rule
    val activityRule = ActivityScenarioRule(DeckPicker::class.java)

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun before() {
        disableIntroductionSlide()
    }

    @Ignore("This test appears to be flaky everywhere")
    @Test
    fun checkIfClickOnCountsLayoutOpensStudyOptionsOnMobile() {
        // Run the test only on emulator.
        assumeTrue(isEmulator())
        assumeFalse("Test flaky in CI - #9282, skipping", wasBuiltOnCI())

        // For mobile. If it is not a mobile, then test will be ignored.
        assumeTrue(!isTablet)
        val deckName = createDeckWithCard()

        // Go to RecyclerView item having "Test Deck" and click on the counts layout
        tapOnCountLayouts(deckName)

        // Check if currently open Activity is StudyOptionsActivity
        assertThat(
            activityInstance,
            instanceOf(StudyOptionsActivity::class.java)
        )
    }

    @Test
    fun checkIfStudyOptionsIsDisplayedOnTablet() {
        // Run the test only on emulator.
        assumeTrue(isEmulator())
        assumeFalse("Test flaky in CI - #9282, skipping", wasBuiltOnCI())

        // For tablet. If it is not a tablet, then test will be ignored.
        assumeTrue(isTablet)
        discardPreliminaryViews()
        createDeckWithCard()

        // Check if currently open Fragment is StudyOptionsFragment
        onView(withId(R.id.studyoptions_fragment))
            .check(ViewAssertions.matches(isDisplayed()))
    }

    private fun createDeckWithCard(): String {
        // Create a new deck
        val deckName = createDeckWithUniqueName()

        // The deck is currently empty, so if we tap on it, it becomes the selected deck but doesn't enter
        tapOnCountLayouts(deckName)

        // Create a card belonging to the new deck, using Basic type (guaranteed to exist)
        onView(withId(R.id.fab_main)).perform(click())
        onView(withId(R.id.fab_main)).perform(click())

        // Close the keyboard, it auto-focuses and obscures enough of the screen
        // on some devices that espresso complains about global visibility being <90%
        closeSoftKeyboard()
        onView(withId(R.id.note_type_spinner)).perform(click())
        onView(withText("Basic")).perform(click())
        onView(withContentDescription("Front"))
            .perform(typeText("SampleText"))
        onView(withId(R.id.action_save)).perform(click())
        closeSoftKeyboard()

        // Go back to Deck Picker
        pressBack()
        return deckName
    }
}
