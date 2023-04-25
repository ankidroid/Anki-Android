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

import android.os.Bundle
import androidx.fragment.app.commit
import com.ichi2.annotations.NeedsTest

/**
 * Activity used to log in into an AnkiWeb account, which can:
 *
 * * Log in
 * * Reset the password
 * * Sign up
 *
 * [EXTRA_HIDE_REGISTER] hides the sign up option, which can be useful
 * for a 'load from AnkiWeb' flow, in which we only wanted to encourage an
 * existing user to sync from AnkiWeb, to ensure that they don't have two collections,
 * causing a sync conflict.
 *
 * Most responsibilities are on [LoginFragment] and [LoggedInFragment].
 * This class only is necessary while [DeckPicker] and other callers can't handle using Fragments
 */
@NeedsTest("check result codes based on login result")
@NeedsTest("activity is closed if started when logged in")
class LoginActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_layout)
        enableToolbar().apply {
            setTitle(R.string.sync_account)
        }
        supportFragmentManager.commit {
            replace(R.id.fragment_container, if (isLoggedIn()) LoggedInFragment() else LoginFragment())
        }
    }

    companion object {
        const val EXTRA_HIDE_REGISTER = "hide_register"
        const val EXTRA_FINISH_ACTIVITY_AFTER_LOGIN = "finish_after_login"
    }
}
