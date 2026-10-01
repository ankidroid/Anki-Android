// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.utils

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.utils.MessageQueue.Message
import com.ichi2.anki.utils.MessageQueue.MessageId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** Shows the oldest of [messages] while the view is started, then the next once it is acknowledged. */
fun <T> Fragment.observeMessages(
    messages: StateFlow<List<Message<T>>>,
    show: suspend (Message<T>) -> Unit,
) {
    val owner = viewLifecycleOwner
    owner.lifecycleScope.launch {
        var shownId: MessageId? = null
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            messages
                .mapNotNull { it.firstOrNull() }
                .filter { it.id != shownId }
                .collect {
                    shownId = it.id
                    show(it)
                }
        }
    }
}

/** Acknowledges the message with [id] however the dialog is dismissed. */
fun AlertDialog.Builder.acknowledgeOnDismiss(
    id: MessageId,
    acknowledge: (MessageId) -> Unit,
): AlertDialog.Builder = setOnDismissListener { acknowledge(id) }
