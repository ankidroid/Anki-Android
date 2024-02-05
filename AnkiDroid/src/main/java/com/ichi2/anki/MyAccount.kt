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
import com.ichi2.anki.databinding.MyAccountBinding
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import timber.log.Timber

/**
 * Note: [LoginActivity] extends this and should not handle account creation
 */
open class MyAccount : AnkiActivity() {

    lateinit var binding: MyAccountBinding
        private set

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
        binding = MyAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initAllContentViews()

        if (isLoggedIn()) {
            switchState(LoginState.LOGGED_IN)
        } else {
            switchState(LoginState.LOGGED_OUT)
        }
        if (isScreenSmall && this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.ankidroidLogo.visibility = View.GONE
            binding.myAccountLoggedInLayout.ankidroidLogo.visibility = View.GONE
        } else {
            binding.ankidroidLogo.visibility = View.VISIBLE
            binding.myAccountLoggedInLayout.ankidroidLogo.visibility = View.VISIBLE
        }
    }

    private fun switchState(state: LoginState) {
        when (state) {
            LoginState.LOGGED_IN -> {
                val toolbar = binding.myAccountLoggedInLayout.toolbar.toolbar
                toolbar.title = getString(R.string.sync_account)
                setSupportActionBar(toolbar)

                binding.myAccountLoggedInLayout.root.visibility = View.VISIBLE
                binding.logoutView.visibility = View.GONE

                setLoginState(LoginState.LOGGED_IN)
                val username = baseContext.sharedPrefs().getString("username", "")
                binding.myAccountLoggedInLayout.usernameLoggedIn.text = username
            }
            LoginState.LOGGED_OUT -> {
                val toolbar = binding.toolbar.toolbar
                toolbar.title = getString(R.string.sync_account)
                setSupportActionBar(toolbar)

                binding.logoutView.visibility = View.VISIBLE
                binding.myAccountLoggedInLayout.root.visibility = View.GONE

                setLoginState(LoginState.LOGGED_OUT)
            }
        }
    }

    private fun attemptLogin() {
        val username = binding.username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = binding.password.text.toString()
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
        inputMethodManager.hideSoftInputFromWindow(binding.username.windowToken, 0)
        val username = binding.username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = binding.password.text.toString()
        handleNewLogin(username, password)
    }

    private fun logout() {
        launchCatchingTask {
            switchState(LoginState.LOGGED_OUT)
            syncLogout(baseContext)
            binding.logoutView.visibility = View.VISIBLE
            binding.myAccountLoggedInLayout.root.visibility = View.GONE
        }
    }

    private fun resetPassword() {
        super.openUrl(Uri.parse(resources.getString(R.string.resetpw_url)))
    }

    private fun initAllContentViews() {
        val loginButton = binding.loginButton

        binding.username.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = binding.username.text.toString().trim()
                binding.usernameLayout.apply {
                    isErrorEnabled = email.isNotEmpty()
                    error = if (email.isEmpty()) getString(R.string.invalid_email) else null
                }
            } else {
                binding.usernameLayout.isErrorEnabled = false
            }
        }

        binding.password.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val password = binding.password.text.toString()
                if (password.isEmpty()) {
                    binding.passwordLayout.isErrorEnabled = true
                    binding.passwordLayout.error = getString(R.string.password_empty)
                }
            } else {
                binding.passwordLayout.isErrorEnabled = false
            }
        }

        binding.password.setOnKeyListener(
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
                val email = binding.username.text.toString().trim()
                val password = binding.password.text.toString()
                val isFilled = email.isNotEmpty() && password.isNotEmpty()
                loginButton.isEnabled = isFilled
            }

            override fun afterTextChanged(s: Editable?) {
                // Not needed here
            }
        }
        binding.username.addTextChangedListener(textWatcher)
        binding.password.addTextChangedListener(textWatcher)
        loginButton.setOnClickListener { login() }
        val resetPWButton = binding.resetPasswordButton
        resetPWButton.setOnClickListener { resetPassword() }
        val signUpButton = binding.signUpButton
        val url = Uri.parse(resources.getString(R.string.register_url))
        signUpButton.setOnClickListener { openUrl(url) }

        // Add button to link to instructions on how to find AnkiWeb email
        val lostEmail = binding.lostMailInstructions
        val lostMailUrl = Uri.parse(resources.getString(R.string.link_ankiweb_lost_email_instructions))
        lostEmail.setOnClickListener { openUrl(lostMailUrl) }
        binding.myAccountLoggedInLayout.logoutButton
            .setOnClickListener { logout() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.password.setAutoFillListener {
                // disable "show password".
                binding.passwordLayout.isEndIconVisible = false
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
            binding.ankidroidLogo.visibility = View.GONE
            binding.myAccountLoggedInLayout.ankidroidLogo.visibility = View.GONE
        } else {
            binding.ankidroidLogo.visibility = View.VISIBLE
            binding.myAccountLoggedInLayout.ankidroidLogo.visibility = View.VISIBLE
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

    open fun setLoginState(state: LoginState) {
        loginState = state
    }

    companion object {
        var loginState: LoginState = LoginState.LOGGED_OUT
    }
}

enum class LoginState {
    LOGGED_IN,
    LOGGED_OUT
}
