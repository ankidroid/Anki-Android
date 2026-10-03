// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.utils

import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.show
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MessageQueueObserverTest : RobolectricTest() {
    private val queue = MessageQueue<String>()

    @Test
    fun `cancelling a dialog acknowledges only its message`() {
        queue.enqueue("first")
        queue.enqueue("second")
        val (first, second) = queue.messages.value

        AlertDialog
            .Builder(ContextThemeWrapper(targetContext, R.style.Theme_Light))
            .show { acknowledgeOnDismiss(first.id, queue::acknowledge) }
            .cancel()
        advanceRobolectricLooper()

        assertEquals(listOf(second), queue.messages.value)
    }
}
