/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2025 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
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

package com.ichi2.anki.dialogs

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * ViewModel for [AnkiProgressDialogFragment] that manages dialog state using StateFlow pattern.
 *
 * The ViewModel:
 * - Survives configuration changes automatically
 * - Can be easily tested in isolation
 * - Encapsulates dialog state logic
 * - Uses StateFlow/SharedFlow for reactive UI updates
 */
class AnkiProgressDialogViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    @Parcelize
    data class Progress(
        val currentProgress: Int,
        val maxProgress: Int,
    ) : Parcelable

    // StateFlows for UI state
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    private val _cancelableViaBackButton = MutableStateFlow(false)
    val cancelableViaBackButton: StateFlow<Boolean> = _cancelableViaBackButton

    private val _cancelButtonText = MutableStateFlow<String?>(null)
    val cancelButtonText: StateFlow<String?> = _cancelButtonText

    private var onCancelListener: (() -> Unit)? = null

    init {
        _message.value = savedStateHandle.get<String>(KEY_MESSAGE) ?: ""
        _cancelableViaBackButton.value = savedStateHandle.get<Boolean>(KEY_CANCELABLE) ?: false
        _cancelButtonText.value = savedStateHandle.get<String>(KEY_CANCEL_BUTTON_TEXT)
        _progress.value = savedStateHandle.get<Progress>(KEY_PROGRESS)
    }

    fun updateMessage(newMessage: String) {
        Timber.d(
            "ViewModel.updateMessage called with: '%s', current message: '%s'",
            newMessage,
            _message.value,
        )

        if (newMessage.isNotBlank()) {
            _message.value = newMessage
            savedStateHandle[KEY_MESSAGE] = newMessage
            Timber.d("Message updated to: '%s'", newMessage)
        }
    }

    fun updateProgress(
        current: Int,
        max: Int,
    ) {
        val newProgress = Progress(current, max)
        _progress.value = newProgress
        savedStateHandle[KEY_PROGRESS] = newProgress
    }

    fun cancel() {
        onCancelListener?.invoke()
    }

    fun setOnCancelListener(listener: (() -> Unit)?) {
        onCancelListener = listener
        savedStateHandle[KEY_HAS_CANCEL_LISTENER] = listener != null
    }

    fun setCancelable(cancelable: Boolean) {
        _cancelableViaBackButton.value = cancelable
        savedStateHandle[KEY_CANCELABLE] = cancelable
    }

    fun setCancelButtonText(text: String?) {
        _cancelButtonText.value = text
        savedStateHandle[KEY_CANCEL_BUTTON_TEXT] = text
    }

    fun hasCancelListener(): Boolean = onCancelListener != null

    fun setup(
        message: String,
        cancelableViaBackButton: Boolean = false,
        cancelButtonText: String? = null,
        onCancelListener: (() -> Unit)? = null,
    ) {
        _message.value = message
        _cancelableViaBackButton.value = cancelableViaBackButton
        _cancelButtonText.value = cancelButtonText
        this.onCancelListener = onCancelListener

        savedStateHandle[KEY_MESSAGE] = message
        savedStateHandle[KEY_CANCELABLE] = cancelableViaBackButton
        savedStateHandle[KEY_CANCEL_BUTTON_TEXT] = cancelButtonText
        savedStateHandle[KEY_HAS_CANCEL_LISTENER] = onCancelListener != null
    }

    companion object {
        private const val KEY_MESSAGE = "message"
        private const val KEY_CANCELABLE = "cancelableViaBackButton"
        private const val KEY_CANCEL_BUTTON_TEXT = "cancelButtonText"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_HAS_CANCEL_LISTENER = "hasCancelListener"
    }
}
