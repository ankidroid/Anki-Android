// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.utils.Permissions
import timber.log.Timber

/**
 * Whether the blocker is actually able to do its job, and the user-facing
 * warning shown when it isn't.
 *
 * Android revokes accessibility permission whenever the app is force-stopped or
 * reinstalled, which silently disables blocking. Rather than fail quietly — the
 * worst outcome for something the user is relying on — a notification is posted
 * so the gap is visible.
 */
object BlockerStatus {
    /** True when the user turned the blocker on but the OS isn't running our service. */
    fun isEnabledButInactive(context: Context): Boolean = BlockerPrefs.isEnabled && !isAccessibilityServiceEnabled(context)

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        val component = ComponentName(context, BlockerAccessibilityService::class.java)
        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == component }
    }

    /** Intent to the system screen where the user re-enables the service. */
    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /**
     * Posts or clears the "blocker isn't running" warning. Safe to call often;
     * it is a no-op when the state hasn't changed in a user-visible way.
     */
    fun refreshInactiveNotification(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!isEnabledButInactive(context)) {
            manager.cancel(NotificationId.BLOCKER_INACTIVE)
            return
        }
        if (!Permissions.canPostNotifications(context)) {
            Timber.i("Blocker: inactive, but notification permission is not granted")
            return
        }
        Timber.w("Blocker: enabled but accessibility service is not running")
        val tapIntent =
            PendingIntent.getActivity(
                context,
                0,
                accessibilitySettingsIntent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, NotificationChannel.BLOCKER.id)
                .setSmallIcon(R.drawable.ic_star_notify)
                .setContentTitle(context.getString(R.string.blocker_inactive_title))
                .setContentText(context.getString(R.string.blocker_inactive_message))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.blocker_inactive_message)))
                .setContentIntent(tapIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
        manager.notify(NotificationId.BLOCKER_INACTIVE, notification)
    }
}
