/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.Context
import android.content.Intent
import android.widget.TimePicker
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.ReminderNotificationHelper
import com.ichi2.anki.services.BootService
import com.ichi2.anki.services.NotificationService
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.AdaptionUtil

/**
 * Fragment with preferences related to notifications
 */
class NotificationsSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_notifications
    override val analyticsScreenNameConstant: String
        get() = "prefs.notifications"

    override fun initSubscreen() {
        initNewNotificationSubscreen()
        initLegacyNotificationSubscreen()
    }

    private fun initNewNotificationSubscreen() {
        val sharedPreference = AnkiDroidApp.getSharedPrefs(context)
        val notificationHelper = ReminderNotificationHelper(requireContext())
        val globalNotification =
            requirePreference<SwitchPreference>(R.string.pref_global_notification)
        val remindAtPreference = requirePreference<Preference>(R.string.pref_remind_at)

        val notificationEnabled = sharedPreference.getBoolean(
            ReminderNotificationHelper.GLOBAL_NOTIFICATION_ENABLED,
            false
        )
        val notificationTime = sharedPreference.getString(
            ReminderNotificationHelper.GLOBAL_NOTIFICATION_TIME,
            ReminderNotificationHelper.GLOBAL_NOTIFICATION_DEFAULT_TIME
        ) ?: ReminderNotificationHelper.GLOBAL_NOTIFICATION_DEFAULT_TIME
        var hourOfNotification = notificationTime.split(":")[0].toInt()
        var minutesOfNotification = notificationTime.split(":")[1].toInt()

        globalNotification.isChecked = notificationEnabled
        remindAtPreference.summary = notificationTime

        val timeSetListener = OnTimeSetListener { _: TimePicker, hour: Int, minutes: Int ->
            val time = requireContext().getString(
                R.string.notification_remind_at_summary,
                hour,
                minutes
            )
            hourOfNotification = hour
            minutesOfNotification = minutes
            remindAtPreference.summary = time
            sharedPreference.edit {
                putString(ReminderNotificationHelper.GLOBAL_NOTIFICATION_TIME, time)
            }
            notificationHelper.scheduleGlobalNotificationWorker()
        }
        remindAtPreference.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                TimePickerDialog(
                    context,
                    timeSetListener,
                    hourOfNotification,
                    minutesOfNotification,
                    true
                ).show()
                true
            }
        }

        globalNotification.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    notificationHelper.scheduleGlobalNotificationWorker()
                } else {
                    notificationHelper.cancelNotificationWorker()
                }
                sharedPreference.edit {
                    putBoolean(ReminderNotificationHelper.GLOBAL_NOTIFICATION_ENABLED, enabled)
                }
                true
            }
        }
    }

    private fun initLegacyNotificationSubscreen() {
        if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
            /** These preferences should be searchable or not based
             * on this same condition at [Preferences.configureSearchBar] */
            preferenceScreen.removePreference(requirePreference<SwitchPreference>(R.string.pref_notifications_vibrate_key))
            // TODO: Remove this preference when a phone don't have light.
            preferenceScreen.removePreference(requirePreference<SwitchPreference>(R.string.pref_notifications_blink_key))
        }
        // Minimum cards due
        // The number of cards that should be due today in a deck to justify adding a notification.
        requirePreference<ListPreference>(R.string.pref_notifications_minimum_cards_due_key).apply {
            updateNotificationPreference(this)
            setOnPreferenceChangeListener { preference, newValue ->
                updateNotificationPreference(preference as ListPreference)
                if ((newValue as String).toInt() < Preferences.PENDING_NOTIFICATIONS_ONLY) {
                    BootService.scheduleNotification(TimeManager.time, requireContext())
                } else {
                    val intent = CompatHelper.compat.getImmutableBroadcastIntent(
                        requireContext(), 0,
                        Intent(requireContext(), NotificationService::class.java), 0
                    )
                    val alarmManager =
                        requireActivity().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.cancel(intent)
                }
                true
            }
        }
    }

    private fun updateNotificationPreference(listPreference: ListPreference) {
        val entries = listPreference.entries
        val values = listPreference.entryValues
        for (i in entries.indices) {
            val value = values[i].toString().toInt()
            if (entries[i].toString().contains("%d")) {
                entries[i] = String.format(entries[i].toString(), value)
            }
        }
        listPreference.entries = entries
    }
}
