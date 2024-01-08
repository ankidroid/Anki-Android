/***************************************************************************************
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

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.ui.TextInputEditField
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

/**
 * Note: [LoginActivity] extends this and should not handle account creation
 */
open class MyAccount : AnkiActivity() {
    private lateinit var loginToMyAccountView: View
    private lateinit var loggedIntoMyAccountView: View
    private lateinit var username: TextInputEditText
    private lateinit var userNameLayout: TextInputLayout
    private lateinit var password: TextInputEditField
    private lateinit var usernameLoggedIn: TextView

    var toolbar: Toolbar? = null
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var ankidroidLogo: ImageView
    open fun switchToState(newState: Int) {
        when (newState) {
            STATE_LOGGED_IN -> {
                val username = baseContext.sharedPrefs().getString("username", "")
                usernameLoggedIn.text = username
                toolbar = loggedIntoMyAccountView.findViewById(R.id.toolbar)
                if (toolbar != null) {
                    toolbar!!.title =
                        getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(toolbar)
                }
                setContentView(loggedIntoMyAccountView)
            }
            STATE_LOG_IN -> {
                toolbar = loginToMyAccountView.findViewById(R.id.toolbar)
                if (toolbar != null) {
                    toolbar!!.title = getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(toolbar)
                }
                setContentView(loginToMyAccountView)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (isUserATestClient) {
            finish()
            return
        }
        mayOpenUrl(Uri.parse(resources.getString(R.string.register_url)))
        initAllContentViews()
        if (isLoggedIn()) {
            switchToState(STATE_LOGGED_IN)
        } else {
            switchToState(STATE_LOG_IN)
        }
        if (isScreenSmall && this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ankidroidLogo.visibility = View.GONE
        } else {
            ankidroidLogo.visibility = View.VISIBLE
        }
    }

    private fun attemptLogin() {
        val username = username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = password.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Timber.i("Auto-login cancelled - username/password missing")
            return
        }
        Timber.i("Attempting auto-login")
        handleNewLogin(username, password)
    }

    private fun login() {
        // Hide soft keyboard
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(username.windowToken, 0)
        val username = username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = password.text.toString()
        handleNewLogin(username, password)
    }

    private fun logout() {
        launchCatchingTask {
            syncLogout(baseContext)
            switchToState(STATE_LOG_IN)
        }
    }

    private fun resetPassword() {
        super.openUrl(Uri.parse(resources.getString(R.string.resetpw_url)))
    }

    private fun initAllContentViews() {
        loginToMyAccountView = layoutInflater.inflate(R.layout.my_account, null)
        loginToMyAccountView.let {
            username = it.findViewById(R.id.username)
            userNameLayout = it.findViewById(R.id.username_layout)
            password = it.findViewById(R.id.password)
            passwordLayout = it.findViewById(R.id.password_layout)
            ankidroidLogo = it.findViewById(R.id.ankidroid_logo)
        }
        val loginButton = loginToMyAccountView.findViewById<Button>(R.id.login_button)

        username.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = username.text.toString().trim()
                userNameLayout.apply {
                    isErrorEnabled = email.isNotEmpty()
                    error = if (email.isEmpty()) getString(R.string.invalid_email) else null
                }
            } else {
                userNameLayout.isErrorEnabled = false
            }
        }

        password.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val password = password.text.toString()
                if (password.isEmpty()) {
                    passwordLayout.isErrorEnabled = true
                    passwordLayout.error = getString(R.string.password_empty)
                }
            } else {
                passwordLayout.isErrorEnabled = false
            }
        }

        username.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_TAB) {
                password.requestFocus()
                return@setOnKeyListener true
            }
            false
        }

        password.setOnKeyListener(
            View.OnKeyListener { _: View?, keyCode: Int, event: KeyEvent ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (loginButton.isEnabled) login()
                            return@OnKeyListener true
                        }
                        else -> {}
                    }
                }
                false
            }
        )

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed here
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = username.text.toString().trim()
                val password = password.text.toString()
                val isFilled = email.isNotEmpty() && password.isNotEmpty()
                loginButton.isEnabled = isFilled
            }

            override fun afterTextChanged(s: Editable?) {
                // Not needed here
            }
        }
        username.addTextChangedListener(textWatcher)
        password.addTextChangedListener(textWatcher)
        loginButton.setOnClickListener { login() }
        val resetPWButton = loginToMyAccountView.findViewById<Button>(R.id.reset_password_button)
        resetPWButton.setOnClickListener { resetPassword() }
        val signUpButton = loginToMyAccountView.findViewById<Button>(R.id.sign_up_button)
        val url = Uri.parse(resources.getString(R.string.register_url))
        signUpButton.setOnClickListener { openUrl(url) }

        // Add button to link to instructions on how to find AnkiWeb email
        val lostEmail = loginToMyAccountView.findViewById<Button>(R.id.lost_mail_instructions)
        val lostMailUrl = Uri.parse(resources.getString(R.string.link_ankiweb_lost_email_instructions))
        lostEmail.setOnClickListener { openUrl(lostMailUrl) }
        loggedIntoMyAccountView = layoutInflater.inflate(R.layout.my_account_logged_in, null)
        usernameLoggedIn = loggedIntoMyAccountView.findViewById(R.id.username_logged_in)
        val logoutButton = loggedIntoMyAccountView.findViewById<Button>(R.id.logout_button)
        loggedIntoMyAccountView.let {
            ankidroidLogo = it.findViewById(R.id.ankidroid_logo)
        }
        logoutButton.setOnClickListener { logout() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            password.setAutoFillListener {
                // disable "show password".
                passwordLayout.isEndIconVisible = false
                Timber.i("Attempting login from autofill")
                attemptLogin()
            }
        }
    }

    private val isScreenSmall: Boolean
        get() = (
            (
                this.applicationContext.resources.configuration.screenLayout
                    and Configuration.SCREENLAYOUT_SIZE_MASK
                )
                < Configuration.SCREENLAYOUT_SIZE_LARGE
            )

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isScreenSmall && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ankidroidLogo.visibility = View.GONE
        } else {
            ankidroidLogo.visibility = View.VISIBLE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            Timber.i("MyAccount - onBackPressed()")
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        @KotlinCleanup("change to enum")
        internal const val STATE_LOG_IN = 1
        internal const val STATE_LOGGED_IN = 2
    }
}
