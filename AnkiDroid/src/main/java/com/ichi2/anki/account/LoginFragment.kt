// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.account

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.account.AccountActivity.Companion.ARG_START_FOR_RESULT
import com.ichi2.anki.databinding.FragmentMyAccountBinding
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.showErrorAndAwaitDismissal
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.bottomCornerClearance
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.ext.isCompactWidth
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.hideKeyboard
import com.ichi2.anki.utils.observeMessages
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.utils.savedStateViewModelFactory
import com.ichi2.anki.utils.showAndAwaitDismissal
import com.ichi2.anki.utils.showSnackbarAndAwaitDismissal
import com.ichi2.utils.Permissions
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class LoginFragment : Fragment(R.layout.fragment_my_account) {
    private val viewModel: LoginViewModel by viewModels {
        savedStateViewModelFactory(create = ::LoginViewModel)
    }
    private val binding by viewBinding(FragmentMyAccountBinding::bind)
    private val credentialManager by lazy { CredentialManager.create(requireContext()) }
    private var credentialLookup: Job? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupEdgeToEdge(view)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)

        activity.supportActionBar?.apply {
            title = TR.sentenceCase.ankiWebAccount
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.loginButton.text = TR.sentenceCase.logIn
        updateLogoVisibility(resources.configuration)

        initListeners()
        initObservers()

        // a saved AnkiWeb password must not be sent to a custom server
        val afterLogout = arguments?.getBoolean(ARG_AFTER_LOGOUT) ?: false
        if (!viewModel.credentialLookupFinished && !afterLogout && usesAnkiWebEndpoint()) {
            loginWithSavedCredential()
        }
    }

    /** Applies edge-to-edge insets for the screen */
    private fun setupEdgeToEdge(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            val withKeyboard =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime(),
                )
            binding.toolbarContainer.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.accountContent.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(withKeyboard.bottom, insets.bottomCornerClearance(binding.accountContent)),
            )
            insets
        }
    }

    private fun login() {
        viewModel.finishCredentialLookup()
        credentialLookup?.cancel()
        hideKeyboard()

        val enteredUsername =
            binding.username.text
                .toString()
                .trim()
        val username = enteredUsername.removePrefix(ANKIWEB_CREDENTIAL_PREFIX)
        val fromPasswordManager = username != enteredUsername
        val password = binding.password.text.toString()
        viewModel.login(
            username = username,
            password = password,
            fromPasswordManager = fromPasswordManager,
            offerCredentialSave =
                !fromPasswordManager ||
                    enteredUsername != viewModel.autofilledUsername?.trim() ||
                    password != viewModel.autofilledPassword,
        )
    }

    private fun initListeners() {
        initUsernameListeners()
        initPasswordListeners()
        initButtonListeners()
    }

    private fun initUsernameListeners() {
        binding.username.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onUserNameFocusChange(hasFocus, binding.username.text.toString())
        }

        binding.username.doOnTextChanged { text, _, _, _ ->
            viewModel.onTextChanged(text.toString(), binding.password.text.toString())
        }
    }

    private fun initPasswordListeners() {
        binding.password.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onPasswordFocusChange(hasFocus, binding.password.text.toString())
        }

        binding.password.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (binding.loginButton.isEnabled) login()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        binding.password.doOnTextChanged { text, _, _, _ ->
            viewModel.onTextChanged(binding.username.text.toString(), text.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!usesAnkiWebEndpoint()) {
                binding.username.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                binding.password.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                return
            }
            binding.username.setAutoFillListener { value ->
                if (value.isText) viewModel.autofilledUsername = value.textValue.toString()
            }
            binding.password.setAutoFillListener { value ->
                if (value.isText) viewModel.autofilledPassword = value.textValue.toString()
                binding.passwordLayout.isEndIconVisible = false
            }
        }
    }

    private fun initButtonListeners() {
        binding.loginButton.setOnClickListener { login() }

        binding.resetPasswordButton.setOnClickListener { openUrl(resources.getString(R.string.resetpw_url).toUri()) }

        binding.signUpButton.setOnClickListener { openUrl(resources.getString(R.string.register_url).toUri()) }

        binding.lostMailInstructions.setOnClickListener {
            openUrl(resources.getString(R.string.link_ankiweb_lost_email_instructions).toUri())
        }

        binding.privacyPolicyButton.setOnClickListener {
            Timber.i("Opening 'Privacy policy'")
            showDialogFragment(HelpDialog.newPrivacyPolicyInstance())
        }
    }

    private fun initObservers() {
        observeProgress(viewModel) { getString(CommonString.sign_in) }

        observeMessages(viewModel.pendingMessages, viewModel::messageShown, ::showMessage)

        viewModel.loginButtonEnabled.collectInViewLifecycle { isEnabled ->
            binding.loginButton.isEnabled = isEnabled
        }

        viewModel.userNameError.collectInViewLifecycle { error ->
            binding.usernameLayout.error = error?.toHumanReadableString(requireContext())
        }

        viewModel.passwordError.collectInViewLifecycle { error ->
            binding.passwordLayout.error = error?.toHumanReadableString(requireContext())
        }
    }

    /** Returns once the user has dealt with [message], so it is shown again if the screen is recreated first */
    private suspend fun showMessage(message: LoginMessage) {
        when (message) {
            is LoginMessage.SaveCredential -> saveCredential(message.credential)
            LoginMessage.LoggedIn -> onLoginSuccess()
            is LoginMessage.Failed -> showLoginError(message.exception)
        }
    }

    private suspend fun onLoginSuccess() {
        Timber.i("Login Successful")
        val activity = requireActivity()
        val isForResult = arguments?.getBoolean(ARG_START_FOR_RESULT) ?: false

        // If the user explicitly came from a sync prompt (onboarding/pressing sync)
        // then their intent was to sync after login success
        if (isForResult) {
            activity.setResult(RESULT_OK)
            activity.finish()
            return
        }
        showLoginSuccessDialog()
    }

    private suspend fun showLoginError(exception: Exception) {
        when (exception) {
            // the locked-collection guidance is too long for a snackbar
            is CollectionLockedException ->
                requireContext().showErrorAndAwaitDismissal(
                    exception.localizedMessage!!,
                    exception.toCrashReportData(requireContext(), reportException = false),
                )
            else -> showSnackbarAndAwaitDismissal(exception.message.toString())
        }
    }

    /**
     * Submits a credential explicitly chosen from the system's [CredentialManager], if any.
     * Leave the user to fix manually if sign-in fails.
     */
    private fun loginWithSavedCredential() {
        val request = GetCredentialRequest(listOf(GetPasswordOption()))
        val owner = viewLifecycleOwner
        credentialLookup =
            owner.lifecycleScope.launch {
                val canLookUpCredential = owner.withResumed { usesAnkiWebEndpoint() }
                if (!canLookUpCredential) return@launch
                val credential =
                    try {
                        credentialManager.getCredential(requireActivity(), request).credential
                    } catch (_: GetCredentialCancellationException) {
                        Timber.i("User cancelled credential retrieval")
                        return@launch
                    } catch (_: NoCredentialException) {
                        Timber.i("No saved credentials found")
                        return@launch
                    } catch (e: GetCredentialException) {
                        Timber.w(e, "Failed to retrieve credentials")
                        return@launch
                    } finally {
                        // If recreation cancelled the lookup, the new view should offer the picker again.
                        if (isActive) viewModel.finishCredentialLookup()
                    }
                ensureActive()
                if (credential !is PasswordCredential) {
                    Timber.w("Unexpected credential type: %s", credential.type)
                    return@launch
                }
                // Autofill providers may also return app passwords saved for a custom server.
                if (!credential.id.startsWith(ANKIWEB_CREDENTIAL_PREFIX) || !usesAnkiWebEndpoint()) return@launch
                Timber.i("Attempting login from saved credential")
                viewModel.login(
                    username = credential.id.removePrefix(ANKIWEB_CREDENTIAL_PREFIX).trim(),
                    password = credential.password,
                    fromPasswordManager = true,
                )
            }
    }

    private suspend fun saveCredential(credential: PasswordCredential) {
        try {
            credentialManager.createCredential(
                requireActivity(),
                CreatePasswordRequest(id = ANKIWEB_CREDENTIAL_PREFIX + credential.id, password = credential.password),
            )
            Timber.i("Credential saved")
        } catch (_: CreateCredentialCancellationException) {
            Timber.i("User declined to save credential")
        } catch (e: CreateCredentialException) {
            // also thrown if no provider is available or saving is disabled in the system settings
            Timber.i("Credential not saved: %s", e.type)
        }
    }

    /**
     * Displays a dialog asking if a user would like to sync after a login success
     *
     * * **Positive:** opens the Deck Picker and starts a sync
     * * **Negative:** continues to [LoggedInFragment]
     */
    private suspend fun showLoginSuccessDialog() {
        /** @see LoggedInFragment */
        fun showLoggedInView() {
            Timber.i("Showing LoggedIn view")
            val fragmentManager = requireActivity().supportFragmentManager
            fragmentManager.popBackStack(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
            fragmentManager.commit {
                replace(R.id.fragment_container, LoggedInFragment())
            }
            Permissions.requestNotificationPermissionsForSyncing(requireActivity())
        }

        /** @see DeckPicker.onNewIntent */
        fun openDeckPickerAndSync() {
            Timber.i("Opening Deck Picker for Sync")
            val intent =
                DeckPicker.getIntent(
                    requireContext(),
                    autoSync = true,
                )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            requireActivity().finish()
        }

        MaterialAlertDialogBuilder(requireContext()).showAndAwaitDismissal {
            Timber.i("Showing dialog: 'Sync now?'")
            setTitle(CommonString.login_successful)
            setIcon(R.drawable.ic_sync)
            setMessage(CommonString.sync_now)
            positiveButton(CommonString.button_sync) { openDeckPickerAndSync() }
            negativeButton(CommonString.dialog_continue) { showLoggedInView() }
            setOnCancelListener { showLoggedInView() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLogoVisibility(newConfig)
    }

    private fun updateLogoVisibility(configuration: Configuration) {
        binding.loginLogo.isVisible = !(isCompactWidth && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    companion object {
        private const val ANKIWEB_CREDENTIAL_PREFIX = "AnkiWeb: "

        /** Whether the user just logged out, so they are not signed in again with a saved credential */
        private const val ARG_AFTER_LOGOUT = "ARG_AFTER_LOGOUT"

        fun afterLogout() = LoginFragment().apply { arguments = Bundle().apply { putBoolean(ARG_AFTER_LOGOUT, true) } }
    }
}
