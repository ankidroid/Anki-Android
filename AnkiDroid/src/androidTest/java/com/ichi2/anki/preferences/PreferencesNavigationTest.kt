/**
 *  Copyright (c) 2025 Amit Bisht <iamitsbisht07@gmail.com>
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
package com.ichi2.anki.preferences

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withResourceName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.utils.isWindowCompact
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesNavigationTest {
    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission)

    /**
     * This test verifies the navigation when search bar is clicked.
     * When user searches for something in the search bar,It should close the search view
     * on backPress() instead of closing the settings activity.
     */
    @Test
    fun testOnCompactMode() {
        fun isCompactMode(context: Context): Boolean = context.resources.isWindowCompact()

        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(isCompactMode(context))
        ActivityScenario.launch(IntentHandler::class.java)
        onView(withId(R.id.get_started)).perform(click())
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.search)).perform(click())
        onView(allOf(withId(R.id.search), hasFocus())).perform(typeText("Controls"))
        pressBack()
        // Checking the list of Settings Categories are displayed on the basis of our search "Controls"
        onView(allOf(withResourceName("list"), isAssignableFrom(RecyclerView::class.java))).check(
            matches(
                hasMinimumChildCount(1),
            ),
        )
        pressBack()
        onView(withId(R.id.settings_container)).check(matches(isDisplayed()))
    }

    /**
     * This test verifies the navigation when multiple menus are opened.
     * Even after opening multiple menus, when the back button is pressed,
     * it should close the settings activity instead of going to the previous menus.
     */
    @Test
    fun testOnNonCompactMode() {
        fun isTablet(context: Context): Boolean = context.resources.configuration.smallestScreenWidthDp >= 600

        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(isTablet(context))
        ActivityScenario.launch(IntentHandler::class.java)
        onView(withId(R.id.get_started)).perform(click())
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.search)).perform(click())
        onView(allOf(withId(R.id.search), hasFocus())).perform(typeText("Controls"))
        onView(allOf(withText(R.string.pref_cat_controls), hasFocus())).perform(click())
        pressBack()
        onView(withId(R.id.settings_container)).check(matches(isDisplayed()))
        onView(withText(R.string.notification_pref)).perform(click())
        pressBack()
        onView(withClassName(endsWith("PreferencesActivity"))).check(doesNotExist())
    }
}
