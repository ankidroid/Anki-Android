package com.ichi2.anki

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.MyAccount.Companion.STATE_LOGGED_IN
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest : RobolectricTest() {
    @Test
    fun activityIsClosedIfStartedWhenLoggedIn() {
        val scenario = launch(LoginActivity::class.java)

        scenario.moveToState(Lifecycle.State.CREATED)

        scenario.onActivity { activity ->
            run {
                activity.switchToState(STATE_LOGGED_IN)
                assert(activity.isFinishing)
            }
        }
    }
}
