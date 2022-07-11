/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Dorrin Sotoudeh <dorrinsotoudeh123@gmail.com>                     *
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

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ichi2.anki.TestUtils.first
import com.ichi2.libanki.utils.TimeManager
import org.junit.Rule
import org.junit.Test

class ReviewerTest {
    @get:Rule
    val mActivityRule = ActivityScenarioRule(DeckPicker::class.java)

    /**
     * Test for #11831
     */
    @Test
    fun markingCardInCBUpdatesNoteEditorIfMoreThan1ActivityInStack() {
        val deckName = "Deck" + TimeManager.time.intTimeMS()
        // create deck
        onView(withId(R.id.fab_main)).perform(click())
        onView(withId(R.id.add_deck_action)).perform(click())
        onView(withId(R.id.md_input_message)).perform(typeText(deckName))
        onView(withId(R.id.md_button_positive)).perform(click())
        // select deck
        onView(withText(deckName)).perform(click())
        // add note
        onView(withId(R.id.snackbar_action)).perform(click())
        onView(first(withId(R.id.id_note_editText))).perform(typeText("1"))
        onView(withId(R.id.action_save)).perform(click())
        closeSoftKeyboard()
        Espresso.pressBack()

        // start reviewing note
        onView(withText(deckName)).perform(click())
        onView(withId(R.id.mark_icon)).check(isInvisible())

        // go to Stats
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withId(R.id.nav_stats)).perform(click())
        // go to CardBrowser
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withId(R.id.nav_browser)).perform(click())

        // select card
        onView(withId(R.id.card_item_browser)).perform(longClick())
        // mark card
        onView(withId(R.id.action_mark_card)).perform(click())

        // go back to reviewer
        Espresso.pressBack() // close multiselect
        Espresso.pressBack() // back to stats
        Espresso.pressBack() // back to reviewer

        onView(withId(R.id.mark_icon)).check(isVisible())
    }

    companion object {
        private fun isVisible() = withVisibility(Visibility.VISIBLE)
        private fun isInvisible() = withVisibility(Visibility.INVISIBLE)
        private fun withVisibility(vis: Visibility) = matches(withEffectiveVisibility(vis))
    }
}
