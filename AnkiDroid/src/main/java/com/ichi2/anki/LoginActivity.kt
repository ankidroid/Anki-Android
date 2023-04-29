/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.annotations.NeedsTest
import timber.log.Timber

/**
 * An activity which **only** involves logging in to an account:
 *
 * * Logging in
 * * Resetting the password
 *
 * This was created for a 'load from AnkiWeb' flow, in which we only wanted to encourage an existing
 * user to sync from AnkiWeb, to ensure that they don't have two collections, causing a sync conflict.
 *
 * Use [MyAccount] if you want to handle the 'logged in' state, and creating a new account
 *
 * Activity Results:
 * * [RESULT_OK] - login was successful OR login had already occurred
 * * [RESULT_CANCELED] - login did not occur
 *
 * TODO: Move this to a fragment
 */
@NeedsTest("check result codes based on login result")
class LoginActivity : MyAccount() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<View>(R.id.sign_up_button)?.visibility = GONE
        findViewById<View>(R.id.no_account_text)?.visibility = GONE
    }

    /**
     * Handles closing the activity and setting the result when the user is logged in
     */
    override fun switchToState(newState: Int) {
        if (newState == STATE_LOGGED_IN) {
            // This was intended to be shown from the 'app intro' where a user should not be logged in
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                showThemedToast(this, R.string.already_logged_in, true)
                Timber.w("LoginActivity shown when user was logged in")
            }
            setResult(RESULT_OK)
            finish()
            return
        }
        super.switchToState(newState)
    }
}
