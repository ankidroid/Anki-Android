// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.utils

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import com.ichi2.anki.utils.MessageQueue.Message
import com.ichi2.anki.utils.MessageQueue.MessageId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Shows the oldest of [messages] once the view is started, and acknowledges it when [show] returns. */
fun <T> Fragment.observeMessages(
    messages: StateFlow<List<Message<T>>>,
    acknowledge: (MessageId) -> Unit,
    show: suspend (T) -> Unit,
) {
    val owner = viewLifecycleOwner
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            messages.mapNotNull { it.firstOrNull() }.collect { pending ->
                owner.withStarted {}
                show(pending.message)
                acknowledge(pending.id)
            }
        }
    }
}

/** Shows the dialog and returns once it is dismissed. Cancelling the caller dismisses it. */
suspend fun AlertDialog.Builder.showAndAwaitDismissal(block: AlertDialog.Builder.() -> Unit) {
    block()
    suspendCancellableCoroutine { continuation ->
        val dialog = setOnDismissListener { continuation.resume(Unit) }.show()
        continuation.invokeOnCancellation { dialog.dismiss() }
    }
}
