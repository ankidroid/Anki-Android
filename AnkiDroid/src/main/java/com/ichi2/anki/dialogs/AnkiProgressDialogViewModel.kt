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
import kotlinx.parcelize.Parcelize

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

    val message = MutableStateFlow(savedStateHandle.get<String>(KEY_MESSAGE) ?: "")

    val cancelableViaBackButton = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_CANCELABLE) ?: false)

    val cancelButtonText = MutableStateFlow(savedStateHandle.get<String>(KEY_CANCEL_BUTTON_TEXT))

    val progress = MutableStateFlow(savedStateHandle.get<Progress>(KEY_PROGRESS))

    private var onCancelListener: (() -> Unit)? = null

    fun updateMessage(newMessage: String) {
        if (newMessage.isNotBlank()) {
            savedStateHandle[KEY_MESSAGE] = newMessage
            message.value = newMessage
        }
    }

    fun updateProgress(
        current: Int,
        max: Int,
    ) {
        val newProgress = Progress(current, max)
        progress.value = newProgress
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
        savedStateHandle[KEY_CANCELABLE] = cancelable
        cancelableViaBackButton.value = cancelable
    }

    fun setCancelButtonText(text: String?) {
        savedStateHandle[KEY_CANCEL_BUTTON_TEXT] = text
        cancelButtonText.value = text
    }

    fun hasCancelListener(): Boolean = onCancelListener != null

    fun setup(
        message: String,
        cancelableViaBackButton: Boolean = false,
        cancelButtonText: String? = null,
        onCancelListener: (() -> Unit)? = null,
    ) {
        updateMessage(message)
        setCancelable(cancelableViaBackButton)
        setCancelButtonText(cancelButtonText)
        setOnCancelListener(onCancelListener)
    }

    companion object {
        private const val KEY_MESSAGE = "message"
        private const val KEY_CANCELABLE = "cancelableViaBackButton"
        private const val KEY_CANCEL_BUTTON_TEXT = "cancelButtonText"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_HAS_CANCEL_LISTENER = "hasCancelListener"
    }
}
