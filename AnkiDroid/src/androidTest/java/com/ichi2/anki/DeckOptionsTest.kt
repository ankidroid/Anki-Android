/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2024 Arthur Milchior <arthur@milchior.fr>                              *
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

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withResourceName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ichi2.anki.TestUtils.wasBuiltOnCI
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.createDeckWithUniqueName
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.discardPreliminaryViews
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import org.hamcrest.Matchers.containsString
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeckOptionsTest : InstrumentedTest() {
    // using the deck picker as starting point, because we test opening and closing the deck options
    // and the deck picker is in charge of deciding whether the options can be dismissed or not.
    @get:Rule
    val activityRule = ActivityScenarioRule(DeckPicker::class.java)

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Before
    fun before() {
        assumeFalse("Test flaky in CI - #9282, skipping", wasBuiltOnCI())
        disableIntroductionSlide()
        discardPreliminaryViews()
    }

    @Test
    fun testDeckOptionsWithoutChangeClose() {
        val deckName = createDeckWithUniqueName()
        openDeckOptions(deckName)

        // Close without change
        pressBack()
        assertWebViewDoesNotExists()
    }

    @Test
    fun testDeckOptionsWithChangeShowAlert() {
        val deckName = createDeckWithUniqueName()
        // Do change, back and cancel
        openDeckOptions(deckName)
        performArbitraryOptionsChange()
        pressBack()
        // If the click succeed, it means the alert was correctly displayed.
        onView(withText(CollectionManager.TR.addingKeepEditing())).perform(click())
        assertWebViewIsDisplayed()
        // Back and confirm
        pressBack()
        // If the click succeed, it means the alert was correctly displayed.
        onView(withText(R.string.discard)).perform(click())
        assertWebViewDoesNotExists()
    }

    @Test
    fun testDeckOptionsWithChangeCanSave() {
        val deckName = createDeckWithUniqueName()
        openDeckOptions(deckName)
        onWebView().withElement(findElement(Locator.CLASS_NAME, "save")).perform(webClick())
        // We must wait to let the webview close. It seems that espresso does not wait for espresso-web to be done.
        assertWebViewDoesNotExists()
    }

    /**
     * Do an arbitrary change in the deck options. Assumes the deck options are displayed.
     */
    private fun performArbitraryOptionsChange() {
        // Toggle the first checkbox to ensure there is a change recorded.
        onWebView().withElement(findElement(Locator.CSS_SELECTOR, """[type="checkbox"]"""))
            .perform(
                webClick()
            )
    }

    /**
     * Open the deck options and enable javascript in it.
     */
    private fun openDeckOptions(deckName: String) {
        openDeckContextMenu(deckName)
        onView(withText(R.string.menu__deck_options)).perform(click())
        assertWebViewIsDisplayed()
        onWebView().forceJavascriptEnabled()
    }

    private fun assertWebViewIsDisplayed() {
        onView(withResourceName("webview")).check(matches(isDisplayed()))
        // It seems I can't just check that there is an element of class "save", I must check something in it. So let's check its content.
        onWebView().withElement(findElement(Locator.CLASS_NAME, "save")).check(webMatches(getText(), containsString("Save")))
    }

    private fun assertWebViewDoesNotExists() {
        onView(withResourceName("webview")).checkWithTimeout(doesNotExist(), 100, 2000)
    }

    private fun openDeckContextMenu(deckName: String) {
        onView(withId(R.id.decks)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText(deckName)),
                longClick()
            )
        )
    }
}
