/*
   Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
This program is free software; you can redistribute it and/or modify it under
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

package com.ichi2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Currently used in Deck Picker Widget and Card Analysis Extra Widget.
 */
abstract class WidgetAlarm : AnalyticsWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.ichi2.widget.ACTION_UPDATE_WIDGET"

        /**
         * Sets a recurring alarm to update the widget every minute.
         *
         * @param context the context of the application
         * @param appWidgetId the ID of the widget
         * @param widgetClass the class of the widget
         */
        fun setRecurringAlarm(context: Context, appWidgetId: Int, widgetClass: Class<*>) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, widgetClass).apply {
                action = ACTION_UPDATE_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

            if (pendingIntent != null) {
                Timber.v("Recurring alarm PendingIntent already exists for widget ID: $appWidgetId")
                return
            }

            Timber.v("Creating a new recurring alarm PendingIntent for widget ID: $appWidgetId")
            val newPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            // Set alarm to trigger every minute
            val ONE_MINUTE_MILLIS = 60.seconds.inWholeMilliseconds
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + ONE_MINUTE_MILLIS,
                ONE_MINUTE_MILLIS,
                newPendingIntent
            )
        }

        /**
         * Cancels the recurring alarm for the widget.
         *
         * @param context the context of the application
         * @param appWidgetId the ID of the widget
         * @param widgetClass the class of the widget
         */
        fun cancelRecurringAlarm(context: Context, appWidgetId: Int, widgetClass: Class<*>) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, widgetClass).apply {
                action = ACTION_UPDATE_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            Timber.d("Canceling recurring alarm for widget ID: $appWidgetId")
            alarmManager.cancel(pendingIntent)
        }
    }
}
