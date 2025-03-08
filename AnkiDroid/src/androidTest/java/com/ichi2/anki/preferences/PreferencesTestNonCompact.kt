package com.ichi2.anki.preferences

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import org.hamcrest.Matchers.endsWith
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * This test verifies the navigation when multiple menus are opened.
 * Even after opening multiple menus, when the back button is pressed,
 * it should close the settings activity instead of going to the previous menus.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesTestNonCompact {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )

    @Test
    fun testOnNonCompactMode() {
        fun isTablet(context: Context): Boolean = context.resources.configuration.smallestScreenWidthDp >= 600

        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(isTablet(context))
        ActivityScenario.launch(IntentHandler::class.java)
        onView(withId(R.id.get_started)).perform(click())
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withText("Sync")).perform(click())
        onView(withText("Controls")).perform(click())
        onView(withText("Notifications")).perform(click())
        pressBack()
        onView(withClassName(endsWith("PreferencesActivity"))).check(doesNotExist())
    }
}
