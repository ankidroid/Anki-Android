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

import android.app.Activity
import android.util.DisplayMetrics
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ichi2.annotations.KotlinCleanup
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

@KotlinCleanup("IDE Lint")
object TestUtils {
    /**
     * Get view at a particular index when there are multiple views with the same ID
     */
    fun withIndex(matcher: Matcher<View?>, index: Int): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            var currentIndex = 0
            override fun describeTo(description: Description) {
                description.appendText("with index: ")
                description.appendValue(index)
                matcher.describeTo(description)
            }

            public override fun matchesSafely(view: View?): Boolean {
                return matcher.matches(view) && currentIndex++ == index
            }
        }
    }

    /**
     * Get instance of current activity
     */
    val activityInstance: Activity?
        get() {
            val activity = arrayOfNulls<Activity>(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val resumedActivities: Collection<*> =
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(
                        Stage.RESUMED
                    )
                if (resumedActivities.iterator().hasNext()) {
                    val currentActivity = resumedActivities.iterator().next() as Activity
                    activity[0] = currentActivity
                }
            }
            return activity[0]
        }

    /**
     * Returns true if device is a tablet
     */
    @Suppress("deprecation") // #9333: getDefaultDisplay & getMetrics
    val isScreenSw600dp: Boolean
        get() {
            val displayMetrics = DisplayMetrics()
            activityInstance!!.windowManager.defaultDisplay.getMetrics(displayMetrics)
            val widthDp = displayMetrics.widthPixels / displayMetrics.density
            val heightDp = displayMetrics.heightPixels / displayMetrics.density
            val screenSw = Math.min(widthDp, heightDp)
            return screenSw >= 600
        }

    /**
     * Click on a view using its ID inside a RecyclerView item
     */
    fun clickChildViewWithId(id: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View>? {
                return null
            }

            override fun getDescription(): String {
                return "Click on a child view with specified id."
            }

            override fun perform(uiController: UiController, view: View) {
                val v = view.findViewById<View>(id)
                v.performClick()
            }
        }
    }

    /** @return if the instrumented tests were built on a CI machine
     */
    fun wasBuiltOnCI(): Boolean {
        // DO NOT COPY THIS INTO AN CODE WHICH IS RELEASED PUBLICLY

        // We use BuildConfig as we couldn't detect an envvar after `adb root && adb shell setprop`. See: #9293
        // TODO: See if we can fix this to use an envvar, and rename to isCi().
        return BuildConfig.CI
    }
}
