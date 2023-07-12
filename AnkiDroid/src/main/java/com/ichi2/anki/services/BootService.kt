//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.services

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ichi2.anki.*
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.preferences.Preferences
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.Permissions.hasStorageAccessPermission
import timber.log.Timber
import java.util.Calendar

class BootService : BroadcastReceiver() {
    private var mFailedToShowNotifications = false
    override fun onReceive(context: Context, intent: Intent) {
        if (sWasRun) {
            Timber.d("BootService - Already run")
            return
        }
        if (!hasStorageAccessPermission(context)) {
            Timber.w("Boot Service did not execute - no permissions")
            return
        }
        // There are cases where the app is installed, and we have access, but nothing exist yet
        val col = getColSafe(context)
        if (col == null) {
            Timber.w("Boot Service did not execute - error loading collection")
            return
        }
        Timber.i("Executing Boot Service")
        catchAlarmManagerErrors(context) { scheduleDeckReminder(context) }
        catchAlarmManagerErrors(context) { scheduleNotification(TimeManager.time, context) }
        mFailedToShowNotifications = false
        sWasRun = true
    }

    private fun catchAlarmManagerErrors(context: Context, runnable: Runnable) {
        // #6332 - Too Many Alarms on Samsung Devices - this stops a fatal startup crash.
        // We warn the user if they breach this limit
        var error: Int? = null
        try {
            runnable.run()
        } catch (ex: SecurityException) {
            Timber.w(ex)
            error = R.string.boot_service_too_many_notifications
        } catch (e: Exception) {
            Timber.w(e)
            error = R.string.boot_service_failed_to_schedule_notifications
        }
        if (error != null) {
            if (!mFailedToShowNotifications) {
                showThemedToast(context, context.getString(error), false)
            }
            mFailedToShowNotifications = true
        }
    }

    private fun getColSafe(context: Context): Collection? {
        // #6239 - previously would crash if ejecting, we don't want a report if this happens so don't use
        // getInstance().getColSafe
        return try {
            CollectionHelper.instance.getCol(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get collection for boot service - possibly media ejecting")
            null
        }
    }

    private fun scheduleDeckReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (deckConfiguration in CollectionHelper.instance.getCol(context)!!.decks.allConf()) {
            if (deckConfiguration.has("reminder")) {
                val reminder = deckConfiguration.getJSONObject("reminder")
                if (reminder.getBoolean("enabled")) {
                    val reminderIntent = CompatHelper.compat.getImmutableBroadcastIntent(
                        context,
                        deckConfiguration.getLong("id").toInt(),
                        Intent(context, ReminderService::class.java).putExtra(
                            ReminderService.EXTRA_DECK_OPTION_ID,
                            deckConfiguration.getLong("id")
                        ),
                        0
                    )
                    val calendar = DeckOptionsActivity.reminderToCalendar(TimeManager.time, reminder)
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        reminderIntent
                    )
                }
            }
        }
    }

    companion object {
        /**
         * This service is also run when the app is started (from [com.ichi2.anki.AnkiDroidApp],
         * so we need to make sure that it isn't run twice.
         */
        private var sWasRun = false

        fun scheduleNotification(time: Time, context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sp = context.sharedPrefs()
            // Don't schedule a notification if the due reminders setting is not enabled
            if (sp.getString(
                    Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION,
                    Integer.toString(Preferences.PENDING_NOTIFICATIONS_ONLY)
                )!!.toInt() >= Preferences.PENDING_NOTIFICATIONS_ONLY
            ) {
                return
            }
            val calendar = time.calendar()
            calendar.apply {
                set(Calendar.HOUR_OF_DAY, getRolloverHourOfDay(context))
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val notificationIntent = CompatHelper.compat.getImmutableBroadcastIntent(
                context,
                0,
                Intent(context, NotificationService::class.java),
                0
            )
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                notificationIntent
            )
        }

        /** Returns the hour of day when rollover to the next day occurs  */
        protected fun getRolloverHourOfDay(context: Context): Int {
            // TODO; We might want to use the BootService retry code here when called from preferences.
            val defValue = 4
            return try {
                val col = CollectionHelper.instance.getCol(context)!!
                when (col.schedVer()) {
                    1 -> {
                        val sp = context.sharedPrefs()
                        sp.getInt("dayOffset", defValue)
                    }
                    2 -> col.get_config("rollover", defValue)!!
                    else -> {
                        val sp = context.sharedPrefs()
                        sp.getInt("dayOffset", defValue)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
                defValue
            }
        }
    }
}
