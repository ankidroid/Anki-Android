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

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

typealias DeckId = Long
typealias AppWidgetId = Int

/**
 * Data class representing the data for a deck displayed in the widget.
 *
 * @property deckId The ID of the deck.
 * @property name The name of the deck.
 * @property reviewCount The number of cards due for review.
 * @property learnCount The number of cards in the learning phase.
 * @property newCount The number of new cards.
 */
data class DeckPickerWidgetData(
    val deckId: DeckId,
    val name: String,
    val reviewCount: Int,
    val learnCount: Int,
    val newCount: Int
)

/**
 * This widget displays a list of decks with their respective new, learning, and review card counts.
 * It updates every minute.
 * It can be resized vertically & horizontally.
 * No user actions can be performed from this widget as of now; it is for display purposes only.
 * There is only one way to configure the widget i.e. while adding it on home screen,
 */
class DeckPickerWidget : AnalyticsWidgetProvider() {

    companion object {
        /**
         * Action identifier to trigger updating the app widget.
         * This constant is used to trigger the update of all widgets by the AppWidgetManager.
         */
        const val ACTION_APPWIDGET_UPDATE = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        /**
         * Custom action to update the widget.
         * This constant is used to trigger the widget update via a custom broadcast intent.
         */
        const val ACTION_UPDATE_WIDGET = "com.ichi2.widget.ACTION_UPDATE_WIDGET"

        /**
         * Updates the widget with the deck data.
         *
         * This method replaces the entire view content with entries for each deck ID
         * provided in the `deckIds` array. If any decks are deleted or unavailable,
         * they will be ignored, and only the available decks will be displayed.
         *
         * @param context the context of the application
         * @param appWidgetManager the AppWidgetManager instance
         * @param appWidgetId the ID of the app widget
         * @param deckIds the array of deck IDs to be displayed in the widget.
         *                Each ID corresponds to a specific deck, and the view will
         *                contain exactly the decks whose IDs are in this list.
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            deckIds: LongArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_deck_picker_large)

            AnkiDroidApp.applicationScope.launch {
                val deckData = getDeckNameAndStats(deckIds.toList().map { it })

                remoteViews.removeAllViews(R.id.deckCollection)

                for (deck in deckData) {
                    val deckView = RemoteViews(context.packageName, R.layout.widget_item_deck_main)

                    deckView.setTextViewText(R.id.deckName, deck.name)
                    deckView.setTextViewText(R.id.deckNew, deck.newCount.toString())
                    deckView.setTextViewText(R.id.deckDue, deck.reviewCount.toString())
                    deckView.setTextViewText(R.id.deckLearn, deck.learnCount.toString())

                    remoteViews.addView(R.id.deckCollection, deckView)
                }
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        }

        /**
         * Sets a recurring alarm to update the widget every minute, if necessary.
         *
         * If the alarm is already set for the widget, this method does nothing.
         * This ensures that multiple alarms are not created for the same widget,
         * preventing potential performance issues or unexpected behavior.
         *
         * @param context the context of the application
         * @param appWidgetId the ID of the widget
         */
        private fun setRecurringAlarm(context: Context, appWidgetId: AppWidgetId) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DeckPickerWidget::class.java).apply {
                action = ACTION_UPDATE_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

            /**
             * When onUpdate is called, the code checks if an existing alarm PendingIntent
             * is already set for the widget. If an alarm already exists, PendingIntent.getBroadcast
             * returns the existing PendingIntent, and pendingIntent is not null.
             * In this case, the method returns early and no new alarm is set.
             */

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
         */
        private fun cancelRecurringAlarm(context: Context, appWidgetId: AppWidgetId) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DeckPickerWidget::class.java).apply {
                action = ACTION_UPDATE_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            Timber.d("Canceling recurring alarm for widget ID: $appWidgetId")
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        usageAnalytics: UsageAnalytics
    ) {
        val widgetPreferences = WidgetPreferences(context)

        for (widgetId in appWidgetIds) {
            val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferencesDeckPickerWidget(widgetId)

            /**Explanation of behavior when selectedDeckIds is empty
             * If selectedDeckIds is empty, the widget will retain the previous deck list.
             * This behavior ensures that the widget does not display an empty view, which could be
             * confusing to the user. Instead, it maintains the last known state until a new valid
             * list of deck IDs is provided. This approach prioritizes providing a consistent
             * user experience over showing an empty or default state.
             */
            if (selectedDeckIds.isNotEmpty()) {
                updateWidget(context, appWidgetManager, widgetId, selectedDeckIds)
            }
            setRecurringAlarm(context, widgetId)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Timber.e("Context or intent is null in onReceive")
            return
        }
        super.onReceive(context, intent)

        val widgetPreferences = WidgetPreferences(context)

        when (intent.action) {
            // This action is received when the AppWidgetManager broadcasts an update request.
            // It typically happens when the widget is added, resized, or updated from an external trigger.
            ACTION_APPWIDGET_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                val selectedDeckIds = intent.getLongArrayExtra("deck_picker_widget_selected_deck_ids")

                if (appWidgetIds != null && selectedDeckIds != null) {
                    for (appWidgetId in appWidgetIds) {
                        updateWidget(context, appWidgetManager, appWidgetId, selectedDeckIds)
                    }
                }
            }
            // This custom action is received to update a specific widget.
            // It is triggered by the setRecurringAlarm method to refresh the widget's data periodically.
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferencesDeckPickerWidget(appWidgetId)
                    if (selectedDeckIds.isNotEmpty()) {
                        updateWidget(context, appWidgetManager, appWidgetId, selectedDeckIds)
                    }
                }
            }
        }
    }

    /**
     * Cancels the recurring alarm for the deleted widgets, and the preference values associated to those widgets.
     *
     * @param context the context of the application
     * @param appWidgetIds the array of widget IDs being deleted
     */
    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        // Ensure context is not null
        if (context == null) {
            Timber.e("Context is null in onDeleted")
            return
        }

        val widgetPreferences = WidgetPreferences(context)

        // Proceed with deleting widget preferences and canceling alarms
        appWidgetIds?.forEach { widgetId ->
            cancelRecurringAlarm(context, widgetId)
            widgetPreferences.deleteDeckPickerWidgetData(widgetId)
        }
    }
}

/**
 * Map deck id to the associated DeckPickerWidgetData. Omits any id that does not correspond to a deck.
 *
 * Note: This operation may be slow, as it involves processing the entire deck collection.
 *
 * @param deckIds the list of deck IDs to retrieve data for
 * @return a list of DeckPickerWidgetData objects containing deck names and statistics
 */
suspend fun getDeckNameAndStats(deckIds: List<DeckId>): List<DeckPickerWidgetData> {
    val result = mutableListOf<DeckPickerWidgetData>()

    val deckTree = withCol { sched.deckDueTree() }

    deckTree.forEach { node ->
        if (node.did !in deckIds) return@forEach
        result.add(
            DeckPickerWidgetData(
                deckId = node.did,
                name = node.lastDeckNameComponent,
                reviewCount = node.revCount,
                learnCount = node.lrnCount,
                newCount = node.newCount
            )
        )
    }

    val deckIdToData = result.associateBy { it.deckId }
    return deckIds.mapNotNull { deckIdToData[it] }
}
