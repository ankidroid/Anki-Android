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

import android.app.Activity.RESULT_OK
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResult
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.LoginActivity.Companion.EXTRA_FINISH_ACTIVITY_AFTER_LOGIN
import com.ichi2.anki.LoginActivity.Companion.EXTRA_HIDE_REGISTER
import com.ichi2.anki.preferences.SyncSettingsFragment
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.web.HostNumFactory.getInstance
import com.ichi2.async.Connection
import com.ichi2.libanki.sync.CustomSyncServerUrlException
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.ui.TextInputEditField
import com.ichi2.utils.show
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.net.UnknownHostException

/**
 * Fragment used to log in into an AnkiWeb account, which can:
 *
 * * Log in
 * * Reset the password
 * * Sign up
 *
 * [EXTRA_HIDE_REGISTER] hides the sign up option, which can be useful
 * for a 'load from AnkiWeb' flow, in which we only wanted to encourage an
 * existing user to sync from AnkiWeb, to ensure that they don't have two collections,
 * causing a sync conflict.
 */
class MyAccount : Fragment() {
    private lateinit var username: EditText
    private lateinit var password: TextInputEditField

    @Suppress("Deprecation")
    private var progressDialog: android.app.ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isLoggedIn()) {
            changeToLoggedInFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.my_account, container, false)

        if (requireActivity().intent?.getBooleanExtra(EXTRA_HIDE_REGISTER, false) == true) {
            view.findViewById<View>(R.id.sign_up_button)?.visibility = GONE
            view.findViewById<View>(R.id.no_account_text)?.visibility = GONE
        }

        username = view.findViewById(R.id.username)
        password = view.findViewById(R.id.password)
        password.setOnKeyListener(
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

        view.findViewById<Button>(R.id.login_button).setOnClickListener { login() }
        view.findViewById<Button>(R.id.reset_password_button).setOnClickListener {
            (requireActivity() as AnkiActivity).openUrl(R.string.resetpw_url)
        }
        view.findViewById<Button>(R.id.sign_up_button).setOnClickListener {
            (requireActivity() as AnkiActivity).openUrl(R.string.register_url)
        }

        // Add button to link to instructions on how to find AnkiWeb email
        view.findViewById<Button>(R.id.lost_mail_instructions).setOnClickListener {
            (requireActivity() as AnkiActivity).openUrl(R.string.link_ankiweb_lost_email_instructions)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            password.setAutoFillListener {
                // disable "show password".
                view.findViewById<TextInputLayout>(R.id.password_layout).isEndIconVisible = false
                Timber.i("Attempting login from autofill")
                attemptLogin()
            }
        }
        return view
    }

    private fun attemptLogin() {
        val username = username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = password.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Timber.i("Auto-login cancelled - username/password missing")
            return
        }
        Timber.i("Attempting auto-login")
        if (!BackendFactory.defaultLegacySchema) {
            handleNewLogin(username, password)
        } else {
            Connection.login(
                loginListener,
                Connection.Payload(
                    arrayOf(
                        username,
                        password,
                        getInstance(requireContext())
                    )
                )
            )
        }
    }

    private fun saveUserInformation(username: String, hkey: String) {
        val preferences = AnkiDroidApp.getSharedPrefs(requireContext())
        preferences.edit {
            putString("username", username)
            putString("hkey", hkey)
        }
    }

    private fun login() {
        // Hide soft keyboard
        val inputMethodManager = requireContext().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(username.windowToken, 0)
        val username = username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = password.text.toString()
        if (username.isEmpty()) {
            this.username.error = getString(R.string.email_id_empty)
            this.username.requestFocus()
            return
        }
        if (password.isEmpty()) {
            this.password.error = getString(R.string.password_empty)
            this.password.requestFocus()
            return
        }
        if (!BackendFactory.defaultLegacySchema) {
            handleNewLogin(username, password)
        } else {
            Connection.login(
                loginListener,
                Connection.Payload(
                    arrayOf(
                        username,
                        password,
                        getInstance(requireContext())
                    )
                )
            )
        }
    }

    private fun showLoginLogMessage(@StringRes messageResource: Int, loginMessage: String?) {
        if (loginMessage.isNullOrEmpty()) {
            if (messageResource == R.string.youre_offline && !Connection.allowLoginSyncOnNoConnection) {
                // #6396 - Add a temporary "Try Anyway" button until we sort out `isOnline`
                showSnackbar(messageResource) {
                    setAction(R.string.sync_even_if_offline) {
                        Connection.allowLoginSyncOnNoConnection = true
                        login()
                    }
                }
            } else {
                showSnackbar(messageResource)
            }
        } else {
            AlertDialog.Builder(requireContext()).show {
                setTitle(messageResource)
                setMessage(loginMessage)
            }
        }
    }

    /** Destroys the fragment and starts [LoggedInFragment] */
    private fun changeToLoggedInFragment() {
        parentFragmentManager.popBackStack()
        parentFragmentManager.commit {
            replace(R.id.fragment_container, LoggedInFragment())
            addToBackStack(null)
        }
        setFragmentResult(
            SyncSettingsFragment.LOGIN_STATUS_CHANGED_REQUEST_KEY,
            bundleOf(
                SyncSettingsFragment.LOGIN_STATUS_CHANGED_REQUEST_KEY to true
            )
        )
    }

    private val loginListener: Connection.TaskListener = object : Connection.TaskListener {
        override fun onProgressUpdate(vararg values: Any?) {
            // Pass
        }

        override fun onPreExecute() {
            Timber.d("loginListener.onPreExecute()")
            if (progressDialog == null || !progressDialog!!.isShowing) {
                progressDialog = StyledProgressDialog.show(
                    requireContext(),
                    null,
                    resources.getString(R.string.alert_logging_message),
                    false
                )
            }
        }

        override fun onPostExecute(data: Connection.Payload) {
            if (progressDialog != null) {
                progressDialog!!.dismiss()
            }
            if (data.success) {
                Timber.i("User successfully logged in!")
                saveUserInformation(data.data[0] as String, data.data[1] as String)
                val i = requireActivity().intent
                if (i.getBooleanExtra(EXTRA_FINISH_ACTIVITY_AFTER_LOGIN, false)) {
                    requireActivity().setResult(RESULT_OK, i)
                    (requireActivity() as AnkiActivity).finishWithAnimation(ActivityTransitionAnimation.Direction.FADE)
                } else {
                    changeToLoggedInFragment()
                }
            } else {
                Timber.e("Login failed, error code %d", data.returnType)
                if (data.returnType == 403) {
                    showSnackbar(R.string.invalid_username_password)
                } else {
                    val message = resources.getString(R.string.connection_error_message)
                    val result = data.result
                    if (result.isNotEmpty() && result[0] is Exception) {
                        AlertDialog.Builder(requireContext()).show {
                            setTitle(message)
                            setMessage(getHumanReadableLoginErrorMessage(result[0] as Exception))
                        }
                    } else {
                        showSnackbar(message)
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
}
