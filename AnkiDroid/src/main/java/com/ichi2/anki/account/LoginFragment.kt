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

import android.app.Activity.RESULT_OK
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.account.AccountActivity.Companion.START_FROM_DECKPICKER
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.getEndpoint
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.isCompactWidth
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.hideKeyboard
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.ui.TextInputEditField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class LoginFragment : Fragment(R.layout.my_account) {
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

        val toolbar: MaterialToolbar = view.findViewById(R.id.toolbar)
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(toolbar)

        activity.supportActionBar?.apply {
            title = TR.preferencesAccount().toSentenceCase(requireContext(), R.string.sync_account)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

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
        hideKeyboard()
        val username = username.text.toString().trim()
        val password = password.text.toString()
        handleNewLogin(username, password)
    }

    private fun initListeners() {
        initUsernameListeners()
        initPasswordListeners()
        initButtonListeners()
        initObservers()
    }

    private fun initUsernameListeners() {
        username.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onUserNameFocusChange(hasFocus, username.text.toString())
        }

        username.doOnTextChanged { text, _, _, _ ->
            onUsernameChanged(text.toString())
        }
    }

    private fun initPasswordListeners() {
        password.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onPasswordFocusChange(hasFocus, password.text.toString())
        }

        password.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (loginButton.isEnabled) login()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        password.doOnTextChanged { text, _, _, _ ->
            onPasswordChanged(text.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            password.setAutoFillListener {
                passwordLayout.isEndIconVisible = false
                Timber.i("Attempting login from autofill")
                attemptLogin()
            }
        }
    }

    private fun initButtonListeners() {
        loginButton.setOnClickListener { login() }

        requireView()
            .findViewById<Button>(R.id.reset_password_button)
            .setOnClickListener { openUrl(resources.getString(R.string.resetpw_url).toUri()) }

        requireView()
            .findViewById<Button>(R.id.sign_up_button)
            .setOnClickListener { openUrl(resources.getString(R.string.register_url).toUri()) }

        requireView()
            .findViewById<Button>(R.id.lost_mail_instructions)
            .setOnClickListener { openUrl(resources.getString(R.string.link_ankiweb_lost_email_instructions).toUri()) }

        requireView()
            .findViewById<Button>(R.id.privacy_policy_button)
            .setOnClickListener {
                Timber.i("Opening 'Privacy policy'")
                showDialogFragment(HelpDialog.newPrivacyPolicyInstance())
            }
    }

    private fun onUsernameChanged(newUsername: String) {
        viewModel.onTextChanged(newUsername, password.text.toString())
    }

    private fun onPasswordChanged(newPassword: String) {
        viewModel.onTextChanged(username.text.toString(), newPassword)
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loginButtonEnabled.collect { isEnabled ->
                loginButton.isEnabled = isEnabled
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userNameError.collect { error ->
                userNameLayout.error = error?.toHumanReadableString(requireContext())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.passwordError.collect { error ->
                passwordLayout.error = error?.toHumanReadableString(requireContext())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Success -> {
                        val activity = requireActivity()
                        val isForResult = arguments?.getBoolean(START_FROM_DECKPICKER) ?: false

                        if (isForResult) {
                            activity.setResult(RESULT_OK)
                            activity.finish()
                        } else {
                            AccountActivity.checkNotificationPermission(requireContext(), notificationPermissionLauncher)

                            val fragmentManager = activity.supportFragmentManager
                            fragmentManager
                                .beginTransaction()
                                .replace(R.id.fragment_container, LoggedInFragment())
                                .commit()
                            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                        }
                    }
                    is LoginState.Error -> {
                        showSnackbar(text = state.exception.message.toString())
                    }
                    is LoginState.Idle -> { /* Not needed */ }
                }
            }
        }
    }

    private fun attemptLogin() {
        val username = username.text.toString().trim()
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
        val endpoint = getEndpoint()

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
        loginLogo.isVisible = !(isCompactWidth && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }
}
