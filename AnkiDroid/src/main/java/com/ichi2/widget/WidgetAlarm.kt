/*
 * Copyright (c) 2021 Prateek Singh <prateeksingh3212@gmail.com>
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.widget

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ichi2.anki.CollectionHelper
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.util.*

/**
 * Updates the widget using Alarm Manager.
 * Used this implementation to conserve the battery as suggested by android documentation.
 * */
class WidgetAlarm() : BroadcastReceiver() {

    // Updates the widget in every 30 minutes.
    private val TIME_INTERVAL: Long = AlarmManager.INTERVAL_HALF_HOUR
    private val REQUEST_CODE: Int = 5

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Widget Alarm BRODCAST RECIVED")
        // Update the widget.
        AnkiDroidWidgetSmall.UpdateService().doUpdate(context)
    }

    /**
     * Starts the Widget Alarm. Used to update Widget in fixed interval of time.
     * @param context Application Context.
     * */
    fun setAlarm(context: Context) {
        val calendar: Calendar = CollectionHelper.getInstance().getCol(context).time.calendar()
        calendar.add(Calendar.MILLISECOND, TIME_INTERVAL.toInt())

        val alarmIntent = Intent(context, WidgetAlarm::class.java).let { intent ->
            CompatHelper.getCompat().getImmutableBroadcastIntent(context, REQUEST_CODE, intent, 0)
        }
        with(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
            setRepeating(AlarmManager.RTC, calendar.timeInMillis, TIME_INTERVAL, alarmIntent)
            Timber.d("Widget Alarm Set At: ${calendar.timeInMillis} interval -> $TIME_INTERVAL")
        }
    }

    /**
     * Stops the Widget Alarm.
     * @param context Application Context.
     * */
    fun stopAlarm(context: Context) {
        val alarmIntent = Intent(context, WidgetAlarm::class.java)
        val pendingIntent = CompatHelper.getCompat().getImmutableBroadcastIntent(context, REQUEST_CODE, alarmIntent, 0)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        Timber.d("Small Widget Alarm Stopped.")
    }
}
