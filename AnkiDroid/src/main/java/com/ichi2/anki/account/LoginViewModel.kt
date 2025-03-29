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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.sync.SyncAuth
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.libanki.syncLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.exceptions.BackendSyncException

/**
 * ViewModel that manages the state for user login. It handles the login process,
 * validates input fields, and provides results for login operations.
 */
class LoginViewModel : ViewModel() {
    private val _loginButtonEnabled = MutableStateFlow(false)
    val loginButtonEnabled: StateFlow<Boolean> = _loginButtonEnabled.asStateFlow()

    private val _userNameError = MutableStateFlow<LoginError>(LoginError.NONE)
    val userNameError: StateFlow<LoginError> = _userNameError.asStateFlow()

    private val _passwordError = MutableStateFlow<LoginError>(LoginError.NONE)
    val passwordError: StateFlow<LoginError> = _passwordError.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun onUserNameFocusChange(
        hasFocus: Boolean,
        userName: String,
    ) {
        _userNameError.value = if (!hasFocus && userName.isEmpty()) LoginError.EMPTY_USERNAME else LoginError.NONE
    }

    fun onPasswordFocusChange(
        hasFocus: Boolean,
        password: String,
    ) {
        _passwordError.value = if (!hasFocus && password.isEmpty()) LoginError.EMPTY_PASSWORD else LoginError.NONE
    }

    fun onTextChanged(
        userName: String,
        password: String,
    ) {
        _loginButtonEnabled.value = userName.isNotEmpty() && password.isNotEmpty()
    }

    /**
     * Handles the login process by attempting to authenticate the user with the provided credentials.
     * If authentication is successful, it updates the login state; otherwise, it handles authentication failure.
     *
     * @param username The username entered by the user.
     * @param password The password entered by the user.
     * @param endpoint An endpoint for authentication.
     */
    fun handleLogin(
        username: String,
        password: String,
        endpoint: String?,
    ) {
        viewModelScope.launch {
            try {
                val auth = syncLogin(username, password, endpoint)
                updateLogin(username, auth.hkey)
                _loginState.value = LoginState.Success
            } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
                updateLogin("", "")
                _loginState.value = LoginState.Error(exc)
            }
        }
    }

    private suspend fun syncLogin(
        username: String,
        password: String,
        endpoint: String?,
    ): SyncAuth =
        withCol {
            syncLogin(username, password, endpoint)
        }

    private fun updateLogin(
        username: String,
        hkey: String,
    ) {
        Prefs.username = username
        Prefs.hkey = hkey
    }
}

/**
 * Enum representing the possible error states for the login fields.
 * It is used to track errors related to the username and password fields.
 */
enum class LoginError {
    EMPTY_USERNAME,
    EMPTY_PASSWORD,
    NONE,
    ;

    fun toHumanReadableString(context: Context): String? =
        when (this) {
            EMPTY_USERNAME -> context.getString(R.string.invalid_email)
            EMPTY_PASSWORD -> context.getString(R.string.password_empty)
            NONE -> null
        }
}

/** Handles the Login State */
sealed class LoginState {
    data object Idle : LoginState()

    data object Success : LoginState()

    data class Error(
        val exception: Exception,
    ) : LoginState()
}
