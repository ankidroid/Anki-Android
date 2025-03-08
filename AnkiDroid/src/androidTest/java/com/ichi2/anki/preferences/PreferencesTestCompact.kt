package com.ichi2.anki.preferences

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * This test verifies the navigation when the user tries to search and clicks the search bar.
 * When the search bar is clicked and the back button is pressed, it should close the search view,
 * and the previous screen (Settings) should be displayed.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesTestCompact {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )

    @Test
    fun testOnCompactMode() {
        ActivityScenario.launch(IntentHandler::class.java)
        onView(withId(R.id.get_started)).perform(click())
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.search)).perform(click())
        pressBack()
        pressBack()
        onView(withId(R.id.settings_container)).check(matches(isDisplayed()))
    }
}
