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
import com.ichi2.anki.IntentHandler.Companion.intentToReviewDeckFromShorcuts
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Decks.Companion.NOT_FOUND_DECK_ID
import com.ichi2.widget.ACTION_UPDATE_WIDGET
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.cancelRecurringAlarm
import com.ichi2.widget.deckpicker.DeckWidgetData
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
         * to the provided deck ID. If the deck is deleted, the widget will be show a message "Missing deck. Please reconfigure".
         *
         * @param context the context of the application
         * @param appWidgetManager the AppWidgetManager instance
         * @param appWidgetId the ID of the app widget
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val deckId = getDeckIdForWidget(context, appWidgetId)
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_card_analysis)

            if (deckId == NOT_FOUND_DECK_ID) {
                // If deckId is null, it means no deck was selected or the selected deck was deleted.
                // In this case, we don't save the null value to preferences because we want to
                // keep the previous deck ID if the user reconfigures the widget later.
                // Instead, we show a message prompting the user to reconfigure the widget.
                showMissingDeck(context, appWidgetManager, appWidgetId, remoteViews)
                return
            }

            AnkiDroidApp.applicationScope.launch {
                val isCollectionEmpty = isCollectionEmpty()
                if (isCollectionEmpty) {
                    showCollectionDeck(context, appWidgetManager, appWidgetId, remoteViews)
                    return@launch
                }

                val deckData = getDeckNameAndStats(deckId)
                if (deckData == null) {
                    // The deck was found but no data could be fetched, so update the preferences to remove the deck.
                    // This ensures that the widget does not retain a reference to a non-existent or invalid deck.
                    CardAnalysisWidgetPreferences(context).saveSelectedDeck(appWidgetId, NOT_FOUND_DECK_ID)
                    showMissingDeck(context, appWidgetManager, appWidgetId, remoteViews)
                    return@launch
                }
                showDeck(context, appWidgetManager, appWidgetId, remoteViews, deckData)
            }
        }

        private fun getDeckIdForWidget(context: Context, appWidgetId: Int): DeckId {
            val widgetPreferences = CardAnalysisWidgetPreferences(context)
            return widgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId) ?: NOT_FOUND_DECK_ID
        }

        private fun showCollectionDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            remoteViews: RemoteViews
        ) {
            remoteViews.setTextViewText(R.id.empty_widget, context.getString(R.string.empty_collection_state_in_widget))
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
        }

        private fun showMissingDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            remoteViews: RemoteViews
        ) {
            // Show empty_widget and set click listener to open configuration
            remoteViews.setTextViewText(R.id.empty_widget, context.getString(R.string.empty_widget_state))
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
        }

        private fun showDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            remoteViews: RemoteViews,
            deckData: DeckWidgetData
        ) {
            remoteViews.setTextViewText(R.id.deckNameCardAnalysis, deckData.name)
            remoteViews.setTextViewText(R.id.deckNew_card_analysis_widget, deckData.newCount.toString())
            remoteViews.setTextViewText(R.id.deckDue_card_analysis_widget, deckData.reviewCount.toString())
            remoteViews.setTextViewText(R.id.deckLearn_card_analysis_widget, deckData.learnCount.toString())

            // Hide empty_widget and show the actual widget content
            remoteViews.setViewVisibility(R.id.empty_widget, View.GONE)
            remoteViews.setViewVisibility(R.id.cardAnalysisDataHolder, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.deckNameCardAnalysis, View.VISIBLE)

            val isEmptyDeck = deckData.newCount == 0 && deckData.reviewCount == 0 && deckData.learnCount == 0

            val intent = if (!isEmptyDeck) {
                intentToReviewDeckFromShorcuts(context, deckData.deckId)
            } else {
                DeckOptions.getIntent(context, deckData.deckId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                deckData.deckId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(R.id.deckNameCardAnalysis, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        /**
         * Updates the Card Analysis Widgets based on the current state of the application.
         * It fetches the App Widget IDs and updates each widget with the associated deck ID.
         */
        fun updateCardAnalysisWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            val provider = ComponentName(context, CardAnalysisWidget::class.java)
            Timber.d("Fetching appWidgetIds for provider: ${provider.shortClassName}")

            val appWidgetIds = appWidgetManager.getAppWidgetIds(provider)
            Timber.d("AppWidgetIds to update: ${appWidgetIds.joinToString(", ")}")

            for (appWidgetId in appWidgetIds) {
                getDeckIdForWidget(context, appWidgetId)
                updateWidget(context, appWidgetManager, appWidgetId)
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

        for (widgetId in appWidgetIds) {
            Timber.d("Updating widget with ID: $widgetId")

            // Get the selected deck ID internally
            val selectedDeckId = getDeckIdForWidget(context, widgetId)

            /**
             * Explanation of behavior when selectedDeckId is empty
             * If selectedDeckId is empty, the widget will retain the previous deck.
             * This behavior ensures that the widget does not display an empty view, which could be
             * confusing to the user. Instead, it maintains the last known state until a new valid
             * deck ID is provided. This approach prioritizes providing a consistent
             * user experience over showing an empty or default state.
             */
            Timber.d("Selected deck ID: $selectedDeckId for widget ID: $widgetId")

            // Update the widget with the selected deck ID
            updateWidget(context, appWidgetManager, widgetId)
            // Set the recurring alarm for the widget
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

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Timber.d("Updating widget with ID: $appWidgetId")

                    // Update the widget using the internally fetched deck ID
                    updateWidget(context, appWidgetManager, appWidgetId)

                    Timber.d("Widget update process completed for widget ID: $appWidgetId")
                }
            }
            // Custom action to update a specific widget, triggered by the setRecurringAlarm method
            ACTION_UPDATE_WIDGET -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Timber.d("Received ACTION_UPDATE_WIDGET for widget ID: $appWidgetId")

                    // Update the widget using the internally fetched deck ID
                    updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                // TODO: #17151 not yet handled. Exists to stop ACRA errors
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
