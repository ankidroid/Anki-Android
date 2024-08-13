/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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

package com.ichi2.widget

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.compat.CompatHelper.Companion.registerReceiverCompat
import timber.log.Timber

/**
 * CardsDueWidget is a widget for displaying the number of due cards with additional card analysis data.
 */
class CardsDueWidget : AnalyticsWidgetProvider() {

    /**
     * Updates the widget when called. This is the main method for refreshing the widget's view.
     *
     * @param context The context from which the method is called.
     * @param appWidgetManager The AppWidgetManager instance used to update the widget.
     * @param appWidgetIds The IDs of the widgets to be updated.
     * @param usageAnalytics The usage analytics instance for tracking widget interactions.
     */
    override fun performUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, usageAnalytics: UsageAnalytics) {
        updateWidget(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val preferences = context.sharedPrefs()
        preferences.edit(commit = true) { putBoolean("cardsDueWidgetEnabled", true) }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val preferences = context.sharedPrefs()
        preferences.edit(commit = true) { putBoolean("cardsDueWidgetEnabled", false) }
    }

    /**
     * Handles broadcasted intents of the widget.
     * Triggers a widget update upon receiving any supported action.
     *
     * @param context The context from which the method is called.
     * @param intent The intent that was received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action.contentEquals("com.sec.android.widgetapp.APPWIDGET_RESIZE")) {
            updateWidget(context)
        }
        updateWidget(context) // Ensures the widget is updated after receiving any intent
    }

    /**
     * UpdateService is a background service responsible for updating the widget periodically or on demand.
     */
    class UpdateService : Service() {
        /** The cached number of total due cards. */
        private var dueCardsCount = 0

        /**
         * Called when the service is started. Updates the widget immediately.
         *
         * @param intent The intent that started the service.
         * @param flags Additional data about the start request.
         * @param startId A unique integer representing this specific request to start.
         * @return The mode in which the system should handle the service if it is killed.
         */
        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            Timber.i("CardsDueWidget: onStartCommand")
            val manager = getAppWidgetManager(this) ?: return super.onStartCommand(intent, flags, startId)
            val updateViews = buildUpdate(this)
            val thisWidget = ComponentName(this, CardsDueWidget::class.java)
            manager.updateAppWidget(thisWidget, updateViews)
            updateWidget(this) // Trigger an instant update
            return super.onStartCommand(intent, flags, startId)
        }

        /**
         * Constructs the RemoteViews object that defines the widget's layout and updates it with
         * the latest data.
         *
         * @param context The context from which the method is called.
         * @return The RemoteViews object that will be displayed in the widget.
         */
        private fun buildUpdate(context: Context): RemoteViews {
            Timber.d("buildUpdate")
            val updateViews = RemoteViews(context.packageName, R.layout.widget_cards_due)
            val mounted = AnkiDroidApp.isSdCardMounted
            if (!mounted) {
                updateViews.setViewVisibility(R.id.dueNumberTextCardsDue, View.INVISIBLE)
                updateViews.setViewVisibility(R.id.etaNumberTextCardsDue, View.INVISIBLE)
                if (mountReceiver == null) {
                    mountReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val action = intent.action
                            if (action != null && action == Intent.ACTION_MEDIA_MOUNTED) {
                                Timber.d("mountReceiver - Action = Media Mounted")
                                if (remounted) {
                                    WidgetStatus.updateInBackground(AnkiDroidApp.instance)
                                    remounted = false
                                    if (mountReceiver != null) {
                                        AnkiDroidApp.instance.unregisterReceiver(mountReceiver)
                                    }
                                } else {
                                    remounted = true
                                }
                            }
                        }
                    }
                    val iFilter = IntentFilter()
                    iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED)
                    iFilter.addDataScheme("file")
                    AnkiDroidApp.instance.registerReceiverCompat(mountReceiver, iFilter, ContextCompat.RECEIVER_EXPORTED)
                }
            } else {
                val counts = WidgetStatus.fetchSmall(context)
                dueCardsCount = counts[0]
                val eta = counts[1]
                updateViews.setViewVisibility(R.id.dueNumberTextCardsDue, if (dueCardsCount > 0) View.VISIBLE else View.INVISIBLE)
                updateViews.setViewVisibility(R.id.etaNumberTextCardsDue, if (eta > 0 && dueCardsCount > 0) View.VISIBLE else View.INVISIBLE)
                updateViews.setTextViewText(R.id.dueNumberTextCardsDue, dueCardsCount.toString())
                updateViews.setTextViewText(R.id.etaNumberTextCardsDue, eta.toString())
            }

            val ankiDroidIntent = Intent(context, IntentHandler::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Ensures the correct task is opened
            }
            val pendingAnkiDroidIntent = PendingIntent.getActivity(
                context,
                0,
                ankiDroidIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Ensures the intent is immutable if needed
            )
            updateViews.setOnClickPendingIntent(R.id.mainLayoutCardsDue, pendingAnkiDroidIntent)
            return updateViews
        }

        /**
         * This service does not support binding, so this method returns null.
         *
         * @param intent The intent that was used to bind to the service.
         * @return Always returns null as binding is not supported.
         */
        override fun onBind(intent: Intent?): IBinder? {
            Timber.d("onBind")
            return null
        }
    }

    companion object {
        private var mountReceiver: BroadcastReceiver? = null
        private var remounted = false

        /**
         * Updates the widget by fetching the latest data and applying it to the widget's views.
         *
         * @param context The context from which the method is called.
         */
        private fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, CardsDueWidget::class.java))
            for (id in ids) {
                val updateViews = RemoteViews(context.packageName, R.layout.widget_cards_due)
                val counts = WidgetStatus.fetchSmall(context)
                val dueCardsCount = counts[0]
                val eta = counts[1]
                updateViews.setViewVisibility(R.id.dueNumberTextCardsDue, if (dueCardsCount > 0) View.VISIBLE else View.INVISIBLE)
                updateViews.setViewVisibility(R.id.etaNumberTextCardsDue, if (eta > 0 && dueCardsCount > 0) View.VISIBLE else View.INVISIBLE)
                updateViews.setTextViewText(R.id.dueNumberTextCardsDue, dueCardsCount.toString())
                updateViews.setTextViewText(R.id.etaNumberTextCardsDue, eta.toString())

                appWidgetManager.updateAppWidget(id, updateViews)
            }
        }
    }
}
