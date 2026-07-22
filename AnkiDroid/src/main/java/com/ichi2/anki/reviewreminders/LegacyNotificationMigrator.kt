/*
 *  Copyright (c) 2026 AnkiDroid Contributors
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.preferences.PENDING_NOTIFICATIONS_ONLY
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.settings.Prefs
import timber.log.Timber

/**
 * One-time migration from the legacy daily due-card notification preference to the
 * [ReviewReminder] system.
 *
 * The legacy path used [AlarmManager.setRepeating] and read a cached widget due-count from MetaDB.
 * Both of those behaviors are unreliable on modern Android (Doze / process death), so production
 * traffic is moved to [com.ichi2.anki.services.AlarmManagerService] + live due-count queries.
 *
 * Migration preserves the user's threshold and fires at the collection day-rollover hour
 * (same wall-clock intent as the legacy daily alarm).
 */
object LegacyNotificationMigrator {
    /**
     * Promotes the review-reminder system and, when the user previously had legacy notifications
     * enabled, creates an equivalent global [ReviewReminder].
     *
     * Safe to call repeatedly: subsequent invocations are no-ops once
     * [Prefs.legacyNotificationsMigrated] is true.
     */
    suspend fun migrateIfNeeded(context: Context) {
        if (Prefs.legacyNotificationsMigrated) {
            Timber.d("Legacy notification migration already completed")
            return
        }

        Timber.i("Starting legacy notification → review reminder migration")
        Prefs.newReviewRemindersEnabled = true

        val legacyThreshold = readLegacyThreshold(context)
        if (legacyThreshold < PENDING_NOTIFICATIONS_ONLY) {
            migrateEnabledLegacyPreference(context, legacyThreshold)
        } else {
            Timber.i("Legacy notifications were disabled; enabling review reminders UI only")
        }

        cancelLegacyRepeatingAlarm(context)
        Prefs.legacyNotificationsMigrated = true
        Timber.i("Legacy notification migration completed")
    }

    @VisibleForTesting
    internal suspend fun migrateEnabledLegacyPreference(
        context: Context,
        legacyThreshold: Int,
    ) {
        val existingGlobal = ReviewRemindersDatabase.getRemindersForScope(ReviewReminderScope.Global)
        if (!existingGlobal.isEmpty()) {
            Timber.i(
                "Skipping reminder creation: %d global review reminder(s) already exist",
                existingGlobal.getRemindersList().size,
            )
        } else {
            val hour = readRolloverHourOfDay(context)
            val reminder =
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(hour, 0),
                    cardTriggerThreshold = ReviewReminderCardTriggerThreshold(legacyThreshold),
                    scope = ReviewReminderScope.Global,
                    enabled = true,
                )
            ReviewRemindersDatabase.insertReminder(reminder)
            Timber.i(
                "Created migrated global review reminder id=%d threshold=%d hour=%d",
                reminder.id.value,
                legacyThreshold,
                hour,
            )
        }

        // Disable the legacy pref so the two systems cannot both appear active in settings.
        context.sharedPrefs().edit {
            putString(
                context.getString(R.string.pref_notifications_minimum_cards_due_key),
                PENDING_NOTIFICATIONS_ONLY.toString(),
            )
        }
    }

    @VisibleForTesting
    internal fun readLegacyThreshold(context: Context): Int =
        context
            .sharedPrefs()
            .getString(
                context.getString(R.string.pref_notifications_minimum_cards_due_key),
                PENDING_NOTIFICATIONS_ONLY.toString(),
            )!!
            .toInt()

    /**
     * Matches the hour used by the legacy [com.ichi2.anki.services.BootService.scheduleNotification]
     * daily alarm so migrated users keep the same notification wall-clock time.
     */
    @VisibleForTesting
    internal fun readRolloverHourOfDay(context: Context): Int {
        val defValue = 4
        return try {
            val col = CollectionManager.getColUnsafe()
            when (col.schedVer()) {
                1 -> context.sharedPrefs().getInt("dayOffset", defValue)
                2 -> col.config.get("rollover") ?: defValue
                else -> context.sharedPrefs().getInt("dayOffset", defValue)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read day rollover hour for migration; using default %d", defValue)
            defValue
        }
    }

    /**
     * Cancels the legacy request-code-0 [NotificationService] repeating alarm if one exists.
     */
    @VisibleForTesting
    internal fun cancelLegacyRepeatingAlarm(context: Context) {
        val pendingIntent =
            PendingIntentCompat.getBroadcast(
                context,
                0,
                Intent(context, NotificationService::class.java),
                0,
                false,
            ) ?: return
        try {
            context.getSystemService<AlarmManager>()?.cancel(pendingIntent)
            Timber.i("Cancelled legacy repeating notification alarm")
        } catch (e: Exception) {
            Timber.w(e, "Failed to cancel legacy repeating notification alarm")
        }
    }
}
