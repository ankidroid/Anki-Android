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
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showSnackbar
import com.ichi2.anki.databinding.MyAccountBinding
import com.ichi2.anki.databinding.MyAccountLoggedInBinding
import com.ichi2.anki.databinding.ToolbarBinding
import com.ichi2.anki.web.HostNumFactory.getInstance
import com.ichi2.async.Connection
import com.ichi2.libanki.sync.CustomSyncServerUrlException
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import timber.log.Timber
import java.lang.Exception
import java.net.UnknownHostException

class MyAccount : AnkiActivity() {
    private lateinit var binding: MyAccountBinding
    private lateinit var loggedInBinding: MyAccountLoggedInBinding
    private lateinit var toolbarBinding: ToolbarBinding
    private var mProgressDialog: MaterialDialog? = null
    private fun switchToState(newState: Int) {
        when (newState) {
            STATE_LOGGED_IN -> {
                val username = AnkiDroidApp.getSharedPrefs(baseContext).getString("username", "")
                loggedInBinding.usernameLoggedIn.text = username
                toolbarBinding.toolbar.title = getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                setSupportActionBar(toolbarBinding.toolbar)
                setContentView(loggedInBinding.root)
            }
            STATE_LOG_IN -> {
                toolbarBinding.toolbar.title = getString(R.string.sync_account) // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                setSupportActionBar(toolbarBinding.toolbar)
                setContentView(binding.root)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        binding = MyAccountBinding.inflate(layoutInflater)
        toolbarBinding = ToolbarBinding.bind(binding.root)
        loggedInBinding = MyAccountLoggedInBinding.inflate(layoutInflater)
        if (isUserATestClient) {
            finishWithoutAnimation()
            return
        }
        mayOpenUrl(Uri.parse(resources.getString(R.string.register_url)))
        initAllContentViews()
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        if (preferences.getString("hkey", "")!!.isNotEmpty()) {
            switchToState(STATE_LOGGED_IN)
        } else {
            switchToState(STATE_LOG_IN)
        }
        if (isScreenSmall && this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.ankidroidLogo.visibility = View.GONE
        } else {
            binding.ankidroidLogo.visibility = View.VISIBLE
        }
    }

    fun attemptLogin() {
        val username = binding.username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = binding.password.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Timber.i("Auto-login cancelled - username/password missing")
            return
        }
        Timber.i("Attempting auto-login")
        Connection.login(
            mLoginListener,
            Connection.Payload(
                arrayOf(
                    username, password,
                    getInstance(this)
                )
            )
        )
    }

    private fun saveUserInformation(username: String, hkey: String) {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        preferences.edit {
            putString("username", username)
            putString("hkey", hkey)
        }
    }

    private fun login() {
        // Hide soft keyboard
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.username.windowToken, 0)
        val username = binding.username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = binding.password.text.toString()
        if (username.isEmpty()) {
            binding.username.error = getString(R.string.email_id_empty)
            binding.username.requestFocus()
            return
        }
        if (password.isEmpty()) {
            binding.password.error = getString(R.string.password_empty)
            binding.password.requestFocus()
            return
        }
        Connection.login(
            mLoginListener,
            Connection.Payload(
                arrayOf(
                    username, password,
                    getInstance(this)
                )
            )
        )
    }

    private fun logout() {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        preferences.edit {
            putString("username", "")
            putString("hkey", "")
        }
        getInstance(this).reset()
        //  force media resync on deauth
        col.media.forceResync()
        switchToState(STATE_LOG_IN)
    }

    private fun resetPassword() {
        super.openUrl(Uri.parse(resources.getString(R.string.resetpw_url)))
    }

    private fun initAllContentViews() {
        binding.password.setOnKeyListener(
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

        binding.loginButton.setOnClickListener { login() }
        binding.resetPasswordButton.setOnClickListener { resetPassword() }
        val url = Uri.parse(resources.getString(R.string.register_url))
        binding.signUpButton.setOnClickListener { openUrl(url) }

        // Add button to link to instructions on how to find AnkiWeb email
        val lostMailUrl = Uri.parse(resources.getString(R.string.link_ankiweb_lost_email_instructions))
        binding.lostMailInstructions.setOnClickListener { openUrl(lostMailUrl) }
        loggedInBinding = MyAccountLoggedInBinding.inflate(layoutInflater)
        loggedInBinding.logoutButton.setOnClickListener { logout() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.password.setAutoFillListener {
                // disable "show password".
                binding.passwordLayout.isEndIconVisible = false
                Timber.i("Attempting login from autofill")
                attemptLogin()
            }
        }
    }

    private fun showLoginLogMessage(@StringRes messageResource: Int, loginMessage: String?) {
        run {
            if (loginMessage.isNullOrEmpty()) {
                if (messageResource == R.string.youre_offline && !Connection.getAllowLoginSyncOnNoConnection()) {
                    // #6396 - Add a temporary "Try Anyway" button until we sort out `isOnline`
                    // val root = this.findViewById<View>(R.id.root_layout)
                    showSnackbar(this, messageResource, false, R.string.sync_even_if_offline, {
                        Connection.setAllowLoginSyncOnNoConnection(true)
                        login()
                    }, null)
                } else {
                    showSimpleSnackbar(this, messageResource, false)
                }
            } else {
                val res = AnkiDroidApp.getAppResources()
                showSimpleMessageDialog(res.getString(messageResource), loginMessage, false)
            }
        }
    }

    /**
     * Listeners
     */
    private val mLoginListener: Connection.TaskListener = object : Connection.TaskListener {
        override fun onProgressUpdate(vararg values: Any) {
            // Pass
        }

        override fun onPreExecute() {
            Timber.d("loginListener.onPreExecute()")
            if (mProgressDialog == null || !mProgressDialog!!.isShowing) {
                mProgressDialog = StyledProgressDialog.show(
                    this@MyAccount, null,
                    resources.getString(R.string.alert_logging_message), false
                )
            }
        }

        override fun onPostExecute(data: Connection.Payload) {
            if (mProgressDialog != null) {
                mProgressDialog!!.dismiss()
            }
            if (data.success) {
                Timber.i("User successfully logged in!")
                saveUserInformation(data.data[0] as String, data.data[1] as String)
                val i = this@MyAccount.intent
                if (i.hasExtra("notLoggedIn") && i.extras!!.getBoolean("notLoggedIn", false)) {
                    this@MyAccount.setResult(RESULT_OK, i)
                    finishWithAnimation(ActivityTransitionAnimation.Direction.FADE)
                } else {
                    // Show logged view
                    loggedInBinding.usernameLoggedIn.text = data.data[0] as String
                    switchToState(STATE_LOGGED_IN)
                }
            } else {
                Timber.e("Login failed, error code %d", data.returnType)
                if (data.returnType == 403) {
                    showSimpleSnackbar(this@MyAccount, R.string.invalid_username_password, true)
                } else {
                    val message = resources.getString(R.string.connection_error_message)
                    val result = data.result
                    if (!result.isNullOrEmpty() && result[0] is Exception) {
                        showSimpleMessageDialog(message, getHumanReadableLoginErrorMessage(result[0] as Exception), false)
                    } else {
                        showSimpleSnackbar(this@MyAccount, message, false)
                    }
                }
            }
        }

        override fun onDisconnected() {
            showLoginLogMessage(R.string.youre_offline, "")
        }
    }

    private fun getHumanReadableLoginErrorMessage(exception: Exception?): String? {
        if (exception == null) {
            return ""
        }
        if (exception is CustomSyncServerUrlException) {
            val url = exception.url
            return resources.getString(R.string.sync_error_invalid_sync_server, url)
        }
        if (exception.cause != null) {
            val cause = exception.cause
            if (cause is UnknownHostException) {
                return getString(R.string.sync_error_unknown_host_readable, exception.localizedMessage)
            }
        }
        return exception.localizedMessage
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
        } else {
            binding.ankidroidLogo.visibility = View.VISIBLE
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
        private const val STATE_LOG_IN = 1
        private const val STATE_LOGGED_IN = 2
    }
}
