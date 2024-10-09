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

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.pages.RemoveAccountFragment
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.utils.ext.removeFragmentFromContainer
import com.ichi2.ui.TextInputEditField
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.Permissions
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
    private lateinit var loginLogo: ImageView
    private lateinit var loggedInLogo: ImageView

    // if the 'remove account' fragment is open, close it first
    private val onRemoveAccountBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeRemoveAccountScreen()
        }
    }

    open fun switchToState(newState: Int) {
        when (newState) {
            STATE_LOGGED_IN -> {
                val username = baseContext.sharedPrefs().getString("username", "")
                usernameLoggedIn.text = username
                toolbar = loggedIntoMyAccountView.findViewById<Toolbar?>(R.id.toolbar)?.also { toolbar ->
                    toolbar.title =
                        getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(toolbar)
                }
                setContentView(loggedIntoMyAccountView)
            }
            STATE_LOG_IN -> {
                toolbar = loginToMyAccountView.findViewById<Toolbar?>(R.id.toolbar)?.also { toolbar ->
                    toolbar.title = getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(toolbar)
                }
                setContentView(loginToMyAccountView)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Timber.i("notification permission: %b", it)
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
            loginLogo.visibility = View.GONE
            loggedInLogo.visibility = View.GONE
        } else {
            loginLogo.visibility = View.VISIBLE
            loggedInLogo.visibility = View.VISIBLE
        }
        onBackPressedDispatcher.addCallback(this, onRemoveAccountBackCallback)
    }

    private fun attemptLogin() {
        val username = username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = password.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Timber.i("Auto-login cancelled - username/password missing")
            return
        }
        Timber.i("Attempting auto-login")
        handleNewLogin(username, password, notificationPermissionLauncher)
    }

    private fun login() {
        // Hide soft keyboard
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(username.windowToken, 0)
        val username = username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = password.text.toString()
        handleNewLogin(username, password, notificationPermissionLauncher)
    }

    private fun logout() {
        launchCatchingTask {
            syncLogout(baseContext)
            switchToState(STATE_LOG_IN)
        }
    }

    /**
     * Opens the AnkiWeb 'remove account' WebView
     * @see RemoveAccountFragment
     * @see R.string.remove_account_url
     */
    private fun openRemoveAccountScreen() {
        Timber.i("opening 'remove account'")
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.remove_account_frame, RemoveAccountFragment())
            .commit()
        findViewById<View>(R.id.remove_account_frame).isVisible = true
        findViewById<View>(R.id.logged_in_layout).isVisible = false
        onRemoveAccountBackCallback.isEnabled = true
    }

    private fun closeRemoveAccountScreen() {
        Timber.i("closing 'remove account'")
        // remove the fragment - this resets the navigation
        // in case of user error
        supportFragmentManager.removeFragmentFromContainer(R.id.remove_account_frame)
        findViewById<View>(R.id.remove_account_frame).isVisible = false
        findViewById<View>(R.id.logged_in_layout).isVisible = true
        onRemoveAccountBackCallback.isEnabled = false
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
            loginLogo = it.findViewById(R.id.login_logo)
        }
        val loginButton = loginToMyAccountView.findViewById<Button>(R.id.login_button)
        loginToMyAccountView.findViewById<Button>(R.id.privacy_policy_button).apply {
            setOnClickListener { openAnkiDroidPrivacyPolicy() }
        }
        username.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = username.text.toString().trim()
                userNameLayout.error = if (email.isEmpty()) getString(R.string.invalid_email) else null
            } else {
                userNameLayout.error = null
            }
        }

        password.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val password = password.text.toString()
                if (password.isEmpty()) {
                    passwordLayout.error = getString(R.string.password_empty)
                }
            } else {
                passwordLayout.error = null
            }
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
        loggedIntoMyAccountView = layoutInflater.inflate(R.layout.my_account_logged_in, null).apply {
            usernameLoggedIn = findViewById(R.id.username_logged_in)
            findViewById<Button>(R.id.logout_button).apply {
                setOnClickListener { logout() }
            }
            findViewById<Button>(R.id.remove_account_button).apply {
                setOnClickListener { openRemoveAccountScreen() }
            }
            findViewById<Button>(R.id.privacy_policy_button).apply {
                setOnClickListener { openAnkiDroidPrivacyPolicy() }
            }
            loggedInLogo = findViewById(R.id.login_logo)
        }

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
            loginLogo.visibility = View.GONE
            loggedInLogo.visibility = View.GONE
        } else {
            loginLogo.visibility = View.VISIBLE
            loggedInLogo.visibility = View.VISIBLE
        }
    }

    private fun openAnkiDroidPrivacyPolicy() {
        Timber.i("Opening 'Privacy policy'")
        showDialogFragment(HelpDialog.newPrivacyPolicyInstance())
    }

    companion object {
        @KotlinCleanup("change to enum")
        internal const val STATE_LOG_IN = 1
        internal const val STATE_LOGGED_IN = 2

        /**
         * Displays a system prompt: "Allow AnkiDroid to send you notifications"
         *
         * [launcher] receives a callback result (`boolean`) unless:
         *  * Permissions were already granted
         *  * We are < API 33
         *
         * Permissions may permanently be denied, in which case [launcher] immediately
         * receives a failure result
         */
        fun checkNotificationPermission(
            context: Context,
            launcher: ActivityResultLauncher<String>
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return
            }
            val permission = Permissions.postNotification
            if (permission != null && ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(permission)
            }
        }
    }
}
