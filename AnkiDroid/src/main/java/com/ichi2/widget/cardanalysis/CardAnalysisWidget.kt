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

package com.ichi2.widget.cardanalysis

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.widget.ACTION_UPDATE_WIDGET
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.cancelRecurringAlarm
import com.ichi2.widget.deckpicker.getDeckNameAndStats
import com.ichi2.widget.setRecurringAlarm
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This widget displays a deck with the respective new, learning, and review card counts.
 * It updates every minute and if there is any changes in study queues.
 * It allows user to open the reviewer directly by clicking on the deck same as deckpicker.
 * It can be configured and reconfigured by holding the widget.
 */
class CardAnalysisWidget : AnalyticsWidgetProvider() {

    companion object {

        /**
         * Key used for passing the selected deck ID in the intent extras.
         */
        const val EXTRA_SELECTED_DECK_ID = "card_analysis_widget_selected_deck_id"

        /**
         * Updates the widget with the deck data.
         *
         * This method updates the widget view content with the deck data corresponding
         * to the provided deck ID. If the deck is deleted, the widget will be cleared.
         *
         * @param context the context of the application
         * @param appWidgetManager the AppWidgetManager instance
         * @param appWidgetId the ID of the app widget
         * @param deckId the ID of the deck to be displayed in the widget.
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            deckId: LongArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_card_analysis)

            AnkiDroidApp.applicationScope.launch {
                val deckData = getDeckNameAndStats(deckId.toList())

                if (deckData.isEmpty()) {
                    // If the deck was deleted, clear the stored deck ID
                    val widgetPreferences = CardAnalysisWidgetPreferences(context)
                    val selectedDeck = longArrayOf().map { it.toString() }
                    widgetPreferences.saveSelectedDeck(appWidgetId, selectedDeck)

                    // Show empty_widget and set click listener to open configuration
                    remoteViews.setViewVisibility(R.id.empty_widget, View.VISIBLE)
                    remoteViews.setViewVisibility(R.id.cardAnalysisDataHolder, View.GONE)
                    remoteViews.setViewVisibility(R.id.deckNameCardAnalysis, View.GONE)

                    val configIntent = Intent(context, CardAnalysisWidgetConfig::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val configPendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        configIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    remoteViews.setOnClickPendingIntent(R.id.empty_widget, configPendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                    return@launch
                }

                val deck = deckData[0]
                remoteViews.setTextViewText(R.id.deckNameCardAnalysis, deck.name)
                remoteViews.setTextViewText(R.id.deckNew_card_analysis_widget, deck.newCount.toString())
                remoteViews.setTextViewText(R.id.deckDue_card_analysis_widget, deck.reviewCount.toString())
                remoteViews.setTextViewText(R.id.deckLearn_card_analysis_widget, deck.learnCount.toString())

                // Hide empty_widget and show the actual widget content
                remoteViews.setViewVisibility(R.id.empty_widget, View.GONE)
                remoteViews.setViewVisibility(R.id.cardAnalysisDataHolder, View.VISIBLE)
                remoteViews.setViewVisibility(R.id.deckNameCardAnalysis, View.VISIBLE)

                val isEmptyDeck = deck.newCount == 0 && deck.reviewCount == 0 && deck.learnCount == 0

                val intent = if (!isEmptyDeck) {
                    Intent(context, Reviewer::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra("deckId", deck.deckId)
                    }
                } else {
                    DeckOptions.getIntent(context, deck.deckId)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    deck.deckId.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                remoteViews.setOnClickPendingIntent(R.id.deckNameCardAnalysis, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        }

        /**
         * Updates the Card Analysis Widgets based on the current state of the application.
         * It fetches the App Widget IDs and updates each widget with the associated deck ID.
         */
        fun updateCardAnalysisWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            val provider = ComponentName(context, CardAnalysisWidget::class.java)
            Timber.d("Fetching appWidgetIds for provider: $provider")

            val appWidgetIds = appWidgetManager.getAppWidgetIds(provider)
            Timber.d("AppWidgetIds to update: ${appWidgetIds.joinToString(", ")}")

            for (appWidgetId in appWidgetIds) {
                val widgetPreferences = CardAnalysisWidgetPreferences(context)
                val deckId = widgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId)
                updateWidget(context, appWidgetManager, appWidgetId, deckId)
            }
        }
    }

    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        usageAnalytics: UsageAnalytics
    ) {
        Timber.d("Performing widget update for appWidgetIds: %s", appWidgetIds)

        val widgetPreferences = CardAnalysisWidgetPreferences(context)

        for (widgetId in appWidgetIds) {
            Timber.d("Updating widget with ID: $widgetId")
            val selectedDeckId = widgetPreferences.getSelectedDeckIdFromPreferences(widgetId)

            /**Explanation of behavior when selectedDeckId is empty
             * If selectedDeckId is empty, the widget will retain the previous deck.
             * This behavior ensures that the widget does not display an empty view, which could be
             * confusing to the user. Instead, it maintains the last known state until a new valid
             * deck ID is provided. This approach prioritizes providing a consistent
             * user experience over showing an empty or default state.
             */
            Timber.d("Selected deck ID: $selectedDeckId for widget ID: $widgetId")
            updateWidget(context, appWidgetManager, widgetId, selectedDeckId)
            setRecurringAlarm(context, widgetId, CardAnalysisWidget::class.java)
        }

        Timber.d("Widget update process completed for appWidgetIds: ${appWidgetIds.joinToString(", ")}")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Timber.e("Context or intent is null in onReceive")
            return
        }
        super.onReceive(context, intent)

        val widgetPreferences = CardAnalysisWidgetPreferences(context)

        when (intent.action) {
            ACTION_APPWIDGET_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)

                // Retrieve the widget ID from the intent
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val selectedDeckId = intent.getLongExtra(EXTRA_SELECTED_DECK_ID, -1L)

                Timber.d("Received ACTION_APPWIDGET_UPDATE with widget ID: $appWidgetId and selectedDeckId: $selectedDeckId")

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && selectedDeckId != -1L) {
                    Timber.d("Updating widget with ID: $appWidgetId")
                    // Wrap selectedDeckId into a LongArray
                    updateWidget(context, appWidgetManager, appWidgetId, longArrayOf(selectedDeckId))
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
                    cancelRecurringAlarm(context, appWidgetId, CardAnalysisWidget::class.java)
                    widgetPreferences.deleteDeckData(appWidgetId)
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
                    "CardAnalysisWidget - onReceive",
                    null,
                    onlyIfSilent = true
                )
            }
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        if (context == null) {
            Timber.w("Context is null in onDeleted")
            return
        }

        val widgetPreferences = CardAnalysisWidgetPreferences(context)

        appWidgetIds?.forEach { widgetId ->
            cancelRecurringAlarm(context, widgetId, CardAnalysisWidget::class.java)
            widgetPreferences.deleteDeckData(widgetId)
        }
    }
}
