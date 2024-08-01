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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This widget displays a decks with respective name, new, learning, and review card counts.
 * It updates every minute .
 * No user actions can be performed from this widget as of now; it is for display purposes only.
 */
class CardAnalysisExtraWidget : WidgetAlarm() {

    companion object {
        const val ACTION_APPWIDGET_UPDATE = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        const val ACTION_UPDATE_WIDGET = "com.ichi2.widget.ACTION_UPDATE_WIDGET"

        /**
         * Updates the widget with the deck data.
         *
         * @param context the context of the application
         * @param appWidgetManager the app widget manager
         * @param widgetId the array of widget IDs
         * @param deckIds the array of deck IDs
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: IntArray,
            deckIds: LongArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_card_analysis_extra)

            AnkiDroidApp.applicationScope.launch {
                val deckData = getDeckNameAndStats(deckIds.toList())

                if (deckData.isNotEmpty()) {
                    val deck = deckData[0]
                    remoteViews.setTextViewText(R.id.deckNameCardAnalysisExtra, deck.name)
                    remoteViews.setTextViewText(R.id.deckNew_card_analysis_extra_widget, deck.newCount.toString())
                    remoteViews.setTextViewText(R.id.deckDue_card_analysis_extra_widget, deck.reviewCount.toString())
                    remoteViews.setTextViewText(R.id.deckLearn_card_analysis_extra_widget, deck.learnCount.toString())
                }
                appWidgetManager.updateAppWidget(widgetId, remoteViews)
            }
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
            val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferencesCardAnalysisExtraData(widgetId)
            if (selectedDeckIds.isNotEmpty()) {
                updateWidget(context, appWidgetManager, intArrayOf(widgetId), selectedDeckIds)
            }
            setRecurringAlarm(context, widgetId, CardAnalysisExtraWidget::class.java)
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
            ACTION_APPWIDGET_UPDATE -> {
                Timber.i("Received ACTION_APPWIDGET_UPDATE")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                val selectedDeckIds = intent.getLongArrayExtra("card_analysis_extra_widget_selected_deck_ids")

                if (appWidgetIds != null && selectedDeckIds != null) {
                    Timber.d("Updating widget with appWidgetIds: ${appWidgetIds.joinToString()} and selectedDeckIds: ${selectedDeckIds.joinToString()}")
                    updateWidget(context, appWidgetManager, appWidgetIds, selectedDeckIds)
                }
            }
            ACTION_UPDATE_WIDGET -> {
                Timber.i("Received ACTION_UPDATE_WIDGET")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferencesCardAnalysisExtraData(appWidgetId)
                    if (selectedDeckIds.isNotEmpty()) {
                        Timber.d("Updating widget with appWidgetId: $appWidgetId and selectedDeckIds: ${selectedDeckIds.joinToString()}")
                        updateWidget(context, appWidgetManager, intArrayOf(appWidgetId), selectedDeckIds)
                    }
                }
            }
        }
    }

    /**
     * Triggers the cancel recurring alarm when the widget is deleted.
     *
     * @param context the context of the application
     * @param appWidgetIds the array of widget IDs being deleted
     */
    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds?.forEach { widgetId ->
            cancelRecurringAlarm(context!!, widgetId, CardAnalysisExtraWidget::class.java)
        }
    }
}
