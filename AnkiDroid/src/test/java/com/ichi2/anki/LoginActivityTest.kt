package com.ichi2.anki

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LoginActivityTest : RobolectricTest() {
    @Test
    fun activityIsClosedIfStartedWhenLoggedIn() {
        // Effectively mocks isLoggedIn() to return true.
        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit()
            .putString(SyncPreferences.HKEY, "anything not null")
            .apply()

        val scenario = launchActivityForResult(LoginActivity::class.java)

        // When the user is logged in, we expect the activity to call finish() from onCreate().
        // Since this is expected behaviour, we also expect the result to be "OK".
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun activityIsNotFinishedOnStartupIfNotLoggedIn() {
        // Effectively mocks isLoggedIn() to return false.
        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit()
            .putString(SyncPreferences.HKEY, "")
            .apply()

        val scenario = launchActivityForResult(LoginActivity::class.java)

        // Since the activity state is different than STATE_LOGGED_IN, we *don't* expect the
        // activity to finish immediately.
        assertEquals(Lifecycle.State.RESUMED, scenario.state)

        // Now we close the activity and check the result.
        scenario.close()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }
}
