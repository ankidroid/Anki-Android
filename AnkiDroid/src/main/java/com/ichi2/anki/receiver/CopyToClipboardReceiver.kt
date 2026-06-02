// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 LUwUcifer <luwucifwer@proton.me>
package com.ichi2.anki.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.utils.copyToClipboard
import timber.log.Timber

class CopyToClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val text =
            intent.getStringExtra(EXTRA_SYNC_ERROR_LOG) ?: run {
                Timber.w("CopyToClipboardReceiver: no error log found")
                showThemedToast(context, R.string.something_wrong, shortLength = true)
                return
            }
        NotificationManagerCompat.from(context).cancel(NotificationId.SYNC_MEDIA)
        context.copyToClipboard(text)
    }

    companion object {
        const val EXTRA_SYNC_ERROR_LOG = "COPY ERROR"
    }
}
