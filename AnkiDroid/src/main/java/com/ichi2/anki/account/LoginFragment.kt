/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.account

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatActivity.INPUT_METHOD_SERVICE
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.getEndpoint
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.ui.TextInputEditField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class LoginFragment : Fragment(R.layout.my_account) {
    private val isScreenSmall: Boolean
        get() = (
            (
                requireActivity()
                    .applicationContext.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            ) <
                Configuration.SCREENLAYOUT_SIZE_LARGE
        )

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var username: TextInputEditText
    private lateinit var userNameLayout: TextInputLayout
    private lateinit var password: TextInputEditField
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var loginLogo: ImageView
    private lateinit var loginButton: Button

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Timber.i("notification permission: %b", it)
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.sync_account)

        passwordLayout = view.findViewById(R.id.password_layout)
        username = view.findViewById(R.id.username)
        userNameLayout = view.findViewById(R.id.username_layout)
        password = view.findViewById(R.id.password)
        loginLogo = view.findViewById(R.id.login_logo)
        loginButton = view.findViewById(R.id.login_button)

        initListeners()
        initObservers()
    }

    private fun login() {
        // Hide soft keyboard
        val inputMethodManager = requireContext().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(username.windowToken, 0)
        val username = username.text.toString().trim { it <= ' ' } // trim spaces, issue 1586
        val password = password.text.toString()
        handleNewLogin(username, password)
    }

    private fun initListeners() {
        username.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onUserNameFocusChange(hasFocus, username.text.toString())
        }

        password.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onPasswordFocusChange(hasFocus, password.text.toString())
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
            },
        )

        username.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    viewModel.onTextChanged(username.text.toString(), password.text.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        password.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    viewModel.onTextChanged(username.text.toString(), password.text.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        loginButton.setOnClickListener {
            login()
        }

        requireView()
            .findViewById<Button>(R.id.reset_password_button)
            .setOnClickListener { openUrl(resources.getString(R.string.resetpw_url).toUri()) }

        initObservers()

        requireView().findViewById<Button>(R.id.sign_up_button).setOnClickListener {
            openUrl(resources.getString(R.string.register_url).toUri())
        }

        requireView().findViewById<Button>(R.id.lost_mail_instructions).setOnClickListener {
            openUrl(resources.getString(R.string.link_ankiweb_lost_email_instructions).toUri())
        }

        requireView().findViewById<Button>(R.id.privacy_policy_button).setOnClickListener {
            Timber.i("Opening 'Privacy policy'")
            showDialogFragment(HelpDialog.newPrivacyPolicyInstance())
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

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.loginButtonEnabled.collect { isEnabled ->
                    loginButton.isEnabled = isEnabled
                }
            }

            launch {
                viewModel.userNameError.collect { error ->
                    userNameLayout.error = error.toHumanReadableString(requireContext())
                }
            }

            launch {
                viewModel.passwordError.collect { error ->
                    passwordLayout.error = error.toHumanReadableString(requireContext())
                }
            }

            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Success -> {
                        // TODO: handle logged in state
                    }
                    is LoginState.Error -> {
                        showSnackbar(text = state.exception.message.toString())
                    }
                    else -> {} // Not needed
                }
            }
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

    private fun handleNewLogin(
        username: String,
        password: String,
    ) {
        val endpoint = getEndpoint(requireContext())

        lifecycleScope.launch {
            requireActivity().withProgress(
                extractProgress = {
                    text = getString(R.string.sign_in)
                },
                onCancel = { backend -> backend.setWantsAbort() },
            ) {
                viewModel.handleLogin(
                    username,
                    password,
                    endpoint,
                )

                viewModel.loginState.first { it is LoginState.Success || it is LoginState.Error }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loginLogo.visibility =
            if (isScreenSmall && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
            ) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }
}
