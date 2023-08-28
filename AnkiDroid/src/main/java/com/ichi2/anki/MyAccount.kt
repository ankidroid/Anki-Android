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
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.ui.TextInputEditField
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

/**
 * Note: [LoginActivity] extends this and should not handle account creation
 */
open class MyAccount : AnkiActivity() {
    private lateinit var mLoginToMyAccountView: View
    private lateinit var mLoggedIntoMyAccountView: View
    private lateinit var mUsername: EditText
    private lateinit var mPassword: TextInputEditField
    private lateinit var mUsernameLoggedIn: TextView

    @Suppress("Deprecation")
    private var mProgressDialog: android.app.ProgressDialog? = null
    var toolbar: Toolbar? = null
    private lateinit var mPasswordLayout: TextInputLayout
    private lateinit var mAnkidroidLogo: ImageView
    open fun switchToState(newState: Int) {
        when (newState) {
            STATE_LOGGED_IN -> {
                val username = baseContext.sharedPrefs().getString("username", "")
                mUsernameLoggedIn.text = username
                toolbar = mLoggedIntoMyAccountView.findViewById(R.id.toolbar)
                if (toolbar != null) {
                    toolbar!!.title =
                        getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(toolbar)
                }
                setContentView(mLoggedIntoMyAccountView)
            }
            STATE_LOG_IN -> {
                toolbar = mLoginToMyAccountView.findViewById(R.id.toolbar)
                if (toolbar != null) {
                    toolbar!!.title = getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(toolbar)
                }
                setContentView(mLoginToMyAccountView)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (isUserATestClient) {
            finishWithoutAnimation()
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
            mAnkidroidLogo.visibility = View.GONE
        } else {
            mAnkidroidLogo.visibility = View.VISIBLE
        }
    }

    private fun attemptLogin() {
        val username = mUsername.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = mPassword.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Timber.i("Auto-login cancelled - username/password missing")
            return
        }
        Timber.i("Attempting auto-login")
        handleNewLogin(username, password)
    }

    private fun saveUserInformation(username: String, hkey: String) {
        val preferences = baseContext.sharedPrefs()
        preferences.edit {
            putString("username", username)
            putString("hkey", hkey)
        }
    }

    private fun login() {
        // Hide soft keyboard
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(mUsername.windowToken, 0)
        val username = mUsername.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = mPassword.text.toString()
        if (username.isEmpty()) {
            mUsername.error = getString(R.string.email_id_empty)
            mUsername.requestFocus()
            return
        }
        if (password.isEmpty()) {
            mPassword.error = getString(R.string.password_empty)
            mPassword.requestFocus()
            return
        }
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
        mLoginToMyAccountView = layoutInflater.inflate(R.layout.my_account, null)
        mLoginToMyAccountView.let {
            mUsername = it.findViewById(R.id.username)
            mPassword = it.findViewById(R.id.password)
            mPasswordLayout = it.findViewById(R.id.password_layout)
            mAnkidroidLogo = it.findViewById(R.id.ankidroid_logo)
        }

        mPassword.setOnKeyListener(
            View.OnKeyListener { _: View?, keyCode: Int, event: KeyEvent ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            login()
                            return@OnKeyListener true
                        }
                        else -> {}
                    }
                }
                false
            }
        )

        val loginButton = mLoginToMyAccountView.findViewById<Button>(R.id.login_button)
        loginButton.setOnClickListener { login() }
        val resetPWButton = mLoginToMyAccountView.findViewById<Button>(R.id.reset_password_button)
        resetPWButton.setOnClickListener { resetPassword() }
        val signUpButton = mLoginToMyAccountView.findViewById<Button>(R.id.sign_up_button)
        val url = Uri.parse(resources.getString(R.string.register_url))
        signUpButton.setOnClickListener { openUrl(url) }

        // Add button to link to instructions on how to find AnkiWeb email
        val lostEmail = mLoginToMyAccountView.findViewById<Button>(R.id.lost_mail_instructions)
        val lostMailUrl = Uri.parse(resources.getString(R.string.link_ankiweb_lost_email_instructions))
        lostEmail.setOnClickListener { openUrl(lostMailUrl) }
        mLoggedIntoMyAccountView = layoutInflater.inflate(R.layout.my_account_logged_in, null)
        mUsernameLoggedIn = mLoggedIntoMyAccountView.findViewById(R.id.username_logged_in)
        val logoutButton = mLoggedIntoMyAccountView.findViewById<Button>(R.id.logout_button)
        mLoggedIntoMyAccountView.let {
            mAnkidroidLogo = it.findViewById(R.id.ankidroid_logo)
        }
        logoutButton.setOnClickListener { logout() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPassword.setAutoFillListener {
                // disable "show password".
                mPasswordLayout.isEndIconVisible = false
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
            mAnkidroidLogo.visibility = View.GONE
        } else {
            mAnkidroidLogo.visibility = View.VISIBLE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            Timber.i("MyAccount - onBackPressed()")
            finishWithAnimation(ActivityTransitionAnimation.Direction.FADE)
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
