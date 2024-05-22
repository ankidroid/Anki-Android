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
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.pages.DeckOptions
import kotlinx.coroutines.launch
import timber.log.Timber

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
 * It allows user to open the reviewer directly by clicking on the deck same as deckpicker.
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
         * Key used for passing the selected deck IDs in the intent extras.
         */
        const val EXTRA_SELECTED_DECK_IDS = "deck_picker_widget_selected_deck_ids"

        /**
         * Updates the widget with the deck data.
         *
         * This method replaces the entire view content with entries for each deck ID
         * provided in the `deckIds` array. If any decks are deleted,
         * they will be ignored, and only the rest of the decks will be displayed.
         *
         * @param context the context of the application
         * @param appWidgetManager the AppWidgetManager instance
         * @param appWidgetId the ID of the app widget
         * @param deckIds the array of deck IDs to be displayed in the widget.
         *                Each ID corresponds to a specific deck, and the view will
         *                contain exactly the decks whose IDs are in this list.
         *
         * TODO: If the deck is completely empty (no cards at all), display a Snackbar or Toast message
         *       saying "The deck is empty" instead of opening any activity.
         *
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            deckIds: LongArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_deck_picker_large)

            AnkiDroidApp.applicationScope.launch {
                val deckData = getDeckNameAndStats(deckIds.toList())

                remoteViews.removeAllViews(R.id.deckCollection)

                for (deck in deckData) {
                    val deckView = RemoteViews(context.packageName, R.layout.widget_item_deck_main)

                    deckView.setTextViewText(R.id.deckName, deck.name)
                    deckView.setTextViewText(R.id.deckNew, deck.newCount.toString())
                    deckView.setTextViewText(R.id.deckDue, deck.reviewCount.toString())
                    deckView.setTextViewText(R.id.deckLearn, deck.learnCount.toString())

                    val isEmptyDeck = deck.newCount == 0 && deck.reviewCount == 0 && deck.learnCount == 0

                    if (!isEmptyDeck) {
                        val intent = Intent(context, Reviewer::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra("deckId", deck.deckId)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            deck.deckId.toInt(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        deckView.setOnClickPendingIntent(R.id.deckName, pendingIntent)
                    } else {
                        val intent = DeckOptions.getIntent(context, deck.deckId)
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            deck.deckId.toInt(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        deckView.setOnClickPendingIntent(R.id.deckName, pendingIntent)
                    }

                    remoteViews.addView(R.id.deckCollection, deckView)
                }
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        }
    }

    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        usageAnalytics: UsageAnalytics
    ) {
        Timber.d("Performing widget update for appWidgetIds: ${appWidgetIds.joinToString(", ")}")

        val widgetPreferences = WidgetPreferences(context)

        for (widgetId in appWidgetIds) {
            Timber.d("Updating widget with ID: $widgetId")
            val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferencesDeckPickerWidget(widgetId)

            /**Explanation of behavior when selectedDeckIds is empty
             * If selectedDeckIds is empty, the widget will retain the previous deck list.
             * This behavior ensures that the widget does not display an empty view, which could be
             * confusing to the user. Instead, it maintains the last known state until a new valid
             * list of deck IDs is provided. This approach prioritizes providing a consistent
             * user experience over showing an empty or default state.
             */
            if (selectedDeckIds.isNotEmpty()) {
                Timber.d("Selected deck IDs: ${selectedDeckIds.joinToString(", ")} for widget ID: $widgetId")
                updateWidget(context, appWidgetManager, widgetId, selectedDeckIds)
            }
        }

        Timber.d("Widget update process completed for appWidgetIds: ${appWidgetIds.joinToString(", ")}")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Timber.e("Context or intent is null in onReceive")
            return
        }
        super.onReceive(context, intent)

        val widgetPreferences = WidgetPreferences(context)

        when (intent.action) {
            ACTION_APPWIDGET_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)

                // Retrieve the widget ID from the intent
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val selectedDeckIds = intent.getLongArrayExtra(EXTRA_SELECTED_DECK_IDS)

                Timber.d("Received ACTION_APPWIDGET_UPDATE with widget ID: $appWidgetId and selectedDeckIds: ${selectedDeckIds?.joinToString(", ")}")

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && selectedDeckIds != null) {
                    Timber.d("Updating widget with ID: $appWidgetId")
                    updateWidget(context, appWidgetManager, appWidgetId, selectedDeckIds)
                    Timber.d("Widget update process completed for widget ID: $appWidgetId")
                }
            }
            // This custom action is received to update a specific widget.
            // It is triggered by the setRecurringAlarm method to refresh the widget's data periodically.
            ACTION_UPDATE_WIDGET -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Timber.d("Received ACTION_UPDATE_WIDGET for widget ID: $appWidgetId")
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                Timber.d("ACTION_APPWIDGET_DELETED received")
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Timber.d("Deleting widget with ID: $appWidgetId")
                    widgetPreferences.deleteDeckPickerWidgetData(appWidgetId)
                } else {
                    Timber.e("Invalid widget ID received in ACTION_APPWIDGET_DELETED")
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> {
                Timber.d("Widget enabled")
            }
            AppWidgetManager.ACTION_APPWIDGET_DISABLED -> {
                Timber.d("Widget disabled")
            }
            else -> {
                Timber.e("Unexpected action received: ${intent.action}")
                CrashReportService.sendExceptionReport(
                    Exception("Unexpected action received: ${intent.action}"),
                    "DeckPickerWidget - onReceive",
                    null,
                    onlyIfSilent = true
                )
            }
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        if (context == null) {
            Timber.e("Context is null in onDeleted")
            return
        }

        val widgetPreferences = WidgetPreferences(context)

        appWidgetIds?.forEach { widgetId ->
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
