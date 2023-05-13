/*
 *  Copyright (c) 2023 Tomasz Garbus <tomasz.garbus1@gmail.com>
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

package com.ichi2.anki

import android.app.Activity
import androidx.core.content.edit
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
        getPreferences().edit { putString(SyncPreferences.HKEY, "anything not null") }

        launchActivityForResult(LoginActivity::class.java).use { scenario ->
            // When the user is logged in, we expect the activity to call finish() from onCreate().
            // Since this is expected behaviour, we also expect the result to be "OK".
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun activityIsNotFinishedOnStartupIfNotLoggedIn() {
        // Effectively mocks isLoggedIn() to return false.
        getPreferences().edit { putString(SyncPreferences.HKEY, "") }

        val scenario = launchActivityForResult(LoginActivity::class.java)

        // Since the activity state is different than STATE_LOGGED_IN, we *don't* expect the
        // activity to finish immediately.
        assertEquals(Lifecycle.State.RESUMED, scenario.state)

        // Now we close the activity and check the result.
        scenario.close()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }
}
