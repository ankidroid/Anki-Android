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
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

data class DeckPickerWidgetData(
    val deckId: Long,
    val name: String,
    val reviewCount: Int,
    val learnCount: Int,
    val newCount: Int
)

suspend fun getDeckNameAndStats(deckIds: List<Long>): List<DeckPickerWidgetData> {
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

class DeckPickerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Timber.d("onUpdate")

        for (widgetId in appWidgetIds) {
            val selectedDeckIds = getSelectedDeckIdsFromPreferences(context, widgetId)
            if (selectedDeckIds.isNotEmpty()) {
                updateWidget(context, appWidgetManager, intArrayOf(widgetId), selectedDeckIds)
            }
            setRecurringAlarm(context, widgetId)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            val selectedDeckIds = intent.getLongArrayExtra("selected_deck_ids")

            if (appWidgetIds != null && selectedDeckIds != null) {
                updateWidget(context!!, appWidgetManager, appWidgetIds, selectedDeckIds)
            }
        } else if (intent?.action == "com.ichi2.widget.ACTION_UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val selectedDeckIds = getSelectedDeckIdsFromPreferences(context!!, appWidgetId)
                if (selectedDeckIds.isNotEmpty()) {
                    updateWidget(context, appWidgetManager, intArrayOf(appWidgetId), selectedDeckIds)
                }
            }
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds?.forEach { widgetId ->
            cancelRecurringAlarm(context!!, widgetId)
        }
    }

    companion object {

        @OptIn(DelicateCoroutinesApi::class)
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: IntArray,
            deckIds: LongArray
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_deck_picker_large)

            // Launch a coroutine to fetch deck stats
            GlobalScope.launch(Dispatchers.Main) {
                val deckData = getDeckNameAndStats(deckIds.toList())

                // Clear previous deck views
                remoteViews.removeAllViews(R.id.deckCollection)

                // Inflate deck views dynamically based on deckData
                for (deck in deckData) {
                    val deckView = RemoteViews(context.packageName, R.layout.widget_item_deck_main)

                    deckView.setTextViewText(R.id.deckName, deck.name)
                    deckView.setTextViewText(R.id.deckNew, deck.newCount.toString())
                    deckView.setTextViewText(R.id.deckDue, deck.reviewCount.toString())
                    deckView.setTextViewText(R.id.deckLearn, deck.learnCount.toString())

                    remoteViews.addView(R.id.deckCollection, deckView)
                }
                appWidgetManager.updateAppWidget(widgetId, remoteViews)
            }
        }

        private fun getSelectedDeckIdsFromPreferences(context: Context, appWidgetId: Int): LongArray {
            val sharedPreferences = context.getSharedPreferences("DeckPickerWidgetPrefs", Context.MODE_PRIVATE)
            val selectedDecks = sharedPreferences.getStringSet("selected_decks_$appWidgetId", emptySet())
            return selectedDecks?.map { it.toLong() }?.toLongArray() ?: longArrayOf()
        }

        private fun setRecurringAlarm(context: Context, appWidgetId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DeckPickerWidget::class.java).apply {
                action = "com.ichi2.widget.ACTION_UPDATE_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            // Set alarm to trigger every minute
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 60000,
                60000,
                pendingIntent
            )
        }

        private fun cancelRecurringAlarm(context: Context, appWidgetId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DeckPickerWidget::class.java).apply {
                action = "com.ichi2.widget.ACTION_UPDATE_WIDGET"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pendingIntent)
        }
    }
}
